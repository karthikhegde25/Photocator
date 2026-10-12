package com.Photocator.ui.camera

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.Photocator.Screen
import com.Photocator.Session
import com.Photocator.core.AspectKind
import com.Photocator.core.FlashKind
import com.Photocator.core.GridKind
import com.Photocator.core.ShootMode
import com.Photocator.media.Pics
import com.Photocator.shoot.BindSpec
import com.Photocator.stamp.StampInput
import com.Photocator.ui.C
import com.Photocator.ui.Host
import com.Photocator.ui.RoundBtn
import com.Photocator.ui.Spin
import com.Photocator.ui.StampGeometry
import com.Photocator.ui.StampLayer
import com.Photocator.ui.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ShootScreen(session: Session, host: Host) {
    val perms by session.perms.collectAsState()
    val requesting by session.requesting.collectAsState()
    when {
        requesting -> Box(Modifier.fillMaxSize().background(C.Black), contentAlignment = Alignment.Center) { Spin(size = 36.dp) }
        !perms.all -> DeniedScreen(session, host)
        else -> CameraBody(session, host)
    }
}

@Composable
private fun DeniedScreen(session: Session, host: Host) {
    val perms by session.perms.collectAsState()
    Column(
        Modifier.fillMaxSize().background(C.Black).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T("Permissions required", 24f, weight = FontWeight.Bold, align = TextAlign.Center)
        T(
            "Photocator needs these to take geotagged photos. If the system no longer asks, open the app settings and turn them on under Permissions.",
            14f, C.TextDim, Modifier.padding(vertical = 16.dp), align = TextAlign.Center,
        )
        PermRow("Camera", "take photos", perms.camera)
        PermRow("Location", "add GPS data to your photos", perms.location)
        PermRow("Photos and media", "save to your album", perms.media)
        Box(Modifier.height(24.dp))
        com.Photocator.ui.BigButton("Open app settings", { host.openAppSettings() }, Modifier.fillMaxWidth())
        Box(Modifier.height(10.dp))
        com.Photocator.ui.BigButton("Try again", { host.requestPermissions() }, Modifier.fillMaxWidth(), bg = C.GlassLight, fg = Color.White)
    }
}

@Composable
private fun PermRow(title: String, desc: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        T(if (ok) "✓" else "✕", 20f, if (ok) C.Green else C.Red, Modifier.width(32.dp), weight = FontWeight.Bold)
        Column {
            T(title, 15f, weight = FontWeight.SemiBold)
            T(desc, 12f, C.TextFaint)
        }
    }
}

