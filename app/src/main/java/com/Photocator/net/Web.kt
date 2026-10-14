package com.Photocator.net

import com.Photocator.core.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Reverse(val placeName: String, val address: String)
data class Weather(val tempC: Double?, val condition: String, val humidity: Int?, val windKmh: Double?)
data class PlaceHit(val title: String, val subtitle: String, val lat: Double, val lon: Double)

object Web {
    const val USER_AGENT = "Photocator/1.0 (Android; open-source geotag camera; +https://github.com/karthikhegde25/Photocator)"

    /** GET with the app-wide timeouts; null on any failure or non-200 response. */
    fun getBytes(url: String, userAgent: Boolean = true): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                if (userAgent) setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun getText(url: String, userAgent: Boolean = true): String? =
        getBytes(url, userAgent)?.toString(Charsets.UTF_8)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Nominatim reverse geocoding with the title/address rules of the spec. */
    suspend fun reverse(lat: Double, lon: Double): Reverse = withContext(Dispatchers.IO) {
        val fallback = Reverse("Unknown Location", Fmt.latLon6(lat, lon))
        try {
            val txt = getText("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18")
                ?: return@withContext fallback
            val o = JSONObject(txt)
            val a = o.optJSONObject("address") ?: JSONObject()
            val own = o.optString("name", "")
            val place = if (own.isNotEmpty()) own else
                listOf("city", "town", "village", "suburb", "county").map { a.optString(it, "") }.firstOrNull { it.isNotEmpty() } ?: ""
            val parts = listOf(place, a.optString("state", ""), a.optString("country", ""))
                .filter { it.isNotEmpty() }.distinct()
            val title = if (parts.isEmpty()) "Unknown Location" else parts.joinToString(", ")
            val display = o.optString("display_name", "")
            Reverse(title, if (display.isNotEmpty()) display else Fmt.latLon6(lat, lon))
        } catch (_: Exception) {
            fallback
        }
    }

    private fun conditionWord(code: Int): String = when (code) {
        0 -> "Clear"
        in 1..3 -> "Partly Cloudy"
        in 4..49 -> "Foggy"
        in 50..59 -> "Drizzle"
        in 60..69 -> "Rain"
        in 70..79 -> "Snow"
        in 80..84 -> "Showers"
        in 85..99 -> "Thunderstorm"
        else -> "Unknown"
    }

    /** Open-Meteo current conditions; no custom User-Agent on this request. */
    suspend fun weather(lat: Double, lon: Double): Weather = withContext(Dispatchers.IO) {
        val none = Weather(null, "Unknown", null, null)
        try {
            val txt = getText(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m",
                userAgent = false,
            ) ?: return@withContext none
            val c = JSONObject(txt).optJSONObject("current") ?: return@withContext none
            Weather(
                tempC = if (c.has("temperature_2m") && !c.isNull("temperature_2m")) c.getDouble("temperature_2m") else null,
                condition = if (c.has("weather_code") && !c.isNull("weather_code")) conditionWord(c.getInt("weather_code")) else "Unknown",
                humidity = if (c.has("relative_humidity_2m") && !c.isNull("relative_humidity_2m")) c.getDouble("relative_humidity_2m").toInt() else null,
                windKmh = if (c.has("wind_speed_10m") && !c.isNull("wind_speed_10m")) c.getDouble("wind_speed_10m") else null,
            )
        } catch (_: Exception) {
            none
        }
    }

    /** Explicit search: first Nominatim result only. */
    suspend fun searchFirst(query: String): PlaceHit? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null
        try {
            val txt = getText("https://nominatim.openstreetmap.org/search?format=json&q=${enc(query)}&limit=6")
                ?: return@withContext null
            val arr = org.json.JSONArray(txt)
            if (arr.length() == 0) return@withContext null
            val o = arr.getJSONObject(0)
            PlaceHit(o.optString("display_name", query), "", o.getString("lat").toDouble(), o.getString("lon").toDouble())
        } catch (_: Exception) {
            null
        }
    }

    /** Photon autocomplete (biased towards the map when [biasLat] is given). */
    suspend fun suggest(query: String, biasLat: Double?, biasLon: Double?): List<PlaceHit> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        try {
            var url = "https://photon.komoot.io/api/?q=${enc(query)}&limit=8"
            if (biasLat != null && biasLon != null) url += "&lat=$biasLat&lon=$biasLon"
            val txt = getText(url) ?: return@withContext emptyList()
            val feats = JSONObject(txt).optJSONArray("features") ?: return@withContext emptyList()
            val out = ArrayList<PlaceHit>()
            val seen = HashSet<Pair<String, String>>()
            for (i in 0 until feats.length()) {
                val f = feats.getJSONObject(i)
                val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                val p = f.optJSONObject("properties") ?: continue
                fun s(k: String) = p.optString(k, "")
                val streetLine = if (s("street").isNotEmpty()) (if (s("housenumber").isNotEmpty()) "${s("street")} ${s("housenumber")}" else s("street")) else ""
                val title = listOf(s("name"), streetLine, s("city"), s("state"), s("country")).firstOrNull { it.isNotEmpty() } ?: continue
                val sub = listOf(s("street"), s("district"), s("city"), s("county"), s("state"), s("country"))
                    .filter { it.isNotEmpty() && it != title }.distinct().joinToString(", ")
                if (!seen.add(title to sub)) continue
                out.add(PlaceHit(title, sub, coords.getDouble(1), coords.getDouble(0)))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}
