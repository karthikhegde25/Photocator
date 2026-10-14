package com.Photocator.ui.review

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.Photocator.ReviewSource
import com.Photocator.Session
import com.Photocator.core.MapStyles
import com.Photocator.core.Prefs
import com.Photocator.core.QrStyle
import com.Photocator.core.SavedPlace
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
    /** The photos this screen works on: the captured file, or every photo picked from the gallery. */
    private val sources: List<Any> = when (source) {
        is ReviewSource.Captured -> listOf(source.file)
        is ReviewSource.Imported -> source.uris
    }
    val isCapture: Boolean = source is ReviewSource.Captured
    val count: Int = sources.size
    /** The photos as uris (empty for a capture), for the thumbnail strip. */
    val uris: List<android.net.Uri> get() = (source as? ReviewSource.Imported)?.uris ?: emptyList()
    var previewIndex by mutableStateOf(0)              // which picked photo is shown (the geotag is the same for all)
    var progress by mutableStateOf(0)                  // photos already saved while a batch is being saved

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
    var qrOn by mutableStateOf(s.prefs.value.qrOn)     // per-photo: QR code of the location in the corner instead of a watermark

    /** The corner holds either a watermark or the QR code, never both. */
    fun chooseWatermark(id: String?) { watermarkId = id; qrOn = false }
    fun chooseQr() { watermarkId = null; qrOn = true }
    fun qrStyle(p: Prefs): QrStyle? = if (qrOn) QrStyle(p.qrFg, p.qrBg) else null
    var saving by mutableStateOf(false)
    var showEdit by mutableStateOf(false)
    var showPosition by mutableStateOf(false)
    var showMap by mutableStateOf(false)
    var showPlaces by mutableStateOf(false)            // saved locations list open
    var locating by mutableStateOf(false)              // add-flow "use current location" running
    var fromBlank by mutableStateOf(false)             // geotag came from the blank manual template
    private var started = false

    fun start() {
        if (started) return
        started = true
        loadPreview(0)
        if (isCapture) loadCaptureData() else loading = false
    }

    /** Shows another of the picked photos. The old one stays visible until the new one has decoded. */
    fun selectPreview(i: Int) {
        if (i !in sources.indices || i == previewIndex) return
        previewIndex = i
        loadPreview(i)
    }

    private fun loadPreview(i: Int) {
        scope.launch {
            val dims = withContext(Dispatchers.IO) { Pics.size(s.app, sources[i]) }
            val bmp = withContext(Dispatchers.IO) { Pics.decode(s.app, sources[i], 4096) }
            if (i != previewIndex) return@launch          // the user already moved on to another photo
            if (dims != null) { imgW = dims.first; imgH = dims.second }
            photo = bmp
        }
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

    /** A saved location was chosen: use its name and address, refresh weather and map, keep date, magnetic field and heading. */
    fun applySaved(p: SavedPlace) {
        showPlaces = false
        showEdit = false
        scope.launch {
            loading = true
            try {
                val prev = data
                val built = fullPipeline(p.lat, p.lon, null, null)
                val named = built.copy(placeName = p.name, address = p.address.ifBlank { built.address })
                val merged = if (prev != null) named.copy(timeMs = prev.timeMs, magneticUt = prev.magneticUt ?: named.magneticUt, headingDeg = prev.headingDeg ?: named.headingDeg, note = "") else named
                data = s.withSensorFallback(merged, prev)
                thumb = buildThumb(p.lat, p.lon)
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
        progress = 0
        scope.launch {
            val total = sources.size
            var saved = 0
            try {
                val p = s.prefs.value
                val wm = s.marks.load(s.marks.find(watermarkId))
                val input = StampInput(d, thumb, wm, p.showPlus, p.sensorData, p.tagRounded, qrStyle(p), p.tagMapRight)
                for (i in 0 until total) {
                    // one photo at a time keeps memory flat however many were picked
                    if (withContext(Dispatchers.Default) { saveOne(sources[i], input, p) }) saved++
                    progress = i + 1
                }
            } catch (_: Exception) {
            }
            saving = false
            if (saved > 0 && source is ReviewSource.Captured) withContext(Dispatchers.IO) { source.file.delete() }
            when {
                saved == total -> {
                    s.toast(if (total == 1) "Photo saved" else "$total photos saved")
                    onDone()
                }
                saved > 0 -> {
                    s.showAlert("Saved $saved of $total photos. The others could not be saved.")
                    onDone()
                }
                else -> s.showAlert(if (total == 1) "Failed to save photo. Please try again." else "Failed to save the photos. Please try again.")
            }
        }
    }

    /** Stamps and saves one photo; false when it could not be decoded or saved. */
    private suspend fun saveOne(src: Any, input: StampInput, p: Prefs): Boolean = try {
        val full = Pics.decode(s.app, src, 16384)
        if (full == null) false
        else try {
            val out = StampRender.compose(
                full, input, p.tagWidth, p.tagStretch, p.tagX, p.tagY,
                below = placement == TagPlacement.BELOW_PHOTO, dragFraction = dragFraction,
            )
            try {
                val tmp = File(s.app.cacheDir, "tagged_${System.currentTimeMillis()}_${(0..999).random()}.jpg")
                FileOutputStream(tmp).use { out.compress(Bitmap.CompressFormat.JPEG, 100, it) }
                s.savePhotoFile(tmp)
            } finally {
                if (out !== full) out.recycle()
            }
        } finally {
            full.recycle()
        }
    } catch (_: Exception) {
        false
    }

    /** Auto-save: runs shortly after everything is ready, unless the user opened an editor meanwhile. */
    suspend fun autoSaveWhenReady(onDone: () -> Unit) {
        if (!isCapture || !s.prefs.value.autoSave) return
        if (!ready || saving || showEdit || showPosition || showMap || showPlaces) return
        delay(100)
        if (!saving && !showEdit && !showPosition && !showMap && !showPlaces) save(onDone)
    }
}
