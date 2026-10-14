package com.Photocator.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import com.Photocator.media.Pics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.Photocator.ReviewSource
import com.Photocator.Session
import com.Photocator.core.TagPlacement
import com.Photocator.stamp.Placement
import com.Photocator.stamp.Stamp
import com.Photocator.stamp.StampInput
import com.Photocator.ui.BigButton
import com.Photocator.ui.C
import com.Photocator.ui.Host
import com.Photocator.ui.RoundBtn
import com.Photocator.ui.Segmented
import com.Photocator.ui.Spin
import com.Photocator.ui.StampGeometry
import com.Photocator.ui.StampLayer
import com.Photocator.ui.T
import com.Photocator.ui.map.MapPicker
import com.Photocator.ui.places.SavedPlacesScreen
import kotlin.math.max
import kotlin.math.min

/** Preview after a capture, or Add Geotag for a gallery photo. */
@Composable
fun ReviewScreen(session: Session, source: ReviewSource, host: Host) {
    val scope = rememberCoroutineScope()
    val model = remember(source) { ReviewModel(session, source, scope) }
    val prefs by session.prefs.flow.collectAsState()
    val marks by session.marks.flow.collectAsState()
    LaunchedEffect(model) { model.start() }
    val close = { session.pop(); Unit }

    // auto-save once data and photo are ready (capture preview only)
    LaunchedEffect(model.ready, model.saving, model.showEdit, model.showPosition, model.showMap, model.showPlaces) {
        model.autoSaveWhenReady { session.pop() }
    }

    BackHandler { if (!model.saving) close() }

    Box(Modifier.fillMaxSize().background(C.Black)) {
        Column(Modifier.fillMaxSize()) {
            // header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundBtn(Icons.Filled.Close, { if (!model.saving) close() }, size = 48.dp, bg = Color.Transparent, desc = "Close")
                Box(Modifier.weight(1f).padding(start = 4.dp)) {
                    val title = when {
                        model.saving && model.count > 1 -> "Saving ${minOf(model.progress + 1, model.count)} of ${model.count}"
                        model.isCapture -> "Preview"
                        model.count > 1 -> "Add Geotag (${model.count} photos)"
                        else -> "Add Geotag"
                    }
                    T(title, 19f, weight = FontWeight.SemiBold)
                }
                if (model.ready) {
                    RoundBtn(Icons.Filled.Edit, { model.showEdit = true }, size = 44.dp, bg = C.GlassLight, desc = "Edit")
                } else Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Spin(size = 20.dp) }
                Box(Modifier.width(12.dp))
                if (model.ready && !model.saving) {
                    RoundBtn(Icons.Filled.Check, { model.save { session.pop() } }, size = 44.dp, bg = C.Green, tint = Color(0xFF04210F), desc = "Save")
                } else Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Spin(size = 20.dp) }
                Box(Modifier.width(4.dp))
            }

            val err = model.error
            if (err != null) {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    T(err, 16f, C.Red, align = TextAlign.Center)
                    Box(Modifier.height(20.dp))
                    BigButton("Go back", { close() }, Modifier.width(180.dp), bg = Color.White, fg = Color.Black)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val d = model.data
                    if (d != null) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Segmented(listOf("On photo", "Below photo"), if (model.placement == TagPlacement.ON_PHOTO) 0 else 1, {
                                model.placement = if (it == 0) TagPlacement.ON_PHOTO else TagPlacement.BELOW_PHOTO
                            })
                            T(
                                if (model.placement == TagPlacement.ON_PHOTO) "Drawn over the bottom of the photo, original resolution kept."
                                else "Added under the photo, nothing is covered.",
                                12f, C.TextFaint,
                            )
                        }
                    }
                    val wm = remember(model.watermarkId, marks) { session.marks.load(session.marks.find(model.watermarkId)) }
                    val input = d?.let { StampInput(it, model.thumb, wm, prefs.showPlus, prefs.sensorData, prefs.tagRounded, model.qrStyle(prefs), prefs.tagMapRight) }
                    val geo = StampGeometry(prefs.tagWidth, prefs.tagStretch, prefs.tagX, prefs.tagY)
                    val aspect = if (model.imgW > 0 && model.imgH > 0) model.imgW.toFloat() / model.imgH else if (model.isCapture) 3f / 4f else 4f / 3f

                    Box(Modifier.fillMaxWidth().aspectRatio(aspect)) {
                        val ph = model.photo
                        if (ph != null) Image(ph.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        else Box(Modifier.fillMaxSize().background(C.Card), contentAlignment = Alignment.Center) { Spin(size = 28.dp) }
                        if (input != null && model.placement == TagPlacement.ON_PHOTO) {
                            StampLayer(Modifier.fillMaxSize(), input, geo, 0, model.dragFraction)
                        }
                        if (d == null && !model.isCapture) Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                    }
                    if (d == null && !model.isCapture) {
                        // gallery photo without a geotag: choose where it was taken (outside the photo so the image size never trims it)
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            T(
                                if (model.count > 1) "These photos have no geotag yet. Choose where they were taken. The same geotag is added to all ${model.count}."
                                else "This photo has no geotag yet. Choose where it was taken.",
                                14f, C.TextDim, align = TextAlign.Center, modifier = Modifier.padding(bottom = 14.dp),
                            )
                            BigButton("Use current location", { model.useCurrentLocation() }, Modifier.fillMaxWidth(), busy = model.locating, enabled = !model.locating)
                            Box(Modifier.height(8.dp))
                            BigButton("Pick on map", { model.showMap = true }, Modifier.fillMaxWidth(), bg = C.GlassLight, fg = Color.White, enabled = !model.locating)
                            Box(Modifier.height(8.dp))
                            BigButton("Use saved address", { model.showPlaces = true }, Modifier.fillMaxWidth(), bg = C.GlassLight, fg = Color.White, enabled = !model.locating)
                            Box(Modifier.height(8.dp))
                            BigButton("Enter manually", { model.startManual() }, Modifier.fillMaxWidth(), bg = C.GlassLight, fg = Color.White, enabled = !model.locating)
                        }
                    }
                    if (input != null && model.placement == TagPlacement.BELOW_PHOTO) {
                        StampStrip(input, geo)
                    }
                    if (model.count > 1) PhotoStrip(session, model)
                    Box(Modifier.height(24.dp))
                }
            }
        }

        val d = model.data
        if (model.showEdit && d != null) EditSheet(session, model, d) { model.showEdit = false }
        if (model.showPosition && d != null) {
            PositionEditor(session, model, d)
        }
        if (model.showPlaces) {
            SavedPlacesScreen(session, onClose = { model.showPlaces = false }, onPick = { model.applySaved(it) })
        }
        if (model.showMap) {
            MapPicker(
                session = session,
                start = model.data?.let { it.lat to it.lon },
                onClose = { model.showMap = false },
                onPick = { la, lo -> model.applyPicked(la, lo) },
            )
        }
    }
}

