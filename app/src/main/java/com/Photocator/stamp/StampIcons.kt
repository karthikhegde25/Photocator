package com.Photocator.stamp

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

enum class Glyph { SUN, CLOUD, DRIZZLE, UMBRELLA, SNOW, BOLT, COMPASS, DROP, WIND, MOUNTAIN, WAVES }

/** Small hand-drawn glyphs, drawn in a unit square then scaled to [size]. */
object StampIcons {
    fun forCondition(c: String): Glyph = when (c) {
        "Clear" -> Glyph.SUN
        "Drizzle" -> Glyph.DRIZZLE
        "Rain", "Showers" -> Glyph.UMBRELLA
        "Snow" -> Glyph.SNOW
        "Thunderstorm" -> Glyph.BOLT
        else -> Glyph.CLOUD
    }

    fun tintFor(c: String): Int = if (c == "Clear" || c == "Thunderstorm") 0xFFFFD54F.toInt() else 0xFF90CAF9.toInt()

    private fun newFill() = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private fun newStroke() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 0.09f
    }

    fun draw(c: Canvas, g: Glyph, x: Float, y: Float, size: Float, color: Int) {
        val fill = newFill()
        val stroke = newStroke()
        c.save()
        c.translate(x, y)
        c.scale(size, size)
        fill.color = color
        stroke.color = color
        when (g) {
            Glyph.SUN -> {
                c.drawCircle(0.5f, 0.5f, 0.2f, fill)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val cs = Math.cos(a).toFloat(); val sn = Math.sin(a).toFloat()
                    c.drawLine(0.5f + cs * 0.31f, 0.5f + sn * 0.31f, 0.5f + cs * 0.45f, 0.5f + sn * 0.45f, stroke)
                }
            }
            Glyph.CLOUD -> cloud(c, 0f, fill)
            Glyph.DRIZZLE -> {
                cloud(c, -0.1f, fill)
                for (dx in floatArrayOf(0.3f, 0.5f, 0.7f)) c.drawCircle(dx, 0.82f, 0.05f, fill)
            }
            Glyph.UMBRELLA -> {
                c.drawArc(RectF(0.08f, 0.1f, 0.92f, 0.78f), 180f, 180f, true, fill)
                c.drawLine(0.5f, 0.44f, 0.5f, 0.86f, stroke)
                c.drawArc(RectF(0.5f, 0.74f, 0.7f, 0.94f), 0f, 180f, false, stroke)
            }
            Glyph.SNOW -> {
                for (i in 0 until 3) {
                    val a = Math.toRadians(i * 60.0)
                    val cs = Math.cos(a).toFloat() * 0.4f; val sn = Math.sin(a).toFloat() * 0.4f
                    c.drawLine(0.5f - cs, 0.5f - sn, 0.5f + cs, 0.5f + sn, stroke)
                }
            }
            Glyph.BOLT -> {
                val p = Path()
                p.moveTo(0.58f, 0.04f); p.lineTo(0.22f, 0.54f); p.lineTo(0.47f, 0.54f)
                p.lineTo(0.38f, 0.96f); p.lineTo(0.8f, 0.4f); p.lineTo(0.55f, 0.4f); p.close()
                c.drawPath(p, fill)
            }
            Glyph.COMPASS -> {
                c.drawCircle(0.5f, 0.5f, 0.4f, stroke)
                val up = Path(); up.moveTo(0.5f, 0.16f); up.lineTo(0.62f, 0.5f); up.lineTo(0.38f, 0.5f); up.close()
                c.drawPath(up, fill)
                val dn = Path(); dn.moveTo(0.5f, 0.84f); dn.lineTo(0.62f, 0.5f); dn.lineTo(0.38f, 0.5f); dn.close()
                c.drawPath(dn, stroke)
            }
            Glyph.DROP -> {
                val p = Path()
                p.moveTo(0.5f, 0.06f)
                p.cubicTo(0.78f, 0.42f, 0.82f, 0.56f, 0.82f, 0.64f)
                p.arcTo(RectF(0.18f, 0.46f, 0.82f, 0.9f), 0f, 180f, false)
                p.cubicTo(0.18f, 0.56f, 0.22f, 0.42f, 0.5f, 0.06f)
                p.close()
                c.drawPath(p, fill)
            }
            Glyph.WIND -> {
                c.drawLine(0.08f, 0.32f, 0.62f, 0.32f, stroke)
                c.drawArc(RectF(0.5f, 0.14f, 0.84f, 0.5f), 90f, -270f, false, stroke)
                c.drawLine(0.08f, 0.52f, 0.78f, 0.52f, stroke)
                c.drawLine(0.08f, 0.72f, 0.5f, 0.72f, stroke)
                c.drawArc(RectF(0.38f, 0.56f, 0.7f, 0.9f), 270f, 270f, false, stroke)
            }
            Glyph.MOUNTAIN -> {
                val p = Path()
                p.moveTo(0.04f, 0.86f); p.lineTo(0.38f, 0.22f); p.lineTo(0.58f, 0.58f)
                p.lineTo(0.7f, 0.4f); p.lineTo(0.96f, 0.86f); p.close()
                c.drawPath(p, fill)
            }
            Glyph.WAVES -> {
                c.drawCircle(0.5f, 0.5f, 0.09f, fill)
                c.drawArc(RectF(0.28f, 0.28f, 0.72f, 0.72f), -45f, 90f, false, stroke)
                c.drawArc(RectF(0.28f, 0.28f, 0.72f, 0.72f), 135f, 90f, false, stroke)
                c.drawArc(RectF(0.1f, 0.1f, 0.9f, 0.9f), -45f, 90f, false, stroke)
                c.drawArc(RectF(0.1f, 0.1f, 0.9f, 0.9f), 135f, 90f, false, stroke)
            }
        }
        c.restore()
    }

    private fun cloud(c: Canvas, dy: Float, fill: Paint) {
        c.drawCircle(0.32f, 0.58f + dy, 0.17f, fill)
        c.drawCircle(0.52f, 0.46f + dy, 0.22f, fill)
        c.drawCircle(0.74f, 0.6f + dy, 0.15f, fill)
        c.drawRect(0.32f, 0.58f + dy, 0.74f, 0.75f + dy, fill)
    }

    /** Red map pin, [h] tall, centred at (cx, cy). */
    fun pin(c: Canvas, cx: Float, cy: Float, h: Float, hole: Int) {
        val fill = newFill()
        val w = h * 0.66f
        val r = w / 2f
        val top = cy - h / 2f
        val p = Path()
        p.moveTo(cx, cy + h / 2f)
        p.cubicTo(cx - r * 0.2f, cy + h * 0.2f, cx - r, cy + h * 0.02f, cx - r, top + r)
        p.arcTo(RectF(cx - r, top, cx + r, top + w), 180f, 180f, false)
        p.cubicTo(cx + r, cy + h * 0.02f, cx + r * 0.2f, cy + h * 0.2f, cx, cy + h / 2f)
        p.close()
        fill.color = 0xFFE53935.toInt()
        c.drawPath(p, fill)
        fill.color = hole
        c.drawCircle(cx, top + r, r * 0.38f, fill)
    }
}
