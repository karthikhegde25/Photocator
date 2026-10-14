package com.Photocator.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.Photocator.core.Fmt
import com.Photocator.core.QrStyle
import com.Photocator.core.TagData
import kotlin.math.max
import kotlin.math.min

/** Everything the painter needs to know about one geotag. */
data class StampInput(
    val data: TagData,
    val thumb: Bitmap?,
    val watermark: Bitmap?,
    val showPlus: Boolean,
    val sensors: Boolean,
    /** Round the panel's corners (only when drawn over a photo; the strip below a photo stays square). */
    val rounded: Boolean = false,
    /** When set, the top-right corner holds a QR code of the location (and any watermark is ignored). */
    val qr: QrStyle? = null,
    /** Put the map strip on the right; the watermark or QR code then moves to the left. */
    val mapRight: Boolean = false,
)

private const val K = 8f                 // measuring oversampling
private const val MAP_W = 70f
private const val TEXT_LEFT = 76f
private const val TEXT_RIGHT_PAD = 6f
private const val FULL_W = 360f
private const val BASE_TS = 1f
private const val V_SCALE = 1.15f         // the panel is 15% taller than before, which also makes the text larger
private const val TOP = 3.5f             // top padding in design units
private const val BOTTOM_PAD = 3f        // bottom padding in design units
private const val QR_SIDE_DU = 30f       // QR block (code plus its padding), smaller than the title/address block
private const val QR_PAD_DU = 2.5f       // background left around the code
private const val QR_RADIUS_DU = 3.5f    // corner radius of the QR block
private const val CORNER_DU = 12f        // corner radius of a rounded panel, in design units

private val FACE_REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
private val FACE_MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
private val FACE_BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
private val FACE_ITALIC: Typeface = Typeface.create("sans-serif", Typeface.ITALIC)

/** Text measuring in design units (du). */
private class Ruler {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private fun set(face: Typeface, size: Float) { p.typeface = face; p.textSize = size * K }
    fun width(face: Typeface, size: Float, s: String): Float { set(face, size); return p.measureText(s) / K }
    fun lineH(face: Typeface, size: Float): Float { set(face, size); val f = p.fontMetrics; return (f.descent - f.ascent) / K }
    fun ascent(face: Typeface, size: Float): Float { set(face, size); return -p.fontMetrics.ascent / K }

    fun ellipsize(face: Typeface, size: Float, s: String, maxW: Float): String {
        if (width(face, size, s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && width(face, size, "$t…") > maxW) t = t.dropLast(1)
        return if (t.isEmpty()) "…" else "$t…"
    }

    /** Word wrap into at most [maxLines] lines; whatever does not fit is ellipsised into the last line. */
    fun wrap(face: Typeface, size: Float, text: String, maxW: Float, maxLines: Int): List<String> {
        var rest = text.replace('\n', ' ').trim()
        val lines = ArrayList<String>()
        while (rest.isNotEmpty() && lines.size < maxLines) {
            if (lines.size == maxLines - 1 || width(face, size, rest) <= maxW) {
                lines.add(ellipsize(face, size, rest, maxW))
                break
            }
            var cut = rest.length
            while (cut > 1 && width(face, size, rest.substring(0, cut)) > maxW) cut--
            var brk = cut
            if (cut < rest.length && rest[cut] != ' ') {
                val sp = rest.lastIndexOf(' ', cut - 1)
                if (sp > 0) brk = sp
            }
            lines.add(rest.substring(0, brk).trimEnd())
            rest = rest.substring(brk).trimStart()
        }
        return lines
    }
}

internal class Metric(val glyph: Glyph, val tint: Int, val text: String)

/** Laid-out geotag in design units. Everything is relative to the panel's top-left corner. */
class StampPlan internal constructor(
    val widthPx: Int,
    val heightPx: Int,
    internal val scale: Float,
    internal val layoutW: Float,
    internal val input: StampInput,
    internal val b: Built,
) {
    val aspectHW: Float get() = heightPx.toFloat() / widthPx.toFloat()
}

internal class Built(
    val hDu: Float,
    val titleText: String, val titleSize: Float, val titleBase: Float,
    val addrLines: List<String>, val addrBase: Float, val addrLineH: Float,
    val leftLines: List<Triple<String, Typeface, Int>>, val leftBase: Float, val leftLineH: Float,
    val rightRows: List<Metric>, val rowTop: Float, val rowH: Float,
    val bottomItems: List<Metric>, val bottomXs: List<Float>, val bottomFont: Float, val bottomTop: Float,
    val wmRect: RectF?,
    val textRight: Float,
    val textLeft: Float,
    val titleX: Float,
    val mapRight: Boolean,
    val ts: Float,
)

object Stamp {
    private fun metricsFor(d: TagData): Pair<List<Metric>, List<Metric>> {
        val rows = listOf(
            Metric(StampIcons.forCondition(d.condition), StampIcons.tintFor(d.condition), Fmt.temperature(d.tempC)),
            Metric(Glyph.COMPASS, 0xFF4DD0E1.toInt(), Fmt.heading(d.headingDeg)),
            Metric(Glyph.DROP, 0xFF64B5F6.toInt(), Fmt.humidity(d.humidity)),
        )
        val bottom = listOf(
            Metric(Glyph.WIND, 0xFF4DB6AC.toInt(), Fmt.wind(d.windKmh)),
            Metric(Glyph.MOUNTAIN, 0xFFFFB74D.toInt(), Fmt.altitude(d.altitude)),
            Metric(Glyph.WAVES, 0xFF81D4FA.toInt(), Fmt.magnetic(d.magneticUt)),
        )
        return Pair(rows, bottom)
    }

