package com.Photocator.shoot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.KeyEvent
import com.Photocator.ReviewSource
import com.Photocator.Screen
import com.Photocator.Session
import com.Photocator.core.FlashKind
import com.Photocator.core.ShootMode
import com.Photocator.core.TagData
import com.Photocator.stamp.Placement
import com.Photocator.stamp.Stamp
import com.Photocator.stamp.StampInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Everything the camera screen does: modes, zoom, capture pipeline, recording, volume keys. */
class ShootModel(private val s: Session) {
    val mode = MutableStateFlow(ShootMode.PHOTO)
    val front = MutableStateFlow(false)
    val zoom = MutableStateFlow(1f)
    val dialOpen = MutableStateFlow(false)
    val panelOpen = MutableStateFlow(false)
    val countdown = MutableStateFlow<Int?>(null)
    val busy = MutableStateFlow(false)               // capturing or saving
    val recording = MutableStateFlow(false)
    val recordSecs = MutableStateFlow(0)
    val holdSteady = MutableStateFlow(false)         // night shot being processed
    /** Bumped on every dial / quick-option interaction so the dial's 3 s auto-close restarts. */
    val dialTouch = MutableStateFlow(0)

    /** Set by the activity: asks for the microphone permission once. */
    var requestMicrophone: (() -> Unit)? = null

    private var lastPress = 0L
    private var lastVolume = 0L
    private var stampJob: Job? = null
    private var timerJob: Job? = null

    val zoomState: StateFlow<Float> get() = zoom

    fun setMode(m: ShootMode) {
        if (recording.value) return
        mode.value = m
        if (m == ShootMode.VIDEO && !s.hasMicrophone()) requestMicrophone?.invoke()
    }

    fun flip() {
        if (recording.value) return
        front.value = !front.value
        zoom.value = 1f
    }

    fun cycleFlash() {
        s.prefs.update { it.copy(flash = when (it.flash) { FlashKind.OFF -> FlashKind.ON; FlashKind.ON -> FlashKind.TORCH; FlashKind.TORCH -> FlashKind.OFF }) }
        s.rig.setFlash(s.prefs.value.flash, mode.value)
    }

    fun setZoom(z: Float) {
        val v = z.coerceIn(1f, s.rig.maxZoom.value)
        zoom.value = v
        s.rig.setZoom(v)
    }

    fun toggleDial() { dialOpen.value = !dialOpen.value; if (dialOpen.value) touchDial() }
    fun touchDial() { dialTouch.value = dialTouch.value + 1 }

    // ------------------------------------------------------------------ shutter

    /** The shutter button / volume key: photo capture (with timer) or recording toggle. */
    fun onShutter() {
        if (mode.value == ShootMode.VIDEO) { toggleRecording(); return }
        requestPhoto()
    }

    /** Tap on the viewfinder with "touch to capture": only in Photo mode. */
    fun onTapCapture() { if (mode.value == ShootMode.PHOTO) requestPhoto() }

