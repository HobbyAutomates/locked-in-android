package com.sohum.bandlog.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.FoodApi
import com.sohum.bandlog.data.LabelReport
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.PlateItem
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.InfoIcon
import com.sohum.bandlog.ui.components.MicIcon
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SmallChip
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.DictationState
import com.sohum.bandlog.util.FoodBits
import com.sohum.bandlog.util.rememberDictation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * v2.18 Area A on the Scan screen (web twins: components/food/VoiceHold.tsx, PlateTools.tsx,
 * LabelReality.tsx): hold-to-talk with the photo (A1), "Sized using" (A5), "Ate part of it" (A6),
 * the split sheet (A7) and the label-vs-reality note (A8).
 */

/** What the person said with the photo, plus the recogniser. [text] grows with each final phrase. */
class VoiceNote(val dictation: DictationState, val toggle: () -> Unit) {
    var text by mutableStateOf("")

    /** Stop listening (if we are) and wait for the last words, at most 1.5 s. */
    suspend fun settle() {
        if (!dictation.listening) return
        dictation.finish()
        withTimeoutOrNull(1500) { snapshotFlow { dictation.listening }.first { !it } }
    }
}

@Composable
fun rememberVoiceNote(): VoiceNote {
    val holder = remember { arrayOfNulls<VoiceNote>(1) }
    val (dict, toggle) = rememberDictation { t -> holder[0]?.let { v -> v.text = (if (v.text.isBlank()) t else "${v.text}, $t").take(400) } }
    val note = remember(dict) { VoiceNote(dict, toggle) }
    holder[0] = note
    return note
}

private const val HOLD_MS = 350L

/**
 * The round mic: press and hold to talk (let go to stop), or a quick tap to toggle hands-free.
 * [onStop] runs right after the person stops talking.
 */
@Composable
fun HoldMic(voice: VoiceNote, dark: Boolean, size: Dp = 44.dp, label: String = "Hold to add details by voice", onStop: () -> Unit = {}) {
    val p = palette
    val on = voice.dictation.listening
    val stop by rememberUpdatedState(onStop)
    val toggle by rememberUpdatedState(voice.toggle)
    val bg = when { on -> p.red; dark -> Color(0xB81C1C1E); else -> p.card2 }
    val fg = if (on || dark) Color(0xFFF5F5F7) else p.ink
    Box(
        Modifier.size(size).clip(CircleShape).background(bg)
            .pointerInput(voice) {
                detectTapGestures(onPress = {
                    val wasOn = voice.dictation.listening
                    val t0 = System.currentTimeMillis()
                    if (!wasOn) toggle()
                    tryAwaitRelease()
                    val held = System.currentTimeMillis() - t0
                    // After a hold (or a tap on a live mic) the person is done: finish the recogniser if it's
                    // still going (it may have ended itself on a pause), and always hand over the words.
                    if (held >= HOLD_MS || wasOn) {
                        if (voice.dictation.listening) voice.dictation.finish()
                        stop()
                    }
                })
            }
            .semantics {
                role = Role.Button
                contentDescription = if (on) "Listening. Tap to stop" else label
                onClick(label = if (on) "Stop" else "Talk") { if (voice.dictation.listening) { voice.dictation.finish(); stop() } else toggle(); true }
            },
        contentAlignment = Alignment.Center,
    ) { Icon(MicIcon, null, tint = fg, modifier = Modifier.size(size * 0.42f)) }
}

@Composable
fun LangToggle(voice: VoiceNote, dark: Boolean) {
    val p = palette
    Box(
        Modifier.heightIn(min = 44.dp).clickable { voice.dictation.toggleLang() }.padding(horizontal = 6.dp)
            .semantics { contentDescription = "Voice language: ${if (voice.dictation.lang == "hi-IN") "Hindi" else "English and Hinglish"}. Tap to switch." },
        contentAlignment = Alignment.Center,
    ) { Text(if (voice.dictation.lang == "hi-IN") "हिं" else "EN", fontSize = 12.sp, fontWeight = FontWeight(700), color = if (dark) Color(0xFFF5F5F7) else p.muted) }
}