    private fun build(inp: StampInput, lw: Float, sensors: Boolean, ts: Float): Built {
        val ls = 9.4f * ts
        val r = Ruler()
        val d = inp.data
        val tw = lw - MAP_W - 6f - TEXT_RIGHT_PAD            // text area width: starts at 76, ends 6 before the edge
        val mir = inp.mapRight
        val textLeft = if (mir) TEXT_RIGHT_PAD else TEXT_LEFT
        val textRight = if (mir) lw - TEXT_LEFT else lw - TEXT_RIGHT_PAD

        var wm: RectF? = null
        if (inp.qr != null) {
            // a compact square that fits inside the title and address block, so the panel keeps its usual height
            val side = min(QR_SIDE_DU * ts, r.lineH(FACE_BOLD, 12.5f * ts) + 2 * r.lineH(FACE_MEDIUM, ls))
            wm = if (mir) RectF(textLeft, TOP, textLeft + side, TOP + side) else RectF(textRight - side, TOP, textRight, TOP + side)
        } else inp.watermark?.let { bmp ->
            val a = if (bmp.width > 0 && bmp.height > 0) bmp.width.toFloat() / bmp.height else 1f
            val ww = min(64f, 32f * a)
            wm = if (mir) RectF(textLeft, TOP, textLeft + ww, TOP + ww / a) else RectF(textRight - ww, TOP, textRight, TOP + ww / a)
        }
        val wmW = wm?.width() ?: 0f
        val availTitle = tw - (if (wm != null) wmW + 6f else 0f)
        val titleX = if (mir && wm != null) textLeft + wmW + 6f else textLeft

        // title: shrink in 6% steps down to 70%, then cut with an ellipsis
        var tsize = 12.5f * ts
        var title = d.placeName
        var fits = false
        for (f in floatArrayOf(1f, 0.94f, 0.88f, 0.82f, 0.76f, 0.70f)) {
            tsize = 12.5f * ts * f
            if (r.width(FACE_BOLD, tsize, title) <= availTitle) { fits = true; break }
        }
        if (!fits) title = r.ellipsize(FACE_BOLD, tsize, title, availTitle)
        // The panel height never depends on the text itself (title length, address length), only on whether a
        // note is present (a watermark sits in the corner without adding height), so every location gets the same default size.
        val titleH = r.lineH(FACE_BOLD, 12.5f * ts)
        val titleBase = TOP + r.ascent(FACE_BOLD, tsize)

        val addrSize = ls
        val addrLines = r.wrap(FACE_MEDIUM, addrSize, d.address, availTitle, 2)
        val addrLineH = r.lineH(FACE_MEDIUM, addrSize)
        val addrTop = TOP + titleH
        val topBlock = titleH + 2 * addrLineH
        val headH = max(topBlock, wm?.height() ?: 0f)

        val (rows, bottom) = metricsFor(d)
        val metricFont = 9.6f * ts
        val rowH = max(11f * ts, r.lineH(FACE_BOLD, metricFont))

        val bodyW = if (sensors) tw - 60f * ts - 4f else tw
        val left = ArrayList<Triple<String, Typeface, Int>>()
        left.add(Triple(r.ellipsize(FACE_REGULAR, ls, Fmt.coords(d.lat, d.lon), bodyW), FACE_REGULAR, Color.WHITE))
        if (inp.showPlus && d.plusCode.isNotEmpty())
            left.add(Triple(r.ellipsize(FACE_REGULAR, ls, "Plus Code ${d.plusCode}", bodyW), FACE_REGULAR, Color.WHITE))
        left.add(Triple(r.ellipsize(FACE_REGULAR, ls, Fmt.dateTime(d.timeMs), bodyW), FACE_REGULAR, Color.WHITE))
        val note = d.note.trim()
        if (note.isNotEmpty()) left.add(Triple(r.ellipsize(FACE_ITALIC, ls, note, bodyW), FACE_ITALIC, 0xFFC8C8C8.toInt()))
        val leftLineH = r.lineH(FACE_REGULAR, ls)
        val leftH = left.size * leftLineH
        val bodyH = max(leftH, if (sensors) 3 * rowH else 0f)
        val bodyTop = TOP + headH + 0.5f

        var bottomFont = 1f
        var bottomXs: List<Float> = emptyList()
        var bottomTop = 0f
        var hDu = bodyTop + bodyH + BOTTOM_PAD
        if (sensors) {
            bottomTop = bodyTop + bodyH + 1f
            var f = 1f
            var widths = bottom.map { 14f * ts * f + r.width(FACE_BOLD, metricFont * f, it.text) }
            while (widths.sum() > tw && f > 0.6f) {
                f -= 0.05f
                widths = bottom.map { 14f * ts * f + r.width(FACE_BOLD, metricFont * f, it.text) }
            }
            val gap = max(2f, (tw - widths.sum()) / 2f)
            bottomFont = f
            bottomXs = listOf(textLeft, textLeft + widths[0] + gap, textRight - widths[2])
            hDu = bottomTop + rowH + BOTTOM_PAD
        }
        return Built(
            hDu = hDu,
            titleText = title, titleSize = tsize, titleBase = titleBase,
            addrLines = addrLines, addrBase = addrTop + r.ascent(FACE_MEDIUM, addrSize), addrLineH = addrLineH,
            leftLines = left, leftBase = bodyTop + r.ascent(FACE_REGULAR, ls), leftLineH = leftLineH,
            rightRows = if (sensors) rows else emptyList(), rowTop = bodyTop, rowH = rowH,
            bottomItems = if (sensors) bottom else emptyList(), bottomXs = bottomXs, bottomFont = bottomFont, bottomTop = bottomTop,
            wmRect = wm, textRight = textRight, textLeft = textLeft, titleX = titleX, mapRight = mir, ts = ts,
        )
    }

