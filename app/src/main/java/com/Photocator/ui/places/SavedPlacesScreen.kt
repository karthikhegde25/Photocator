package com.Photocator.ui.places

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Photocator.Session
import com.Photocator.core.Fmt
import com.Photocator.core.SavedPlace
import com.Photocator.net.Web
import com.Photocator.ui.BigButton
import com.Photocator.ui.C
import com.Photocator.ui.ConfirmDialog
import com.Photocator.ui.RoundBtn
import com.Photocator.ui.ScreenHeader
import com.Photocator.ui.Spin
import com.Photocator.ui.T
import com.Photocator.ui.map.MapPicker
import kotlinx.coroutines.launch

/**
 * The saved locations. With [onPick] set (choosing a location for a photo) tapping a row selects it;
 * without it (opened from the camera) tapping a row renames it. The + button adds a location found on the map or by search.
 */
@Composable
fun SavedPlacesScreen(session: Session, onClose: () -> Unit, onPick: ((SavedPlace) -> Unit)? = null) {
    val places by session.places.flow.collectAsState()
    val scope = rememberCoroutineScope()
    var showMap by remember { mutableStateOf(false) }
    var looking by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<Triple<Double, Double, Pair<String, String>>?>(null) }   // lat, lon, (name, address)
    var renaming by remember { mutableStateOf<SavedPlace?>(null) }
    var deleting by remember { mutableStateOf<SavedPlace?>(null) }

    BackHandler { onClose() }

    Box(Modifier.fillMaxSize().background(C.Black).pointerInput(Unit) { }) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(if (onPick != null) "Choose a saved location" else "Saved locations", onBack = onClose) {
                if (looking) Spin(size = 22.dp) else RoundBtn(Icons.Filled.Add, { showMap = true }, size = 40.dp, bg = C.GlassLight, desc = "Add location")
            }
            if (places.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    T("No saved locations yet", 18f, align = TextAlign.Center)
                    T("Tap + to add a place from the map or by searching. You can then use it on any photo.", 13f, C.TextDim, Modifier.padding(vertical = 10.dp), align = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(places, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(C.Card)
                                .clickable { if (onPick != null) onPick(p) else renaming = p }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.LocationOn, null, tint = C.Green, modifier = Modifier.size(26.dp))
                            Column(Modifier.weight(1f)) {
                                T(p.name, 15f, weight = FontWeight.SemiBold, maxLines = 1)
                                if (p.address.isNotBlank()) T(p.address, 12f, C.TextDim, maxLines = 2)
                                T(Fmt.coords(p.lat, p.lon), 11f, C.TextFaint, maxLines = 1)
                            }
                            if (onPick != null) Icon(Icons.Filled.Edit, "Rename", tint = C.TextDim, modifier = Modifier.size(22.dp).clickable { renaming = p })
                            Icon(Icons.Filled.Delete, "Delete", tint = C.Red, modifier = Modifier.size(22.dp).clickable { deleting = p })
                        }
                    }
                }
            }
            BigButton("Add location", { showMap = true }, Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), bg = C.Blue, fg = C.BlueDark, busy = looking)
        }

        if (showMap) {
            MapPicker(
                session = session,
                start = session.pos.live.value.data?.let { it.lat to it.lon },
                onClose = { showMap = false },
                onPick = { la, lo ->
                    showMap = false
                    scope.launch {
                        looking = true
                        val r = try { Web.reverse(la, lo) } catch (_: Exception) { null }
                        looking = false
                        naming = Triple(la, lo, (r?.placeName ?: "") to (r?.address ?: ""))
                    }
                },
            )
        }
    }

    naming?.let { (la, lo, na) ->
        NameDialog("Save location", na.first, "Save", { naming = null }) { name ->
            naming = null
            session.places.add(name, na.second, la, lo)
            session.toast("Location saved")
        }
    }
    renaming?.let { p ->
        NameDialog("Rename location", p.name, "Save", { renaming = null }) { name ->
            renaming = null
            session.places.rename(p.id, name)
        }
    }
    deleting?.let { p ->
        ConfirmDialog("Delete location", "Delete \"${p.name}\"?", "Delete", onConfirm = {
            deleting = null
            session.places.delete(p.id)
        }, onDismiss = { deleting = null }, danger = true)
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial.take(60)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { T(title, 18f, weight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(60) }, singleLine = true,
                label = { T("Name", 12f, C.TextDim) },
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Blue, unfocusedBorderColor = C.Line, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = C.Blue),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { T(confirm, 15f, C.Blue, weight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { T("Cancel", 15f, C.TextDim) } },
    )
}