/** Above the shutter in Scan food mode: EN / हिं, the mic, and what was heard (✕ clears it). */
@Composable
fun CameraVoiceRow(voice: VoiceNote) {
    if (!voice.dictation.available) return
    val listening = voice.dictation.listening
    val shown = if (listening) voice.text.ifBlank { "Listening…" } else voice.text
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LangToggle(voice, dark = true)
        HoldMic(voice, dark = true, size = 42.dp, label = "Hold to say what's on the plate")
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).height(38.dp).background(Color(0xB81C1C1E), CircleShape).padding(start = 12.dp, end = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (shown.isBlank()) "Hold the mic: “2 roti, less oil”" else "“$shown”", Modifier.weight(1f),
                fontSize = 13.sp, fontWeight = FontWeight(600), color = Color(0xFFF5F5F7).copy(alpha = if (shown.isBlank()) 0.75f else 1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (voice.text.isNotBlank() && !listening) Box(Modifier.size(36.dp).clickable { voice.text = "" }.semantics { contentDescription = "Clear what you said" }, contentAlignment = Alignment.Center) {
                Icon(CrossIcon, null, tint = Color(0xFFF5F5F7), modifier = Modifier.size(14.dp))
            }
        }
    }
    val err = voice.dictation.error
    if (err != null) Text(err, Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 6.dp), fontSize = 12.sp, color = Color(0xFFFF8A80))
}

