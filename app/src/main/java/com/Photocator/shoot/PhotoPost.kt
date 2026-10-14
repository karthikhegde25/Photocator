package com.Photocator.shoot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.Photocator.core.QualityKind
import com.Photocator.media.Pics
import java.io.File
import java.io.FileOutputStream

/** Post-processing of a camera JPEG according to the quality setting (and the software night lift). */
object PhotoPost {
    /** Returns false if the file could not be processed. Ultra without night boost leaves the file untouched. */
    fun process(ctx: Context, file: File, quality: QualityKind, nightBoost: Boolean): Boolean {
        val q = when (quality) {
            QualityKind.STANDARD -> 88
            QualityKind.HIGH -> 95
            QualityKind.ULTRA -> 100
        }
        if (quality == QualityKind.ULTRA && !nightBoost) return true
        val bmp = Pics.decode(ctx, file, 16384) ?: return false
        val out = if (nightBoost) lift(bmp) else bmp
        return try {
            FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, q, it) }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Every colour channel x1.35 then +14 (0-255 scale), alpha unchanged. */
    private fun lift(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix(floatArrayOf(
            1.35f, 0f, 0f, 0f, 14f,
            0f, 1.35f, 0f, 0f, 14f,
            0f, 0f, 1.35f, 0f, 14f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
