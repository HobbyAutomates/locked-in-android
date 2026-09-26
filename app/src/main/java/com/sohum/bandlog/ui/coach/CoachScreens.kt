package com.sohum.bandlog.ui.coach

import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.CoachMessage
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.motion.riseIn
import com.sohum.bandlog.ui.onboarding.OnbIcons
import com.sohum.bandlog.ui.onboarding.TunePlanFlow
import com.sohum.bandlog.ui.theme.Bricolage
import com.sohum.bandlog.ui.theme.EyebrowStyle
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Goals
import com.sohum.bandlog.util.MealTypes
import com.sohum.bandlog.util.OnboardingV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

const val COMING_SOON = "Coming with the next update"

/** The coach pages (and the buddy page) over the tab shell. One call from MainActivity. */
@Composable
fun CoachOverlays(vm: AppViewModel) {
    val cvm: CoachViewModel = viewModel()
    // A reply that logged a meal / water / a fast: Home, the rings and the streak need the new numbers.
    LaunchedEffect(cvm.loggedTick) { if (cvm.loggedTick > 0) vm.refresh() }
    AnimatedContent(
        targetState = CoachNav.page, label = "coach",
        transitionSpec = { (slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects())).togetherWith(slideOutVertically(Motion.spatialFast()) { it / 3 } + fadeOut(Motion.effectsFast())) },
    ) { page ->
        if (page == null) return@AnimatedContent
        val back: () -> Unit = { CoachNav.back() }
        if (page != CoachPage.TUNE) BackHandler { back() }
        when (page) {
            CoachPage.CHAT -> CoachChatScreen(vm, cvm, back)
            CoachPage.MEMORY -> CoachMemoryScreen(vm, cvm, back)
            CoachPage.STYLE -> CoachStyleSettingsScreen(vm, back)
            CoachPage.BUDDY -> BuddyPage(cvm, back)
            CoachPage.TUNE -> Box(Modifier.fillMaxSize()) {
                TunePlanFlow(vm.profile, onClose = back, onSaved = { back(); vm.refresh() })
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------------------------

/** The coach's iris disc with its star mark. */
@Composable
fun CoachAvatar(size: androidx.compose.ui.unit.Dp = 34.dp) {
    val p = palette
    Box(Modifier.size(size).background(p.iris, CircleShape), contentAlignment = Alignment.Center) {
        Icon(OnbIcons.Star, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun CoachTopBar(title: String, onBack: () -> Unit, sub: String? = null, subColor: Color? = null, avatar: Boolean = false, actions: @Composable () -> Unit = {}) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(p.card, CircleShape).clickable(onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(OnbIcons.Back, "Back", tint = p.ink, modifier = Modifier.size(18.dp))
        }
        if (avatar) CoachAvatar(38.dp)
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Bricolage, fontSize = if (avatar) 16.sp else 24.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
            if (sub != null) Text(sub, fontSize = 12.sp, fontWeight = FontWeight(600), color = subColor ?: p.muted)
        }
        actions()
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val p = palette
    Box(Modifier.size(40.dp).background(p.card, CircleShape).clickable(onClickLabel = label, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = p.ink, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ComingSoonCard(body: String) {
    val p = palette
    Column(Modifier.padding(20.dp).fillMaxWidth().background(p.card, RoundedCornerShape(22.dp)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(COMING_SOON, fontWeight = FontWeight(700), fontSize = 16.sp, color = p.ink)
        Text(body, fontSize = 14.sp, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Chat
// ---------------------------------------------------------------------------------------------

@Composable
fun CoachChatScreen(vm: AppViewModel, cvm: CoachViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(CoachNav.prompt.orEmpty()) }
    var photo by remember { mutableStateOf<Pair<String, android.graphics.Bitmap>?>(null) }
    LaunchedEffect(Unit) { CoachNav.prompt = null; cvm.loadChat() }
    val list = rememberLazyListState()
    LaunchedEffect(cvm.messages.size, cvm.sending) { if (cvm.messages.isNotEmpty()) list.animateScrollToItem(cvm.messages.size + 1) }
    val (dictation, toggleMic) = com.sohum.bandlog.util.rememberDictation { chunk -> text = (text.trim() + " " + chunk).trim() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
            } ?: return@launch
            val small = withContext(Dispatchers.Default) { com.sohum.bandlog.util.Images.fitWithin(bmp, 1280) }
            val b64 = withContext(Dispatchers.Default) { com.sohum.bandlog.ui.scan.toJpegBase64(small, 82) }
            photo = b64 to small
        }
    }
    fun send(msg: String) {
        val img = photo?.first
        photo = null
        cvm.send(msg, img) { restore -> text = restore }
        text = ""
    }
    val chips = listOf("Calories left?", "Log 500 ml water", "What should I eat?") + if (cvm.chatStyle == "no_excuses") listOf("Roast my week") else emptyList()

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().imePadding()) {
        CoachTopBar(
            "Your coach", onBack, avatar = true,
            sub = if (cvm.remember) "● remembers what you tell it" else "memory is off", subColor = if (cvm.remember) p.iris else p.muted,
        ) {
            RoundAction(OnbIcons.Brain, "What your coach knows") { CoachNav.open(CoachPage.MEMORY) }
            RoundAction(OnbIcons.Scale, "Coach style") { CoachNav.open(CoachPage.STYLE) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
        when (cvm.chatAvailable) {
            null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = p.muted) }
            false -> Box(Modifier.weight(1f)) { ComingSoonCard("The coach needs a quick server update. Your logs and streaks work as usual.") }
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (cvm.messages.isEmpty()) item("hello") {
                    CoachBubble("Hey! Ask me anything about food or training, or just tell me what you ate and I'll log it. Try: “2 roti, dal and paneer for lunch”.")
                }
                items(cvm.messages, key = { it.id }) { m -> MessageBlock(m, cvm) }
                item("typing") { if (cvm.sending) TypingDots() else Spacer(Modifier.height(1.dp)) }
            }
        }
        if (cvm.chatAvailable == true) Column(Modifier.fillMaxWidth().background(p.bg).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
            (cvm.chatError ?: dictation.error)?.let { Text(it, fontSize = 13.sp, color = p.red, modifier = Modifier.padding(bottom = 6.dp)) }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { c ->
                    Box(Modifier.pressable().background(p.card, CircleShape).clickable(enabled = !cvm.sending) { send(c) }.padding(horizontal = 13.dp, vertical = 9.dp)) {
                        Text(c, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1)
                    }
                }
            }
            photo?.let { (_, bmp) ->
                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(bmp.asImageBitmap(), "Photo to send", Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Text("Remove", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.clickable { photo = null }.padding(6.dp))
                }
            }
            Row(Modifier.fillMaxWidth().height(50.dp).background(p.card, CircleShape).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text("Ask or log anything…", fontSize = 15.sp, color = p.muted)
                    BasicTextField(
                        text, { text = it.take(2000) }, Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.iris),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.Sentences),
                        keyboardActions = KeyboardActions(onSend = { send(text) }),
                    )
                }
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = "Attach a photo") { pick.launch("image/*") }, contentAlignment = Alignment.Center) {
                    Icon(OnbIcons.Camera, "Attach a photo", tint = p.muted, modifier = Modifier.size(19.dp))
                }
                Box(Modifier.size(36.dp).background(if (dictation.listening) p.irisBg else Color.Transparent, CircleShape).clickable(onClickLabel = if (dictation.listening) "Stop listening" else "Speak", onClick = toggleMic), contentAlignment = Alignment.Center) {
                    Icon(OnbIcons.Mic, if (dictation.listening) "Stop listening" else "Speak", tint = if (dictation.listening) p.iris else p.muted, modifier = Modifier.size(19.dp))
                }
                val can = !cvm.sending && (text.isNotBlank() || photo != null)
                Box(Modifier.size(38.dp).background(p.btn, CircleShape).alpha(if (can) 1f else 0.5f).clickable(enabled = can, onClickLabel = "Send") { send(text) }, contentAlignment = Alignment.Center) {
                    Icon(OnbIcons.Send, "Send", tint = p.btnInk, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun CoachBubble(text: String) {
    val p = palette
    Row(Modifier.fillMaxWidth(0.9f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CoachAvatar()
        Text(
            text, fontSize = 14.5.sp, lineHeight = 21.sp, color = p.ink,
            modifier = Modifier.background(p.irisBg, RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)).border(1.dp, p.iris.copy(alpha = 0.33f), RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun MessageBlock(m: CoachMessage, cvm: CoachViewModel) {
    val p = palette
    val t = com.sohum.bandlog.ui.motion.rememberMotion("msg-${m.id}", 0, 700)
    Column(Modifier.fillMaxWidth().riseIn(t), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth()) {
            if (m.fromCoach) CoachBubble(m.text)
            else Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    m.text, fontSize = 14.5.sp, lineHeight = 20.sp, color = p.btnInk,
                    modifier = Modifier.widthIn(max = 290.dp).background(p.btn, RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)).padding(horizontal = 15.dp, vertical = 12.dp),
                )
            }
        }
        if (m.fromCoach && m.cards.isNotEmpty()) Column(Modifier.padding(start = 44.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            m.cards.forEach { c -> ToolCard(c, cvm) }
        }
    }
}

@Composable
private fun TypingDots() {
    val p = palette
    val tr = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        CoachAvatar()
        Row(
            Modifier.background(p.irisBg, RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)).border(1.dp, p.iris.copy(alpha = 0.33f), RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)).padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics { contentDescription = "Coach is typing" },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(3) { i ->
                val a by tr.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 160), RepeatMode.Reverse), label = "d$i")
                Box(Modifier.size(8.dp).alpha(a).background(p.iris, CircleShape))
            }
        }
    }
}

/** A tool result under the coach's reply (web docs/v214-spec.md CoachCard). */
@Composable
private fun ToolCard(c: JSONObject, cvm: CoachViewModel) {
    val p = palette
    val ctx = LocalContext.current
    val box = Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
    when (c.optString("type")) {
        "meal_logged" -> Row(box) {
            Column(Modifier.weight(1f)) {
                val mt = c.optString("meal_type")
                Text("Logged · ${if (MealTypes.isType(mt)) MealTypes.label(mt).removeSuffix("s") else "Meal"}", fontSize = 13.5.sp, fontWeight = FontWeight(700), color = p.ink)
                Text(c.optString("title"), fontSize = 13.5.sp, color = p.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${c.optInt("kcal")} kcal", fontSize = 13.5.sp, fontWeight = FontWeight(700), color = p.ink)
                Text("+${num(c.optDouble("protein_g", 0.0))} g protein", fontSize = 13.5.sp, fontWeight = FontWeight(700), color = p.ink)
            }
        }
        "water_logged" -> Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight(700))) { append("Logged · Water ") }
            withStyle(SpanStyle(color = p.muted)) { append("+${c.optInt("ml")} ml") }
        }, fontSize = 13.5.sp, color = p.ink, modifier = box)
        "remaining" -> Row(box, horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("${c.optInt("kcal")}" to "KCAL LEFT", "${c.optInt("protein")} g" to "PROTEIN", "${c.optInt("carbs")} g" to "CARBS", "${c.optInt("fat")} g" to "FAT").forEach { (v, l) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, fontSize = 14.sp, fontWeight = FontWeight(800), color = p.ink)
                    Text(l, style = EyebrowStyle.copy(fontSize = 9.sp), color = p.muted)
                }
            }
        }
        "suggestions" -> Column(box, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val arr = c.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                Row {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight(700))) { append(it.optString("name")) }
                        withStyle(SpanStyle(color = p.muted)) { append(" ${it.optString("portion")}") }
                    }, fontSize = 13.5.sp, color = p.ink, modifier = Modifier.weight(1f))
                    Text("${it.optInt("kcal")} kcal · ${num(it.optDouble("protein", 0.0))} g P", fontSize = 13.sp, color = p.ink)
                }
            }
        }
        "fast_started" -> Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight(700))) { append("Fast started ") }
            withStyle(SpanStyle(color = p.muted)) { append("${c.optInt("hours")} h · see it on Home") }
        }, fontSize = 13.5.sp, color = p.ink, modifier = box)
        "memory" -> {
            val id = c.optString("id")
            val decided = cvm.decided[id]
            Column(Modifier.fillMaxWidth().background(p.irisBg, RoundedCornerShape(16.dp)).border(1.dp, p.iris.copy(alpha = 0.33f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight(700), color = p.iris)) { append("Learned: ") }
                    append("“${c.optString("text")}”")
                    if (decided != null) withStyle(SpanStyle(color = p.muted)) { append(if (decided) " · kept" else " · forgotten") }
                }, fontSize = 13.5.sp, color = p.ink)
                if (decided == null && id.isNotBlank()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Keep", fontSize = 13.5.sp, fontWeight = FontWeight(700), color = p.iris, modifier = Modifier.clickable { cvm.decide(id, true) }.padding(vertical = 4.dp))
                    Text("Forget", fontSize = 13.5.sp, fontWeight = FontWeight(700), color = p.iris, modifier = Modifier.clickable { cvm.decide(id, false) }.padding(vertical = 4.dp))
                }
            }
        }
        "helpline" -> Column(box, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Talk to someone today", fontSize = 13.5.sp, fontWeight = FontWeight(700), color = p.ink)
            Goals.HELPLINES.forEach { h ->
                Column {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight(600))) { append(h.name) }
                        withStyle(SpanStyle(color = p.muted)) { append(" ${h.detail}") }
                    }, fontSize = 13.sp, color = p.ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        h.phones.forEach { ph ->
                            Text(ph, fontSize = 13.5.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.clickable {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + ph.filter { it.isDigit() || it == '+' })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }.padding(vertical = 3.dp))
                        }
                    }
                }
            }
            Text(Goals.HELPLINE_NOTE, fontSize = 12.sp, color = p.muted)
        }
    }
}

