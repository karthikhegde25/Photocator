package com.Photocator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Photocator.Session

@Composable
fun Spin(modifier: Modifier = Modifier, size: Dp = 22.dp, color: Color = C.Blue) {
    CircularProgressIndicator(modifier = modifier.size(size), color = color, strokeWidth = 2.dp)
}

/** Round icon button. */
@Composable
fun RoundBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    bg: Color = C.Glass,
    tint: Color = C.Text,
    border: Color? = null,
    enabled: Boolean = true,
    desc: String? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .let { if (border != null) it.border(BorderStroke(1.5.dp, border), CircleShape) else it }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = if (enabled) tint else tint.copy(alpha = 0.4f), modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun BackBtn(onClick: () -> Unit, modifier: Modifier = Modifier) {
    RoundBtn(Icons.AutoMirrored.Filled.ArrowBack, onClick, modifier, size = 40.dp, bg = C.GlassLight, desc = "Back")
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = C.Green,
    fg: Color = Color(0xFF04210F),
    enabled: Boolean = true,
    busy: Boolean = false,
    outline: Color? = null,
) {
    Box(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .let { if (outline != null) it.border(BorderStroke(1.5.dp, outline), RoundedCornerShape(14.dp)) else it }
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) Spin(color = fg) else T(text, 15f, fg, weight = FontWeight.SemiBold)
    }
}

/** Segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(C.GlassLight)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) C.Blue else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                T(label, 13f, if (sel) C.BlueDark else C.TextDim, weight = if (sel) FontWeight.SemiBold else FontWeight.Normal, align = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
fun Sw(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White, uncheckedTrackColor = C.CardHi, uncheckedThumbColor = C.TextDim),
    )
}

/** Header with a back arrow and a title. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) BackBtn(onBack)
        Box(Modifier.weight(1f)) { T(title, 20f, weight = FontWeight.SemiBold, maxLines = 1) }
        trailing()
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.Card)
            .padding(vertical = 4.dp),
    ) { content() }
}

/** One-button alert host. Place once at the root. */
@Composable
fun AlertHost(session: Session) {
    val a by session.alert.collectAsState()
    val msg = a ?: return
    AlertDialog(
        onDismissRequest = { session.dismissAlert() },
        confirmButton = { TextButton(onClick = { session.dismissAlert() }) { T("OK", 15f, C.Blue, weight = FontWeight.SemiBold) } },
        title = if (msg.title != null) { { T(msg.title, 18f, weight = FontWeight.SemiBold) } } else null,
        text = { T(msg.message, 15f, C.TextDim) },
        containerColor = C.Card,
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { T(confirmLabel, 15f, if (danger) C.Red else C.Blue, weight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { T("Cancel", 15f, C.TextDim) } },
        title = { T(title, 18f, weight = FontWeight.SemiBold) },
        text = { T(message, 15f, C.TextDim) },
        containerColor = C.Card,
    )
}
