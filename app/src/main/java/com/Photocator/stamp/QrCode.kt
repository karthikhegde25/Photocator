package com.Photocator.stamp

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Locale

/** QR code of a map location. Modules are returned without any quiet zone, so the code fills its whole square. */
object QrCode {
    /** What the QR holds: a map link that opens in any maps app or browser. */
    fun locationText(lat: Double, lon: Double): String =
        String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", lat, lon)

    class Modules(val size: Int, private val bits: BooleanArray) {
        fun dark(x: Int, y: Int): Boolean = bits[y * size + x]
    }

    private var lastText: String? = null
    private var lastModules: Modules? = null

    /** Modules of the QR for [text]; the last result is kept because the geotag is redrawn often. */
    @Synchronized
    fun modules(text: String): Modules? {
        if (text == lastText) return lastModules
        val m = try {
            val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L)
            val bm = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
            val n = bm.width
            val bits = BooleanArray(n * bm.height)
            for (y in 0 until bm.height) for (x in 0 until n) bits[y * n + x] = bm.get(x, y)
            Modules(n, bits)
        } catch (_: Exception) { null }
        lastText = text
        lastModules = m
        return m
    }
}
