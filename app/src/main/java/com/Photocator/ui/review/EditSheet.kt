package com.Photocator.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.Photocator.Session
import com.Photocator.core.Fmt
import com.Photocator.core.PlusCode
import com.Photocator.core.TagData
import com.Photocator.core.TagPlacement
import com.Photocator.ui.BigButton
import com.Photocator.ui.C
import com.Photocator.ui.RoundBtn
import com.Photocator.ui.T
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DATE_RE = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")
private val TIME_RE = Regex("^(\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?$")

/** Combines the typed date and time (local zone); null unless both are valid and a real calendar moment. */
private fun parseWhen(date: String, time: String): Long? {
    val d = DATE_RE.matchEntire(date.trim()) ?: return null
    val t = TIME_RE.matchEntire(time.trim()) ?: return null
    return try {
        val cal = Calendar.getInstance()
        cal.isLenient = false
        cal.clear()
        cal.set(
            d.groupValues[1].toInt(), d.groupValues[2].toInt() - 1, d.groupValues[3].toInt(),
            t.groupValues[1].toInt(), t.groupValues[2].toInt(), t.groupValues[3].ifEmpty { "0" }.toInt(),
        )
        cal.timeInMillis
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, numeric: Boolean = false, singleLine: Boolean = true, minLines: Int = 1, hint: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { T(label, 12f, C.TextDim) },
        placeholder = if (hint != null) { { T(hint, 14f, C.TextFaint) } } else null,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Blue, unfocusedBorderColor = C.Line,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = C.Blue,
        ),
    )
}