private fun num(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)

// ---------------------------------------------------------------------------------------------
// What your coach knows
// ---------------------------------------------------------------------------------------------

private val KINDS = listOf("goal" to "GOALS", "food" to "FOOD", "life" to "LIFE", "style" to "STYLE", "body" to "BODY")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachMemoryScreen(vm: AppViewModel, cvm: CoachViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { cvm.loadMemory() }
    var adding by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val kept = cvm.memories.filter { it.kept }
    val pending = cvm.memories.filter { !it.kept && it.id !in cvm.decided }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        CoachTopBar("What your coach knows", onBack)
        when (cvm.memoryAvailable) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = p.muted) }
            false -> ComingSoonCard("Coach memory needs a quick server update.")
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
                Text("Everything here shapes your notes and suggestions. Tap × to forget it.", fontSize = 14.5.sp, lineHeight = 21.sp, color = p.muted, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp))
                (cvm.memoryError)?.let { Text(it, fontSize = 13.sp, color = p.red, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp)) }
                cvm.memoryNotice?.let { Text(it, fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp)) }
                if (kept.isEmpty()) Text("Nothing yet. Chat with your coach, or add something below.", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp))
                KINDS.forEach { (k, label) ->
                    val list = kept.filter { it.kind == k }
                    if (list.isNotEmpty()) Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                        Text(label, style = EyebrowStyle, color = p.muted, modifier = Modifier.padding(bottom = 10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            list.forEach { m ->
                                Row(Modifier.background(p.card, CircleShape).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(m.text, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.padding(vertical = 9.dp))
                                    Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClickLabel = "Forget ${m.text}") { cvm.forget(m.id) }, contentAlignment = Alignment.Center) {
                                        Icon(OnbIcons.X, "Forget", tint = p.muted, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                pending.forEach { m ->
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth().background(p.irisBg, RoundedCornerShape(18.dp)).border(1.dp, p.iris.copy(alpha = 0.33f), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight(700), color = p.iris)) { append("Learned ${ago(m.createdAt)}: ") }
                            append("“${m.text}”.")
                        }, fontSize = 14.sp, lineHeight = 20.sp, color = p.ink)
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Keep", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.iris, modifier = Modifier.clickable { cvm.decide(m.id, true) }.padding(vertical = 4.dp))
                            Text("Forget", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.iris, modifier = Modifier.clickable { cvm.decide(m.id, false) }.padding(vertical = 4.dp))
                        }
                    }
                }
                Row(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth().pressable().background(p.card, RoundedCornerShape(18.dp)).clickable { adding = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(OnbIcons.Plus, null, tint = p.ink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Tell your coach something", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                }
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth().background(p.card, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Let coach remember", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text("Only you see this. Delete anything, anytime.", fontSize = 12.5.sp, color = p.muted)
                    }
                    Switch(
                        cvm.remember,
                        { on ->
                            cvm.setRememberLocal(on)
                            vm.patchProfileV37(JSONObject().put("coach_remember", on), { it.copy(coachRemember = on) }) { ok -> if (!ok) { cvm.setRememberLocal(!on); cvm.memoryError = "Couldn't save that. Try again." } }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = p.iris, checkedThumbColor = Color.White, uncheckedTrackColor = p.track, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.hair),
                    )
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth().background(p.card, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp)) {
                    LinkRow("Export chat + memories") { cvm.export(ctx) }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
                    LinkRow("Delete chat history", danger = true) { confirm = "chat" }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
                    LinkRow("Forget everything", danger = true) { confirm = "all" }
                }
            }
        }
    }
    if (adding) AddMemorySheet(cvm) { adding = false }
    confirm?.let { what ->
        BottomSheet(
            title = if (what == "chat") "Delete chat history?" else "Forget everything?",
            subtitle = if (what == "chat") "Your coach keeps its memories; only the messages go." else "Every memory goes. Your coach starts from your profile again.",
            onDismiss = { confirm = null }, primary = if (what == "chat") "Delete chat" else "Forget everything",
            onPrimary = { if (what == "chat") cvm.deleteChat() else cvm.forgetEverything(); confirm = null },
        ) {}
    }
}

