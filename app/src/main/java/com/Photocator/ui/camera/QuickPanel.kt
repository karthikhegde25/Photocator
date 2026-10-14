package com.Photocator.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.Photocator.Screen
import com.Photocator.Session
import com.Photocator.core.AspectKind
import com.Photocator.core.GridKind
import com.Photocator.core.QualityKind
import com.Photocator.ui.C
import com.Photocator.ui.RoundBtn
import com.Photocator.ui.Segmented
import com.Photocator.ui.T

/** The gear panel: aspect, timer, quality, grid, watermark and the Location (geotag) switch. */
@Composable
fun QuickPanel(session: Session, modifier: Modifier = Modifier) {
    val prefs by session.prefs.flow.collectAsState()
    val marks by session.marks.flow.collectAsState()
    val shoot = session.shoot
    val close = { shoot.panelOpen.value = false }

    var contentBottom by remember { mutableStateOf(Int.MAX_VALUE) }
    // Tapping the empty dark area around the controls closes the panel; taps on the controls do not.
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .pointerInput(Unit) { detectTapGestures { p -> if (p.y > contentBottom) close() } },
    ) {
    Column(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { contentBottom = it.height }
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(Icons.Filled.Close, { close() }, size = 40.dp, bg = C.GlassLight, desc = "Close")
            Box(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).clickable { close(); session.push(Screen.Settings) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T("More settings", 14f, C.Blue, weight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Blue)
            }
        }

        Label("Shape")
        val aspects = listOf(AspectKind.SQUARE to "1:1", AspectKind.R4_3 to "4:3", AspectKind.R16_9 to "16:9", AspectKind.FULL to "Full")
        Segmented(aspects.map { it.second }, aspects.indexOfFirst { it.first == prefs.aspect }.coerceAtLeast(0), {
            session.prefs.update { p -> p.copy(aspect = aspects[it].first) }; shoot.touchDial()
        })

        Label("Self-timer")
        val timers = listOf(0, 2, 5, 10)
        Segmented(listOf("Off", "2s", "5s", "10s"), timers.indexOf(prefs.timerSec).coerceAtLeast(0), {
            session.prefs.update { p -> p.copy(timerSec = timers[it]) }; shoot.touchDial()
        })

        Label("Quality")
        val quals = listOf(QualityKind.STANDARD, QualityKind.HIGH, QualityKind.ULTRA)
        Segmented(listOf("Standard", "High", "Ultra"), quals.indexOf(prefs.quality), {
            session.prefs.update { p -> p.copy(quality = quals[it]) }; shoot.touchDial()
        })

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val gridText = when (prefs.grid) { GridKind.OFF -> "Off"; GridKind.THIRDS -> "3x3"; GridKind.GOLDEN -> "Golden" }
            Tile("Grid lines", gridText, prefs.grid != GridKind.OFF, Modifier.weight(1f)) {
                session.prefs.update { p ->
                    p.copy(grid = when (p.grid) { GridKind.OFF -> GridKind.THIRDS; GridKind.THIRDS -> GridKind.GOLDEN; GridKind.GOLDEN -> GridKind.OFF })
                }
                shoot.touchDial()
            }
            // one tap cycles: off -> watermark -> QR code -> off (only one of them at a time)
            Tile("Watermark / QR", if (prefs.qrOn) "QR" else if (prefs.watermarkId != null) "Logo" else "Off", prefs.qrOn || prefs.watermarkId != null, Modifier.weight(1f)) {
                val cur = prefs.watermarkId
                when {
                    prefs.qrOn -> session.prefs.update { p -> p.copy(qrOn = false) }
                    cur != null -> session.prefs.update { p -> p.copy(watermarkId = null, rememberedWatermarkId = cur, qrOn = true) }
                    prefs.rememberedWatermarkId != null && marks.any { it.id == prefs.rememberedWatermarkId } ->
                        session.prefs.update { p -> p.copy(watermarkId = p.rememberedWatermarkId, qrOn = false) }
                    else -> session.prefs.update { p -> p.copy(qrOn = true) }
                }
                shoot.touchDial()
            }
            Tile("Location", if (prefs.tagEnabled) "On" else "Off", prefs.tagEnabled, Modifier.weight(1f)) {
                session.prefs.update { p -> p.copy(tagEnabled = !p.tagEnabled) }
                session.applyLiveState()
                shoot.touchDial()
            }
        }
        T("Turning Location off hides the geotag on photos and videos.", 12f, C.TextFaint)
    }
    }
}

@Composable
private fun Label(text: String) { T(text, 13f, C.TextDim, weight = FontWeight.SemiBold) }

@Composable
private fun Tile(title: String, state: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) C.Blue else Color(0xFF1B2733))
            .border(1.dp, if (active) C.Blue else C.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        T(title, 13f, if (active) C.BlueDark else C.TextDim, weight = FontWeight.SemiBold, maxLines = 1)
        T(state, 18f, if (active) C.BlueDark else Color.White, weight = FontWeight.Bold)
    }
}
