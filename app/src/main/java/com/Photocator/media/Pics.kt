package com.Photocator.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

/** Image decoding with EXIF orientation applied. A source is either a [File] or a content [Uri]. */
object Pics {
    private fun open(ctx: Context, src: Any): InputStream? = when (src) {
        is File -> src.inputStream()
        is Uri -> ctx.contentResolver.openInputStream(src)
        else -> null
    }

    private fun exifOrientation(ctx: Context, src: Any): Int = try {
        open(ctx, src)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun swapsAxes(o: Int) = o == ExifInterface.ORIENTATION_ROTATE_90 || o == ExifInterface.ORIENTATION_ROTATE_270 ||
        o == ExifInterface.ORIENTATION_TRANSPOSE || o == ExifInterface.ORIENTATION_TRANSVERSE

    /** Upright pixel size (width, height) without decoding the pixels, or null if unreadable. */
    fun size(ctx: Context, src: Any): Pair<Int, Int>? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(ctx, src)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0 || o.outHeight <= 0) null
        else if (swapsAxes(exifOrientation(ctx, src))) o.outHeight to o.outWidth else o.outWidth to o.outHeight
    } catch (_: Exception) { null }

    /** Decodes upright; the longer side is halved (power of two) until it is at most [maxSide]. */
    fun decode(ctx: Context, src: Any, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open(ctx, src)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = open(ctx, src)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            upright(raw, exifOrientation(ctx, src))
        } catch (_: Exception) {
            null
        }
    }

    private fun upright(b: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return b
        }
        val out = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        if (out !== b) b.recycle()
        return out
    }

    /** Square-ish thumbnail decode (longer side at most [maxSide]). */
    fun thumb(ctx: Context, src: Any, maxSide: Int = 256): Bitmap? = decode(ctx, src, maxSide)
}