@Composable
private fun LinkRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val p = palette
    Text(label, fontSize = 15.sp, fontWeight = FontWeight(600), color = if (danger) p.red else p.ink, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMemorySheet(cvm: CoachViewModel, onDismiss: () -> Unit) {
    val p = palette
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("food") }
    BottomSheet(
        title = "Tell your coach", subtitle = "A routine, a food you love or hate, a constraint. Keep it short.", onDismiss = onDismiss,
        primary = "Remember this", primaryEnabled = text.trim().length >= 3,
        onPrimary = { cvm.add(text, kind) { ok -> if (ok) onDismiss() } },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KINDS.forEach { (k, l) ->
                val on = k == kind
                Box(Modifier.background(if (on) p.btn else p.card2, CircleShape).clickable { kind = k }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(l.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight(600), color = if (on) p.btnInk else p.ink)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(52.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text("e.g. Gym at 6 pm, 4 days", fontSize = 15.sp, color = p.muted)
            BasicTextField(text, { text = it.take(120) }, Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.iris))
        }
        cvm.memoryError?.let { Text(it, fontSize = 13.sp, color = p.red, modifier = Modifier.padding(top = 8.dp)) }
    }
}

private fun ago(iso: String?): String = runCatching {
    val d = java.time.OffsetDateTime.parse(iso!!).atZoneSameInstant(com.sohum.bandlog.util.Dates.ZONE).toLocalDate()
    when (java.time.temporal.ChronoUnit.DAYS.between(d, java.time.LocalDate.now(com.sohum.bandlog.util.Dates.ZONE))) {
        0L -> "today"
        1L -> "yesterday"
        else -> "this week"
    }
}.getOrDefault("recently")

// ---------------------------------------------------------------------------------------------
// Coach style settings
// ---------------------------------------------------------------------------------------------

@Composable
fun CoachStyleSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val prof = vm.profile
    val teen = Goals.isTeen(prof.age)
    var error by remember { mutableStateOf<String?>(null) }
    val available = prof.v37
    fun save(fields: JSONObject, local: (com.sohum.bandlog.data.Profile) -> com.sohum.bandlog.data.Profile) {
        error = null
        vm.patchProfileV37(fields, local) { ok -> if (!ok) error = "Couldn't save that. Try again." }
    }
    val style = OnboardingV2.effectiveCoachStyle(prof.coachStyle, prof.age)
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        CoachTopBar("Coach style", onBack)
        if (!available) { ComingSoonCard("Coach settings need a quick server update."); return@Column }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { Text(it, fontSize = 13.sp, color = p.red) }
            OnboardingV2.STYLES.forEach { s ->
                val on = s.key == style
                val locked = teen && s.key == "no_excuses"
                val grad = when (s.key) {
                    "calm" -> Brush.linearGradient(listOf(p.iris, p.iris.copy(alpha = 0.55f)))
                    "no_excuses" -> Brush.linearGradient(listOf(p.ember, com.sohum.bandlog.ui.theme.Brand.EmberLight))
                    else -> null
                }
                Column(
                    Modifier.fillMaxWidth().alpha(if (locked) 0.45f else 1f).background(p.card, RoundedCornerShape(22.dp))
                        .then(if (on) Modifier.border(2.dp, p.ember, RoundedCornerShape(22.dp)) else Modifier)
                        .clickable(enabled = !locked && !on) {
                            save(JSONObject().put("coach_style", s.key).apply { if (s.key != "no_excuses") put("coach_weekly_roast", false) }) { it.copy(coachStyle = s.key, coachWeeklyRoast = if (s.key != "no_excuses") false else it.coachWeeklyRoast) }
                        }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) { CoachMark(s.key, 24.dp, p.ink, gradient = grad) }
                        Column(Modifier.weight(1f)) {
                            Text(s.label, fontFamily = Bricolage, fontSize = 17.sp, fontWeight = FontWeight(800), color = p.ink)
                            Text(if (locked) "18+ only" else s.short, fontSize = 13.sp, color = p.muted)
                        }
                        Box(Modifier.size(22.dp).border(2.dp, if (on) p.ember else p.hair, CircleShape), contentAlignment = Alignment.Center) {
                            if (on) Box(Modifier.size(11.dp).background(p.ember, CircleShape))
                        }
                    }
                    Text("“${s.sample}”", fontSize = 14.sp, fontStyle = FontStyle.Italic, color = p.muted)
                }
            }
            Column(Modifier.padding(top = 4.dp).fillMaxWidth().background(p.card, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                val noteTime = prof.coachNoteTime ?: "08:00"
                SettingLine("Morning note", clock(noteTime)) {
                    pickTime(ctx, noteTime) { t -> save(JSONObject().put("coach_note_time", t)) { it.copy(coachNoteTime = t) } }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
                val qf = prof.coachQuietFrom ?: "23:00"
                val qt = prof.coachQuietTo ?: "07:00"
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Quiet hours", fontSize = 15.sp, color = p.ink, modifier = Modifier.weight(1f))
                    Text(clock(qf), fontSize = 15.sp, color = p.muted, modifier = Modifier.clickable { pickTime(ctx, qf) { t -> save(JSONObject().put("coach_quiet_from", t)) { it.copy(coachQuietFrom = t) } } }.padding(4.dp))
                    Text("–", fontSize = 15.sp, color = p.muted)
                    Text(clock(qt), fontSize = 15.sp, color = p.muted, modifier = Modifier.clickable { pickTime(ctx, qt) { t -> save(JSONObject().put("coach_quiet_to", t)) { it.copy(coachQuietTo = t) } } }.padding(4.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
                val roastOk = style == "no_excuses" && !teen
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).alpha(if (roastOk) 1f else 0.45f), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Weekly roast (Sunday)", fontSize = 15.sp, color = p.ink)
                        if (!roastOk) Text("No excuses only", fontSize = 12.sp, color = p.muted)
                    }
                    Switch(
                        roastOk && prof.coachWeeklyRoast == true,
                        { on -> save(JSONObject().put("coach_weekly_roast", on)) { it.copy(coachWeeklyRoast = on) } },
                        enabled = roastOk,
                        colors = SwitchDefaults.colors(checkedTrackColor = p.ember, checkedThumbColor = p.onEmber, uncheckedTrackColor = p.track, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.hair),
                    )
                }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(OnbIcons.Shield, null, tint = p.muted, modifier = Modifier.size(18.dp))
                Text(
                    "Every style is about habits, never looks. Under 18s get Balanced at most. If you mention not eating or feeling low, the coach switches to calm and shares help.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = p.muted,
                )
            }
        }
    }
}

