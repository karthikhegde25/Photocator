package com.Photocator.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Photocator.core.ShootMode

/** Hand-drawn mode icons (camera, moon with star, video camera) drawn on a 24 x 24 grid. */
@Composable
fun ModeIcon(mode: ShootMode, tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val sw = 1.9f * u
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        when (mode) {
            ShootMode.PHOTO -> {
                val body = Path().apply {
                    moveTo(3.5f * u, 8f * u)
                    lineTo(7.2f * u, 8f * u)
                    lineTo(8.8f * u, 5.5f * u)
                    lineTo(15.2f * u, 5.5f * u)
                    lineTo(16.8f * u, 8f * u)
                    lineTo(20.5f * u, 8f * u)
                    lineTo(20.5f * u, 19f * u)
                    lineTo(3.5f * u, 19f * u)
                    close()
                }
                drawPath(body, tint, style = stroke)
                drawCircle(tint, radius = 3.6f * u, center = o(12f, 13.2f), style = stroke)
            }
            ShootMode.NIGHT -> {
                // crescent moon: big disc minus an offset disc
                val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(o(3f, 3.5f), Size(15f * u, 15f * u))) }
                val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(o(8f, 1f), Size(14f * u, 14f * u))) }
                val moon = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, disc, cut)
                drawPath(moon, tint)
                // star
                val star = Path().apply {
                    val cx = 18.5f; val cy = 17f; val r = 3.6f; val ri = 1.2f
                    moveTo(cx * u, (cy - r) * u)
                    lineTo((cx + ri) * u, (cy - ri) * u)
                    lineTo((cx + r) * u, cy * u)
                    lineTo((cx + ri) * u, (cy + ri) * u)
                    lineTo(cx * u, (cy + r) * u)
                    lineTo((cx - ri) * u, (cy + ri) * u)
                    lineTo((cx - r) * u, cy * u)
                    lineTo((cx - ri) * u, (cy - ri) * u)
                    close()
                }
                drawPath(star, tint)
            }
            ShootMode.VIDEO -> {
                drawRoundRect(tint, topLeft = o(2.5f, 6.5f), size = Size(13f * u, 11f * u), cornerRadius = CornerRadius(2.8f * u), style = stroke)
                val lens = Path().apply {
                    moveTo(15.5f * u, 10.8f * u)
                    lineTo(21.3f * u, 7.4f * u)
                    lineTo(21.3f * u, 16.6f * u)
                    lineTo(15.5f * u, 13.2f * u)
                    close()
                }
                drawPath(lens, tint)
            }
        }
    }
}
