package com.Photocator.ui.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Photocator.Session
import com.Photocator.core.Watermark
import com.Photocator.media.Pics
import com.Photocator.ui.BigButton
import com.Photocator.ui.C
import com.Photocator.ui.Card
import com.Photocator.ui.ConfirmDialog
import com.Photocator.ui.Host
import com.Photocator.ui.ScreenHeader
import com.Photocator.ui.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WatermarksScreen(session: Session, host: Host) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by session.prefs.flow.collectAsState()
    val marks by session.marks.flow.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Watermark?>(null) }
    var deleting by remember { mutableStateOf<Watermark?>(null) }

    Column(Modifier.fillMaxSize().background(C.Black)) {
        ScreenHeader("Watermarks", onBack = { session.pop() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            T(
                "Upload your logos and choose one to show in the top right of the geotag. None is selected by default. You can also add or remove a watermark for a single photo in the edit sheet.",
                13f, C.TextDim,
            )
            Card {
                Row(
                    Modifier.fillMaxWidth().clickable { session.prefs.update { it.copy(watermarkId = null) } }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(C.CardHi), contentAlignment = Alignment.Center) { T("—", 20f, C.TextDim) }
                    Box(Modifier.weight(1f)) { T("No watermark", 15f) }
                    RadioButton(prefs.watermarkId == null, { session.prefs.update { it.copy(watermarkId = null) } }, colors = RadioButtonDefaults.colors(selectedColor = C.Blue))
                }
                marks.forEach { w ->
                    val bmp = remember(w.id) { session.marks.load(w, 128) }
                    Row(
                        Modifier.fillMaxWidth().clickable { session.prefs.update { it.copy(watermarkId = w.id) } }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF7A828A))) {
                            if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                        }
                        Box(Modifier.weight(1f)) { T(w.name, 15f, maxLines = 1) }
                        Icon(Icons.Filled.Edit, "Rename", tint = C.TextDim, modifier = Modifier.size(22.dp).clickable { renaming = w })
                        Icon(Icons.Filled.Delete, "Delete", tint = C.Red, modifier = Modifier.size(22.dp).clickable { deleting = w })
                        RadioButton(prefs.watermarkId == w.id, { session.prefs.update { it.copy(watermarkId = w.id) } }, colors = RadioButtonDefaults.colors(selectedColor = C.Blue))
                    }
                }
            }
            BigButton("Add watermark", {
                if (busy) return@BigButton
                host.pickWatermark { uri ->
                    if (uri != null) {
                        pendingUri = uri
                        pendingName = displayNameOf(ctx, uri) ?: "Watermark ${marks.size + 1}"
                    }
                }
            }, Modifier.fillMaxWidth(), bg = C.Blue, fg = C.BlueDark)
            Box(Modifier.height(24.dp))
        }
    }

    pendingUri?.let { uri ->
        NameDialog("Name your watermark", pendingName, "Save", { pendingUri = null }) { name ->
            pendingUri = null
            scope.launch {
                busy = true
                val bmp = withContext(Dispatchers.IO) { Pics.decode(ctx, uri, 512) }
                if (bmp == null) session.showAlert("Could not read that image.")
                else if (withContext(Dispatchers.IO) { session.marks.add(name, bmp) } == null) session.showAlert("Could not read that image.")
                busy = false
            }
        }
    }
    renaming?.let { w ->
        NameDialog("Rename watermark", w.name, "Save", { renaming = null }) { name ->
            renaming = null
            session.marks.rename(w.id, name)
        }
    }
    deleting?.let { w ->
        ConfirmDialog("Delete watermark", "Delete \"${w.name}\"?", "Delete", onConfirm = {
            deleting = null
            session.marks.delete(w.id)
            if (session.prefs.value.watermarkId == w.id) session.prefs.update { it.copy(watermarkId = null) }
        }, onDismiss = { deleting = null }, danger = true)
    }
}

private fun displayNameOf(ctx: android.content.Context, uri: Uri): String? = try {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.')?.ifBlank { null } else null
    }
} catch (_: Exception) { null }

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial.take(40)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { T(title, 18f, weight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(40) }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Blue, unfocusedBorderColor = C.Line, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = C.Blue),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { T(confirm, 15f, C.Blue, weight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { T("Cancel", 15f, C.TextDim) } },
    )
}