/** "Sized using: katori" (A5) and "From your voice" (A1) under the plate title. */
@Composable
fun PlateMeta(sizedUsing: String?, voice: String?, changes: List<String>) {
    val p = palette
    val label = FoodBits.sizedUsingLabel(sizedUsing)
    if (label != null) Box(Modifier.padding(top = 6.dp).background(p.card2, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight(700), color = p.ink)
    }
    if (!voice.isNullOrBlank() && changes.isNotEmpty()) Column(Modifier.padding(top = 8.dp).fillMaxWidth().background(p.card2, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text("From your voice: “$voice”", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink)
        Text(changes.joinToString(" · "), fontSize = 12.sp, color = p.muted, lineHeight = 16.sp)
    }
}

/**
 * Add details after the photo: hold the mic or type ("2 roti, less oil, extra dal"). The plate on
 * screen is merged on the server (/api/scan-voice); [onApply] gets the new items and what changed.
 */
@Composable
fun PlateVoice(items: List<PlateItem>, plateNote: String, onApply: (List<PlateItem>, List<String>) -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val voice = rememberVoiceNote()
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val latestItems by rememberUpdatedState(items)
    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        scope.launch {
            busy = true; error = null; msg = null
            try {
                val r = FoodApi.scanVoice(latestItems, t, plateNote)
                if (r.changes.isNotEmpty()) onApply(r.items, r.changes)
                msg = (r.changes + r.notes).joinToString(" · ").ifBlank { "Nothing to change." }
                voice.text = ""; typed = ""
            } catch (e: com.sohum.bandlog.data.NotYetAvailable) {
                error = "Adding details by voice is coming with the next update."
            } catch (e: Exception) { error = e.message ?: "Couldn't add that" } finally { busy = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (voice.dictation.available) {
                HoldMic(voice, dark = false, size = 40.dp, onStop = { scope.launch { voice.settle(); send(voice.text) } })
                LangToggle(voice, dark = false)
            }
            Box(Modifier.weight(1f).height(44.dp).background(p.card, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                val listening = voice.dictation.listening
                BasicTextField(
                    if (listening) voice.text.ifBlank { "Listening…" } else typed, { if (!listening) typed = it },
                    Modifier.fillMaxWidth().semantics { contentDescription = "Details about the plate" }, singleLine = true, readOnly = listening,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(typed) }),
                    textStyle = TextStyle(fontSize = 14.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                    decorationBox = { inner -> if (typed.isEmpty() && !listening) Text(if (voice.dictation.available) "Hold the mic: “2 roti, less oil”" else "Details: “2 roti, less oil”", fontSize = 14.sp, color = p.muted, maxLines = 1); inner() },
                )
            }
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.ink)
            else if (typed.isNotBlank()) SmallChip("Add", { send(typed) }, filled = true)
        }
        msg?.let { Text(it, fontSize = 12.sp, color = p.muted, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
        voice.dictation.error?.let { Text(it, fontSize = 12.sp, color = p.orange, modifier = Modifier.padding(horizontal = 4.dp)) }
        ErrorNote(error)
    }
}

/** "Ate: All · ¾ · ½ · ¼" (A6). The rest waits as leftovers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EatenChips(value: Double, onChange: (Double) -> Unit, leftKcal: Int) {
    val p = palette
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
            Text("Ate", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp))
            FoodBits.LEFTOVER_FRACTIONS.forEach { f -> SmallChip(FoodBits.fractionLabel(f), { onChange(f) }, filled = value == f) }
        }
        if (value < 1.0) Text("The rest ($leftKcal kcal) waits as leftovers for the next 3 days.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
    }
}

/**
 * A7: split a dish across squadmates (and family who aren't on the app). Shares are equal by
 * default, −/+ per person. [onConfirm] gets the share weights (mine first, then [people] picked,
 * then family) and the picked squadmates.
 */
@Composable
fun SplitSheet(dish: String, totalKcal: Int, busy: Boolean, error: String?, onDismiss: () -> Unit, onConfirm: (weights: List<Double>, picked: List<FoodApi.Person>) -> Unit) {
    val p = palette
    var people by remember { mutableStateOf<List<FoodApi.Person>?>(null) }
    var picked by remember { mutableStateOf(listOf<FoodApi.Person>()) }
    var family by remember { mutableStateOf(0) }
    val weights = remember { mutableStateMapOf<String, Double>() }
    LaunchedEffect(Unit) { people = runCatching { FoodApi.splitPeople() }.getOrDefault(emptyList()) }
    val keys = listOf("me") + picked.map { it.id } + (0 until family).map { "family-$it" }
    val w = keys.map { weights[it] ?: 1.0 }
    val fr = FoodBits.normalizeShares(w)
    fun kcal(k: String) = (totalKcal * (fr.getOrNull(keys.indexOf(k)) ?: 0.0)).roundToInt()
    fun bump(k: String, d: Double) { weights[k] = ((weights[k] ?: 1.0) + d).coerceIn(0.5, 4.0) }
    BottomSheet(
        "Split this dish", onDismiss, subtitle = "$dish · $totalKcal kcal in all",
        primary = if (busy) "Logging…" else "Log my share · ${kcal("me")} kcal", primaryEnabled = !busy, onPrimary = { onConfirm(w, picked) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShareRow("You", kcal("me"), weights["me"] ?: 1.0, { bump("me", -0.5) }, { bump("me", 0.5) }, null)
            picked.forEach { pp -> ShareRow(pp.name, kcal(pp.id), weights[pp.id] ?: 1.0, { bump(pp.id, -0.5) }, { bump(pp.id, 0.5) }) { picked = picked - pp } }
            (0 until family).forEach { i -> ShareRow("Family ${i + 1} (not on the app)", kcal("family-$i"), weights["family-$i"] ?: 1.0, { bump("family-$i", -0.5) }, { bump("family-$i", 0.5) }, if (i == family - 1) ({ family-- }) else null) }
            Hair()
            Text("Add people", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (people == null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = p.ink)
                people.orEmpty().filter { it !in picked }.take(16).forEach { pp -> SmallChip("+ ${pp.name}", { picked = picked + pp }) }
                SmallChip("+ Family (not on the app)", { if (family < 8) family++ })
            }
            if (people?.isEmpty() == true) Text("Squadmates show up here. Family who aren't on the app just take a share.", fontSize = 12.sp, color = p.muted)
            if (picked.isNotEmpty()) Text("Each squadmate gets their share to accept. It lands in their day only when they tap Accept.", fontSize = 12.sp, color = p.muted)
            ErrorNote(error)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ShareRow(name: String, kcal: Int, weight: Double, onMinus: () -> Unit, onPlus: () -> Unit, onRemove: (() -> Unit)?) {
    val p = palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$kcal kcal", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(end = 6.dp))
        SmallChip("−", onMinus)
        Text("${com.sohum.bandlog.ui.today.fmt(weight)}×", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.padding(horizontal = 4.dp))
        SmallChip("+", onPlus)
        if (onRemove != null) Box(Modifier.size(40.dp).clickable(onClick = onRemove).semantics { contentDescription = "Remove $name" }, contentAlignment = Alignment.Center) {
            Icon(CrossIcon, null, tint = p.muted, modifier = Modifier.size(14.dp))
        } else Spacer(Modifier.width(40.dp))
    }
}

/**
 * A8: one quiet web check of a scanned label (/api/label-check). A calm note with the source when
 * the pack's numbers are more than 20 % off; nothing otherwise.
 */
@Composable
fun LabelRealityCard(report: LabelReport) {
    val p = palette
    val uri = LocalUriHandler.current
    val per = report.per100
    val cal = per["calories"]
    val key = "${report.product}|$cal|${per["protein_g"]}"
    var check by remember(key) { mutableStateOf(RealityCache.get(key)) }
    LaunchedEffect(key) {
        if (check != null || report.product.isBlank() || cal == null || RealityCache.asked(key)) return@LaunchedEffect
        RealityCache.mark(key)
        val r = FoodApi.labelCheck(report.product, FoodBits.Per100(cal, per["protein_g"], per["carbs_g"], per["fat_g"]))
        if (r != null) { RealityCache.put(key, r); check = r }
    }
    val c = check ?: return
    Row(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(20.dp)).padding(14.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Box(Modifier.size(32.dp).background(p.orangeBg, CircleShape), contentAlignment = Alignment.Center) { Icon(InfoIcon, null, tint = p.orange, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Worth a second look", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
            Text("This pack's numbers don't match what the web says about ${c.matchedName ?: "it"}. Labels are allowed some tolerance, so it may be fine.", fontSize = 12.sp, color = p.muted, lineHeight = 17.sp)
            c.lines.forEach { Text(it, fontSize = 12.sp, color = p.ink, lineHeight = 17.sp, modifier = Modifier.padding(top = 3.dp)) }
            if (c.sourceUrl != null) Box(Modifier.heightIn(min = 40.dp).clickable { runCatching { uri.openUri(c.sourceUrl) } }, contentAlignment = Alignment.CenterStart) {
                Text("Source: ${c.sourceLabel ?: c.sourceUrl}", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** One web check per product and numbers per app run (reopening a scan doesn't ask again). */
private object RealityCache {
    private val done = mutableMapOf<String, FoodApi.LabelCheck>()
    private val asked = mutableSetOf<String>()
    fun get(k: String) = synchronized(this) { done[k] }
    fun put(k: String, v: FoodApi.LabelCheck) = synchronized(this) { done[k] = v }
    fun asked(k: String) = synchronized(this) { k in asked }
    fun mark(k: String) = synchronized(this) { asked.add(k) }
}

/** The split / leftovers save for a plate: my share or the eaten part logged, the rest stored. */
object PlateSave {
    /** Saves the leftovers part quietly (skipped before schema_v42). */
    suspend fun leftovers(name: String, left: List<MealItem>, fraction: Double) {
        if (left.isEmpty()) return
        runCatching { FoodApi.saveLeftover(name, left, fraction, null) }
        FoodStore.refresh()
    }
}