    /**
     * Lays the geotag out for a panel [panelWidthPx] wide. [stretch] sets the layout width (360 / stretch).
     * The layout width is narrowed until the panel reaches a fixed proportion (the full layout's proportion
     * times [V_SCALE]), so the panel is the same size with or without the weather/compass rows. Fewer rows
     * mean slightly bigger text; with the rows on the text is a little smaller.
     */
    fun plan(inp: StampInput, panelWidthPx: Int, stretch: Float): StampPlan {
        val fullLw = FULL_W / stretch
        val target = build(inp.copy(sensors = true), fullLw, true, BASE_TS).hDu / fullLw * V_SCALE
        val minLw = MAP_W + 12f + 6f + 150f     // fixed, so the result never depends on the text
        fun ratio(l: Float) = build(inp, l, inp.sensors, BASE_TS).hDu / l
        var lw = fullLw
        if (minLw < fullLw) {
            if (ratio(minLw) <= target) {
                lw = minLw
            } else {
                var lo = minLw
                var hi = fullLw
                repeat(12) {
                    val mid = (lo + hi) / 2f
                    if (ratio(mid) > target) lo = mid else hi = mid
                }
                lw = hi
            }
        }
        val built = build(inp, lw, inp.sensors, BASE_TS)
        val w = max(1, panelWidthPx)
        val scale = w / lw
        return StampPlan(w, max(1, Fmt.round((built.hDu * scale).toDouble()).toInt()), scale, lw, inp, built)
    }

