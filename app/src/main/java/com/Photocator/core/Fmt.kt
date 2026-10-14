package com.Photocator.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

object Fmt {
    /** Nearest integer, halves toward +infinity. */
    fun round(v: Double): Long = floor(v + 0.5).toLong()

    fun coords(lat: Double, lon: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return String.format(Locale.US, "Lat %.4f° %s Long %.4f° %s", abs(lat), ns, abs(lon), ew)
    }

    fun dateTime(ms: Long): String =
        SimpleDateFormat("EEEE, d MMMM yyyy HH:mm:ss", Locale.ENGLISH).format(Date(ms))

    fun latLon6(lat: Double, lon: Double): String = String.format(Locale.US, "%.6f, %.6f", lat, lon)

    fun temperature(t: Double?): String = if (t == null) "--°C" else "${round(t)}°C"
    fun humidity(h: Int?): String = "${h ?: 0}%"
    fun wind(w: Double?): String = "${if (w == null) 0 else round(w)} km/h"
    fun altitude(a: Double?): String = "${if (a == null) 0 else round(a)} m"
    fun magnetic(m: Double?): String = "${if (m == null) 0 else round(m)} µT"

    private val WORDS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun compassWord(deg: Double): String {
        val d = ((deg % 360.0) + 360.0) % 360.0
        val idx = (floor((d + 22.5) / 45.0).toInt()) % 8
        return WORDS[idx]
    }

    fun heading(deg: Double?): String =
        if (deg == null) "--" else "${deg.toInt()}° ${compassWord(deg)}"

    /** Plain decimal text of a stored value without a trailing ".0" noise for the edit sheet. */
    fun plain(v: Double?): String {
        if (v == null) return ""
        val s = v.toString()
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }
}