@Composable
private fun SettingLine(label: String, value: String, onClick: () -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = p.ink, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = p.muted)
    }
}

/** "08:00" → "8:00 am". */
fun clock(hhmm: String): String {
    val h = hhmm.take(2).toIntOrNull() ?: 8
    val m = hhmm.drop(3).take(2).toIntOrNull() ?: 0
    val ap = if (h < 12) "am" else "pm"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12:${m.toString().padStart(2, '0')} $ap"
}

private fun pickTime(ctx: android.content.Context, current: String, onPick: (String) -> Unit) {
    val h = current.take(2).toIntOrNull() ?: 8
    val m = current.drop(3).take(2).toIntOrNull() ?: 0
    runCatching {
        TimePickerDialog(ctx, { _, hh, mm -> onPick("${hh.toString().padStart(2, '0')}:${mm.toString().padStart(2, '0')}") }, h, m, false).show()
    }
}

// ---------------------------------------------------------------------------------------------
// Buddy page (invite, enter a code, the list)
// ---------------------------------------------------------------------------------------------

@Composable
fun BuddyPage(cvm: CoachViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    var code by remember { mutableStateOf(CoachNav.buddyCode.orEmpty()) }
    LaunchedEffect(Unit) { CoachNav.buddyCode = null; cvm.loadBuddies() }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().imePadding()) {
        CoachTopBar("Buddy streaks", onBack)
        if (cvm.buddyAvailable == false) { ComingSoonCard("Buddy streaks need a quick server update."); return@Column }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Both log = streak grows. One skips = the other gets to nudge. Break it and you both start over.", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(600), color = p.ember, modifier = Modifier.fillMaxWidth().background(p.emberBg, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp))
            cvm.buddies.orEmpty().forEach { b -> BuddyRow(b, cvm) }
            Box(Modifier.fillMaxWidth().height(52.dp).background(p.btn, CircleShape).clickable(enabled = !cvm.buddyBusy) { cvm.invite(ctx) }, contentAlignment = Alignment.Center) {
                Text("Invite a buddy", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.btnInk)
            }
            Text("GOT A CODE?", style = EyebrowStyle, color = p.muted, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(50.dp).background(p.card, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    if (code.isEmpty()) Text("ABC234", fontSize = 16.sp, color = p.muted, letterSpacing = 3.sp)
                    BasicTextField(
                        code, { v -> code = v.filter { it.isLetterOrDigit() }.uppercase().take(6) }, singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = p.ink, letterSpacing = 3.sp, fontWeight = FontWeight(700)), cursorBrush = SolidColor(p.ember),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    )
                }
                val ok = code.length == 6 && !cvm.buddyBusy
                Box(Modifier.height(50.dp).background(p.card, CircleShape).border(1.dp, p.hair, CircleShape).alpha(if (ok) 1f else 0.5f).clickable(enabled = ok) { cvm.accept(code) { if (it) code = "" } }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text("Accept", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                }
            }
            cvm.buddyNote?.let { Text(it, fontSize = 13.sp, color = p.muted) }
        }
    }
}

