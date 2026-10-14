package com.Photocator.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import com.Photocator.ui.BackBtn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Photocator.Screen
import com.Photocator.Session
import com.Photocator.core.MapProviders
import com.Photocator.core.MapStyle
import com.Photocator.core.MapStyles
import com.Photocator.core.VolumeAction
import com.Photocator.stamp.QrCode
import com.Photocator.ui.C
import com.Photocator.ui.Card
import com.Photocator.ui.ConfirmDialog
import com.Photocator.ui.Host
import com.Photocator.ui.Sw
import com.Photocator.ui.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(session: Session, host: Host) {
    val prefs by session.prefs.flow.collectAsState()
    val marks by session.marks.flow.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    var styleDialog by remember { mutableStateOf(false) }
    var providerDialog by remember { mutableStateOf(false) }
    var qrDialog by remember { mutableStateOf(-1) }   // 0 = QR colour, 1 = background colour

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF050A12))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x552F8CFF), Color.Transparent),
                        center = Offset(size.width * 0.15f, 0f), radius = size.width * 1.1f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x2E3DDC84), Color.Transparent),
                        center = Offset(size.width, size.height * 0.55f), radius = size.width * 0.9f,
                    ),
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ---- header
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackBtn({ session.pop() })
                Column(Modifier.weight(1f)) {
                    T("Settings", 30f, weight = FontWeight.Bold)
                    T("Make Photocator work your way", 13f, C.TextDim)
                }
                Image(androidx.compose.ui.res.painterResource(com.Photocator.R.drawable.logo), null, Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)))
            }

            Section("Camera", C.Blue) {
                ValueRow(Icons.Filled.Storage, C.Blue, "Storage location", if (prefs.sdCard) "SD card" else "Phone") { session.prefs.update { it.copy(sdCard = !it.sdCard) } }
                Divider()
                ValueRow(Icons.Filled.Tune, C.Blue, "Volume buttons", when (prefs.volumeAction) { VolumeAction.SHUTTER -> "Shutter"; VolumeAction.ZOOM -> "Zoom"; VolumeAction.OFF -> "Off" }) {
                    session.prefs.update { it.copy(volumeAction = when (it.volumeAction) { VolumeAction.SHUTTER -> VolumeAction.ZOOM; VolumeAction.ZOOM -> VolumeAction.OFF; VolumeAction.OFF -> VolumeAction.SHUTTER }) }
                }
                Divider()
                SwitchRow(Icons.Filled.VolumeUp, C.Blue, "Shutter sound", prefs.shutterSound) { v -> session.prefs.update { it.copy(shutterSound = v) } }
                Divider()
                SwitchRow(Icons.Filled.Save, C.Blue, "Auto-save photos", prefs.autoSave) { v -> session.prefs.update { it.copy(autoSave = v) } }
            }

            Section("Geotag", C.Green) {
                ValueRow(
                    Icons.Filled.LocationOn, C.Green, "Size & position",
                    if (prefs.tagIsDefault) "Default" else "${(prefs.tagWidth * 100).toInt()}% wide", chevron = true,
                ) { session.push(Screen.TagEditor) }
                Divider()
                SwitchRow(Icons.Filled.Explore, C.Green, "Weather, compass & sensors", prefs.sensorData) { v -> session.prefs.update { it.copy(sensorData = v) } }
                Divider()
                ValueRow(
                    Icons.Filled.Layers, C.Green, "Watermark or QR code",
                    if (prefs.qrOn) "QR code" else marks.firstOrNull { it.id == prefs.watermarkId }?.name ?: "None", chevron = true,
                ) { session.push(Screen.Watermarks) }
            }

            Section("QR code", C.Cyan) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    QrPreview(QrCode.locationText(12.9716, 77.5946), prefs.qrFg, prefs.qrBg, 72.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T("Scan to open the location in maps. It follows the location of each photo, including edits.", 12f, C.TextDim)
                        T(if (prefs.qrOn) "Shown on the geotag" else "Turn it on in Watermark or QR code", 12f, if (prefs.qrOn) C.Green else C.TextFaint)
                    }
                }
                Divider()
                ValueRow(Icons.Filled.QrCode2, C.Cyan, "QR colour", hexOf(prefs.qrFg), swatch = Color(prefs.qrFg)) { qrDialog = 0 }
                Divider()
                ValueRow(Icons.Filled.FormatColorFill, C.Cyan, "Background colour", hexOf(prefs.qrBg), swatch = Color(prefs.qrBg)) { qrDialog = 1 }
                if (qrContrast(prefs.qrFg, prefs.qrBg) < 3.0) {
                    T("These colours are too close. Phones may not be able to scan the QR code.", 12f, C.Yellow, Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
                Divider()
                ValueRow(Icons.Filled.Refresh, C.Cyan, "Reset QR colours", "Black on white") {
                    session.prefs.update { it.copy(qrFg = 0xFF000000.toInt(), qrBg = 0xFFFFFFFF.toInt()) }
                }
            }

            Section("Map", C.Yellow) {
                ValueRow(Icons.Filled.Public, C.Yellow, "Map provider", MapProviders.byId(MapStyles.byId(prefs.mapStyleId).provider).title, chevron = true) { providerDialog = true }
                Divider()
                ValueRow(Icons.Filled.Map, C.Yellow, "Map style", MapStyles.byId(prefs.mapStyleId).title, chevron = true) { styleDialog = true }
                Divider()
                SwitchRow(Icons.Filled.Place, C.Yellow, "Show Plus Code", prefs.showPlus) { v -> session.prefs.update { it.copy(showPlus = v) } }
            }

            Section("Permissions", C.Cyan) {
                ValueRow(Icons.Filled.Lock, C.Cyan, "App permissions", "Manage", chevron = true) { host.openAppSettings() }
                Divider()
                ValueRow(Icons.Filled.GpsFixed, C.Cyan, "Location services", "GPS", chevron = true) { host.openLocationSettings() }
            }

            // ---- about
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF123A66), Color(0xFF0C1F36))))
                    .border(1.dp, Color(0x335AB4FF), RoundedCornerShape(22.dp))
                    .clickable { session.push(Screen.About) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Image(androidx.compose.ui.res.painterResource(com.Photocator.R.drawable.logo), null, Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)))
                Column(Modifier.weight(1f)) {
                    T("About Photocator", 16f, weight = FontWeight.SemiBold)
                    T("Version ${com.Photocator.BuildConfig.VERSION_NAME} · Karthik Hegde", 12f, C.TextDim)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.TextDim)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x1AFF5252))
                    .border(1.dp, Color(0x66FF5252), RoundedCornerShape(16.dp))
                    .clickable { confirmReset = true },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Refresh, null, tint = C.Red, modifier = Modifier.size(20.dp))
                    T("Reset settings", 15f, C.Red, weight = FontWeight.SemiBold)
                }
            }
            Box(Modifier.height(12.dp))
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            "Reset settings", "Reset all camera settings to their defaults?", "Reset",
            onConfirm = {
                confirmReset = false
                session.prefs.reset()
                session.applyLiveState()
                session.toast("Settings reset")
            },
            onDismiss = { confirmReset = false },
            danger = true,
        )
    }
    if (qrDialog >= 0) {
        val isFg = qrDialog == 0
        ColorDialog(if (isFg) "QR colour" else "Background colour", if (isFg) prefs.qrFg else prefs.qrBg, onDismiss = { qrDialog = -1 }) { c ->
            qrDialog = -1
            session.prefs.update { if (isFg) it.copy(qrFg = c) else it.copy(qrBg = c) }
        }
    }
    if (providerDialog) {
        val current = MapStyles.byId(prefs.mapStyleId).provider
        ProviderDialog(current) { id ->
            providerDialog = false
            if (id != null && id != current) {
                // switch to the provider's first style straight away so the setting is never invalid, then let the user choose
                MapStyles.forProvider(id).firstOrNull()?.let { st -> session.prefs.update { it.copy(mapStyleId = st.id) } }
                styleDialog = true
            }
        }
    }
    if (styleDialog) {
        StyleDialog(session, prefs.mapStyleId) { id ->
            styleDialog = false
            if (id != null) session.prefs.update { it.copy(mapStyleId = id) }
        }
    }
}