@Composable
private fun CameraBody(session: Session, host: Host) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val owner = LocalLifecycleOwner.current
    val shoot = session.shoot
    val rig = session.rig
    val prefs by session.prefs.flow.collectAsState()
    val live by session.pos.live.collectAsState()
    val tilt by session.tilt.tilt.collectAsState()
    val mode by shoot.mode.collectAsState()
    val front by shoot.front.collectAsState()
    val zoom by shoot.zoom.collectAsState()
    val dialOpen by shoot.dialOpen.collectAsState()
    val panelOpen by shoot.panelOpen.collectAsState()
    val countdown by shoot.countdown.collectAsState()
    val busy by shoot.busy.collectAsState()
    val recording by shoot.recording.collectAsState()
    val recordSecs by shoot.recordSecs.collectAsState()
    val holdSteady by shoot.holdSteady.collectAsState()
    val dialTouch by shoot.dialTouch.collectAsState()
    val maxZoom by rig.maxZoom.collectAsState()
    val nightExt by rig.nightExtension.collectAsState()
    val unavailable by rig.unavailable.collectAsState()
    val frontMissing by rig.frontMissing.collectAsState()
    val providerReady by rig.providerReady.collectAsState()

    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(500); mounted = true }
    LaunchedEffect(Unit) { rig.init() }
    LaunchedEffect(frontMissing) { if (frontMissing && front) shoot.front.value = false }

    // the dial closes itself 3 s after the last interaction
    LaunchedEffect(dialOpen, dialTouch) {
        if (dialOpen) { delay(3000); shoot.dialOpen.value = false }
    }

    if (unavailable) {
        Column(Modifier.fillMaxSize().background(C.Black).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            T("Camera unavailable", 22f, weight = FontWeight.Bold, align = TextAlign.Center)
            T("The camera is restricted by the system. Check the device settings or parental controls.", 14f, C.TextDim, Modifier.padding(top = 12.dp), align = TextAlign.Center)
        }
        return
    }
    if (!mounted || !providerReady) {
        Box(Modifier.fillMaxSize().background(C.Black), contentAlignment = Alignment.Center) { Spin(size = 36.dp) }
        return
    }

    val previewView = remember {
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(C.Black)) {
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
        val topZone = statusPx + with(density) { 64.dp.toPx() }
        var barH by remember { mutableIntStateOf(0) }
        val barPx = if (barH == 0) with(density) { 164.dp.toPx() } else barH.toFloat()
        val avail = max(with(density) { 120.dp.toPx() }, sh - topZone - barPx)

        // viewfinder rectangle (what you see is what is saved)
        val ratio = prefs.aspect.ratioWH
        val vfW: Float
        val vfH: Float
        if (ratio == null) { vfW = sw; vfH = avail }
        else if (sw / ratio <= avail) { vfW = sw; vfH = sw / ratio }
        else { vfH = avail; vfW = avail * ratio }
        val vfX = (sw - vfW) / 2f
        val vwI = vfW.roundToInt()
        val vhI = vfH.roundToInt()
        val wide = prefs.aspect == AspectKind.R16_9 || (prefs.aspect == AspectKind.FULL && vfW / vfH < 0.62f)

        LaunchedEffect(front, mode, wide, vwI, vhI) {
            val ok = rig.bind(owner, previewView, BindSpec(front, mode, wide, vwI, vhI, prefs.flash))
            if (ok) {
                shoot.zoom.value = 1f
                rig.setFlash(session.prefs.value.flash, mode)
            }
        }

        // ---- viewfinder
        Box(
            Modifier
                .offset { IntOffset(vfX.roundToInt(), topZone.roundToInt()) }
                .size(with(density) { vfW.toDp() }, with(density) { vfH.toDp() })
                .clip(RoundedCornerShape(0.dp)),
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // grid
            if (prefs.grid != GridKind.OFF) {
                val fr = if (prefs.grid == GridKind.THIRDS) floatArrayOf(1f / 3f, 2f / 3f) else floatArrayOf(0.382f, 0.618f)
                Canvas(Modifier.fillMaxSize()) {
                    for (f in fr) {
                        drawLine(Color.White.copy(alpha = 0.3f), Offset(size.width * f, 0f), Offset(size.width * f, size.height), strokeWidth = 1.dp.toPx())
                        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 1.dp.toPx())
                    }
                }
            }

            // live geotag
            if (prefs.tagEnabled) {
                val wm = remember(prefs.watermarkId, session.marks.flow.collectAsState().value) { session.marks.load(session.marks.find(prefs.watermarkId)) }
                val d = live.data
                val geo = StampGeometry(prefs.tagWidth, prefs.tagStretch, prefs.tagX, prefs.tagY)
                if (d != null) {
                    StampLayer(Modifier.fillMaxSize(), StampInput(d, live.thumb, wm, prefs.showPlus, prefs.sensorData), geo, tilt)
                } else {
                    val off = live.locationSwitchOff
                    val text = if (off) "Location is off - turn on GPS in your phone settings (tap to open Location settings)" else "Acquiring GPS..."
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .padding(bottom = with(density) { (0.09f * vfW).toDp() })
                                .fillMaxWidth(prefs.tagWidth)
                                .background(Color(0x8C000000))
                                .let { if (off) it.clickable { host.openLocationSettings() } else it }
                                .padding(12.dp),
                        ) { T(text, 14f, Color.White) }
                    }
                }
            }

            // gestures: tap to focus (and capture), two-finger pinch zoom
            var focusPt by remember { mutableStateOf<Offset?>(null) }
            var focusKey by remember { mutableIntStateOf(0) }
            val focusAlpha = remember { Animatable(0f) }
            LaunchedEffect(focusKey) {
                if (focusKey == 0) return@LaunchedEffect
                focusAlpha.snapTo(0f)
                focusAlpha.animateTo(1f, tween(200))
                delay(800)
                focusAlpha.animateTo(0f, tween(200))
            }
            val slop = LocalViewConfiguration.current.touchSlop
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        var multi = false
                        var moved = false
                        do {
                            val ev = awaitPointerEvent()
                            if (ev.changes.size >= 2) {
                                multi = true
                                val z = ev.calculateZoom()
                                if (z != 1f) {
                                    shoot.setZoom(shoot.zoom.value * z)
                                    ev.changes.forEach { it.consume() }
                                }
                            } else if (!multi) {
                                if ((ev.changes[0].position - first.position).getDistance() > slop) moved = true
                            }
                        } while (ev.changes.any { it.pressed })
                        if (!multi && !moved) {
                            focusPt = first.position
                            focusKey += 1
                            rig.focusAt(previewView, first.position.x, first.position.y)
                        }
                    }
                },
            )
            focusPt?.let { p ->
                if (focusAlpha.value > 0f) {
                    Box(
                        Modifier
                            .offset { IntOffset((p.x - 40.dp.toPx()).roundToInt(), (p.y - 40.dp.toPx()).roundToInt()) }
                            .size(80.dp)
                            .border(2.dp, C.Blue.copy(alpha = focusAlpha.value), CircleShape),
                    )
                }
            }

            // status chip
            val chip: String? = when (mode) {
                ShootMode.PHOTO -> null
                ShootMode.NIGHT -> if (holdSteady) "Hold steady" else if (nightExt) "Night mode" else "Night mode with low-light boost"
                ShootMode.VIDEO -> if (recording) null else "Video"
            }
            if (chip != null || (mode == ShootMode.VIDEO && recording)) {
                Row(
                    Modifier
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).background(if (recording) C.Red else C.Blue, CircleShape))
                    val text = if (recording) "REC %02d:%02d".format(recordSecs / 60, recordSecs % 60) else chip ?: ""
                    T(text, 12f, Color.White, weight = FontWeight.SemiBold)
                }
            }
        }

        // ---- top bar
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { topZone.toDp() })
                .background(Brush.verticalGradient(listOf(Color(0xFF0A1B2E), Color.Transparent))),
        ) {
            val statusDp = with(density) { statusPx.toDp() }
            FlashButton(prefs.flash, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = statusDp + 16.dp)) { shoot.cycleFlash() }
            RoundBtn(
                Icons.Filled.Refresh, { if (!recording) session.refreshLocation() },
                Modifier.align(Alignment.TopEnd).padding(end = 64.dp, top = statusDp + 16.dp), size = 38.dp, bg = C.Glass, desc = "Refresh location",
            )
            RoundBtn(
                Icons.Filled.Settings, { if (!recording) shoot.panelOpen.value = true },
                Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = statusDp + 16.dp), size = 38.dp, bg = C.Glass, desc = "Settings",
            )
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusDp + 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(C.Glass)
                    .border(1.5.dp, if (dialOpen) C.Yellow else C.Line, RoundedCornerShape(20.dp))
                    .clickable { shoot.toggleDial() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) { T("%.1fx".format(max(1f, zoom)), 14f, if (dialOpen) C.Yellow else Color.White, weight = FontWeight.SemiBold) }
        }
        if (dialOpen) {
            val statusDp = with(density) { statusPx.toDp() }
            ZoomDial(
                zoom, maxZoom, { shoot.setZoom(it) }, { shoot.touchDial() },
                Modifier.align(Alignment.TopCenter).padding(top = statusDp + 16.dp + 40.dp),
            )
        }

        // ---- bottom bar
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { barH = it.height }
                .background(Color.Black, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .border(0.5.dp, C.Line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 14.dp, start = 18.dp, end = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModePill(mode) { shoot.setMode(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                LastPhoto(session) { session.push(Screen.Photos) }
                RoundBtn(Icons.Filled.AddPhotoAlternate, {
                    if (!session.perms.value.media) session.showAlert("Photo library access is needed to add a geotag to an existing photo.")
                    else host.pickPhotoToTag()
                }, size = 46.dp, bg = C.GlassLight, desc = "Add geotag to a photo")
                Shutter(mode, busy, recording) { shoot.onShutter() }
                RoundBtn(Icons.Filled.Cameraswitch, { shoot.flip() }, size = 46.dp, bg = C.GlassLight, desc = "Flip camera")
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, C.Cyan, RoundedCornerShape(14.dp))
                        .clickable { session.push(Screen.Videos) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Videocam, "My videos", tint = C.Cyan, modifier = Modifier.size(28.dp)) }
            }
        }

        // ---- self-timer countdown
        countdown?.let { n ->
            Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(120.dp).border(3.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    T("$n", 64f, Color.White, weight = FontWeight.Bold)
                }
            }
        }

        if (panelOpen) QuickPanel(session)
    }
}

