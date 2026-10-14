package com.Photocator.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Photocator.stamp.QrCode
import com.Photocator.ui.C
import com.Photocator.ui.T

private val SWATCHES = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFF1E1E1E, 0xFF616161, 0xFFBDBDBD, 0xFFF5F0E1,
    0xFF0D47A1, 0xFF1E88E5, 0xFF00897B, 0xFF2E7D32, 0xFFC0CA33, 0xFFFFD600,
    0xFFEF6C00, 0xFFD32F2F, 0xFFAD1457, 0xFF6A1B9A, 0xFF4E342E, 0xFF00BCD4,
).map { it.toInt() }

fun hexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/** Parses "#RRGGBB" or "RRGGBB" into an opaque ARGB int, or null. */
private fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6) return null
    val v = t.toLongOrNull(16) ?: return null
    return (0xFF000000 or v).toInt()
}

/** Contrast ratio of the two colours; scanners need roughly 3 or more. */
fun qrContrast(fg: Int, bg: Int): Double = androidx.core.graphics.ColorUtils.calculateContrast(fg or 0xFF000000.toInt(), bg or 0xFF000000.toInt())

/** The QR exactly as it is stamped: a rounded block with the code inset by a little padding (same proportions as the geotag). */
@Composable
fun QrPreview(text: String, fg: Int, bg: Int, size: Dp, modifier: Modifier = Modifier) {
    val m = remember(text) { QrCode.modules(text) }
    Canvas(modifier.size(size)) {
        val outer = this.size.width
        val radius = outer * (3.5f / 30f)
        val pad = outer * (2.5f / 30f)
        drawRoundRect(Color(bg), cornerRadius = CornerRadius(radius, radius))
        val mm = m ?: return@Canvas
        val n = mm.size
        val inner = Math.round(outer - 2 * pad).toFloat()
        val x0 = Math.round(pad).toFloat()
        for (y in 0 until n) for (x in 0 until n) {
            if (!mm.dark(x, y)) continue
            val xa = x0 + Math.round(x * inner / n).toFloat()
            val ya = x0 + Math.round(y * inner / n).toFloat()
            val xb = x0 + Math.round((x + 1) * inner / n).toFloat()
            val yb = x0 + Math.round((y + 1) * inner / n).toFloat()
            drawRect(Color(fg), Offset(xa, ya), Size(xb - xa, yb - ya))
        }
    }
}

/** Pick one of the swatches or type a hex colour. */
@Composable
fun ColorDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var hex by remember { mutableStateOf(hexOf(initial)) }
    val parsed = parseHex(hex)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { T(title, 18f, weight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SWATCHES.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { c ->
                            val sel = parsed == c
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(if (sel) 3.dp else 1.dp, if (sel) C.Blue else C.Line, CircleShape)
                                    .clickable { hex = hexOf(c) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = hex, onValueChange = { hex = it.take(7) }, singleLine = true,
                    label = { T("Hex colour", 12f, C.TextDim) },
                    isError = parsed == null,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Blue, unfocusedBorderColor = C.Line, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = C.Blue),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onPick) }, enabled = parsed != null) { T("Save", 15f, if (parsed != null) C.Blue else C.TextFaint, weight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { T("Cancel", 15f, C.TextDim) } },
    )
}
