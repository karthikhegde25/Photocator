package com.Photocator.sense

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.location.LocationManagerCompat
import com.Photocator.core.MapStyles
import com.Photocator.core.PlusCode
import com.Photocator.core.PrefsStore
import com.Photocator.core.TagData
import com.Photocator.net.TileStore
import com.Photocator.net.Web
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What the live geotag shows. [data] is null until the first accepted fix. */
data class LiveState(
    val data: TagData? = null,
    val thumb: Bitmap? = null,
    val locationSwitchOff: Boolean = false,
    val stampMs: Long = 0,
)

/**
 * Location, address, weather and sensor pipeline. All entry points are called from the main thread.
 * The pipeline follows the "GPS first" policy: while real GPS is available a coarse fix is held back for up to 12 s.
 */
@SuppressLint("MissingPermission")
class Positioning(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val prefs: PrefsStore,
    private val tiles: TileStore,
    private val sensors: SensorHub,
) {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> get() = _live

    private var current: Location? = null          // best fix, remembered across stop/start
    private var held: Location? = null
    private var seenGps = false
    private var startedAt = 0L
    private var running = false
    private var enriching = false
    private var lastEnrichLoc: Location? = null
    private var lastEnrichAt = 0L
    private val jobs = ArrayList<Job>()
    private val listeners = ArrayList<LocationListener>()

    fun hasFine() = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasCoarse() = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasAny() = hasFine() || hasCoarse()
    fun masterSwitchOn(): Boolean = try { LocationManagerCompat.isLocationEnabled(lm) } catch (_: Exception) { true }
    private fun gpsOn() = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
    private fun netOn() = try { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { false }
    private fun gpsAvailable() = hasFine() && gpsOn()

    private fun acc(l: Location): Float = if (l.hasAccuracy()) l.accuracy else 10_000f
    private fun isGps(l: Location) = l.provider == LocationManager.GPS_PROVIDER

    /** "Is this fix better than the current one" rule. */
    private fun better(n: Location, c: Location?): Boolean {
        if (c == null) return true
        val dt = n.time - c.time
        val na = acc(n)
        val ca = acc(c)
        if (dt < -2 * 60_000L) return false
        if (na > 100f && ca <= 100f && dt < 10 * 60_000L) return false
        if (isGps(c) && !isGps(n) && dt < 10 * 60_000L) return false
        if (dt > 2 * 60_000L) return true
        val d = na - ca
        if (d < 0f) return true
        if (dt > 0 && d == 0f) return true
        if (dt > 0 && d <= 200f && n.provider == c.provider) return true
        return false
    }

    private fun bestLastKnown(): Location? {
        var best: Location? = null
        val names = ArrayList<String>()
        if (hasFine()) names.add(LocationManager.GPS_PROVIDER)
        names.add(LocationManager.NETWORK_PROVIDER)
        names.add(LocationManager.PASSIVE_PROVIDER)
        for (p in names) {
            val l = try { lm.getLastKnownLocation(p) } catch (_: Exception) { null } ?: continue
            if (better(l, best)) best = l
        }
        return best
    }

    private fun listener(onFix: (Location) -> Unit) = object : LocationListener {
        override fun onLocationChanged(location: Location) = onFix(location)
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /** One-shot fix: GPS preferred, coarse accepted only after a grace period or when GPS is unavailable. */
    suspend fun oneShot(timeoutMs: Long = 15_000): Location? = withContext(Dispatchers.Main) {
        if (!hasAny()) return@withContext null
        val providers = ArrayList<String>()
        if (hasFine() && gpsOn()) providers.add(LocationManager.GPS_PROVIDER)
        if (netOn()) providers.add(LocationManager.NETWORK_PROVIDER)
        val last = bestLastKnown()
        if (providers.isEmpty()) return@withContext last
        if (last != null && System.currentTimeMillis() - last.time < 30_000 && acc(last) <= 50f) return@withContext last

        val got = java.util.Collections.synchronizedList(ArrayList<Location>())
        val ls = ArrayList<LocationListener>()
        for (p in providers) {
            val l = listener { got.add(it) }
            try {
                lm.requestLocationUpdates(p, 0L, 0f, l, Looper.getMainLooper())
                ls.add(l)
            } catch (_: Exception) { }
        }
        val start = SystemClock.elapsedRealtime()
        try {
            while (true) {
                delay(100)
                val el = SystemClock.elapsedRealtime() - start
                val snapshot = synchronized(got) { ArrayList(got) }
                if (snapshot.any { isGps(it) && acc(it) <= 50f }) break
                if (!providers.contains(LocationManager.GPS_PROVIDER) && snapshot.isNotEmpty()) break
                if (el >= timeoutMs) break
                if (snapshot.isNotEmpty() && el >= 6_000) break
            }
        } finally {
            ls.forEach { try { lm.removeUpdates(it) } catch (_: Exception) { } }
        }
        var best: Location? = null
        synchronized(got) { for (l in got) if (better(l, best)) best = l }
        if (best != null) return@withContext best
        last?.takeIf { System.currentTimeMillis() - it.time < 10 * 60_000L }
    }

    // ---------------------------------------------------------------- live pipeline

    fun start() {
        if (running || !hasAny()) return
        running = true
        startedAt = SystemClock.elapsedRealtime()
        held = null
        seenGps = false
        lastEnrichLoc = null
        enriching = false
        sensors.start()

        val l = listener { onFix(it) }
        listeners.add(l)
        val names = ArrayList<String>()
        if (hasFine()) names.add(LocationManager.GPS_PROVIDER)
        names.add(LocationManager.NETWORK_PROVIDER)
        for (p in names) {
            try { lm.requestLocationUpdates(p, 1000L, 0f, l, Looper.getMainLooper()) } catch (_: Exception) { }
        }

        jobs.add(scope.launch { oneShot(15_000)?.let { onFix(it) } })
        jobs.add(scope.launch {
            while (true) {
                _live.value = _live.value.copy(locationSwitchOff = !masterSwitchOn())
                delay(2000)
            }
        })
        jobs.add(scope.launch {
            delay(12_000)
            val h = held
            if (h != null && _live.value.data == null) accept(h)
        })
        jobs.add(scope.launch {
            prefs.flow.map { it.mapStyleId to it.mapZoom }.distinctUntilChanged().drop(1).collect { rebuildThumb() }
        })
    }

    /** Throws away the remembered fix and starts the whole lookup again (GPS, address, weather, map). */
    fun refresh() {
        current = null
        held = null
        stop()
        start()
    }

    fun stop() {
        if (!running) return
        running = false
        jobs.forEach { it.cancel() }
        thumbJob?.cancel()
        jobs.clear()
        listeners.forEach { try { lm.removeUpdates(it) } catch (_: Exception) { } }
        listeners.clear()
        sensors.stop()
        _live.value = LiveState()
    }

    private fun onFix(loc: Location) {
        if (!running) return
        val gpsFirst = gpsAvailable()
        if (isGps(loc)) seenGps = true
        else if (gpsFirst && !seenGps && SystemClock.elapsedRealtime() - startedAt < 12_000) {
            if (held == null || better(loc, held)) held = loc
            return
        }
        if (!better(loc, current)) return
        accept(loc)
    }

    private fun accept(loc: Location) {
        current = loc
        val now = System.currentTimeMillis()
        val prev = _live.value.data
        val plus = PlusCode.encode(loc.latitude, loc.longitude)
        val alt = if (loc.hasAltitude()) loc.altitude else null
        val spd = if (loc.hasSpeed()) loc.speed.toDouble() else null
        val d = if (prev == null) {
            TagData(
                lat = loc.latitude, lon = loc.longitude, altitude = alt, speed = spd,
                placeName = "locating...", address = "fetching address...", plusCode = plus, timeMs = now,
                condition = "Unknown",
            )
        } else {
            prev.copy(
                lat = loc.latitude, lon = loc.longitude,
                altitude = alt ?: prev.altitude, speed = spd ?: prev.speed,
                plusCode = plus, timeMs = now,
            )
        }
        _live.value = _live.value.copy(data = d, stampMs = now)
        if (prev == null || _live.value.thumb == null) requestThumb(loc.latitude, loc.longitude)
        maybeEnrich(loc)
    }

    private fun maybeEnrich(loc: Location) {
        if (enriching) return
        val now = SystemClock.elapsedRealtime()
        val last = lastEnrichLoc
        val first = last == null
        val moved = last?.distanceTo(loc) ?: Float.MAX_VALUE
        if (!(first || moved > 100f || now - lastEnrichAt > 30_000)) return
        enriching = true
        lastEnrichLoc = loc
        lastEnrichAt = now
        scope.launch {
            try { enrich(loc.latitude, loc.longitude) } catch (_: Exception) { } finally { enriching = false }
        }
    }

    private suspend fun enrich(lat: Double, lon: Double) {
        coroutineScope {
            val rev = async { Web.reverse(lat, lon) }
            val wx = async { Web.weather(lat, lon) }
            val mag = async { withTimeoutOrNull(1000) { sensors.magnetic.filterNotNull().first() } }
            val head = async { withTimeoutOrNull(900) { sensors.heading.filterNotNull().first() } }
            val r = rev.await(); val w = wx.await(); val m = mag.await(); val h = head.await()
            val cur = _live.value.data ?: return@coroutineScope
            _live.value = _live.value.copy(
                data = cur.copy(
                    placeName = r.placeName, address = r.address,
                    tempC = w.tempC, condition = w.condition, humidity = w.humidity, windKmh = w.windKmh,
                    magneticUt = m ?: cur.magneticUt, headingDeg = h ?: cur.headingDeg,
                ),
                stampMs = System.currentTimeMillis(),
            )
        }
        rebuildThumb(lat, lon)
    }

    private var thumbJob: Job? = null

    /** Builds the map thumbnail right away (not after the slower address/weather lookups), retrying a few times. */
    private fun requestThumb(lat: Double, lon: Double) {
        if (thumbJob?.isActive == true) return
        thumbJob = scope.launch {
            repeat(4) {
                if (_live.value.thumb != null) return@launch
                rebuildThumb(lat, lon)
                if (_live.value.thumb != null) return@launch
                delay(4000)
            }
        }
    }

    private suspend fun rebuildThumb(lat: Double? = null, lon: Double? = null) {
        val d = _live.value.data
        val la = lat ?: d?.lat ?: return
        val lo = lon ?: d?.lon ?: return
        val p = prefs.value
        val bmp = tiles.thumbnail(la, lo, MapStyles.byId(p.mapStyleId), p.mapZoom) ?: return
        _live.value = _live.value.copy(thumb = bmp, stampMs = System.currentTimeMillis())
    }

    // ---------------------------------------------------------------- compass helpers for sensors
    val magnetic: StateFlow<Double?> get() = sensors.magnetic
    val heading: StateFlow<Double?> get() = sensors.heading
}