    /**
     * Draws the location QR on a rounded square of the given bounds. The code itself is inset by [pad] on every side,
     * so a little background colour surrounds it (a quiet zone that also helps scanners).
     */
    private fun drawQr(c: Canvas, lat: Double, lon: Double, st: QrStyle, l: Float, t: Float, r: Float, bt: Float, pad: Float, radius: Float) {
        val m = QrCode.modules(QrCode.locationText(lat, lon)) ?: return
        val outer = max(1, Math.round(min(r - l, bt - t)))
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.bg }
        c.drawRoundRect(RectF(l, t, l + outer, t + outer), radius, radius, bgPaint)
        val x0 = Math.round(l + pad); val y0 = Math.round(t + pad)
        val side = max(1, Math.round(outer - 2 * pad))
        val paint = Paint().apply { isAntiAlias = false; color = st.fg }
        val n = m.size
        // module edges are rounded from the same grid, so neighbouring modules never leave a seam
        for (y in 0 until n) {
            val ya = y0 + Math.round(y * side / n.toFloat()).toFloat()
            val yb = y0 + Math.round((y + 1) * side / n.toFloat()).toFloat()
            for (x in 0 until n) {
                if (!m.dark(x, y)) continue
                val xa = x0 + Math.round(x * side / n.toFloat()).toFloat()
                val xb = x0 + Math.round((x + 1) * side / n.toFloat()).toFloat()
                c.drawRect(xa, ya, xb, yb, paint)
            }
        }
    }

    /** Draws [plan] with its top-left at (left, top) in canvas pixels. */
    fun draw(c: Canvas, plan: StampPlan, left: Float, top: Float, opaque: Boolean = false) {
        val s = plan.scale
        val b = plan.b
        val w = plan.widthPx.toFloat()
        val h = plan.heightPx.toFloat()
        c.save()
        c.translate(left, top)
        val round = plan.input.rounded && !opaque
        val layer = if (round) c.saveLayer(0f, 0f, w, h, null) else -1
        val bg = Paint().apply { color = if (opaque) 0xFF121212.toInt() else Color.argb(140, 0, 0, 0) }
        c.drawRect(0f, 0f, w, h, bg)

        // map thumbnail, centre-cropped to a 70 du wide strip
        val mapW = MAP_W * s
        val mapL = if (b.mapRight) w - mapW else 0f
        c.save()
        c.clipRect(mapL, 0f, mapL + mapW, h)
        val thumb = plan.input.thumb
        if (thumb != null && thumb.width > 0 && thumb.height > 0) {
            val k = max(mapW / thumb.width, h / thumb.height)
            val dw = thumb.width * k
            val dh = thumb.height * k
            val dst = RectF(mapL + (mapW - dw) / 2f, (h - dh) / 2f, mapL + (mapW + dw) / 2f, (h + dh) / 2f)
            c.drawBitmap(thumb, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            c.drawRect(mapL, 0f, mapL + mapW, h, Paint().apply { color = 0xFF333333.toInt() })
        }
        c.restore()
        StampIcons.pin(c, mapL + mapW / 2f, (h - 8f * s) / 2f, 22f * s, 0xFF5A1010.toInt())

        val tp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        // title
        tp.typeface = FACE_BOLD; tp.textSize = b.titleSize * s; tp.color = Color.WHITE
        c.drawText(b.titleText, b.titleX * s, b.titleBase * s, tp)
        // address
        tp.typeface = FACE_MEDIUM; tp.textSize = 9.4f * b.ts * s; tp.color = 0xFFE6E6E6.toInt()
        b.addrLines.forEachIndexed { i, line -> c.drawText(line, b.titleX * s, (b.addrBase + i * b.addrLineH) * s, tp) }
        // watermark
        b.wmRect?.let { rc ->
            val qr = plan.input.qr
            if (qr != null) {
                drawQr(c, plan.input.data.lat, plan.input.data.lon, qr, rc.left * s, rc.top * s, rc.right * s, rc.bottom * s, QR_PAD_DU * s, QR_RADIUS_DU * s)
            } else plan.input.watermark?.let { bmp ->
                val dst = RectF(rc.left * s, rc.top * s, rc.right * s, rc.bottom * s)
                c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }
        }
        // left column
        b.leftLines.forEachIndexed { i, (text, face, color) ->
            tp.typeface = face; tp.textSize = 9.4f * b.ts * s; tp.color = color
            c.drawText(text, b.textLeft * s, (b.leftBase + i * b.leftLineH) * s, tp)
        }
        // right column metrics (right aligned)
        b.rightRows.forEachIndexed { i, m ->
            tp.typeface = FACE_BOLD; tp.textSize = 9.6f * b.ts * s; tp.color = Color.WHITE
            val tw = tp.measureText(m.text)
            val rowTop = (b.rowTop + i * b.rowH) * s
            val total = 14f * b.ts * s + tw
            val x0 = b.textRight * s - total
            val fm = tp.fontMetrics
            val baseline = rowTop + (b.rowH * s - (fm.descent - fm.ascent)) / 2f - fm.ascent
            StampIcons.draw(c, m.glyph, x0, rowTop + (b.rowH * s - 11f * b.ts * s) / 2f, 11f * b.ts * s, m.tint)
            c.drawText(m.text, x0 + 14f * b.ts * s, baseline, tp)
        }
        // bottom row
        b.bottomItems.forEachIndexed { i, m ->
            val f = b.bottomFont
            tp.typeface = FACE_BOLD; tp.textSize = 9.6f * b.ts * f * s; tp.color = Color.WHITE
            val x0 = b.bottomXs[i] * s
            val rowTop = b.bottomTop * s
            val fm = tp.fontMetrics
            val baseline = rowTop + (b.rowH * s - (fm.descent - fm.ascent)) / 2f - fm.ascent
            StampIcons.draw(c, m.glyph, x0, rowTop + (b.rowH * s - 11f * b.ts * f * s) / 2f, 11f * b.ts * f * s, m.tint)
            c.drawText(m.text, x0 + 14f * b.ts * f * s, baseline, tp)
        }
        if (round) {
            // Erase everything outside the rounded rectangle. A mask shape drawn with DST_IN would only touch the
            // pixels it covers and leave the corners in place, so the outside is cleared with an inverse-filled path.
            val r = min(CORNER_DU * s, min(w, h) / 3f)
            val outside = Path().apply {
                addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)
                fillType = Path.FillType.INVERSE_WINDING
            }
            val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            c.drawPath(outside, eraser)
            c.restoreToCount(layer)
        }
        c.restore()
    }
}

