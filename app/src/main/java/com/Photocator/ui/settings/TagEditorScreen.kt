package com.Photocator.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Photocator.Session
import com.Photocator.core.MapStyles
import com.Photocator.core.PlusCode
import com.Photocator.core.TagData
import com.Photocator.stamp.Placement
import com.Photocator.stamp.Stamp
import com.Photocator.stamp.StampInput
import com.Photocator.ui.BigButton
import com.Photocator.ui.C
import com.Photocator.ui.ScreenHeader
import com.Photocator.ui.StampGeometry
import com.Photocator.ui.StampLayer
import com.Photocator.ui.T
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val SAMPLE = TagData(
    lat = 12.9716, lon = 77.5946, altitude = 920.0, placeName = "Bengaluru, Karnataka, India",
    address = "MG Road, Bengaluru, Karnataka, India", plusCode = PlusCode.encode(12.9716, 77.5946),
    tempC = 27.0, condition = "Clear", humidity = 62, windKmh = 8.0, headingDeg = 90.0, magneticUt = 44.0,
)

private enum class Grip { MOVE, LEFT, RIGHT, TOP, BOTTOM, TL, TR, BL, BR }

/** Editor for the geotag's size and position, shown on a sample photo. */
@Composable
fun TagEditorScreen(session: Session) {
    val prefs by session.prefs.flow.collectAsState()
    val live by session.pos.live.collectAsState()
    val marks by session.marks.flow.collectAsState()
    val density = LocalDensity.current
    val focus = LocalFocusManager.current

    var geo by remember { mutableStateOf(StampGeometry(prefs.tagWidth, prefs.tagStretch, prefs.tagX, prefs.tagY)) }
    var mapZoom by remember { mutableStateOf(prefs.mapZoom.coerceIn(10, 16)) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var thumb by remember { mutableStateOf<Bitmap?>(live.thumb) }
    val style = MapStyles.byId(prefs.mapStyleId)
    val data = live.data ?: SAMPLE
    val wm = remember(prefs.watermarkId, marks) { session.marks.load(session.marks.find(prefs.watermarkId)) }

    // thumbnail at the zoom currently chosen on the slider (falls back to the live one)
    LaunchedEffect(mapZoom, style.id, data.lat, data.lon) {
        val b = session.tiles.thumbnail(data.lat, data.lon, style, mapZoom)
        thumb = b ?: live.thumb
    }

    val shown = StampInput(data, thumb, wm, prefs.showPlus, prefs.sensorData)
    // proportions are always measured as if sensor data were on
    val measure = StampInput(data, thumb, wm, prefs.showPlus, true)

    fun aspectAt(s: Float): Float = Stamp.plan(measure, 1000, s).heightPx / 1000f
    val aspect1 = remember(measure) { aspectAt(1f) }

    /** Stretch whose panel aspect (height / width) matches [target]; 14-step bisection in 0.5..2.5. */
    fun solveStretch(target: Float): Float {
        var lo = 0.5f
        var hi = 2.5f
        repeat(14) {
            val mid = (lo + hi) / 2f
            if (aspectAt(mid) < target) lo = mid else hi = mid
        }
        return ((lo + hi) / 2f).coerceIn(0.5f, 2.5f)
    }

    fun panelPx(g: StampGeometry, fw: Int): Pair<Int, Int> {
        val pw = max(1, (g.widthFraction * fw).toInt())
        val ph = Stamp.plan(measure, pw, g.stretch).heightPx
        return pw to ph
    }

    var grip by remember { mutableStateOf<Grip?>(null) }
    // working rectangle (px) while a drag is in progress
    var rl by remember { mutableStateOf(0f) }
    var rt by remember { mutableStateOf(0f) }
    var rr by remember { mutableStateOf(0f) }
    var rb by remember { mutableStateOf(0f) }

    val fw = frame.width
    val fh = frame.height
    val (pwNow, phNow) = if (fw > 0) panelPx(geo, fw) else (1 to 1)
    val lift = 0.09f * fw
    val leftNow = (fw - pwNow) * geo.x
    val topNow = max(0f, fh - phNow - lift) * geo.y

    fun commitRect(l: Float, t: Float, r: Float, b: Float, newStretch: Float, newW: Float) {
        val pw = r - l
        val ph = b - t
        val freeX = fw - pw
        val travel = fh - ph - lift
        geo = StampGeometry(
            widthFraction = newW.coerceIn(0.35f, 1f),
            stretch = newStretch,
            x = if (freeX > 1f) (l / freeX).coerceIn(0f, 1f) else 0.5f,
            y = if (travel > 1f) (t / travel).coerceIn(0f, 1f) else 1f,
        )
    }

    // numeric fields
    fun widthPct() = (geo.widthFraction * 100f).roundToInt()
    fun heightPct() = (aspectAt(geo.stretch) * geo.widthFraction / aspect1 * 100f).roundToInt()
    var wText by remember { mutableStateOf(widthPct().toString()) }
    var hText by remember { mutableStateOf(heightPct().toString()) }
    LaunchedEffect(geo) { wText = widthPct().toString(); hText = heightPct().toString() }

    fun commitWidth(txt: String) {
        val v = txt.toIntOrNull()
        if (v == null || v <= 0) { wText = widthPct().toString(); return }
        val newW = (v / 100f).coerceIn(0.35f, 1f)
        if (abs(newW - geo.widthFraction) < 1e-4f) { wText = widthPct().toString(); return }
        // keep the panel height: aspect = ph / (W * frame width)
        val ph = phNow.toFloat()
        val target = ph / max(1f, newW * max(1, fw))
        val s = solveStretch(target)
        geo = geo.copy(widthFraction = newW, stretch = s)
    }
    fun commitHeight(txt: String) {
        val v = txt.toIntOrNull()
        if (v == null || v <= 0) { hText = heightPct().toString(); return }
        if (v == heightPct()) return
        val target = (v / 100f) * aspect1 / geo.widthFraction
        geo = geo.copy(stretch = solveStretch(target))
    }

    Column(Modifier.fillMaxSize().background(C.Black)) {
        ScreenHeader("Geotag size & position", onBack = { session.pop() })
        T("Drag an edge to resize, a corner to scale, or the middle to move.", 13f, C.TextDim, Modifier.padding(horizontal = 16.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val w = min(maxWidth.value, 440f)
                Box(
                    Modifier
                        .width(w.dp)
                        .aspectRatio(0.75f)
                        .background(Brush.verticalGradient(listOf(Color(0xFF3A6EA5), Color(0xFF7FB77E), Color(0xFF2E3B2C))))
                        .onSizeChanged { frame = it }
                        .pointerInput(frame, measure) {
                            detectDragGestures(
                                onDragStart = { p ->
                                    val (pw, ph) = panelPx(geo, frame.width)
                                    val l = (frame.width - pw) * geo.x
                                    val t = max(0f, frame.height - ph - 0.09f * frame.width) * geo.y
                                    val halo = 14.dp.toPx()
                                    if (p.x < l - halo || p.x > l + pw + halo || p.y < t - halo || p.y > t + ph + halo) { grip = null; return@detectDragGestures }
                                    val band = min(28.dp.toPx(), min(pw, ph) / 3f)
                                    val nl = p.x - l < band
                                    val nr = l + pw - p.x < band
                                    val nt = p.y - t < band
                                    val nb = t + ph - p.y < band
                                    grip = when {
                                        nl && nt -> Grip.TL; nr && nt -> Grip.TR; nl && nb -> Grip.BL; nr && nb -> Grip.BR
                                        nl -> Grip.LEFT; nr -> Grip.RIGHT; nt -> Grip.TOP; nb -> Grip.BOTTOM
                                        else -> Grip.MOVE
                                    }
                                    rl = l; rt = t; rr = l + pw; rb = t + ph
                                },
                                onDragEnd = { grip = null },
                                onDragCancel = { grip = null },
                            ) { change, drag ->
                                val g = grip ?: return@detectDragGestures
                                change.consume()
                                val fW = frame.width.toFloat()
                                val fH = frame.height.toFloat()
                                val limB = fH - 0.09f * fW
                                val minW = 0.35f * fW
                                val dx = drag.x
                                val dy = drag.y
                                val curStretch = geo.stretch
                                when (g) {
                                    Grip.MOVE -> {
                                        val w0 = rr - rl; val h0 = rb - rt
                                        rl = (rl + dx).coerceIn(0f, max(0f, fW - w0)); rr = rl + w0
                                        rt = (rt + dy).coerceIn(0f, max(0f, limB - h0)); rb = rt + h0
                                        commitRect(rl, rt, rr, rb, curStretch, geo.widthFraction)
                                    }
                                    Grip.TL, Grip.TR, Grip.BL, Grip.BR -> {
                                        val sx = if (g == Grip.TR || g == Grip.BR) 1f else -1f
                                        val sy = if (g == Grip.BL || g == Grip.BR) 1f else -1f
                                        val w0 = rr - rl; val h0 = rb - rt
                                        val a = h0 / max(1f, w0)
                                        val dxEq = sx * dx
                                        val dyEq = sy * dy / max(0.01f, a)
                                        val dw = if (abs(dxEq) >= abs(dyEq)) dxEq else dyEq
                                        val anchorLeft = sx > 0
                                        val anchorTop = sy < 0
                                        val maxByX = if (anchorLeft) fW - rl else rr
                                        val maxByY = (if (anchorTop) limB - rt else rb) / max(0.01f, a)
                                        val nw = (w0 + dw).coerceIn(minW, max(minW, min(maxByX, maxByY)))
                                        val nh = nw * a
                                        if (anchorLeft) rr = rl + nw else rl = rr - nw
                                        if (anchorTop) rb = rt + nh else rt = rb - nh
                                        commitRect(rl, rt, rr, rb, curStretch, nw / fW)
                                    }
                                    Grip.LEFT, Grip.RIGHT -> {
                                        val h0 = rb - rt
                                        if (g == Grip.LEFT) rl = (rl + dx).coerceIn(0f, rr - minW) else rr = (rr + dx).coerceIn(rl + minW, fW)
                                        val nw = rr - rl
                                        val s = solveStretch(h0 / max(1f, nw))
                                        val ph = panelPx(StampGeometry(nw / fW, s, 0f, 0f), frame.width).second.toFloat()
                                        rb = rt + ph
                                        commitRect(rl, rt, rr, rb, s, nw / fW)
                                    }
                                    Grip.TOP, Grip.BOTTOM -> {
                                        val w0 = rr - rl
                                        val minH = 0.05f * fW
                                        var reqH = rb - rt
                                        reqH = if (g == Grip.TOP) (reqH - dy).coerceAtLeast(minH) else (reqH + dy).coerceAtLeast(minH)
                                        if (g == Grip.BOTTOM && rt + reqH > limB) reqH = max(minH, limB - rt)
                                        val s = solveStretch(reqH / max(1f, w0))
                                        val ph = panelPx(StampGeometry(w0 / fW, s, 0f, 0f), frame.width).second.toFloat()
                                        if (g == Grip.TOP) rt = rb - ph else rb = rt + ph
                                        commitRect(rl, rt, rr, rb, s, w0 / fW)
                                    }
                                }
                            }
                        },
                ) {
                    StampLayer(Modifier.fillMaxSize(), shown, geo)
                    // outline and eight handles around the panel
                    Canvas(Modifier.fillMaxSize()) {
                        val pw = pwNow.toFloat(); val ph = phNow.toFloat()
                        drawRect(Color.White, Offset(leftNow, topNow), Size(pw, ph), style = Stroke(1.5.dp.toPx()))
                        val xs = floatArrayOf(leftNow, leftNow + pw / 2f, leftNow + pw)
                        val ys = floatArrayOf(topNow, topNow + ph / 2f, topNow + ph)
                        for (ix in 0..2) for (iy in 0..2) {
                            if (ix == 1 && iy == 1) continue
                            drawCircle(Color.White, 6.dp.toPx(), Offset(xs[ix], ys[iy]))
                            drawCircle(Color(0x66000000), 6.dp.toPx(), Offset(xs[ix], ys[iy]), style = Stroke(1.dp.toPx()))
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PctField("Width %", wText, { wText = it.filter(Char::isDigit).take(3) }, { commitWidth(wText); focus.clearFocus() }, { if (!it) commitWidth(wText) }, Modifier.weight(1f))
                PctField("Height %", hText, { hText = it.filter(Char::isDigit).take(3) }, { commitHeight(hText); focus.clearFocus() }, { if (!it) commitHeight(hText) }, Modifier.weight(1f))
            }

            Column {
                T("Map zoom: level $mapZoom of 16", 14f)
                Slider(
                    value = mapZoom.toFloat(), onValueChange = { mapZoom = it.roundToInt().coerceIn(10, 16) },
                    valueRange = 10f..16f, steps = 5,
                    colors = SliderDefaults.colors(thumbColor = C.Blue, activeTrackColor = C.Blue),
                )
                if (mapZoom > style.maxZoom) T("This map style stops at ${style.maxZoom}.", 12f, C.Yellow)
            }
            T("100% is the default size. Text and map grow and shrink with the geotag.", 12f, C.TextFaint)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Default", { geo = StampGeometry(1f, 1f, 0.5f, 1f); mapZoom = 16 }, Modifier.weight(1f), bg = C.GlassLight, fg = Color.White)
                BigButton("Save as default", {
                    session.prefs.update { it.copy(tagWidth = geo.widthFraction, tagStretch = geo.stretch, tagX = geo.x, tagY = geo.y, mapZoom = mapZoom) }
                    session.toast("Geotag saved")
                    session.pop()
                }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PctField(label: String, value: String, onChange: (String) -> Unit, onDone: () -> Unit, onFocus: (Boolean) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = modifier.onFocusChanged { onFocus(it.isFocused) },
        singleLine = true,
        label = { T(label, 12f, C.TextDim) },
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Blue, unfocusedBorderColor = C.Line, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = C.Blue),
    )
}
