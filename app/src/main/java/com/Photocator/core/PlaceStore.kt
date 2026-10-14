package com.Photocator.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A location the user saved to reuse on photos. */
data class SavedPlace(val id: String, val name: String, val address: String, val lat: Double, val lon: Double)

/** Saved locations, kept as a small JSON file in the app's private storage. */
class PlaceStore(ctx: Context) {
    private val file = File(ctx.filesDir, "saved_places.json")
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<List<SavedPlace>> get() = _flow
    val list: List<SavedPlace> get() = _flow.value

    private fun read(): List<SavedPlace> = try {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull {
            val o = arr.getJSONObject(it)
            SavedPlace(o.getString("id"), o.getString("name"), o.optString("address"), o.getDouble("lat"), o.getDouble("lon"))
        }
    } catch (_: Exception) { emptyList() }

    private fun write(items: List<SavedPlace>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("address", it.address).put("lat", it.lat).put("lon", it.lon))
        }
        try { file.writeText(arr.toString()) } catch (_: Exception) { }
        _flow.value = items
    }

    fun add(name: String, address: String, lat: Double, lon: Double) {
        val id = "pl_" + System.currentTimeMillis() + "_" + (0..999).random()
        write(list + SavedPlace(id, name.trim().ifEmpty { "Saved location" }, address, lat, lon))
    }

    fun rename(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        write(list.map { if (it.id == id) it.copy(name = n) else it })
    }

    fun delete(id: String) = write(list.filter { it.id != id })
}
