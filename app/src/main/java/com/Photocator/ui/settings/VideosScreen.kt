package com.Photocator.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.max
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.Photocator.ui.ConfirmDialog
import com.Photocator.ui.RoundBtn
import kotlinx.coroutines.launch
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.Photocator.Screen
import com.Photocator.Session
import com.Photocator.media.MediaItem
import com.Photocator.media.RenameResult
import com.Photocator.media.Pics
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import com.Photocator.ui.BigButton
import com.Photocator.ui.C
import com.Photocator.ui.Host
import com.Photocator.ui.ScreenHeader
import com.Photocator.ui.Spin
import com.Photocator.ui.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VideosScreen(session: Session, host: Host) = GalleryScreen(session, host, video = true)

@Composable
fun PhotosScreen(session: Session, host: Host) = GalleryScreen(session, host, video = false)

@Composable
private fun GalleryScreen(session: Session, host: Host, video: Boolean) {
    val scope = rememberCoroutineScope()
    var reload by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(setOf<Uri>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<MediaItem?>(null) }
    val state by produceState<List<MediaItem>?>(null, reload) {
        value = withContext(Dispatchers.IO) { if (video) session.album.listVideos() else session.album.listPhotos() }
    }
    val selecting = selected.isNotEmpty()
    // the last-photo thumbnail on the camera screen must follow deletions made here
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { session.loadLatestThumb() } }
    BackHandler(enabled = selecting) { selected = emptySet() }

    Column(Modifier.fillMaxSize().background(C.Black)) {
        if (selecting) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RoundBtn(Icons.Filled.Close, { selected = emptySet() }, size = 40.dp, bg = C.GlassLight, desc = "Cancel selection")
                Box(Modifier.weight(1f).padding(start = 6.dp)) { T("${selected.size} selected", 20f, weight = FontWeight.SemiBold) }
                if (selected.size == 1) {
                    RoundBtn(Icons.Filled.Edit, {
                        renaming = state?.firstOrNull { it.uri == selected.first() }
                    }, size = 40.dp, bg = C.GlassLight, desc = "Rename")
                }
                RoundBtn(Icons.Filled.Share, { host.shareMedia(selected.toList(), if (video) "video/mp4" else "image/jpeg") }, size = 40.dp, bg = C.GlassLight, desc = "Share")
                RoundBtn(Icons.Filled.Delete, { confirmDelete = true }, size = 40.dp, bg = C.GlassLight, tint = C.Red, desc = "Delete")
            }
        } else {
            ScreenHeader(if (video) "Videos" else "Photos", onBack = { session.pop() })
        }
        val items = state
        when {
            items == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Spin(size = 32.dp) }
            items.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                T(if (video) "No videos yet" else "No photos yet", 18f, align = TextAlign.Center)
                T(if (video) "Videos you record appear here." else "Photos you take appear here.", 13f, C.TextDim, Modifier.padding(vertical = 10.dp), align = TextAlign.Center)
            }
            else -> {
                if (!selecting) T(if (video) "Touch and hold a video to select, share, rename or delete." else "Touch and hold a photo to select, share, rename or delete.", 12f, C.TextFaint, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                LazyVerticalGrid(
                    GridCells.Fixed(3),
                    Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(items, key = { it.uri.toString() }) { v ->
                        VideoCell(
                            session, v, video, v.uri in selected, selecting,
                            onOpen = {
                                val all = items.map { it.uri }
                                val i = all.indexOf(v.uri).coerceAtLeast(0)
                                session.push(if (video) Screen.Player(all, i) else Screen.PhotoView(all, i))
                            },
                            onToggle = { selected = if (v.uri in selected) selected - v.uri else selected + v.uri },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val n = selected.size
        ConfirmDialog(
            "Delete", if (n == 1) "Delete this ${if (video) "video" else "photo"}? This cannot be undone." else "Delete $n ${if (video) "videos" else "photos"}? This cannot be undone.", "Delete",
            onConfirm = {
                confirmDelete = false
                val victims = selected.toList()
                scope.launch {
                    val failed = withContext(Dispatchers.IO) { session.album.delete(victims) }
                    selected = emptySet()
                    if (failed.isEmpty()) { reload++; session.loadLatestThumb() } else host.confirmDelete(failed) { reload++; session.loadLatestThumb() }
                }
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
    renaming?.let { v ->
        RenameDialog(if (video) "Rename video" else "Rename photo", v.name.substringBeforeLast('.'), onDismiss = { renaming = null }) { name ->
            renaming = null
            scope.launch {
                var r: RenameResult = withContext(Dispatchers.IO) { session.album.rename(v.uri, name) }
                if (r is RenameResult.NeedsAccess) {
                    val granted = CompletableDeferred<Boolean>()
                    host.launchSender(r.sender) { granted.complete(it) }
                    r = if (granted.await()) withContext(Dispatchers.IO) { session.album.rename(v.uri, name, afterAccessGranted = true) } else RenameResult.Failed
                }
                if (r is RenameResult.Ok) { selected = emptySet(); reload++; session.loadLatestThumb() }
                else session.showAlert("Could not rename. The name may be empty, or Android did not allow the change.")
            }
        }
    }
}

@Composable
private fun RenameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { T(title, 18f, weight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Blue, unfocusedBorderColor = C.Line, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = C.Blue),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { T("Save", 15f, C.Blue, weight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { T("Cancel", 15f, C.TextDim) } },
    )
}

@Composable
private fun VideoCell(session: Session, v: MediaItem, video: Boolean, selected: Boolean, selecting: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    val ctx = LocalContext.current
    val thumb by produceState<Bitmap?>(null, v.uri) {
        value = withContext(Dispatchers.IO) { if (video) session.album.videoThumb(v.uri) else Pics.thumb(ctx, v.uri, 360) }
    }
    val curSelecting by rememberUpdatedState(selecting)
    Box(
        Modifier
            .fillMaxWidth().aspectRatio(1f).background(C.Card)
            .pointerInput(v.uri) {
                detectTapGestures(
                    onLongPress = { if (!curSelecting) onToggle() },
                    onTap = { if (curSelecting) onToggle() else onOpen() },
                )
            },
    ) {
        thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (selected) Box(Modifier.fillMaxSize().background(Color(0x665AB4FF)).border(3.dp, C.Blue))
        if (video) Box(
            Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(C.Glass).padding(horizontal = 6.dp, vertical = 2.dp),
        ) { T(duration(v.durationMs), 11f) }
        // selected items show a plain tick (no circle); the tint and outline above mark the selection
        if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp))
    }
}

private fun duration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun PlayerScreen(session: Session, uris: List<Uri>, start: Int) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, max(0, uris.lastIndex))) { uris.size }
    Box(Modifier.fillMaxSize().background(C.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { uris[it].toString() }) { page ->
            VideoPage(uris[page], active = pager.currentPage == page)
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.Photocator.ui.BackBtn({ session.pop() })
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (uris.size > 1) T("${pager.currentPage + 1} / ${uris.size}", 13f, C.TextDim)
            }
            Box(Modifier.size(40.dp))
        }
    }
}