/** A titled group of rows on a frosted card. */
@Composable
private fun Section(title: String, accent: Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(width = 4.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            T(title.uppercase(), 12f, accent, weight = FontWeight.Bold)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xCC111B27))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(22.dp)),
        ) { content() }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 64.dp).height(1.dp).background(Color(0x14FFFFFF)))
}

@Composable
private fun IconBadge(icon: ImageVector, accent: Color) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ValueRow(icon: ImageVector, accent: Color, title: String, value: String, chevron: Boolean = false, swatch: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, accent)
        Box(Modifier.weight(1f)) { T(title, 15f, weight = FontWeight.Medium) }
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x1FFFFFFF)).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (swatch != null) Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(swatch).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp)))
            T(value, 13f, C.TextDim, maxLines = 1)
        }
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.TextFaint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, accent: Color, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, accent)
        Box(Modifier.weight(1f)) { T(title, 15f, weight = FontWeight.Medium) }
        Sw(checked, onChange)
    }
}

/** Radio list of the map providers. */
@Composable
private fun ProviderDialog(selected: String, onResult: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onResult(null) },
        containerColor = C.Card,
        title = { T("Map provider", 18f, weight = FontWeight.SemiBold) },
        text = {
            Column {
                MapProviders.all.forEach { pr ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onResult(pr.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            T(pr.title, 15f, weight = FontWeight.SemiBold, maxLines = 1)
                            T("${pr.blurb} · ${MapStyles.forProvider(pr.id).size} styles", 12f, C.TextDim, maxLines = 2)
                        }
                        RadioButton(pr.id == selected, { onResult(pr.id) }, colors = RadioButtonDefaults.colors(selectedColor = C.Blue))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onResult(null) }) { T("Close", 15f, C.Blue, weight = FontWeight.SemiBold) } },
    )
}

/** Radio list of the styles of the current provider; each row shows a sample tile fetched on demand. */
@Composable
private fun StyleDialog(session: Session, selected: String, onResult: (String?) -> Unit) {
    val provider = MapStyles.byId(selected).provider
    AlertDialog(
        onDismissRequest = { onResult(null) },
        containerColor = C.Card,
        title = { T("Map style · ${MapProviders.byId(provider).title}", 18f, weight = FontWeight.SemiBold) },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                items(MapStyles.forProvider(provider)) { st -> StyleRow(session, st, st.id == selected) { onResult(st.id) } }
            }
        },
        confirmButton = { TextButton(onClick = { onResult(null) }) { T("Close", 15f, C.Blue, weight = FontWeight.SemiBold) } },
    )
}

@Composable
private fun StyleRow(session: Session, st: MapStyle, selected: Boolean, onClick: () -> Unit) {
    val sample by produceState<Bitmap?>(null, st.id) {
        value = withContext(Dispatchers.IO) { session.tiles.loadTileBlocking(st.base, 4, 11, 7) }
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF333333))) {
            sample?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f)) {
            T(st.title, 15f, weight = FontWeight.SemiBold, maxLines = 1)
            T(st.blurb, 12f, C.TextDim, maxLines = 1)
        }
        RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = C.Blue))
    }
}
