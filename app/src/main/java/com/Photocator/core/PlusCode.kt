package com.Photocator.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Open Location Code (public algorithm), 10 digits (about 14 m). */
object PlusCode {
    private const val ALPHABET = "23456789CFGHJMPQRVWX"
    private const val STEPS_PER_DEGREE = 8000.0

    fun encode(lat: Double, lon: Double): String {
        val la = min(max(lat, -90.0), 90.0 - 0.0000625)
        var lo = lon
        while (lo < -180.0) lo += 360.0
        while (lo >= 180.0) lo -= 360.0
        var latV = floor((la + 90.0) * STEPS_PER_DEGREE + 1e-6).toLong()
        var lonV = floor((lo + 180.0) * STEPS_PER_DEGREE + 1e-6).toLong()
        val latDigits = IntArray(5)
        val lonDigits = IntArray(5)
        for (i in 4 downTo 0) {
            latDigits[i] = (latV % 20).toInt(); latV /= 20
            lonDigits[i] = (lonV % 20).toInt(); lonV /= 20
        }
        val sb = StringBuilder()
        for (i in 0 until 5) {
            if (i == 4) sb.append('+')
            sb.append(ALPHABET[latDigits[i]])
            sb.append(ALPHABET[lonDigits[i]])
        }
        return sb.toString()
    }
}
