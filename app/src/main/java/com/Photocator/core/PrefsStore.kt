package com.Photocator.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Settings persisted as a single JSON blob; each field falls back to its default when invalid. */
class PrefsStore(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("photocator_settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<Prefs> get() = _flow
    val value: Prefs get() = _flow.value

    fun update(change: (Prefs) -> Prefs) {
        val next = change(_flow.value)
        _flow.value = next
        save(next)
    }

    fun reset() = update { Prefs() }

    private fun save(p: Prefs) {
        val o = JSONObject()
        o.put("sd", p.sdCard)
        o.put("vol", p.volumeAction.name)
        o.put("click", p.shutterSound)
        o.put("tapShot", p.touchCapture)
        o.put("auto", p.autoSave)
        o.put("grid", p.grid.name)
        o.put("timer", p.timerSec)
        o.put("aspect", p.aspect.name)
        o.put("quality", p.quality.name)
        o.put("flash", p.flash.name)
        o.put("tag", p.tagEnabled)
        o.put("style", p.mapStyleId)
        o.put("plus", p.showPlus)
        o.put("wm", p.watermarkId ?: JSONObject.NULL)
        o.put("wmMem", p.rememberedWatermarkId ?: JSONObject.NULL)
        o.put("tw", p.tagWidth.toDouble())
        o.put("ts", p.tagStretch.toDouble())
        o.put("tx", p.tagX.toDouble())
        o.put("ty", p.tagY.toDouble())
        o.put("sensors", p.sensorData)
        o.put("mz", p.mapZoom)
        sp.edit().putString("blob", o.toString()).apply()
    }

    private inline fun <reified E : Enum<E>> enumOf(o: JSONObject, key: String, def: E): E =
        try { enumValueOf<E>(o.optString(key, def.name)) } catch (_: Exception) { def }

    private fun load(): Prefs {
        val raw = sp.getString("blob", null) ?: return Prefs()
        return try {
            val o = JSONObject(raw)
            val d = Prefs()
            Prefs(
                sdCard = o.optBoolean("sd", d.sdCard),
                volumeAction = enumOf(o, "vol", d.volumeAction),
                shutterSound = o.optBoolean("click", d.shutterSound),
                touchCapture = o.optBoolean("tapShot", d.touchCapture),
                autoSave = o.optBoolean("auto", d.autoSave),
                grid = enumOf(o, "grid", d.grid),
                timerSec = o.optInt("timer", 0).let { if (it == 2 || it == 5 || it == 10) it else 0 },
                aspect = enumOf(o, "aspect", d.aspect),
                quality = enumOf(o, "quality", d.quality),
                flash = enumOf(o, "flash", d.flash),
                tagEnabled = o.optBoolean("tag", d.tagEnabled),
                mapStyleId = o.optString("style", d.mapStyleId).let { id -> if (MapStyles.all.any { it.id == id }) id else d.mapStyleId },
                showPlus = o.optBoolean("plus", d.showPlus),
                watermarkId = if (o.isNull("wm")) null else o.optString("wm").ifEmpty { null },
                rememberedWatermarkId = if (o.isNull("wmMem")) null else o.optString("wmMem").ifEmpty { null },
                tagWidth = o.optDouble("tw", 1.0).toFloat().coerceIn(0.35f, 1f),
                tagStretch = o.optDouble("ts", 1.0).toFloat().coerceIn(0.5f, 2.5f),
                tagX = o.optDouble("tx", 0.5).toFloat().coerceIn(0f, 1f),
                tagY = o.optDouble("ty", 1.0).toFloat().coerceIn(0f, 1f),
                sensorData = o.optBoolean("sensors", d.sensorData),
                mapZoom = o.optInt("mz", d.mapZoom).coerceIn(10, 16),
            )
        } catch (_: Exception) {
            Prefs()
        }
    }
}

/** Private store of uploaded watermark images (PNG, longest side 512 px). */
class WatermarkStore(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "watermarks").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val _flow = MutableStateFlow(readIndex())
    val flow: StateFlow<List<Watermark>> get() = _flow
    val list: List<Watermark> get() = _flow.value

    private fun readIndex(): List<Watermark> = try {
        val arr = JSONArray(index.readText())
        (0 until arr.length()).mapNotNull {
            val o = arr.getJSONObject(it)
            val w = Watermark(o.getString("id"), o.getString("name"), o.getString("file"))
            if (File(dir, w.file).exists()) w else null
        }
    } catch (_: Exception) { emptyList() }

    private fun writeIndex(items: List<Watermark>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("file", it.file)) }
        index.writeText(arr.toString())
        _flow.value = items
    }

    fun find(id: String?): Watermark? = if (id == null) null else list.firstOrNull { it.id == id }

    fun load(w: Watermark?, maxSide: Int = 512): Bitmap? {
        if (w == null) return null
        return try { BitmapFactory.decodeFile(File(dir, w.file).path)?.let { fit(it, maxSide) } } catch (_: Exception) { null }
    }

    /** Adds a bitmap as a new watermark; returns it, or null on failure. */
    fun add(name: String, bmp: Bitmap): Watermark? = try {
        val scaled = fit(bmp, 512)
        val id = "wm_" + System.currentTimeMillis() + "_" + (0..999).random()
        val file = "$id.png"
        FileOutputStream(File(dir, file)).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val w = Watermark(id, name.trim().ifEmpty { "Watermark" }, file)
        writeIndex(list + w)
        w
    } catch (_: Exception) { null }

    fun rename(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        writeIndex(list.map { if (it.id == id) it.copy(name = n) else it })
    }

    fun delete(id: String) {
        list.firstOrNull { it.id == id }?.let { File(dir, it.file).delete() }
        writeIndex(list.filter { it.id != id })
    }

    private fun fit(b: Bitmap, maxSide: Int): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= maxSide) return b
        val s = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(b, maxOf(1, (b.width * s).toInt()), maxOf(1, (b.height * s).toInt()), true)
    }
}
