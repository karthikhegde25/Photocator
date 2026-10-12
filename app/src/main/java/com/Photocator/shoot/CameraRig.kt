package com.Photocator.shoot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.media.MediaActionSound
import android.os.Handler
import android.os.HandlerThread
import android.util.Rational
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.effects.OverlayEffect
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.Photocator.core.FlashKind
import com.Photocator.core.ShootMode
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How the camera should be bound. */
data class BindSpec(
    val front: Boolean,
    val mode: ShootMode,
    val wideStream: Boolean,          // 16:9 sensor stream instead of 4:3
    val viewW: Int,
    val viewH: Int,
    val flash: FlashKind,
)

/** A pre-rendered geotag panel for the video overlay, positioned in upright frame coordinates of a nominal frame. */
class VideoStamp(val bitmap: Bitmap, val nominalW: Int, val nominalH: Int, val left: Float, val top: Float)

private suspend fun <T> ListenableFuture<T>.await(ctx: Context): T = suspendCancellableCoroutine { c ->
    addListener({
        try { c.resume(get()) } catch (e: Exception) { c.resumeWithException(e) }
    }, ContextCompat.getMainExecutor(ctx))
}

/** CameraX wrapper: binding, zoom, focus, photo capture, video recording with an overlay effect. */
@SuppressLint("MissingPermission")
class CameraRig(private val ctx: Context) {
    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var overlay: OverlayEffect? = null
    private val overlayThread = HandlerThread("photocator-overlay").apply { start() }
    private val sound = MediaActionSound().apply {
        load(MediaActionSound.SHUTTER_CLICK); load(MediaActionSound.START_VIDEO_RECORDING); load(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    private val _maxZoom = MutableStateFlow(1f)
    val maxZoom: StateFlow<Float> get() = _maxZoom
    private val _nightExtension = MutableStateFlow(false)
    val nightExtension: StateFlow<Boolean> get() = _nightExtension
    private val _unavailable = MutableStateFlow(false)
    val unavailable: StateFlow<Boolean> get() = _unavailable
    private val _frontMissing = MutableStateFlow(false)
    val frontMissing: StateFlow<Boolean> get() = _frontMissing
    private val _providerReady = MutableStateFlow(false)
    val providerReady: StateFlow<Boolean> get() = _providerReady

    @Volatile var videoStamp: VideoStamp? = null
    @Volatile private var videoUprightRotation = 0
    @Volatile private var recordingTilt = 0

    suspend fun init() {
        if (provider != null) return
        try {
            val p = ProcessCameraProvider.getInstance(ctx).await(ctx)
            provider = p
            extensions = try { ExtensionsManager.getInstanceAsync(ctx, p).await(ctx) } catch (_: Exception) { null }
            if (!p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) && !p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                _unavailable.value = true
            }
            _providerReady.value = true
        } catch (_: Exception) {
            _unavailable.value = true
            _providerReady.value = true
        }
    }

    fun rotationFor(tilt: Int): Int = when (tilt) {
        1 -> Surface.ROTATION_270
        -1 -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }

    /** Binds the use cases for [spec]. Returns false if nothing could be bound. */
    fun bind(owner: LifecycleOwner, view: PreviewView, spec: BindSpec): Boolean {
        val p = provider ?: return false
        if (spec.viewW <= 0 || spec.viewH <= 0) return false
        stopRecordingQuietly()
        p.unbindAll()
        camera = null; imageCapture = null; videoCapture = null
        overlay?.close(); overlay = null
        _nightExtension.value = false

        var useFront = spec.front
        if (useFront && !p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) { useFront = false; _frontMissing.value = true }
        val base = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (!p.hasCamera(base)) { _unavailable.value = true; return false }

        val ratio = if (spec.wideStream) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
        val ratioStrategy = AspectRatioStrategy(ratio, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        val previewRs = ResolutionSelector.Builder().setAspectRatioStrategy(ratioStrategy).build()
        val captureRs = ResolutionSelector.Builder()
            .setAspectRatioStrategy(ratioStrategy)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()

        fun makeGroup(selector: CameraSelector): Pair<UseCaseGroup, Preview> {
            val preview = Preview.Builder().setResolutionSelector(previewRs).build()
            preview.setSurfaceProvider(view.surfaceProvider)
            val group = UseCaseGroup.Builder().addUseCase(preview)
            if (spec.mode == ShootMode.VIDEO) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD), FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                    .build()
                val vc = VideoCapture.withOutput(recorder)
                videoCapture = vc
                group.addUseCase(vc)
                val fx = OverlayEffect(CameraEffect.VIDEO_CAPTURE, 0, Handler(overlayThread.looper)) { }
                fx.setOnDrawListener { frame -> drawStamp(frame); true }
                overlay = fx
                group.addEffect(fx)
            } else {
                val ic = ImageCapture.Builder()
                    .setResolutionSelector(captureRs)
                    .setCaptureMode(if (spec.mode == ShootMode.NIGHT) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(100)
                    .setFlashMode(if (spec.flash == FlashKind.ON && spec.mode == ShootMode.PHOTO) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()
                imageCapture = ic
                group.addUseCase(ic)
                group.setViewPort(ViewPort.Builder(Rational(spec.viewW, spec.viewH), Surface.ROTATION_0).setScaleType(ViewPort.FILL_CENTER).build())
            }
            return group.build() to preview
        }

        var selector = base
        var usedExtension = false
        if (spec.mode == ShootMode.NIGHT) {
            val em = extensions
            if (em != null && em.isExtensionAvailable(base, ExtensionMode.NIGHT)) {
                selector = em.getExtensionEnabledCameraSelector(base, ExtensionMode.NIGHT)
                usedExtension = true
            }
        }
        val cam: Camera = try {
            camera = null
            p.bindToLifecycle(owner, selector, makeGroup(selector).first)
        } catch (e: Exception) {
            if (usedExtension) {
                // the Night extension failed to bind: silently fall back to the software night mode
                usedExtension = false
                imageCapture = null; videoCapture = null
                try { p.unbindAll(); p.bindToLifecycle(owner, base, makeGroup(base).first) } catch (_: Exception) { _unavailable.value = true; return false }
            } else {
                _unavailable.value = true
                return false
            }
        }
        camera = cam
        _unavailable.value = false
        _nightExtension.value = usedExtension

        val zs = cam.cameraInfo.zoomState.value
        _maxZoom.value = min(10f, max(1f, zs?.maxZoomRatio ?: 1f))
        cam.cameraControl.setZoomRatio(1f)
        cam.cameraControl.enableTorch(spec.flash == FlashKind.TORCH)
        if (spec.mode == ShootMode.NIGHT && !usedExtension) {
            val st = cam.cameraInfo.exposureState
            if (st.isExposureCompensationSupported && st.exposureCompensationRange.upper > 0) {
                val idx = max(1, (0.75f * st.exposureCompensationRange.upper).roundToInt())
                cam.cameraControl.setExposureCompensationIndex(min(idx, st.exposureCompensationRange.upper))
            }
        }
        return true
    }

    fun setZoom(z: Float) { camera?.cameraControl?.setZoomRatio(z.coerceIn(1f, _maxZoom.value)) }
    fun setTorch(on: Boolean) { camera?.cameraControl?.enableTorch(on) }

    /** Applies a flash mode change without rebinding: "On" fires in photo mode only, torch stays lit in every mode. */
    fun setFlash(f: FlashKind, mode: ShootMode) {
        imageCapture?.flashMode = if (f == FlashKind.ON && mode == ShootMode.PHOTO) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        camera?.cameraControl?.enableTorch(f == FlashKind.TORCH)
    }

    fun focusAt(view: PreviewView, x: Float, y: Float) {
        val c = camera ?: return
        try {
            val pt = view.meteringPointFactory.createPoint(x, y)
            c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
        } catch (_: Exception) { }
    }

    /** Plays the shutter click. */
    fun clickSound() { sound.play(MediaActionSound.SHUTTER_CLICK) }

    /** Takes a JPEG into [dest] at the orientation of [tilt]. */
    suspend fun takePhoto(dest: File, tilt: Int): Boolean {
        val ic = imageCapture ?: return false
        ic.targetRotation = rotationFor(tilt)
        return suspendCancellableCoroutine { c ->
            ic.takePicture(
                ImageCapture.OutputFileOptions.Builder(dest).build(),
                ContextCompat.getMainExecutor(ctx),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(r: ImageCapture.OutputFileResults) { if (c.isActive) c.resume(true) }
                    override fun onError(e: ImageCaptureException) { if (c.isActive) c.resume(false) }
                },
            )
        }
    }

    // ------------------------------------------------------------------ video

    val isRecording: Boolean get() = recording != null

    /**
     * Starts recording into [dest]. [onDone] gets true when a valid file exists. The orientation is locked now.
     * Returns false if recording could not start.
     */
    fun startRecording(dest: File, withAudio: Boolean, tilt: Int, onDone: (Boolean) -> Unit): Boolean {
        val vc = videoCapture ?: return false
        if (recording != null) return false
        return try {
            recordingTilt = tilt
            vc.targetRotation = rotationFor(tilt)
            videoUprightRotation = camera?.cameraInfo?.getSensorRotationDegrees(rotationFor(tilt)) ?: 90
            var pending = vc.output.prepareRecording(ctx, FileOutputOptions.Builder(dest).build())
            if (withAudio) pending = pending.withAudioEnabled()
            sound.play(MediaActionSound.START_VIDEO_RECORDING)
            recording = pending.start(ContextCompat.getMainExecutor(ctx)) { ev ->
                if (ev is VideoRecordEvent.Finalize) {
                    recording = null
                    sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                    onDone(!ev.hasError() && dest.exists() && dest.length() > 0)
                }
            }
            true
        } catch (_: Exception) {
            recording = null
            false
        }
    }

    fun stopRecording() { recording?.stop() }

    private fun stopRecordingQuietly() { try { recording?.stop() } catch (_: Exception) { }; recording = null }

    /** Orientation of the frame the recorder will treat as upright: true if it is landscape. */
    val lockedTilt: Int get() = recordingTilt

    private fun drawStamp(frame: androidx.camera.effects.Frame) {
        val canvas = frame.overlayCanvas
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        val s = videoStamp ?: return
        val size = frame.size
        val bw = size.width
        val bh = size.height
        val rot = videoUprightRotation
        val uw = if (rot % 180 == 90) bh else bw
        val k = uw.toFloat() / s.nominalW
        // map upright coordinates to buffer coordinates
        val m = Matrix()
        when (rot) {
            90 -> m.setValues(floatArrayOf(0f, 1f, 0f, -1f, 0f, bh.toFloat(), 0f, 0f, 1f))
            180 -> m.setValues(floatArrayOf(-1f, 0f, bw.toFloat(), 0f, -1f, bh.toFloat(), 0f, 0f, 1f))
            270 -> m.setValues(floatArrayOf(0f, -1f, bw.toFloat(), 1f, 0f, 0f, 0f, 0f, 1f))
        }
        canvas.save()
        canvas.concat(m)
        canvas.translate(s.left * k, s.top * k)
        canvas.scale(k, k)
        canvas.drawBitmap(s.bitmap, 0f, 0f, null)
        canvas.restore()
    }

    fun release() {
        stopRecordingQuietly()
        overlay?.close()
        try { provider?.unbindAll() } catch (_: Exception) { }
        sound.release()
        overlayThread.quitSafely()
    }
}