/** MediaController that reports when its bar is shown or hidden, so our skip buttons can follow it. */
private class SyncedController(ctx: android.content.Context, val onVisible: (Boolean) -> Unit) : MediaController(ctx) {
    override fun show(timeout: Int) { super.show(timeout); onVisible(true) }
    override fun hide() { super.hide(); onVisible(false) }
}

@Composable
private fun VideoPage(uri: Uri, active: Boolean) {
    var view by remember { mutableStateOf<VideoView?>(null) }
    var controls by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(C.Black)) {
        if (active) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val mc = SyncedController(ctx) { controls = it }
                        mc.setAnchorView(this)
                        setMediaController(mc)
                        setVideoURI(uri)
                        setOnPreparedListener { it.isLooping = false; start() }
                        view = this
                    }
                },
                onRelease = { it.stopPlayback(); view = null },
            )
            if (controls) Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(110.dp)) {
                RoundBtn(Icons.Filled.Replay10, {
                    view?.let { v -> v.seekTo((v.currentPosition - 10_000).coerceAtLeast(0)) }
                }, size = 56.dp, bg = C.Glass, desc = "Back 10 seconds")
                RoundBtn(Icons.Filled.Forward10, {
                    view?.let { v -> v.seekTo((v.currentPosition + 10_000).coerceAtMost(max(0, v.duration))) }
                }, size = 56.dp, bg = C.Glass, desc = "Forward 10 seconds")
            }
        }
    }
}

/** Full-screen photos you can swipe between; pinch or double-tap to zoom. */
@Composable
fun PhotoViewScreen(session: Session, uris: List<Uri>, start: Int) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, max(0, uris.lastIndex))) { uris.size }
    var zoomed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(C.Black)) {
        HorizontalPager(
            pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed, key = { uris[it].toString() },
        ) { page ->
            PhotoPage(uris[page], active = pager.currentPage == page) { z -> if (pager.currentPage == page) zoomed = z }
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.Photocator.ui.BackBtn({ session.pop() })
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (uris.size > 1) T("${pager.currentPage + 1} / ${uris.size}", 13f, C.TextDim)
            }
            Box(Modifier.size(40.dp))
        }
    }
}

@Composable
private fun PhotoPage(uri: Uri, active: Boolean, onZoom: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) { value = withContext(Dispatchers.IO) { Pics.decode(ctx, uri, 2400) } }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var box by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    LaunchedEffect(scale) { onZoom(scale > 1.01f) }
    LaunchedEffect(active) { if (!active) { scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero } }

    fun clamp(o: androidx.compose.ui.geometry.Offset, sc: Float): androidx.compose.ui.geometry.Offset {
        val mx = (sc - 1f) * box.width / 2f
        val my = (sc - 1f) * box.height / 2f
        return androidx.compose.ui.geometry.Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    Box(Modifier.fillMaxSize().onSizeChanged { box = it }, contentAlignment = Alignment.Center) {
        val b = bmp
        if (b == null) Spin(size = 32.dp)
        else Image(
            b.asImageBitmap(), null,
            Modifier.fillMaxSize()
                // Pinch always zooms; a one-finger drag only pans while zoomed, so at 1x it falls through to the pager.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val ev = awaitPointerEvent()
                            val multi = ev.changes.count { it.pressed } >= 2
                            if (multi || scale > 1.01f) {
                                val z = ev.calculateZoom()
                                val p = ev.calculatePan()
                                val ns = (scale * z).coerceIn(1f, 6f)
                                scale = ns
                                offset = if (ns <= 1.01f) androidx.compose.ui.geometry.Offset.Zero else clamp(offset + p, ns)
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero } else scale = 2.5f
                    })
                }
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            contentScale = ContentScale.Fit,
        )
    }
}