/** One buddy: the shared flame (ember), today's both-logged dots and Nudge when they haven't logged. */
@Composable
fun BuddyRow(b: com.sohum.bandlog.data.Buddy, cvm: CoachViewModel, onOpen: (() -> Unit)? = null) {
    val p = palette
    val both = b.meToday && b.partnerToday
    val first = b.partnerName.substringBefore(' ')
    Row(
        Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(20.dp)).then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(if (b.streak > 0) p.emberBg else p.card2, CircleShape), contentAlignment = Alignment.Center) {
            Icon(OnbIcons.Flame, null, tint = if (b.streak > 0) p.ember else p.muted, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("${b.streak}-day streak · you + ${b.partnerName}", fontSize = 15.sp, fontWeight = FontWeight(800), color = p.ink, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Dot(b.meToday); Text("you", fontSize = 12.sp, color = p.muted)
                Dot(b.partnerToday); Text(first + if (both) " · today counts" else "", fontSize = 12.sp, color = p.muted, maxLines = 1)
            }
        }
        if (!b.partnerToday) {
            val state = cvm.nudged[b.id]
            Box(
                Modifier.background(p.btn, CircleShape).clickable(enabled = state == null) { cvm.nudge(b) }.padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(state ?: "Nudge", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.btnInk) }
        }
    }
}

@Composable
private fun Dot(on: Boolean) {
    val p = palette
    Box(Modifier.size(8.dp).background(if (on) p.ink else Color.Transparent, CircleShape).border(1.5.dp, p.ink, CircleShape))
}