    private fun requestPhoto() {
        if (busy.value) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPress < 300) return
        lastPress = now
        val shotMode = mode.value
        s.scope.launch {
            val t = s.prefs.value.timerSec
            if (t > 0) {
                for (n in t downTo 1) { countdown.value = n; delay(1000) }
                countdown.value = null
            }
            busy.value = true
            try {
                capture(shotMode)
            } catch (_: Exception) {
                s.showAlert("Failed to capture photo. Please try again.")
            } finally {
                busy.value = false
                holdSteady.value = false
            }
        }
    }

    private suspend fun capture(shotMode: ShootMode) {
        val p = s.prefs.value
        val tilt = s.tilt.tilt.value
        if (p.shutterSound) s.rig.clickSound()
        val tmp = File(s.app.cacheDir, "shot_${System.currentTimeMillis()}.jpg")
        if (shotMode == ShootMode.NIGHT) holdSteady.value = true
        val ok = s.rig.takePhoto(tmp, tilt)
        if (!ok) throw IllegalStateException("capture failed")
        val boost = shotMode == ShootMode.NIGHT && !s.rig.nightExtension.value
        val processed = withContext(Dispatchers.IO) { PhotoPost.process(s.app, tmp, p.quality, boost) }
        if (!processed) throw IllegalStateException("post-processing failed")
        holdSteady.value = false
        if (p.tagEnabled) {
            s.push(Screen.Review(ReviewSource.Captured(tmp)))
        } else {
            if (s.savePhotoFile(tmp)) s.toast("Photo saved") else s.toast("Failed to save")
        }
    }

    // ------------------------------------------------------------------ video

    private fun toggleRecording() {
        if (s.rig.isRecording) {
            s.rig.stopRecording()
            busy.value = true          // spinner while the clip is finalised and saved
            return
        }
        if (busy.value) return
        val tmp = File(s.app.cacheDir, "clip_${System.currentTimeMillis()}.mp4")
        val tilt = s.tilt.tilt.value
        startStampUpdates(tilt)
        val started = s.rig.startRecording(tmp, s.hasMicrophone(), tilt) { ok -> onRecordingFinished(tmp, ok) }
        if (!started) {
            stopStampUpdates()
            s.showAlert("Could not start recording.")
            return
        }
        recording.value = true
        recordSecs.value = 0
        timerJob?.cancel()
        timerJob = s.scope.launch { while (true) { delay(1000); recordSecs.value = recordSecs.value + 1 } }
    }

    private fun onRecordingFinished(tmp: File, ok: Boolean) {
        recording.value = false
        timerJob?.cancel()
        stopStampUpdates()
        if (!ok) {
            s.showAlert("Recording failed or was too short.")
            tmp.delete()
            busy.value = false
            return
        }
        busy.value = true
        s.scope.launch {
            val uri = withContext(Dispatchers.IO) { s.album.saveVideo(tmp) }
            withContext(Dispatchers.IO) { if (uri != null) tmp.delete() }
            if (uri != null) s.toast("Video saved") else s.toast("Failed to save")
            busy.value = false
        }
    }

    // ------------------------------------------------------------------ geotag burned into video

    private fun startStampUpdates(tilt: Int) {
        stampJob?.cancel()
        val fallbackBase = s.withSensorFallback(TagData(0.0, 0.0))
        val wmBitmap = s.marks.load(s.marks.find(s.prefs.value.watermarkId))
        stampJob = s.scope.launch {
            combine(s.pos.live, s.prefs.flow) { live, prefs -> live to prefs }.collect { (live, prefs) ->
                val d = live.data
                if (!prefs.tagEnabled || d == null) { s.rig.videoStamp = null; return@collect }
                val land = tilt != 0
                val iw = if (land) 1920 else 1080
                val ih = if (land) 1080 else 1920
                val stamp = withContext(Dispatchers.Default) {
                    val data = d.copy(
                        magneticUt = d.magneticUt ?: fallbackBase.magneticUt,
                        headingDeg = d.headingDeg ?: fallbackBase.headingDeg,
                    )
                    val input = StampInput(data, live.thumb, wmBitmap, prefs.showPlus, prefs.sensorData)
                    val pw = Placement.panelWidth(iw, ih, prefs.tagWidth)
                    val plan = Stamp.plan(input, pw, prefs.tagStretch)
                    val bmp = Bitmap.createBitmap(plan.widthPx, plan.heightPx, Bitmap.Config.ARGB_8888)
                    Stamp.draw(Canvas(bmp), plan, 0f, 0f)
                    VideoStamp(
                        bmp, iw, ih,
                        Placement.left(iw, plan.widthPx, prefs.tagX),
                        Placement.top(ih, plan.heightPx, prefs.tagY, Placement.lift(iw, ih), null),
                    )
                }
                s.rig.videoStamp = stamp
            }
        }
    }

    private fun stopStampUpdates() {
        stampJob?.cancel()
        stampJob = null
        s.rig.videoStamp = null
    }

    // ------------------------------------------------------------------ volume keys

    /** Returns true when the key event was consumed. Only called while the camera is the visible screen. */
    fun onVolumeKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        return when (s.prefs.value.volumeAction) {
            com.Photocator.core.VolumeAction.SHUTTER -> {
                if (down && event.repeatCount == 0) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastVolume >= 800) { lastVolume = now; onShutter() }
                }
                true
            }
            com.Photocator.core.VolumeAction.ZOOM -> {
                if (down) {
                    val step = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 0.5f else -0.5f
                    setZoom(zoom.value + step)
                }
                true
            }
            // Off: the system handles the key press; the release is still swallowed
            com.Photocator.core.VolumeAction.OFF -> !down
        }
    }
}
