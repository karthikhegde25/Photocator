package com.Photocator

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.Photocator.core.PrefsStore
import com.Photocator.core.TagData
import com.Photocator.core.WatermarkStore
import com.Photocator.media.Album
import com.Photocator.net.TileStore
import com.Photocator.sense.Positioning
import com.Photocator.sense.SensorHub
import com.Photocator.sense.TiltTracker
import com.Photocator.shoot.CameraRig
import com.Photocator.shoot.ShootModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/** Where a review (preview / add-geotag) screen gets its photo from. */
sealed interface ReviewSource {
    /** Photo just taken: a temporary file; nothing is saved until the user confirms. */
    data class Captured(val file: File) : ReviewSource
    /** Photo picked from the gallery: no geotag exists yet. */
    data class Imported(val uri: Uri) : ReviewSource
}

/** Screens stacked above the camera. */
sealed interface Screen {
    data object Settings : Screen
    data object About : Screen
    data object Watermarks : Screen
    data object TagEditor : Screen
    data object Videos : Screen
    data object Photos : Screen
    data class PhotoView(val uris: List<Uri>, val index: Int) : Screen
    data class Player(val uris: List<Uri>, val index: Int) : Screen
    data class Review(val source: ReviewSource) : Screen
}

data class Perms(val camera: Boolean = false, val location: Boolean = false, val media: Boolean = false) {
    val all: Boolean get() = camera && location && media
}

data class AlertMsg(val message: String, val title: String? = null)

/** Process-wide state holder shared by every screen. */
class Session(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val prefs = PrefsStore(app)
    val marks = WatermarkStore(app)
    val album = Album(app)
    val tiles = TileStore(app)
    val sensors = SensorHub(app)
    val tilt = TiltTracker(app)
    val pos = Positioning(app, scope, prefs, tiles, sensors)
    val rig = CameraRig(app)
    val shoot = ShootModel(this)

    // -------------------------------------------------------------- navigation
    private val _stack = MutableStateFlow<List<Screen>>(emptyList())
    val stack: StateFlow<List<Screen>> get() = _stack
    fun push(s: Screen) { _stack.value = _stack.value + s }
    fun pop(): Boolean {
        val cur = _stack.value
        if (cur.isEmpty()) return false
        _stack.value = cur.dropLast(1)
        return true
    }
    fun popTo(count: Int) { _stack.value = _stack.value.take(count) }

    // -------------------------------------------------------------- messages
    private val _alert = MutableStateFlow<AlertMsg?>(null)
    val alert: StateFlow<AlertMsg?> get() = _alert
    fun showAlert(message: String, title: String? = null) { _alert.value = AlertMsg(message, title) }
    fun dismissAlert() { _alert.value = null }
    fun toast(msg: String) { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }

    // -------------------------------------------------------------- permissions
    private val _perms = MutableStateFlow(readPerms())
    val perms: StateFlow<Perms> get() = _perms
    val requesting = MutableStateFlow(false)
    var requestedOnce = false

    private fun granted(p: String) = app.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun hasMicrophone() = granted(Manifest.permission.RECORD_AUDIO)

    private fun readPerms(): Perms {
        val media = when {
            Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_IMAGES)
            Build.VERSION.SDK_INT >= 29 -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE) && granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return Perms(
            camera = granted(Manifest.permission.CAMERA),
            location = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            media = media,
        )
    }

    /** Re-reads permission state; starts thumbnail lookup / live location when something became available. */
    fun refreshPerms() {
        val before = _perms.value
        val now = readPerms()
        _perms.value = now
        if (now.media && !before.media) loadLatestThumb()
        applyLiveState()
    }

    /** The permissions the sequence requests, in order: camera, media, location. */
    fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    // -------------------------------------------------------------- live geotag on/off
    /** Live location runs while the geotag is enabled and location permission exists. */
    fun applyLiveState() {
        val want = prefs.value.tagEnabled && _perms.value.location
        if (want) pos.start() else pos.stop()
    }

    /** Manual "get my location again". */
    fun refreshLocation() {
        when {
            !_perms.value.location -> showAlert("Location permission is off. Turn it on in the app settings.")
            !prefs.value.tagEnabled -> showAlert("The geotag is turned off, so there is no location to refresh.")
            else -> {
                toast("Refreshing location...")
                pos.refresh()
            }
        }
    }

    // -------------------------------------------------------------- thumbnail of last photo
    val thumbUri = MutableStateFlow<Uri?>(null)
    val thumbVersion = MutableStateFlow(0)

    fun loadLatestThumb() {
        scope.launch {
            val u = withContext(Dispatchers.IO) { album.latestPhoto() }
            thumbUri.value = u
            thumbVersion.value = thumbVersion.value + 1
        }
    }

    fun noteSavedPhoto(uri: Uri) {
        thumbUri.value = uri
        thumbVersion.value = thumbVersion.value + 1
    }

    /** Saves a finished JPEG to the album. Deletes the temp file only on success. */
    suspend fun savePhotoFile(f: File): Boolean {
        val u = withContext(Dispatchers.IO) { album.savePhoto(f) }
        if (u == null) return false
        noteSavedPhoto(u)
        withContext(Dispatchers.IO) { f.delete() }
        return true
    }

    // -------------------------------------------------------------- sensor fallback
    /** Heading and magnetic field never disappear: keep the previous value, else a plausible random one. */
    fun withSensorFallback(d: TagData, previous: TagData? = null): TagData {
        val mag = d.magneticUt ?: previous?.magneticUt ?: (Random.nextInt(280, 601) / 10.0)
        val head = d.headingDeg ?: previous?.headingDeg ?: Random.nextInt(0, 360).toDouble()
        return d.copy(magneticUt = mag, headingDeg = head)
    }
}