@Composable
private fun FlashButton(flash: FlashKind, modifier: Modifier, onClick: () -> Unit) {
    val on = flash != FlashKind.OFF
    Box(
        modifier.size(38.dp).clip(CircleShape).background(if (on) C.Yellow else C.Glass).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (on) Icons.Filled.FlashOn else Icons.Filled.FlashOff, "Flash", tint = if (on) Color.Black else Color.White, modifier = Modifier.size(20.dp))
        if (flash == FlashKind.TORCH) {
            Box(Modifier.align(Alignment.BottomEnd).size(14.dp).background(Color.Black, CircleShape), contentAlignment = Alignment.Center) {
                T("T", 9f, C.Yellow, weight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ModePill(mode: ShootMode, onPick: (ShootMode) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(26.dp)).background(C.GlassLight).padding(3.dp)) {
        for ((m, label) in listOf(ShootMode.PHOTO to "Photo", ShootMode.NIGHT to "Night", ShootMode.VIDEO to "Video")) {
            val sel = m == mode
            Box(
                Modifier
                    .clip(RoundedCornerShape(23.dp))
                    .background(if (sel) C.Blue else Color.Transparent)
                    .clickable { onPick(m) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                val tint = if (sel) C.BlueDark else C.TextDim
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ModeIcon(m, tint)
                    T(label, 12f, tint, Modifier.padding(top = 2.dp), weight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun Shutter(mode: ShootMode, busy: Boolean, recording: Boolean, onClick: () -> Unit) {
    val ring = if (mode == ShootMode.VIDEO) C.Red else Color.White
    Box(
        Modifier.size(78.dp).border(4.dp, ring, CircleShape).padding(7.dp).clip(CircleShape).clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            busy -> Spin(size = 30.dp, color = C.Blue)
            recording -> Box(Modifier.size(26.dp).background(C.Red, RoundedCornerShape(6.dp)))
            else -> Box(Modifier.fillMaxSize().background(if (mode == ShootMode.VIDEO) C.Red else Color.White, CircleShape))
        }
    }
}

@Composable
private fun LastPhoto(session: Session, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val uri by session.thumbUri.collectAsState()
    val ver by session.thumbVersion.collectAsState()
    val bmp by produceState<Bitmap?>(null, uri, ver) {
        val u = uri
        value = if (u == null) null else withContext(Dispatchers.IO) { Pics.thumb(ctx, u, 256) }
    }
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1F26))
            .border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), "Last photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.Image, "No photo yet", tint = C.TextFaint, modifier = Modifier.size(26.dp))
    }
}
