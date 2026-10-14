package com.Photocator.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.LruCache
import com.Photocator.core.MapProviders
import com.Photocator.core.MapStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/** Web-Mercator helpers (256 px tiles). */
object Mercator {
    fun worldSize(z: Double): Double = 256.0 * 2.0.pow(z)
    fun x(lon: Double, z: Double): Double = (lon + 180.0) / 360.0 * worldSize(z)
    fun y(lat: Double, z: Double): Double {
        val l = Math.toRadians(lat.coerceIn(-85.05, 85.05))
        return (1.0 - ln(tan(l) + 1.0 / cos(l)) / PI) / 2.0 * worldSize(z)
    }
    fun lon(x: Double, z: Double): Double = x / worldSize(z) * 360.0 - 180.0
    fun lat(y: Double, z: Double): Double {
        val n = PI - 2.0 * PI * y / worldSize(z)
        return Math.toDegrees(kotlin.math.atan(0.5 * (kotlin.math.exp(n) - kotlin.math.exp(-n))))
    }
}

/** Disk + memory cache of map tiles (any provider), and the centred geotag thumbnail builder. */
class TileStore(ctx: Context) {
    companion object {
        const val OSM = "osm_standard"
        /** Closest zoom asked for; imagery at higher levels is often missing. */
        const val MAX_ZOOM = 16
    }
    private val tileDir = File(ctx.cacheDir, "tiles").apply { mkdirs() }
    private val thumbDir = File(ctx.cacheDir, "thumbs").apply { mkdirs() }
    private val memory = LruCache<String, Bitmap>(160)
    private val gate = Semaphore(6)

    private fun url(path: String, z: Int, x: Int, y: Int) = MapProviders.tileUrl(path, z, x, y)

    private fun fileFor(path: String, z: Int, x: Int, y: Int): File =
        File(tileDir, "${path.replace('/', '_')}/$z/${x}_$y.tile")

    /** Blocking tile load (disk, then network). Returns null if unavailable. */
    fun loadTileBlocking(path: String, z: Int, x: Int, y: Int, useMemory: Boolean = false): Bitmap? {
        val key = "$path/$z/$x/$y"
        if (useMemory) memory.get(key)?.let { return it }
        val f = fileFor(path, z, x, y)
        if (f.exists()) {
            val b = try { BitmapFactory.decodeFile(f.path) } catch (_: Throwable) { null }
            if (b != null) { if (useMemory) memory.put(key, b); return b }
            f.delete()
        }
        val bytes = Web.getBytes(url(path, z, x, y)) ?: return null
        val bmp = try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Throwable) { null } ?: return null
        try {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".part")
            FileOutputStream(tmp).use { it.write(bytes) }
            if (!tmp.renameTo(f)) tmp.delete()
        } catch (_: Exception) { /* cache is best effort */ }
        if (useMemory) memory.put(key, bmp)
        return bmp
    }

    /** At most six downloads run at once. */
    suspend fun loadTile(path: String, z: Int, x: Int, y: Int, useMemory: Boolean = false): Bitmap? =
        gate.withPermit { withContext(Dispatchers.IO) { loadTileBlocking(path, z, x, y, useMemory) } }

    fun cachedInMemory(path: String, z: Int, x: Int, y: Int): Bitmap? = memory.get("$path/$z/$x/$y")

    /**
     * Square map image centred exactly on the coordinate. Uses tiles one level deeper than the wanted zoom
     * when the style has them (512 px window), otherwise a 256 px window. Null when no tile could be loaded.
     */
    suspend fun thumbnail(lat: Double, lon: Double, style: MapStyle, userZoom: Int): Bitmap? = withContext(Dispatchers.IO) {
        var best: Bitmap? = null
        var anyReached = false
        // Start at the wanted zoom (never closer than MAX_ZOOM) and zoom out when a level has no imagery there.
        var z0 = min(min(userZoom, style.maxZoom), MAX_ZOOM)
        for (attempt in 0 until 4) {
            if (z0 < 6) break
            val deeper = z0 + 1 <= style.maxZoom
            val z = if (deeper) z0 + 1 else z0
            val size = if (deeper) 512 else 256
            val cx = Mercator.x(lon, z.toDouble())
            val cy = Mercator.y(lat, z.toDouble())
            val cached = File(thumbDir, "${style.id}_${z0}_${cx.roundToInt()}_${cy.roundToInt()}.jpg")
            var bmp: Bitmap? = null
            if (cached.exists()) {
                bmp = try { BitmapFactory.decodeFile(cached.path)?.also { cached.setLastModified(System.currentTimeMillis()) } } catch (_: Throwable) { null }
            }
            if (bmp == null) {
                bmp = stitch(style.base, z, size, cx, cy)
                if (bmp != null && !isBlank(bmp)) {
                    try { FileOutputStream(cached).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } } catch (_: Exception) { }
                    if (Math.random() < 0.05) prune()
                }
            }
            if (bmp != null) {
                anyReached = true
                if (!isBlank(bmp)) return@withContext bmp
                best = bmp
            }
            z0 -= 2
        }
        if (best != null) return@withContext best
        if (anyReached) return@withContext null
        // The chosen basemap could not be reached at all: fall back to OpenStreetMap so a map always shows.
        val zf = min(MAX_ZOOM + 1, 19)
        stitch(OSM, zf, 512, Mercator.x(lon, zf.toDouble()), Mercator.y(lat, zf.toDouble()))
    }

    /** True for flat grey / "map data not available" images (almost no colour variation). */
    private fun isBlank(b: Bitmap): Boolean {
        var lo = 255
        var hi = 0
        val n = 16
        for (iy in 0 until n) for (ix in 0 until n) {
            val p = b.getPixel((ix * (b.width - 1)) / (n - 1), (iy * (b.height - 1)) / (n - 1))
            val l = (Color.red(p) * 3 + Color.green(p) * 6 + Color.blue(p)) / 10
            if (l < lo) lo = l
            if (l > hi) hi = l
        }
        return hi - lo < 14
    }

    /** Builds a [size] px square centred on world pixel ([cx], [cy]) at zoom [z]; null when no tile loaded. */
    private suspend fun stitch(base: String, z: Int, size: Int, cx: Double, cy: Double): Bitmap? {
        val left = cx - size / 2.0
        val top = cy - size / 2.0
        val n = 1 shl z
        val tx0 = floor(left / 256.0).toInt()
        val tx1 = floor((left + size - 1) / 256.0).toInt()
        val ty0 = floor(top / 256.0).toInt()
        val ty1 = floor((top + size - 1) / 256.0).toInt()
        val jobs = ArrayList<Triple<Int, Int, kotlinx.coroutines.Deferred<Bitmap?>>>()
        coroutineScope {
            for (ty in ty0..ty1) {
                if (ty < 0 || ty >= n) continue
                for (tx in tx0..tx1) {
                    val wrapped = ((tx % n) + n) % n
                    jobs.add(Triple(tx, ty, async { loadTile(base, z, wrapped, ty) }))
                }
            }
        }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(0x33, 0x33, 0x33))
        var any = false
        for ((tx, ty, d) in jobs) {
            val tile = d.await() ?: continue
            any = true
            canvas.drawBitmap(tile, (tx * 256 - left).toFloat(), (ty * 256 - top).toFloat(), null)
        }
        return if (any) out else null
    }

    private fun prune() {
        val files = thumbDir.listFiles() ?: return
        if (files.size <= 400) return
        files.sortedByDescending { it.lastModified() }.drop(400).forEach { it.delete() }
    }
}