/** Thumbnails of every picked photo; tap one to preview the shared geotag on it. */
@Composable
private fun PhotoStrip(session: Session, model: ReviewModel) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(
            "The geotag is added to all ${model.count} photos. Tap one to preview it.",
            12f, C.TextFaint, Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(model.uris) { i, uri ->
                val thumb by produceState<Bitmap?>(null, uri) { value = withContext(Dispatchers.IO) { Pics.thumb(session.app, uri, 192) } }
                val sel = i == model.previewIndex
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.Card)
                        .border(if (sel) 3.dp else 1.dp, if (sel) C.Blue else C.Line, RoundedCornerShape(8.dp))
                        .clickable { model.selectPreview(i) },
                ) {
                    thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
            }
        }
    }
}

/** The strip appended under the photo in "Below photo" mode. */
@Composable
fun StampStrip(input: StampInput, geo: StampGeometry) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val plan = remember(input, widthPx, geo.stretch) { Stamp.plan(input, widthPx, geo.stretch) }
        val hDp = with(LocalDensity.current) { plan.heightPx.toDp() }
        Canvas(Modifier.fillMaxWidth().height(hDp)) {
            drawIntoCanvas { c -> Stamp.draw(c.nativeCanvas, plan, 0f, 0f, opaque = true) }
        }
    }
}

/** Full-screen editor: drag the geotag up or down on the photo. */
@Composable
fun PositionEditor(session: Session, model: ReviewModel, data: com.Photocator.core.TagData) {
    val prefs by session.prefs.flow.collectAsState()
    val marks by session.marks.flow.collectAsState()
    val wm = remember(model.watermarkId, marks) { session.marks.load(session.marks.find(model.watermarkId)) }
    val input = StampInput(data, model.thumb, wm, prefs.showPlus, prefs.sensorData, prefs.tagRounded, model.qrStyle(prefs), prefs.tagMapRight)
    val geo = StampGeometry(prefs.tagWidth, prefs.tagStretch, prefs.tagX, prefs.tagY)
    var frac by remember { mutableStateOf(model.dragFraction) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val close = { model.showPosition = false }
    BackHandler { close() }
    val aspect = if (model.imgW > 0 && model.imgH > 0) model.imgW.toFloat() / model.imgH else 3f / 4f

    Column(Modifier.fillMaxSize().background(C.Black).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp).statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(Icons.Filled.Close, { close() }, size = 48.dp, bg = Color.Transparent, desc = "Close")
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { T("Position Geotag", 18f, weight = FontWeight.SemiBold) }
            Box(Modifier.width(48.dp))
        }
        T(
            if (model.count > 1) "Drag on the photo to move the geotag. The position is used for all ${model.count} photos." else "Drag on the photo to move the geotag.",
            13f, C.TextDim, Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 16.dp, end = 16.dp), align = TextAlign.Center,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            val w = min(maxWidth.value, maxHeight.value * aspect)
            Box(
                Modifier
                    .width(w.dp)
                    .aspectRatio(aspect)
                    .onSizeChanged { box = it }
                    .pointerInput(box, geo) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val iw = box.width
                            val ih = box.height
                            if (iw <= 0 || ih <= 0) return@detectDragGestures
                            val ph = Stamp.plan(input, Placement.panelWidth(iw, ih, geo.widthFraction), geo.stretch).heightPx
                            val range = max(1f, (ih - ph).toFloat())
                            val curTop = frac?.let { it * range } ?: (max(0f, ih - ph - Placement.lift(iw, ih)) * geo.y)
                            frac = ((curTop + drag.y) / range).coerceIn(0f, 1f)
                        }
                    },
            ) {
                val ph = model.photo
                if (ph != null) Image(ph.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                StampLayer(Modifier.fillMaxSize(), input, geo, 0, frac)
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("Reset", { frac = null }, Modifier.weight(1f), bg = C.GlassLight, fg = Color.White)
            BigButton("Confirm", { model.dragFraction = frac; close() }, Modifier.weight(1f))
        }
    }
}
