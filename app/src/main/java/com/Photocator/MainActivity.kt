package com.Photocator

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.Photocator.ui.AlertHost
import com.Photocator.ui.C
import com.Photocator.ui.Host
import com.Photocator.ui.PhotocatorTheme
import com.Photocator.ui.camera.ShootScreen
import com.Photocator.ui.review.ReviewScreen
import com.Photocator.ui.settings.AboutScreen
import com.Photocator.ui.settings.PlayerScreen
import com.Photocator.ui.settings.SettingsScreen
import com.Photocator.ui.settings.TagEditorScreen
import com.Photocator.ui.settings.VideosScreen
import com.Photocator.ui.settings.PhotosScreen
import com.Photocator.ui.settings.PhotoViewScreen
import com.Photocator.ui.settings.WatermarksScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), Host {
    private lateinit var session: Session
    private val startMs = android.os.SystemClock.elapsedRealtime()

    private var permWait: CompletableDeferred<Unit>? = null
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permWait?.complete(Unit)
    }
    private var pickWait: ((Uri?) -> Unit)? = null
    private val pickLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cb = pickWait
        pickWait = null
        cb?.invoke(uri)
    }
    private var deleteWait: ((Boolean) -> Unit)? = null
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val cb = deleteWait
        deleteWait = null
        cb?.invoke(res.resultCode == android.app.Activity.RESULT_OK)
    }
    private var sequenceJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        session = (application as PhotocatorApp).session
        enableEdgeToEdge()

        // Splash: leave once everything is granted (after 500 ms) or after 1.5 s when no request is pending.
        splash.setKeepOnScreenCondition {
            val el = android.os.SystemClock.elapsedRealtime() - startMs
            val done = (session.perms.value.all && el >= 500) || (el >= 1500 && !session.requesting.value)
            !done
        }

        session.shoot.requestMicrophone = {
            if (!session.hasMicrophone() && !micAsked) {
                micAsked = true
                permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }

        setContent { PhotocatorTheme { Root() } }

        session.loadLatestThumb()
        session.applyLiveState()
        if (!session.perms.value.all) requestPermissions()
    }

    private var micAsked = false

    override fun onResume() {
        super.onResume()
        hideNav()
        session.refreshPerms()
        session.loadLatestThumb()
        session.tilt.start()
    }

    override fun onPause() {
        session.tilt.stop()
        super.onPause()
    }

    private fun hideNav() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.navigationBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNav()
    }

    // ------------------------------------------------------------------ keys
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = volume(keyCode, event) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = volume(keyCode, event) || super.onKeyUp(keyCode, event)

    private fun volume(keyCode: Int, event: KeyEvent): Boolean {
        if (session.stack.value.isNotEmpty() || !session.perms.value.all) return false
        return session.shoot.onVolumeKey(keyCode, event)
    }

    // ------------------------------------------------------------------ Host
    override fun requestPermissions() {
        if (sequenceJob?.isActive == true) return
        sequenceJob = lifecycleScope.launch {
            session.requesting.value = true
            try {
                delay(500)
                val steps = listOf(
                    arrayOf(Manifest.permission.CAMERA),
                    session.mediaPermissions(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
                for ((i, step) in steps.withIndex()) {
                    val missing = step.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
                    if (missing.isEmpty()) continue
                    if (i > 0) delay(300)
                    val w = CompletableDeferred<Unit>()
                    permWait = w
                    permLauncher.launch(missing.toTypedArray())
                    w.await()
                    session.refreshPerms()
                }
            } finally {
                session.requestedOnce = true
                session.requesting.value = false
                session.refreshPerms()
            }
        }
    }

    override fun openAppSettings() {
        startSafe(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    override fun openLocationSettings() {
        startSafe(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    override fun pickPhotoToTag() {
        pickWait = { uri ->
            if (uri != null) {
                val type = contentResolver.getType(uri)?.lowercase()
                if (type == "image/png" || type == "image/jpeg" || type == "image/jpg") {
                    session.push(Screen.Review(ReviewSource.Imported(uri)))
                } else {
                    session.showAlert("This file type is not supported. Choose a PNG or JPG image.", "Unsupported file")
                }
            }
        }
        launchPicker()
    }

    override fun pickWatermark(onPicked: (Uri?) -> Unit) {
        pickWait = onPicked
        launchPicker()
    }

    private fun launchPicker() {
        try { pickLauncher.launch("image/*") } catch (_: Exception) {
            val cb = pickWait
            pickWait = null
            cb?.invoke(null)
            session.showAlert("No app available to pick images.")
        }
    }

    override fun viewPhoto(uri: Uri?) {
        val target = uri ?: session.thumbUri.value
        if (target == null) {
            // Nothing to open: fall back to the system gallery
            startSafe(Intent(Intent.ACTION_VIEW).setType("image/*"))
            return
        }
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(target, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(view) } catch (_: Exception) {
            startSafe(Intent(Intent.ACTION_VIEW).setType("image/*"))
        }
    }

    override fun openUrl(url: String) {
        startSafe(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun requestVideoAccess(onDone: () -> Unit) {
        val p = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            else -> session.mediaPermissions()
        }
        lifecycleScope.launch {
            val w = CompletableDeferred<Unit>()
            permWait = w
            permLauncher.launch(p)
            w.await()
            session.refreshPerms()
            onDone()
        }
    }

    override fun shareMedia(uris: List<Uri>, mime: String) {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startSafe(Intent.createChooser(intent, "Share"))
    }

    override fun launchSender(sender: android.content.IntentSender, onDone: (Boolean) -> Unit) {
        try {
            deleteWait = onDone
            deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
        } catch (_: Exception) {
            deleteWait = null
            onDone(false)
        }
    }

    override fun confirmDelete(uris: List<Uri>, onDone: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 30 && uris.isNotEmpty()) {
            try {
                val pi = android.provider.MediaStore.createDeleteRequest(contentResolver, uris)
                deleteWait = { onDone() }
                deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build())
                return
            } catch (_: Exception) { }
        }
        session.showAlert("Could not delete some videos.")
        onDone()
    }

    private fun startSafe(i: Intent) {
        try { startActivity(i) } catch (_: ActivityNotFoundException) {
            session.showAlert("Nothing on this device can open that.")
        } catch (_: Exception) { }
    }

    // ------------------------------------------------------------------ UI
    @Composable
    private fun Root() {
        val stack by session.stack.collectAsState()
        val panelOpen by session.shoot.panelOpen.collectAsState()
        BackHandler(enabled = stack.isNotEmpty() || panelOpen) {
            if (stack.isNotEmpty()) session.pop() else session.shoot.panelOpen.value = false
        }
        Box(Modifier.fillMaxSize().background(C.Black)) {
            ShootScreen(session, this@MainActivity)
            stack.forEachIndexed { i, screen ->
                key(i, screen) {
                    val vis = remember { MutableTransitionState(false).apply { targetState = true } }
                    val enter = when (screen) {
                        is Screen.Settings -> fadeIn(tween(250))
                        is Screen.Review -> slideInVertically(tween(300)) { it }
                        else -> slideInHorizontally(tween(300)) { it }
                    }
                    AnimatedVisibility(vis, Modifier.fillMaxSize(), enter = enter) {
                        // A no-ripple click handler stops touches reaching the camera underneath
                        // without getting in the way of scrolling inside the screen.
                        Box(
                            Modifier.fillMaxSize().background(C.Black).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        ) {
                            when (screen) {
                                is Screen.Settings -> SettingsScreen(session, this@MainActivity)
                                is Screen.About -> AboutScreen(session, this@MainActivity)
                                is Screen.Watermarks -> WatermarksScreen(session, this@MainActivity)
                                is Screen.TagEditor -> TagEditorScreen(session)
                                is Screen.Videos -> VideosScreen(session, this@MainActivity)
                                is Screen.Photos -> PhotosScreen(session, this@MainActivity)
                                is Screen.PhotoView -> PhotoViewScreen(session, screen.uris, screen.index)
                                is Screen.Player -> PlayerScreen(session, screen.uris, screen.index)
                                is Screen.Review -> ReviewScreen(session, screen.source, this@MainActivity)
                            }
                        }
                    }
                }
            }
            AlertHost(session)
        }
    }
}
