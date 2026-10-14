package com.Photocator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.Photocator.stamp.Placement
import com.Photocator.stamp.Stamp
import com.Photocator.stamp.StampInput
import com.Photocator.stamp.StampPlan
import kotlin.math.max
import kotlin.math.min

/** Geometry the live overlay, previews and editors share. */
data class StampGeometry(
    val widthFraction: Float,
    val stretch: Float,
    val x: Float,
    val y: Float,
)

/**
 * Draws the geotag over an area (the viewfinder or a displayed photo) with exactly the numbers used by the saved render.
 * [tilt] rotates the panel flush to a side edge (live overlay only). [dragFraction] is the per-photo vertical position.
 * The overlay never intercepts touches.
 */
@Composable
fun StampLayer(
    modifier: Modifier,
    input: StampInput,
    geo: StampGeometry,
    tilt: Int = 0,
    dragFraction: Float? = null,
    below: Boolean = false,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val plan: StampPlan? = remember(input, box, geo, tilt, below) {
        if (box.width <= 0 || box.height <= 0) null
        else {
            val vw = box.width
            val vh = box.height
            val pw = when {
                below -> vw
                tilt != 0 -> min(max(1, (vw * geo.widthFraction).toInt()), vh)
                else -> Placement.panelWidth(vw, vh, geo.widthFraction)
            }
            Stamp.plan(input, pw, geo.stretch)
        }
    }
    Canvas(modifier.clipToBounds().onSizeChanged { box = it }) {
        val p = plan ?: return@Canvas
        val vw = box.width
        val vh = box.height
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            when {
                tilt == 0 -> {
                    val l = Placement.left(vw, p.widthPx, geo.x)
                    val t = Placement.top(vh, p.heightPx, geo.y, Placement.lift(vw, vh), dragFraction)
                    Stamp.draw(nc, p, l, t, opaque = below)
                }
                tilt > 0 -> {
                    // phone turned clockwise: panel rotated 90 degrees counter-clockwise, flush against the right edge
                    val off = (vh - p.widthPx) * (1f - geo.x)
                    nc.save()
                    nc.translate(vw - p.heightPx.toFloat(), off + p.widthPx)
                    nc.rotate(-90f)
                    Stamp.draw(nc, p, 0f, 0f)
                    nc.restore()
                }
                else -> {
                    val off = (vh - p.widthPx) * geo.x
                    nc.save()
                    nc.translate(p.heightPx.toFloat(), off)
                    nc.rotate(90f)
                    Stamp.draw(nc, p, 0f, 0f)
                    nc.restore()
                }
            }
        }
    }
}
