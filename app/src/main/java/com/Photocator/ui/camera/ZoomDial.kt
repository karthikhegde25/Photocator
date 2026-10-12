package com.Photocator.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.Photocator.ui.C
import com.Photocator.ui.T
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DEG_PER_X = 26f

/** Transparent protractor: a half disc hanging below the zoom chip. Drag, tap a number, or pick a quick value. */
@Composable
fun ZoomDial(
    zoom: Float,
    maxZoom: Float,
    onZoom: (Float) -> Unit,
    onTouch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val zoomNow by rememberUpdatedState(zoom)
    val maxNow by rememberUpdatedState(maxZoom)
    val dpPx = density.density
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(320.dp)
                .height(168.dp)
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { onTouch() }) { change, drag ->
                        change.consume()
                        onTouch()
                        // dragging right shows lower values; about 70 dp of travel per 1.0x
                        val d = drag.x / (70f * dpPx)
                        onZoom((zoomNow - d).coerceIn(1f, maxNow))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        onTouch()
                        val cx = size.width / 2f
                        val ang = Math.toDegrees(atan2(p.y.toDouble(), (p.x - cx).toDouble())).toFloat()
                        val v = zoomNow + (90f - ang) / DEG_PER_X
                        onZoom(((v * 2f).roundToInt() / 2f).coerceIn(1f, maxNow))
                    }
                },
        ) {
            Canvas(Modifier.width(320.dp).height(168.dp)) {
                val radius = 0.92f * 168.dp.toPx()
                val cx = size.width / 2f
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 11.dp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                var step = 0
                var v = 1f
                while (v <= maxZoom + 0.001f) {
                    val ang = 90f - (v - zoom) * DEG_PER_X
                    if (ang in 12f..168f) {
                        val whole = step % 10 == 0
                        val half = step % 5 == 0
                        val len = if (whole) 16.dp.toPx() else if (half) 11.dp.toPx() else 7.dp.toPx()
                        val fade = (1f - abs(ang - 90f) / 78f * 0.75f).coerceIn(0.25f, 1f)
                        val rad = Math.toRadians(ang.toDouble())
                        val ca = cos(rad).toFloat(); val sa = sin(rad).toFloat()
                        drawLine(
                            Color.White.copy(alpha = fade),
                            Offset(cx + ca * radius, sa * radius),
                            Offset(cx + ca * (radius - len), sa * (radius - len)),
                            strokeWidth = if (whole) 2.dp.toPx() else 1.dp.toPx(),
                        )
                        if (whole) {
                            paint.color = android.graphics.Color.argb((fade * 255).toInt(), 255, 255, 255)
                            drawIntoCanvas { c ->
                                val lr = radius - len - 6.dp.toPx()
                                c.nativeCanvas.drawText("${v.roundToInt()}x", cx + ca * lr, sa * lr, paint)
                            }
                        }
                    }
                    step++
                    v = 1f + step * 0.1f
                }
                // fixed pointer at the lowest point
                drawLine(C.Yellow, Offset(cx, radius + 4.dp.toPx()), Offset(cx, radius - 22.dp.toPx()), strokeWidth = 2.5.dp.toPx())
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (q in listOf(1f, 2f, 3f, 5f, 8f, 10f)) {
                if (q > maxZoom + 0.001f) continue
                val sel = abs(zoom - q) <= 0.05f
                Box(
                    Modifier
                        .clickable { onTouch(); onZoom(q) }
                        .background(if (sel) C.Yellow else C.Glass, RoundedCornerShape(16.dp))
                        .border(1.dp, if (sel) C.Yellow else C.Line, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { T("${q.toInt()}x", 13f, if (sel) Color.Black else Color.White, weight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
            }
        }
    }
}