/** Where the geotag sits on an image, shared by the live overlay, the previews and the saved render. */
object Placement {
    /** Landscape images (width > 1.02 x height) keep the portrait physical size and sit flush. */
    fun isLandscape(iw: Int, ih: Int) = ih > 0 && iw > 1.02f * ih

    fun lift(iw: Int, ih: Int): Float = if (isLandscape(iw, ih)) 0f else 0.09f * iw

    fun panelWidth(iw: Int, ih: Int, widthFraction: Float): Int {
        var f = widthFraction
        if (isLandscape(iw, ih)) f = (f / (iw.toFloat() / ih)).coerceIn(0.05f, 1f)
        return min(iw, max(1, (iw * f).toInt()))
    }

    fun left(iw: Int, pw: Int, x: Float): Float = (iw - pw) * x

    fun top(ih: Int, ph: Int, y: Float, lift: Float, dragFraction: Float?): Float =
        if (dragFraction != null) max(0f, (ih - ph).toFloat()) * dragFraction
        else max(0f, ih - ph - lift) * y
}

/** Full-resolution composition used when saving. */
object StampRender {
    /**
     * Returns the photo with the geotag drawn on it ("on photo") or with a strip appended below it.
     * [dragFraction] is the per-photo vertical position chosen in the position editor (0 = top, 1 = bottom edge).
     */
    fun compose(
        photo: Bitmap,
        inp: StampInput,
        widthFraction: Float,
        stretch: Float,
        x: Float,
        y: Float,
        below: Boolean,
        dragFraction: Float?,
    ): Bitmap {
        val iw = photo.width
        val ih = photo.height
        if (below) {
            val plan = Stamp.plan(inp, iw, stretch)
            val out = Bitmap.createBitmap(iw, ih + plan.heightPx, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawBitmap(photo, 0f, 0f, null)
            Stamp.draw(c, plan, 0f, ih.toFloat(), opaque = true)
            return out
        }
        val out = photo.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val pw = Placement.panelWidth(iw, ih, widthFraction)
        val plan = Stamp.plan(inp, pw, stretch)
        val l = Placement.left(iw, plan.widthPx, x)
        val t = Placement.top(ih, plan.heightPx, y, Placement.lift(iw, ih), dragFraction)
        Stamp.draw(c, plan, l, t)
        return out
    }
}
