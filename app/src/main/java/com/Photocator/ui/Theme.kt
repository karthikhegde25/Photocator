package com.Photocator.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

object C {
    val Black = Color(0xFF000000)
    val Ink = Color(0xFF0B1118)
    val Card = Color(0xFF16202B)
    val CardHi = Color(0xFF1E2B38)
    val Line = Color(0x33FFFFFF)
    val Blue = Color(0xFF5AB4FF)
    val BlueDark = Color(0xFF062033)
    val Green = Color(0xFF3DDC84)
    val Yellow = Color(0xFFFFD54F)
    val Red = Color(0xFFFF5252)
    val Cyan = Color(0xFF4DD0E1)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFFB4C0CC)
    val TextFaint = Color(0xFF7D8B99)
    val Glass = Color(0x66000000)
    val GlassLight = Color(0x26FFFFFF)
    val Navy = Color(0xFF0E2A47)
}

@Composable
fun PhotocatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Blue,
            onPrimary = C.BlueDark,
            background = C.Black,
            surface = C.Card,
            onSurface = C.Text,
            onBackground = C.Text,
        ),
        content = content,
    )
}

/** Plain text helper with sp sizes (ignores nothing special; the geotag is drawn natively and never scales). */
@Composable
fun T(
    text: String,
    size: Float = 14f,
    color: Color = C.Text,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    italic: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(lineHeight = (size * 1.3f).sp),
    )
}
