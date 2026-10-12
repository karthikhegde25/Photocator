package com.Photocator.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Photocator.Session
import com.Photocator.core.Fmt
import com.Photocator.core.MapStyles
import com.Photocator.net.Mercator
import com.Photocator.net.PlaceHit
import com.Photocator.net.Web
import com.Photocator.ui.BigButton
import com.Photocator.ui.C
import com.Photocator.ui.RoundBtn
import com.Photocator.ui.Spin
import com.Photocator.ui.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Full-screen native map: pick a location by moving the map under a fixed pin. */
@Composable
fun MapPicker(session: Session, start: Pair<Double, Double>?, onClose: () -> Unit, onPick: (Double, Double) -> Unit) {
    val prefs by session.prefs.flow.collectAsState()
    val style = MapStyles.byId(prefs.mapStyleId)
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val hasStart = start != null && !(start.first == 0.0 && start.second == 0.0)

    var lat by remember { mutableDoubleStateOf(if (hasStart) start!!.first else 20.0) }
    var lon by remember { mutableDoubleStateOf(if (hasStart) start!!.second else 0.0) }
    var zoom by remember { mutableFloatStateOf(if (hasStart) 15f else 2f) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var tick by remember { mutableIntStateOf(0) }
    val failed = remember { mutableStateListOf<String>() }
    val inFlight = remember { HashSet<String>() }

    var query by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<PlaceHit>>(emptyList()) }
    var info by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }

    val maxZ = style.maxZoom.toFloat()
    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(style.id) { if (zoom > maxZ) zoom = maxZ }
    BackHandler { onClose() }

    // initial centring on the current location when nothing was supplied
    LaunchedEffect(Unit) {
        if (!hasStart && session.pos.hasAny()) {
            val l = session.pos.oneShot(8_000)
            if (l != null) { lat = l.latitude; lon = l.longitude; zoom = min(15f, maxZ) }
        }
    }

    fun flyTo(la: Double, lo: Double, z: Float) { lat = la.coerceIn(-85.05, 85.05); lon = lo; zoom = z.coerceIn(2f, maxZ) }

    // ---- autocomplete 300 ms after typing stops
    LaunchedEffect(query, typed) {
        if (!typed || query.trim().length < 2) { suggestions = emptyList(); return@LaunchedEffect }
        delay(300)
        val bias = zoom >= 8f
        suggestions = Web.suggest(query.trim(), if (bias) lat else null, if (bias) lon else null)
    }

    // ---- tile geometry
    val zi = min(max(zoom.roundToInt(), 0), style.maxZoom)
    val scale = 2.0.pow((zoom - zi).toDouble())
    val n = 1 shl zi
    val w = mapSize.width
    val h = mapSize.height
    val cx = Mercator.x(lon, zi.toDouble())
    val cy = Mercator.y(lat, zi.toDouble())
    val halfW = w / (2.0 * scale)
    val halfH = h / (2.0 * scale)
    val tx0 = floor((cx - halfW) / 256.0).toInt()
    val tx1 = floor((cx + halfW) / 256.0).toInt()
    val ty0 = max(0, floor((cy - halfH) / 256.0).toInt())
    val ty1 = min(n - 1, floor((cy + halfH) / 256.0).toInt())
    val visibleKey = "$zi/$tx0/$tx1/$ty0/$ty1/${style.id}/$w/$h"

    LaunchedEffect(visibleKey) {
        if (w <= 0 || h <= 0) return@LaunchedEffect
        val layers = listOfNotNull(style.base, style.labels)
        for (path in layers) for (ty in ty0..ty1) for (tx in tx0..tx1) {
            val wx = ((tx % n) + n) % n
            val key = "$path/$zi/$wx/$ty"
            if (session.tiles.cachedInMemory(path, zi, wx, ty) != null || !inFlight.add(key)) continue
            scope.launch {
                val b = session.tiles.loadTile(path, zi, wx, ty, useMemory = true)
                inFlight.remove(key)
                if (b == null) { if (path == style.base && !failed.contains(key)) failed.add(key) }
                else { failed.remove(key); tick += 1 }
            }
        }
    }

    fun pxOf(la: Double, lo: Double, z: Double): Pair<Double, Double> = Mercator.x(lo, z) to Mercator.y(la, z)

    /** Zoom to [newZoom] keeping the geographic point under (fx, fy) fixed. */
    fun zoomAround(newZoom: Float, fx: Float, fy: Float) {
        val z2 = newZoom.coerceIn(2f, maxZ)
        val (px, py) = pxOf(lat, lon, zoom.toDouble())
        val wx = px + (fx - w / 2f)
        val wy = py + (fy - h / 2f)
        val ws = Mercator.worldSize(zoom.toDouble())
        val fLon = (wx / ws) * 360.0 - 180.0
        val fLat = Mercator.lat(wy, zoom.toDouble())
        val (ax, ay) = pxOf(fLat, fLon, z2.toDouble())
        val ncx = ax - (fx - w / 2f)
        val ncy = ay - (fy - h / 2f)
        lon = Mercator.lon(ncx, z2.toDouble())
        lat = Mercator.lat(ncy, z2.toDouble()).coerceIn(-85.05, 85.05)
        zoom = z2
    }

    fun doSearch() {
        if (query.isBlank() || searching) return
        focus.clearFocus(); suggestions = emptyList(); typed = false
        scope.launch {
            searching = true; error = ""; info = ""
            val hit = Web.searchFirst(query.trim())
            if (hit == null) error = "No matching places found." else { info = hit.title; flyTo(hit.lat, hit.lon, 16f) }
            searching = false
        }
    }

    fun doLocate() {
        if (locating) return
        if (!session.pos.hasAny()) { error = "Location permission not granted."; return }
        scope.launch {
            locating = true; error = ""
            try {
                val l = session.pos.oneShot(15_000)
                if (l == null) error = "Could not get current location. Is Location turned on?" else flyTo(l.latitude, l.longitude, 16f)
            } catch (_: Exception) {
                error = "Could not get current location."
            }
            locating = false
        }
    }

    val visibleBaseFailed = failed.any { it.startsWith(style.base + "/" + zi + "/") }
    var anyBaseLoaded = false
    if (w > 0 && h > 0) {
        loop@ for (ty in ty0..ty1) for (tx in tx0..tx1) {
            val wx = ((tx % n) + n) % n
            if (session.tiles.cachedInMemory(style.base, zi, wx, ty) != null) { anyBaseLoaded = true; break@loop }
        }
    }
    val showBanner = visibleBaseFailed && !anyBaseLoaded

    AnimatedVisibility(visible = shown, enter = slideInVertically(tween(280)) { it }) {
        Column(Modifier.fillMaxSize().background(C.Ink).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { T("Pick a location", 19f, weight = FontWeight.SemiBold) }
                RoundBtn(Icons.Filled.Close, onClose, size = 40.dp, bg = C.GlassLight, desc = "Close")
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; typed = true },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { T("Search for a place or address", 14f, C.TextFaint) },
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.Blue, unfocusedBorderColor = C.Line,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = C.Blue,
                    ),
                )
                SquareBtn(searching, Icons.Filled.Search, C.Blue) { doSearch() }
                SquareBtn(locating, Icons.Filled.MyLocation, C.Green) { doLocate() }
            }
            if (error.isNotEmpty()) T(error, 13f, C.Red, Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            else if (info.isNotEmpty()) T(info, 13f, C.TextDim, Modifier.padding(horizontal = 16.dp, vertical = 2.dp), maxLines = 2)

            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF14181D)).clip(RoundedCornerShape(0.dp))) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { mapSize = it }
                        .pointerInput(maxZ, w, h) {
                            detectTransformGestures { centroid, pan, zoomFactor, _ ->
                                // pan first, then zoom around the fingers' centre
                                val (px, py) = pxOf(lat, lon, zoom.toDouble())
                                lon = Mercator.lon(px - pan.x, zoom.toDouble())
                                lat = Mercator.lat(py - pan.y, zoom.toDouble()).coerceIn(-85.05, 85.05)
                                if (zoomFactor != 1f) zoomAround(zoom + (ln(zoomFactor.toDouble()) / ln(2.0)).toFloat(), centroid.x, centroid.y)
                            }
                        }
                        .pointerInput(maxZ, w, h) {
                            detectTapGestures(
                                onDoubleTap = { p -> zoomAround(zoom + 1f, p.x, p.y) },
                                onTap = { p ->
                                    // a tap moves the map so the tapped point becomes the centre
                                    val (px, py) = pxOf(lat, lon, zoom.toDouble())
                                    lon = Mercator.lon(px + (p.x - w / 2f), zoom.toDouble())
                                    lat = Mercator.lat(py + (p.y - h / 2f), zoom.toDouble()).coerceIn(-85.05, 85.05)
                                    focus.clearFocus(); suggestions = emptyList()
                                },
                            )
                        },
                ) {
                    @Suppress("UNUSED_VARIABLE") val dep = tick
                    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                    drawIntoCanvas { c ->
                        val nc = c.nativeCanvas
                        val sc = scale.toFloat()
                        val layers = listOfNotNull(style.base, style.labels)
                        for (path in layers) for (ty in ty0..ty1) for (tx in tx0..tx1) {
                            val wx = ((tx % n) + n) % n
                            val bmp = session.tiles.cachedInMemory(path, zi, wx, ty) ?: continue
                            val left = (size.width / 2f + ((tx * 256.0 - cx) * scale)).toFloat()
                            val top = (size.height / 2f + ((ty * 256.0 - cy) * scale)).toFloat()
                            nc.drawBitmap(bmp, null, android.graphics.RectF(left, top, left + 256f * sc + 1f, top + 256f * sc + 1f), paint)
                        }
                    }
                }
                // fixed pin at the centre; its tip marks the centre point
                Icon(
                    Icons.Filled.LocationOn, null, tint = Color(0xFFE53935),
                    modifier = Modifier.align(Alignment.Center).size(40.dp).offset(y = (-20).dp),
                )
                // zoom buttons
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundBtn(Icons.Filled.Add, { zoomAround(zoom + 1f, w / 2f, h / 2f) }, size = 40.dp, bg = Color(0xCC000000), desc = "Zoom in")
                    RoundBtn(Icons.Filled.Remove, { zoomAround(zoom - 1f, w / 2f, h / 2f) }, size = 40.dp, bg = Color(0xCC000000), desc = "Zoom out")
                }
                T("Map © Esri", 11f, Color.White, Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                if (showBanner) {
                    T(
                        "Map imagery could not be loaded. Check your internet connection.", 12f, Color.White,
                        Modifier.align(Alignment.TopCenter).padding(top = 10.dp).background(Color(0xBF000000), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                        align = TextAlign.Center,
                    )
                }
                if (suggestions.isNotEmpty()) {
                    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(12.dp)).background(C.Card)) {
                        suggestions.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    suggestions = emptyList(); typed = false
                                    query = s.title; info = if (s.subtitle.isEmpty()) s.title else "${s.title}, ${s.subtitle}"; error = ""
                                    focus.clearFocus()
                                    flyTo(s.lat, s.lon, 16f)
                                }.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Filled.LocationOn, null, tint = C.Blue, modifier = Modifier.size(20.dp))
                                Column {
                                    T(s.title, 14f, weight = FontWeight.SemiBold, maxLines = 1)
                                    if (s.subtitle.isNotEmpty()) T(s.subtitle, 12f, C.TextDim, maxLines = 1)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.Line))
                        }
                    }
                }
            }
            T(Fmt.latLon6(lat, lon), 13f, C.TextDim, Modifier.fillMaxWidth().padding(vertical = 8.dp), align = TextAlign.Center)
            BigButton("Use this location", { onPick(lat, lon) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), bg = C.Blue, fg = C.BlueDark)
        }
    }
}

@Composable
private fun SquareBtn(busy: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) Spin(size = 20.dp, color = Color.Black) else Icon(icon, null, tint = Color.Black)
    }
}
