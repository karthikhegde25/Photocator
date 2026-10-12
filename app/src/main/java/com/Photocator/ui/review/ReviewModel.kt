package com.Photocator.ui.review

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.Photocator.ReviewSource
import com.Photocator.Session
import com.Photocator.core.MapStyles
import com.Photocator.core.PlusCode
import com.Photocator.core.TagData
import com.Photocator.core.TagPlacement
import com.Photocator.media.Pics
import com.Photocator.net.Web
import com.Photocator.stamp.StampInput
import com.Photocator.stamp.StampRender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** State and actions of the Preview (after capture) and Add Geotag (gallery photo) screens. */
class ReviewModel(private val s: Session, val source: ReviewSource, private val scope: CoroutineScope) {
    private val src: Any = when (source) {
        is ReviewSource.Captured -> source.file
        is ReviewSource.Imported -> source.uri
    }
    val isCapture: Boolean = source is ReviewSource.Captured

    var photo by mutableStateOf<Bitmap?>(null)         // display copy (<= ~4096 px)
    var imgW by mutableStateOf(0)
    var imgH by mutableStateOf(0)
    var data by mutableStateOf<TagData?>(null)
    var thumb by mutableStateOf<Bitmap?>(null)
    var loading by mutableStateOf(true)                // geotag data not ready yet
    var error by mutableStateOf<String?>(null)
    var placement by mutableStateOf(TagPlacement.ON_PHOTO)
    var dragFraction by mutableStateOf<Float?>(null)   // per-photo vertical position chosen in the editor
    var watermarkId by mutableStateOf(s.prefs.value.watermarkId)   // per-photo choice, not persisted
    var saving by mutableStateOf(false)
    var showEdit by mutableStateOf(false)
    var showPosition by mutableStateOf(false)
    var showMap by mutableStateOf(false)
    var locating by mutableStateOf(false)              // add-flow "use current location" running
    var fromBlank by mutableStateOf(false)             // geotag came from the blank manual template
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val dims = withContext(Dispatchers.IO) { Pics.size(s.app, src) }
            if (dims != null) { imgW = dims.first; imgH = dims.second }
            photo = withContext(Dispatchers.IO) { Pics.decode(s.app, src, 4096) }
        }
        if (isCapture) loadCaptureData() else loading = false
    }

    /** True once a geotag exists and the display photo has decoded. */
    val ready: Boolean get() = data != null && photo != null

    // ------------------------------------------------------------ data pipelines

    private fun loadCaptureData() {
        scope.launch {
            loading = true
            try {
                val first = s.pos.live.value.data
                var tb = s.pos.live.value.thumb
                if (first != null) {
                    var d: TagData = first
                    if (d.address == "fetching address..." || d.placeName == "locating...") {
                        val lat = d.lat
                        val lon = d.lon
                        val (r, w) = coroutineScope {
                            val a = async { Web.reverse(lat, lon) }
                            val b = async { Web.weather(lat, lon) }
                            a.await() to b.await()
                        }
                        d = d.copy(
                            placeName = r.placeName, address = r.address,
                            tempC = w.tempC, condition = w.condition, humidity = w.humidity, windKmh = w.windKmh,
                        )
                    }
                    if (tb == null) tb = buildThumb(d.lat, d.lon)
                    data = s.withSensorFallback(d)
                    thumb = tb
                } else {
                    val loc = s.pos.oneShot(15_000)
                    if (loc == null) {
                        error = "Could not get your location. Make sure Location is on, then try again."
                    } else {
                        val built = fullPipeline(loc.latitude, loc.longitude, if (loc.hasAltitude()) loc.altitude else null, if (loc.hasSpeed()) loc.speed.toDouble() else null)
                        data = s.withSensorFallback(built)
                        thumb = buildThumb(built.lat, built.lon)
                    }
                }
            } catch (_: Exception) {
                error = "Error loading GPS data"
            } finally {
                loading = false
            }
        }
    }

    private suspend fun buildThumb(lat: Double, lon: Double): Bitmap? {
        val p = s.prefs.value
        return s.tiles.thumbnail(lat, lon, MapStyles.byId(p.mapStyleId), p.mapZoom)
    }

    /** Address + weather + sensor snapshot for a coordinate. */
    private suspend fun fullPipeline(lat: Double, lon: Double, alt: Double?, speed: Double?): TagData = coroutineScope {
        val rev = async { Web.reverse(lat, lon) }
        val wx = async { Web.weather(lat, lon) }
        val r = rev.await(); val w = wx.await()
        TagData(
            lat = lat, lon = lon, altitude = alt, speed = speed,
            placeName = r.placeName, address = r.address, plusCode = PlusCode.encode(lat, lon),
            timeMs = System.currentTimeMillis(),
            tempC = w.tempC, condition = w.condition, humidity = w.humidity, windKmh = w.windKmh,
            magneticUt = s.sensors.magnetic.value, headingDeg = s.sensors.heading.value,
        )
    }

    /** Add Geotag button 1 / edit-sheet "Current location". */
    fun useCurrentLocation() {
        if (locating) return
        scope.launch {
            locating = true
            loading = true
            val sensorsWereOff = !(s.prefs.value.tagEnabled && s.perms.value.location)
            s.sensors.start()
            try {
                val loc = s.pos.oneShot(15_000)
                if (loc == null) {
                    s.showAlert("Location unavailable. Try picking a point on the map.")
                } else {
                    val built = fullPipeline(loc.latitude, loc.longitude, if (loc.hasAltitude()) loc.altitude else null, if (loc.hasSpeed()) loc.speed.toDouble() else null)
                    val prev = data
                    data = s.withSensorFallback(built, prev)
                    thumb = buildThumb(built.lat, built.lon)
                    fromBlank = false
                }
            } catch (_: Exception) {
                s.showAlert("Location unavailable. Try picking a point on the map.")
            } finally {
                if (sensorsWereOff) s.sensors.stop()
                locating = false
                loading = false
            }
        }
    }

    /** Add Geotag button 3: a blank template to fill in by hand. */
    fun startManual() {
        data = TagData(lat = 0.0, lon = 0.0, plusCode = PlusCode.encode(0.0, 0.0), timeMs = System.currentTimeMillis())
        thumb = null
        fromBlank = true
        showEdit = true
    }

    /** Map picker confirmed: re-geocode and refresh everything except date, magnetic field and heading. */
    fun applyPicked(lat: Double, lon: Double) {
        showMap = false
        showEdit = false
        scope.launch {
            loading = true
            try {
                val prev = data
                val built = fullPipeline(lat, lon, null, null)
                val merged = if (prev != null) built.copy(timeMs = prev.timeMs, magneticUt = prev.magneticUt ?: built.magneticUt, headingDeg = prev.headingDeg ?: built.headingDeg, note = "") else built
                data = s.withSensorFallback(merged, prev)
                thumb = buildThumb(lat, lon)
                fromBlank = false
            } finally {
                loading = false
            }
        }
    }

    /** Edit sheet "Apply changes". */
    fun applyEdit(edited: TagData, coordsChanged: Boolean) {
        data = edited
        showEdit = false
        if (coordsChanged || fromBlank) {
            scope.launch {
                thumb = buildThumb(edited.lat, edited.lon)
            }
        }
        fromBlank = false
    }

    // ------------------------------------------------------------ saving

    fun save(onDone: () -> Unit) {
        val d = data ?: return
        if (saving) return
        saving = true
        scope.launch {
            val ok = try {
                withContext(Dispatchers.Default) {
                    val full = Pics.decode(s.app, src, 16384) ?: return@withContext false
                    val p = s.prefs.value
                    val wm = s.marks.load(s.marks.find(watermarkId))
                    val input = StampInput(d, thumb, wm, p.showPlus, p.sensorData)
                    val out = StampRender.compose(
                        full, input, p.tagWidth, p.tagStretch, p.tagX, p.tagY,
                        below = placement == TagPlacement.BELOW_PHOTO, dragFraction = dragFraction,
                    )
                    val tmp = File(s.app.cacheDir, "tagged_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(tmp).use { out.compress(Bitmap.CompressFormat.JPEG, 100, it) }
                    s.savePhotoFile(tmp)
                }
            } catch (_: Exception) {
                false
            }
            saving = false
            if (ok) {
                if (source is ReviewSource.Captured) withContext(Dispatchers.IO) { source.file.delete() }
                s.toast("Photo saved")
                onDone()
            } else {
                s.showAlert("Failed to save photo. Please try again.")
            }
        }
    }

    /** Auto-save: runs shortly after everything is ready, unless the user opened an editor meanwhile. */
    suspend fun autoSaveWhenReady(onDone: () -> Unit) {
        if (!isCapture || !s.prefs.value.autoSave) return
        if (!ready || saving || showEdit || showPosition || showMap) return
        delay(100)
        if (!saving && !showEdit && !showPosition && !showMap) save(onDone)
    }
}