/** Bottom sheet that edits every field of the geotag. */
@Composable
fun EditSheet(session: Session, model: ReviewModel, data: TagData, onClose: () -> Unit) {
    val marks by session.marks.flow.collectAsState()
    val initialWm = remember { model.watermarkId }
    val df = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    var name by remember { mutableStateOf(data.placeName) }
    var address by remember { mutableStateOf(data.address) }
    var lat by remember { mutableStateOf(Fmt.plain(data.lat)) }
    var lon by remember { mutableStateOf(Fmt.plain(data.lon)) }
    var date by remember { mutableStateOf(df.format(Date(data.timeMs))) }
    var time by remember { mutableStateOf(tf.format(Date(data.timeMs))) }
    var alt by remember { mutableStateOf(Fmt.plain(data.altitude)) }
    var wind by remember { mutableStateOf(Fmt.plain(data.windKmh)) }
    var hum by remember { mutableStateOf(data.humidity?.toString() ?: "") }
    var head by remember { mutableStateOf(Fmt.plain(data.headingDeg)) }
    var mag by remember { mutableStateOf(data.magneticUt?.let { String.format(Locale.US, "%.1f", it) } ?: "") }
    var temp by remember { mutableStateOf(Fmt.plain(data.tempC)) }
    var weather by remember { mutableStateOf(data.condition) }
    var note by remember { mutableStateOf(data.note) }

    fun reset() {
        name = data.placeName; address = data.address
        lat = Fmt.plain(data.lat); lon = Fmt.plain(data.lon)
        date = df.format(Date(data.timeMs)); time = tf.format(Date(data.timeMs))
        alt = Fmt.plain(data.altitude); wind = Fmt.plain(data.windKmh)
        hum = data.humidity?.toString() ?: ""; head = Fmt.plain(data.headingDeg)
        mag = data.magneticUt?.let { String.format(Locale.US, "%.1f", it) } ?: ""
        temp = Fmt.plain(data.tempC); weather = data.condition; note = data.note
        model.watermarkId = initialWm
    }

    fun build(): Pair<TagData, Boolean> {
        val la = lat.trim().toDoubleOrNull() ?: 0.0
        val lo = lon.trim().toDoubleOrNull() ?: 0.0
        val draft = TagData(
            lat = la, lon = lo,
            altitude = alt.trim().toDoubleOrNull(),
            speed = data.speed,
            placeName = name, address = address,
            plusCode = PlusCode.encode(la, lo),
            timeMs = parseWhen(date, time) ?: data.timeMs,
            tempC = temp.trim().toDoubleOrNull(),
            condition = weather.trim().ifEmpty { "Unknown" },
            humidity = hum.trim().toDoubleOrNull()?.toInt(),
            windKmh = wind.trim().toDoubleOrNull(),
            magneticUt = mag.trim().toDoubleOrNull() ?: data.magneticUt,
            headingDeg = head.trim().toDoubleOrNull() ?: data.headingDeg,
            note = note.trim(),
        )
        val finalData = session.withSensorFallback(draft, data)
        return finalData to (la != data.lat || lo != data.lon)
    }

    BackHandler { onClose() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val screenH = LocalConfiguration.current.screenHeightDp.dp

    Box(Modifier.fillMaxSize().background(Color(0xA6000000)).clickable(onClick = onClose)) {
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(screenH * 0.85f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(C.Card)
                    .clickable(enabled = false) {}
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { T("Edit GPS data", 19f, weight = FontWeight.SemiBold) }
                    RoundBtn(Icons.Filled.Close, onClose, size = 38.dp, bg = C.GlassLight, desc = "Close")
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("Pick on map", { model.showEdit = false; model.showMap = true }, Modifier.weight(1f), bg = C.GlassLight, fg = Color.White)
                        if (!model.isCapture) {
                            BigButton("Current location", { model.showEdit = false; model.useCurrentLocation() }, Modifier.weight(1f), bg = C.GlassLight, fg = Color.White)
                        }
                    }
                    Field("Place name", name, { name = it }, hint = "Eiffel Tower, Paris, France")
                    Field("Address", address, { address = it }, singleLine = false, minLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field("Latitude", lat, { lat = it }, Modifier.weight(1f), numeric = true)
                        Field("Longitude", lon, { lon = it }, Modifier.weight(1f), numeric = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field("Date", date, { date = it }, Modifier.weight(1f), hint = "YYYY-MM-DD")
                        Field("Time", time, { time = it }, Modifier.weight(1f), hint = "HH:mm:ss")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field("Altitude (m)", alt, { alt = it }, Modifier.weight(1f), numeric = true)
                        Field("Wind (km/h)", wind, { wind = it }, Modifier.weight(1f), numeric = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field("Humidity (%)", hum, { hum = it }, Modifier.weight(1f), numeric = true)
                        Field("Heading (°)", head, { head = it }, Modifier.weight(1f), numeric = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field("Magnetic field (µT)", mag, { mag = it }, Modifier.weight(1f), numeric = true)
                        Field("Temperature (°C)", temp, { temp = it }, Modifier.weight(1f), numeric = true)
                    }
                    Field("Weather", weather, { weather = it }, hint = "Clear, Rain...")
                    Field("Note (optional)", note, { note = it.replace('\n', ' ').take(60) }, hint = "Up to 60 characters")

                    T("Watermark", 13f, C.TextDim, weight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { WmChip("None", null, model.watermarkId == null) { model.watermarkId = null } }
                        items(marks) { w ->
                            val bmp = remember(w.id) { session.marks.load(w, 128) }
                            WmChip(w.name, bmp?.asImageBitmap(), model.watermarkId == w.id) { model.watermarkId = w.id }
                        }
                    }
                    T(
                        when {
                            marks.isEmpty() -> "Upload a watermark in Settings > Watermark."
                            model.watermarkId == null -> "No watermark on this photo. Tap one to add it."
                            else -> "Tap None to remove it from this photo."
                        },
                        12f, C.TextFaint,
                    )

                    if (model.placement == TagPlacement.ON_PHOTO) {
                        BigButton("Edit geotag position", {
                            val (d, ch) = build()
                            model.applyEdit(d, ch)
                            model.showPosition = true
                        }, Modifier.fillMaxWidth(), bg = C.GlassLight, fg = Color.White)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Reset", { reset() }, Modifier.weight(0.8f), bg = C.GlassLight, fg = Color.White)
                    BigButton("Apply changes", {
                        val (d, ch) = build()
                        model.applyEdit(d, ch)
                    }, Modifier.weight(1.2f), bg = C.Blue, fg = C.BlueDark)
                }
            }
        }
    }
}

@Composable
private fun WmChip(label: String, bmp: androidx.compose.ui.graphics.ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(70.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF7A828A))
                .border(if (selected) 3.dp else 1.dp, if (selected) C.Blue else C.Line, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) Image(bmp, label, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
            else T("—", 20f, Color.White)
        }
        T(label, 11f, C.TextDim, Modifier.padding(top = 4.dp), maxLines = 1)
    }
}
