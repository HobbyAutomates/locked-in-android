package com.sohum.bandlog.ui.onboarding

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import com.sohum.bandlog.ui.motion.drawPathTrimmed
import com.sohum.bandlog.ui.coach.CoachMark
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.AuthException
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.OnbSync
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.SupabaseAuth
import com.sohum.bandlog.data.V214Api
import com.sohum.bandlog.ui.components.LockedInMark
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.motion.MotionScreen
import com.sohum.bandlog.ui.motion.rememberAnimationsEnabled
import com.sohum.bandlog.ui.squad.shareInvite
import com.sohum.bandlog.ui.theme.AccentStyle
import com.sohum.bandlog.ui.theme.BandLogTheme
import com.sohum.bandlog.ui.theme.Bricolage
import com.sohum.bandlog.ui.theme.EyebrowStyle
import com.sohum.bandlog.ui.theme.HeadlineStyle
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Bmi
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Goals
import com.sohum.bandlog.util.MealTypes
import com.sohum.bandlog.util.OnbStore
import com.sohum.bandlog.util.OnboardingV2
import com.sohum.bandlog.util.rememberDictation
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * v2.14 onboarding, value first (designs BOnb*, web docs/v214-spec.md): 17 screens in the design's
 * order, starting before the account exists. [section] is the progress pill's lit icon.
 */
enum class OnbStep(val section: Int?) {
    SOURCE(0), GOAL(0), FIRST_LOG(0), RESULT(0), STREAK(1), BODY(2), PACE(2), TRAINING(3), EATER(4), OBSTACLES(4), COACH(4),
    BUDDY(5), CHALLENGE(5), BUILDING(null), REVEAL(null), PLEDGE(null), SAVE(null),
}

/** The screens this person sees: no result / streak without a first log, no pace for "just stay locked in", no save when signed in. */
fun onbSteps(a: OnboardingV2.Answers, hasLog: Boolean, signedIn: Boolean): List<OnbStep> = OnbStep.entries.filter { s ->
    when (s) {
        OnbStep.RESULT, OnbStep.STREAK -> hasLog
        OnbStep.PACE -> a.goal != "habits"
        OnbStep.SAVE -> !signedIn
        else -> true
    }
}

/**
 * The flow. Signed out it ends on Save (the account is created there and [onAccountReady] hands
 * over to the shell, which replays everything through /api/onboarding/finish). Signed in (a
 * profile without height / weight / birthday) it ends after the pledge and saves straight away,
 * then calls [onFinished].
 *
 * @param onExit back from the first screen.
 * @param onSignIn "I already have an account" / "I've confirmed my email": the sign-in form (the
 *   answers stay on the device and are saved after that sign-in).
 * @param onSquadCode a squad code typed on the buddy screen (joined once the shell is up).
 */
@Composable
fun OnboardingV2Screen(
    signedIn: Boolean,
    onExit: () -> Unit,
    onFinished: () -> Unit = {},
    onAccountReady: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onSquadCode: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var a by remember { mutableStateOf(OnbStore.answers) }
    var log by remember { mutableStateOf(OnbStore.firstLog) }
    var plan by remember { mutableStateOf<OnboardingV2.Plan?>(null) }
    val steps = onbSteps(a, log != null, signedIn)
    var step by remember { mutableStateOf(OnbStep.entries.getOrNull(OnbStore.step)?.takeIf { it in steps && it != OnbStep.BUILDING } ?: OnbStep.SOURCE) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun update(next: OnboardingV2.Answers) { a = next; OnbStore.answers = next }
    fun go(s: OnbStep) { step = s; error = null; OnbStore.step = s.ordinal }
    fun next() {
        val list = onbSteps(a, log != null, signedIn)
        val i = list.indexOf(step)
        list.getOrNull(i + 1)?.let { go(it) }
    }
    fun back() {
        val list = onbSteps(a, log != null, signedIn)
        val i = list.indexOf(step)
        // Nothing to go back to from the building animation or the save screen's success state.
        val prev = list.getOrNull(i - 1)?.let { if (it == OnbStep.BUILDING) OnbStep.CHALLENGE else it }
        if (prev == null) onExit() else go(prev)
    }
    BackHandler { if (!busy) back() }

    val age = Goals.ageYears(a.dob)
    val teen = Goals.isTeen(age)

    fun finishSignedIn() {
        if (busy) return
        scope.launch {
            busy = true; error = null
            try {
                OnbStore.pending = true
                OnbSync.replayIfPending()
                OnbStore.squadCode?.let { onSquadCode(it); OnbStore.squadCode = null }
                onFinished()
            } catch (e: AuthException) { error = e.message ?: "Sign in again to save your plan." }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Couldn't save your plan. Try again." }
            finally { busy = false }
        }
    }

    AnimatedContent(step, label = "onb", transitionSpec = { fadeIn(tween(260)).togetherWith(fadeOut(tween(160))) }) { s ->
        MotionScreen {
            when (s) {
                OnbStep.SOURCE -> PickScreen(
                    s, ::back, "Quick one", "Where'd you find us?", "Helps us find more people like you.",
                    OnboardingV2.SOURCES.map { it to sourceIcon(it.key) }, a.heardFrom, { update(a.copy(heardFrom = it)) }, onNext = ::next,
                )
                OnbStep.GOAL -> PickScreen(
                    s, ::back, "Your goal", "What are we\nlocking in on?", "Pick one. You can change it anytime.",
                    OnboardingV2.GOALS.map { it to goalIcon(it.key) }, a.goal, { update(a.copy(goal = it)) }, onNext = ::next,
                )
                OnbStep.FIRST_LOG -> FirstLogScreen(signedIn, log?.text.orEmpty(), ::back, onSkip = { log = null; OnbStore.firstLog = null; go(OnbStep.BODY) }) { text, items ->
                    val fl = OnbStore.FirstLog(text, log?.mealType ?: MealTypes.default(), Dates.today(), items)
                    log = fl; OnbStore.firstLog = fl
                    go(OnbStep.RESULT)
                }
                OnbStep.RESULT -> log?.let { l ->
                    ResultScreen(l, ::back, onType = { t -> val nl = l.copy(mealType = t); log = nl; OnbStore.firstLog = nl }, onEdit = { go(OnbStep.FIRST_LOG) }, onNext = ::next)
                } ?: LaunchedEffect(Unit) { go(OnbStep.BODY) }
                OnbStep.STREAK -> StreakScreen(::back, ::next)
                OnbStep.BODY -> BodyScreen(a, ::back, { update(it) }, ::next)
                OnbStep.PACE -> PaceScreen(a, teen, ::back, { update(it) }, ::next)
                OnbStep.TRAINING -> TrainingScreen(a, teen, ::back, { update(it) }, ::next)
                OnbStep.EATER -> EaterScreen(a, teen, ::back, { update(it) }, ::next)
                OnbStep.OBSTACLES -> ObstaclesScreen(a, ::back, { update(it) }, ::next)
                OnbStep.COACH -> CoachStyleScreen(a.coachStyle, teen, ::back, s.section) { update(a.copy(coachStyle = it)); next() }
                OnbStep.BUDDY -> BuddyScreen(signedIn, ::back, onSquadCode = { code ->
                    if (signedIn) onSquadCode(code) else OnbStore.squadCode = code
                }, onNext = ::next)
                OnbStep.CHALLENGE -> ChallengeScreen(a.firstChallenge, ::back) { update(a.copy(firstChallenge = it)); next() }
                OnbStep.BUILDING -> BuildingScreen(a, log != null, onPlan = { plan = it }) { next() }
                OnbStep.REVEAL -> RevealScreen(a, plan, ::back, onPlan = { plan = it }, onNext = ::next)
                OnbStep.PLEDGE -> BandLogTheme(dark = true) {
                    PledgeScreen(a, plan, busy, error, ::back) {
                        if (signedIn) finishSignedIn() else { OnbStore.pending = true; next() }
                    }
                }
                OnbStep.SAVE -> SaveScreen(a, plan, onName = { update(a.copy(name = it)) }, onAccountReady = onAccountReady, onSignIn = onSignIn, onBack = ::back)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Icons per option
// ---------------------------------------------------------------------------------------------

private fun sourceIcon(key: String) = when (key) {
    "instagram" -> OnbIcons.Instagram
    "youtube" -> OnbIcons.Youtube
    "friend" -> OnbIcons.Users
    "college_gym" -> OnbIcons.Cap
    "search" -> OnbIcons.Search
    else -> OnbIcons.Dots
}

private fun goalIcon(key: String) = when (key) {
    "lose" -> OnbIcons.ArrowDown
    "gain" -> OnbIcons.Dumbbell
    "recomp" -> OnbIcons.Plus
    else -> OnbIcons.Lock
}

private fun obstacleIcon(key: String) = when (key) {
    "exam_stress" -> OnbIcons.Book
    "mess_food" -> OnbIcons.Home
    "late_night" -> OnbIcons.Moon
    "no_time" -> OnbIcons.Clock
    "eating_out" -> OnbIcons.Users
    else -> OnbIcons.Chart
}

// ---------------------------------------------------------------------------------------------
// 1 · 2 Source, Goal
// ---------------------------------------------------------------------------------------------

@Composable
private fun PickScreen(
    s: OnbStep, onBack: () -> Unit, eyebrow: String, headline: String, sub: String,
    options: List<Pair<OnboardingV2.Option, androidx.compose.ui.graphics.vector.ImageVector>>, selected: String?, onPick: (String) -> Unit, onNext: () -> Unit,
) {
    OnbScaffold(s.section, onBack, bottom = { OnbPrimary("Continue", onNext, enabled = selected != null) }) {
        OnbHeader(eyebrow, headline, sub)
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEachIndexed { i, (o, icon) ->
                Entrance(1 + i, key = "o$i") { OnbOption(o.label, o.sub.ifBlank { null }, icon, selected == o.key, { onPick(o.key) }) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 3 First log (works signed out)
// ---------------------------------------------------------------------------------------------

@Composable
private fun FirstLogScreen(signedIn: Boolean, initial: String, onBack: () -> Unit, onSkip: () -> Unit, onParsed: (String, List<MealItem>) -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var text by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val (dictation, toggleMic) = rememberDictation { chunk -> text = (text.trim() + " " + chunk).trim() }

    fun parse() {
        val t = text.trim()
        if (t.isBlank() || busy) return
        focus.clearFocus()
        scope.launch {
            busy = true; note = null
            try {
                val r = V214Api.parsePreview(t)
                if (r.items.isEmpty()) note = "Couldn't find any food in that. Try \"2 roti, dal, curd\"."
                else onParsed(t, r.items)
            } catch (e: NotYetAvailable) { note = "Logging before sign-up is coming with the next update. Skip for now and log it after you save your plan." }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { note = e.message ?: "Couldn't read that. Check your connection and try again." }
            finally { busy = false }
        }
    }

    val laterNote = if (signedIn) "Photo and barcode live on the Scan tab. Type or say it for now." else "Photo and barcode work once your plan is saved. Type or say it for now."
    OnbScaffold(OnbStep.FIRST_LOG.section, onBack, bottom = {
        (note ?: dictation.error)?.let { Text(it, fontSize = 13.sp, color = p.muted, textAlign = TextAlign.Center) }
        OnbFootnote("I'll log later", onSkip)
        Row(
            Modifier.fillMaxWidth().height(58.dp).shadow(14.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, CircleShape).padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("2 roti, dal, curd, salad", fontSize = 16.sp, color = p.muted)
                BasicTextField(
                    text, { text = it.take(300) }, Modifier.fillMaxWidth().focusRequester(focusRequester), singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = p.ink), cursorBrush = SolidColor(p.ember),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.None),
                    keyboardActions = KeyboardActions(onSend = { parse() }),
                )
            }
            Box(Modifier.size(42.dp).background(if (dictation.listening) p.emberBg else Color.Transparent, CircleShape).clickable(onClickLabel = "Say it", onClick = toggleMic), contentAlignment = Alignment.Center) {
                Icon(OnbIcons.Mic, "Say it", tint = if (dictation.listening) p.ember else p.ink, modifier = Modifier.size(20.dp))
            }
            Box(Modifier.size(42.dp).background(p.btn, CircleShape).clickable(enabled = text.isNotBlank() && !busy, onClickLabel = "Read my meal") { parse() }, contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.btnInk)
                else Icon(OnbIcons.Send, "Read my meal", tint = p.btnInk, modifier = Modifier.size(18.dp))
            }
        }
    }) {
        OnbHeader("Try it first", "What did you\neat today?", "Log your first meal before we ask you anything. Takes 5 seconds.")
        val ways = listOf(
            Triple("Type it", "2 roti, dal, curd", { focusRequester.requestFocus() }),
            Triple("Say it", "Hinglish works: “do roti aur dal”", { toggleMic() }),
            Triple("Snap it", "Photo of your plate", { note = laterNote }),
            Triple("Scan it", "Any packaged food", { note = laterNote }),
        )
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ways.forEachIndexed { i, (title, sub, act) ->
                Entrance(1 + i, key = "w$i") {
                    Row(
                        Modifier.fillMaxWidth().pressable().background(p.card, RoundedCornerShape(18.dp)).clickable(onClick = act).padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(28.dp).background(p.emberBg, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight(800), color = p.ember)
                        }
                        Column {
                            Text(title, fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
                            Text(sub, fontSize = 13.5.sp, color = p.muted)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 4 Result + coach note
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultScreen(log: OnbStore.FirstLog, onBack: () -> Unit, onType: (String) -> Unit, onEdit: () -> Unit, onNext: () -> Unit) {
    val p = palette
    val kcal = log.items.sumOf { it.calories }
    val protein = log.items.sumOf { it.proteinG }
    val carbs = log.items.sumOf { it.carbsG }
    val fat = log.items.sumOf { it.fatG }
    OnbScaffold(OnbStep.RESULT.section, onBack, bottom = {
        OnbPrimary("Looks right, continue", onNext)
        OnbFootnote("Not quite? Edit it", onEdit)
    }) {
        OnbHeader("Locked In read it", "Here's what\nyou ate.")
        Entrance(1, key = "card") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth().background(p.card, RoundedCornerShape(22.dp)).padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 14.dp)) {
                log.items.forEachIndexed { i, it ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(it.name, fontSize = 16.sp, fontWeight = FontWeight(600), color = p.ink)
                            Text(it.quantityLabel, fontSize = 12.5.sp, color = p.muted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(buildAnnotatedString {
                                append("${it.calories.roundToInt()}")
                                withStyle(SpanStyle(fontSize = 12.sp, color = p.muted, fontWeight = FontWeight(500))) { append(" kcal") }
                            }, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                            Text("${it.proteinG.roundToInt()} g protein", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink.copy(alpha = 0.75f))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("${kcal.roundToInt()} kcal", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
                    Text("P ${protein.roundToInt()} g · C ${carbs.roundToInt()} g · F ${fat.roundToInt()} g", fontSize = 14.sp, color = p.muted)
                }
            }
        }
        Entrance(2, key = "note") {
            IrisBubble(OnboardingV2.firstLogNote(kcal, protein, log.items.map { it.name }), label = "Coach note", modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp))
        }
        Entrance(3, key = "type") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Text("LOG AS", style = EyebrowStyle, color = p.muted, modifier = Modifier.padding(bottom = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MealTypes.BREAKFAST, MealTypes.LUNCH, MealTypes.SNACK, MealTypes.DINNER).forEach { t ->
                        OnbChip(if (t == MealTypes.SNACK) "Snack" else MealTypes.label(t), log.mealType == t) { onType(t) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 5 Day-1 streak
// ---------------------------------------------------------------------------------------------

@Composable
private fun StreakScreen(onBack: () -> Unit, onNext: () -> Unit) {
    val p = palette
    val anim = rememberAnimationsEnabled()
    val bob = if (anim) rememberInfiniteTransition(label = "bob").animateFloat(-4f, 4f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "bobY").value else 0f
    val todayDow = LocalDate.now(Dates.ZONE).dayOfWeek.value % 7 // Sunday = 0
    OnbScaffold(OnbStep.STREAK.section, onBack, bottom = {
        OnbPrimary("Keep going", onNext)
        OnbFootnote("Logged today. That's all it takes.")
    }) {
        OnbHeader(null, "Day 1.\nDon't break\nthe chain.")
        Entrance(1, key = "flame") {
            Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(180.dp).background(Brush.radialGradient(listOf(p.ember.copy(alpha = 0.2f), Color.Transparent)), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(OnbIcons.Flame, null, tint = p.ember, modifier = Modifier.size(84.dp).graphicsLayer { translationY = bob * density })
                }
            }
        }
        Entrance(2, key = "label") {
            Text("STREAK · DAY 1", style = EyebrowStyle, color = p.ember, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        Entrance(3, key = "copy") {
            Text(
                "One log a day keeps it alive. Your squad sees it too, so no pressure… okay, a little pressure.",
                fontSize = 15.sp, lineHeight = 22.sp, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 10.dp),
            )
        }
        Entrance(4, key = "week") {
            Row(Modifier.fillMaxWidth().padding(start = 34.dp, end = 34.dp, top = 26.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(34.dp).background(if (i == todayDow) p.ember else p.card, CircleShape), contentAlignment = Alignment.Center) {
                            if (i == todayDow) Icon(OnbIcons.Flame, null, tint = p.onEmber, modifier = Modifier.size(16.dp))
                        }
                        Text(d, fontSize = 11.sp, fontWeight = FontWeight(600), color = p.muted)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 6 Height + weight (+ birthday and sex, which the targets need)
// ---------------------------------------------------------------------------------------------

private val HEIGHTS = (120..220).toList()
private val WEIGHTS = (60..400).map { it / 2.0 } // 30.0 … 200.0 kg in 0.5 steps

@Composable
private fun BodyScreen(a: OnboardingV2.Answers, onBack: () -> Unit, onChange: (OnboardingV2.Answers) -> Unit, onNext: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    var ftIn by remember { mutableStateOf(false) }
    var lb by remember { mutableStateOf(false) }
    val h = a.heightCm ?: 170.0
    val w = a.weightKg ?: 65.0
    // Defaults count as answers once the screen is shown, so Continue only waits for birthday + sex.
    LaunchedEffect(Unit) { if (a.heightCm == null || a.weightKg == null) onChange(a.copy(heightCm = h, weightKg = w)) }
    val bmi = Bmi.bmi(w, h)
    val age = Goals.ageYears(a.dob)
    OnbScaffold(OnbStep.BODY.section, onBack, bottom = { OnbPrimary("Continue", onNext, enabled = a.dob != null && a.gender != null) }) {
        OnbHeader("About you", "Height and\nweight.", "Honest numbers make a plan that actually works.")
        Entrance(1, key = "units") {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                UnitToggle("cm", "ft/in", ftIn) { ftIn = it }
                UnitToggle("kg", "lb", lb) { lb = it }
            }
        }
        Entrance(2, key = "wheels") {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Wheel(HEIGHTS.size, HEIGHTS.indexOf(h.roundToInt()).coerceAtLeast(0), { i -> if (ftIn) ftInLabel(HEIGHTS[i].toDouble()) else "${HEIGHTS[i]}" }, Modifier.width(140.dp)) {
                        onChange(a.copy(heightCm = HEIGHTS[it].toDouble(), weightKg = w))
                    }
                    Text(if (ftIn) "FT / IN" else "CM", style = EyebrowStyle, color = p.muted, modifier = Modifier.padding(top = 6.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val wi = WEIGHTS.indexOfFirst { abs(it - w) < 0.26 }.coerceAtLeast(0)
                    Wheel(WEIGHTS.size, wi, { i -> if (lb) "${(WEIGHTS[i] * 2.20462).roundToInt()}" else fmtKg(WEIGHTS[i]) }, Modifier.width(140.dp)) {
                        onChange(a.copy(weightKg = WEIGHTS[it], heightCm = h))
                    }
                    Text(if (lb) "LB" else "KG", style = EyebrowStyle, color = p.muted, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Entrance(3, key = "bmi") {
            if (bmi != null && (age == null || !Goals.isTeen(age))) Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth().background(p.card, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Your BMI (Indian ranges)", fontSize = 14.sp, color = p.muted, modifier = Modifier.weight(1f))
                Text("${fmtKg(bmi)} · ${bmiWords(Bmi.categoryIndia(bmi))}", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
            }
        }
        // Not in the design: the targets need age and sex too, so they share one compact row.
        Entrance(4, key = "dob") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth().background(p.card, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val shown = a.dob?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) }.getOrNull() }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val start = runCatching { LocalDate.parse(a.dob) }.getOrDefault(LocalDate.of(2005, 1, 1))
                        runCatching {
                            DatePickerDialog(ctx, { _, y, m, d -> onChange(a.copy(dob = LocalDate.of(y, m + 1, d).toString())) }, start.year, start.monthValue - 1, start.dayOfMonth)
                                .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                        }
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Birthday", fontSize = 15.sp, color = p.muted, modifier = Modifier.weight(1f))
                    Text(shown ?: "Pick date", fontSize = 15.sp, fontWeight = FontWeight(700), color = if (shown == null) p.ember else p.ink)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sex", fontSize = 15.sp, color = p.muted, modifier = Modifier.weight(1f))
                    Row(Modifier.background(p.card2, CircleShape).padding(3.dp)) {
                        listOf("male" to "Male", "female" to "Female", "other" to "Other").forEach { (k, l) ->
                            val on = a.gender == k
                            Box(Modifier.background(if (on) p.card else Color.Transparent, CircleShape).clickable { onChange(a.copy(gender = k)) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text(l, fontSize = 13.sp, fontWeight = FontWeight(600), color = if (on) p.ink else p.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun bmiWords(cat: String) = when (cat) {
    "Normal" -> "healthy range"
    "Underweight" -> "a little under healthy"
    "Overweight" -> "a little above healthy"
    else -> cat.lowercase()
}

private fun fmtKg(v: Double): String = String.format(Locale.US, "%.1f", v)

private fun ftInLabel(cm: Double): String {
    val inches = (cm / 2.54).roundToInt()
    return "${inches / 12}′${inches % 12}″"
}

@Composable
private fun UnitToggle(a: String, b: String, second: Boolean, onChange: (Boolean) -> Unit) {
    val p = palette
    Row(Modifier.background(p.card2, CircleShape).padding(4.dp)) {
        listOf(false to a, true to b).forEach { (v, l) ->
            Box(Modifier.background(if (second == v) p.card else Color.Transparent, CircleShape).clickable { onChange(v) }.padding(horizontal = 18.dp, vertical = 7.dp)) {
                Text(l, fontSize = 14.sp, fontWeight = FontWeight(600), color = if (second == v) p.ink else p.muted)
            }
        }
    }
}

/** A snapping number wheel: five rows, the middle one big on a white card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Wheel(count: Int, selected: Int, label: (Int) -> String, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = palette
    val rowH = 48.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val center by remember { derivedStateOf { (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0).coerceIn(0, count - 1) } }
    // The latest callback (it closes over the latest answers), not the one from the first composition.
    val select by androidx.compose.runtime.rememberUpdatedState(onSelect)
    val current by androidx.compose.runtime.rememberUpdatedState(selected)
    LaunchedEffect(state) { snapshotFlow { center }.distinctUntilChanged().collect { if (it != current) select(it) } }
    Box(modifier.height(rowH * 5), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(68.dp).shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = p.shadow, spotColor = p.shadow).background(p.card, RoundedCornerShape(22.dp)))
        LazyColumn(Modifier.fillMaxSize(), state = state, flingBehavior = fling, contentPadding = PaddingValues(vertical = rowH * 2), horizontalAlignment = Alignment.CenterHorizontally) {
            items(count) { i ->
                val d = abs(i - center)
                Box(Modifier.fillMaxWidth().height(rowH), contentAlignment = Alignment.Center) {
                    Text(
                        label(i), fontFamily = Bricolage, fontSize = when (d) { 0 -> 40.sp; 1 -> 24.sp; else -> 19.sp }, fontWeight = if (d == 0) FontWeight(800) else FontWeight(600),
                        letterSpacing = (-1).sp, color = p.ink, modifier = Modifier.alpha(when (d) { 0 -> 1f; 1 -> 0.45f; else -> 0.2f }), maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 7 Target + pace (no date yet)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PaceScreen(a: OnboardingV2.Answers, teen: Boolean, onBack: () -> Unit, onChange: (OnboardingV2.Answers) -> Unit, onNext: () -> Unit) {
    val p = palette
    val age = Goals.ageYears(a.dob)
    val goalType = OnboardingV2.goalTypeOf(a.goal, age)
    val kg = a.weightKg ?: 65.0
    if (teen || goalType == "maintain") {
        // Under 18: no loss pace, ever (util/Goals). A growth-safe note instead.
        OnbScaffold(OnbStep.PACE.section, onBack, bottom = { OnbPrimary("Continue", { onChange(a.copy(goalWeightKg = null, pace = null)); onNext() }) }) {
            OnbHeader("Your pace", "We'll grow\nwith you.")
            Entrance(1, key = "teen") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp).fillMaxWidth().background(p.card, RoundedCornerShape(22.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("NO WEIGHT-LOSS PACE UNDER 18", style = EyebrowStyle, color = p.muted)
                    Text(
                        "Your body is still growing, so we don't set a loss target. Your plan fuels growth, school and training instead, and we'll track strength and habits. Talk to a doctor or dietitian if weight is on your mind.",
                        fontSize = 15.sp, lineHeight = 22.sp, color = p.ink,
                    )
                }
            }
        }
        return
    }
    val gain = goalType == "gain"
    val defaultTarget = if (gain) kg + 3 else kg - 5
    val target = a.goalWeightKg?.takeIf { if (gain) it > kg else it < kg } ?: defaultTarget
    val paces = listOf("chill", "steady", "aggressive")
    val pace = a.pace ?: "steady"
    val perWeek = OnboardingV2.paceKg(goalType, pace)
    val capped = kotlin.math.min(perWeek, if (gain) Goals.maxSafeWeeklyGainKg(kg) else Goals.maxSafeWeeklyLossKg(kg))
    val weeks = if (capped > 0) ceil(abs(kg - target) / capped).toInt().coerceAtLeast(1) else null
    LaunchedEffect(Unit) { if (a.goalWeightKg == null || a.pace == null) onChange(a.copy(goalWeightKg = target, pace = pace)) }
    val sign = if (gain) "+" else "−"
    OnbScaffold(OnbStep.PACE.section, onBack, bottom = { OnbPrimary("Continue", { onChange(a.copy(goalWeightKg = target, pace = pace)); onNext() }) }) {
        OnbHeader("Your pace", "Where to, and\nhow fast?")
        Entrance(1, key = "target") {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth().border(1.dp, p.hair, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TARGET WEIGHT", style = EyebrowStyle.copy(fontSize = 10.sp), color = p.muted, modifier = Modifier.weight(1f))
                StepButton("−") { val v = ((target - 0.5) * 2).roundToInt() / 2.0; if (if (gain) v > kg else v >= 30) onChange(a.copy(goalWeightKg = v, pace = pace)) }
                Text(fmtKg(target), fontFamily = Bricolage, fontSize = 26.sp, fontWeight = FontWeight(800), color = p.ink, modifier = Modifier.padding(horizontal = 10.dp))
                Text("kg", style = EyebrowStyle.copy(fontSize = 10.sp), color = p.muted)
                Spacer(Modifier.width(8.dp))
                StepButton("+") { val v = ((target + 0.5) * 2).roundToInt() / 2.0; if (if (gain) v <= 200 else v < kg) onChange(a.copy(goalWeightKg = v, pace = pace)) }
            }
        }
        Entrance(2, key = "pace") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth().background(p.card, RoundedCornerShape(24.dp)).border(1.dp, p.hair, RoundedCornerShape(24.dp)).padding(20.dp)) {
                Text("AT THIS PACE", style = EyebrowStyle.copy(fontSize = 10.sp), color = p.muted)
                Text(buildAnnotatedString {
                    append("$sign${trimKg(capped)}")
                    withStyle(SpanStyle(fontFamily = com.sohum.bandlog.ui.theme.Mono, fontSize = 11.sp, color = p.muted, fontWeight = FontWeight(500))) {
                        append(" kg / week${weeks?.let { " · ~$it weeks" } ?: ""}")
                    }
                }, fontFamily = Bricolage, fontSize = 42.sp, fontWeight = FontWeight(800), color = p.ink, modifier = Modifier.padding(top = 4.dp))
                if (capped < perWeek - 1e-9) Text("Capped at a safe ${trimKg(capped)} kg a week for your body weight.", fontSize = 12.5.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                Text("Your exact finish date shows up at the end.", fontSize = 12.5.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Entrance(3, key = "slider") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                Slider(
                    value = paces.indexOf(pace).toFloat(), onValueChange = { v -> onChange(a.copy(pace = paces[v.roundToInt().coerceIn(0, 2)], goalWeightKg = target)) },
                    valueRange = 0f..2f, steps = 1, modifier = Modifier.padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(thumbColor = p.ink, activeTrackColor = p.ink, inactiveTrackColor = p.card2, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    paces.forEach { k ->
                        val on = k == pace
                        Column(Modifier.clickable { onChange(a.copy(pace = k, goalWeightKg = target)) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(k.replaceFirstChar { it.uppercase() }, fontFamily = Bricolage, fontSize = 16.sp, fontWeight = if (on) FontWeight(800) else FontWeight(600), color = if (on) p.ink else p.muted)
                            Text("$sign${trimKg(OnboardingV2.paceKg(goalType, k))}", style = EyebrowStyle.copy(fontSize = 9.sp), color = p.muted)
                        }
                    }
                }
            }
        }
    }
}

private fun trimKg(v: Double): String = if (abs(v * 100 - (v * 100).roundToInt()) < 1e-6 && (v * 10) % 1.0 == 0.0) String.format(Locale.US, "%.1f", v) else String.format(Locale.US, "%.2f", v)

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val p = palette
    Box(Modifier.size(34.dp).background(p.card2, CircleShape).clickable(onClickLabel = if (label == "+") "Increase" else "Decrease", onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight(700), color = p.ink)
    }
}

// ---------------------------------------------------------------------------------------------
// 8 Training days + sports
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainingScreen(a: OnboardingV2.Answers, teen: Boolean, onBack: () -> Unit, onChange: (OnboardingV2.Answers) -> Unit, onNext: () -> Unit, section: Int? = OnbStep.TRAINING.section, button: String = "Continue") {
    val p = palette
    val days = a.trainingDays
    val sports = a.sports.orEmpty()
    OnbScaffold(section, onBack, bottom = { OnbPrimary(button, onNext, enabled = days != null) }) {
        OnbHeader("Your training", "How many days\ndo you train?", "Per week. Be real, we'll build from here.")
        Entrance(1, key = "days") {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                (0..7).forEach { d ->
                    val on = d == days
                    Box(
                        Modifier.width(38.dp).height(48.dp).pressable().background(if (on) p.btn else p.card, RoundedCornerShape(14.dp)).clickable { onChange(a.copy(trainingDays = d)) },
                        contentAlignment = Alignment.Center,
                    ) { Text("$d", fontFamily = Bricolage, fontSize = 18.sp, fontWeight = FontWeight(800), color = if (on) p.btnInk else p.ink) }
                }
            }
        }
        Entrance(2, key = "sports") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
                Text("WHAT DO YOU DO?", style = EyebrowStyle, color = p.muted, modifier = Modifier.padding(bottom = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnboardingV2.SPORTS.forEach { s ->
                        val on = s.key in sports
                        OnbChip(s.label, on) { onChange(a.copy(sports = if (on) sports - s.key else sports + s.key)) }
                    }
                }
            }
        }
        if (days != null) Entrance(3, key = "hint") {
            val first = sports.firstOrNull()?.let { k -> OnboardingV2.SPORTS.firstOrNull { it.key == k }?.label?.lowercase() }
            val line = when {
                teen -> "$days day${if (days == 1) "" else "s"}${first?.let { " + $it" } ?: ""} → protein set for your age, to fuel growing and training."
                days >= 3 -> "$days days${first?.let { " + $it" } ?: ""} → we'll set protein at 1.6 g/kg to keep the gains."
                else -> "$days day${if (days == 1) "" else "s"}${first?.let { " + $it" } ?: ""} → about 1 g/kg of protein, and room to add days later."
            }
            Text(line, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ember, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp).fillMaxWidth().background(p.emberBg, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 9 Eater type (diet modes)
// ---------------------------------------------------------------------------------------------

@Composable
private fun EaterScreen(a: OnboardingV2.Answers, teen: Boolean, onBack: () -> Unit, onChange: (OnboardingV2.Answers) -> Unit, onNext: () -> Unit) {
    var more by remember { mutableStateOf(a.dietMode != null && OnboardingV2.EATER_TYPES.indexOfFirst { it.key == a.dietMode } >= 5) }
    val all = OnboardingV2.EATER_TYPES.filter { !(teen && it.adultsOnly) }
    val shown = if (more) all else all.take(5)
    val sel = a.dietMode ?: "balanced"
    LaunchedEffect(Unit) { if (a.dietMode == null) onChange(a.copy(dietMode = "balanced")) }
    OnbScaffold(OnbStep.EATER.section, onBack, bottom = {
        OnbPrimary("Continue", onNext)
        if (!more) OnbFootnote(if (teen) "More: vegan" else "More: vegan, keto, low carb") { more = true }
    }) {
        OnbHeader("Your food", "What kind of\neater are you?", "Every suggestion follows this. Backed by the science.")
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            shown.forEachIndexed { i, o -> Entrance(1 + i, key = o.key) { OnbOption(o.label, o.sub, null, sel == o.key, { onChange(a.copy(dietMode = o.key)) }) } }
            if (teen) Text("Keto and low carb aren't offered under 18.", fontSize = 13.sp, color = palette.muted, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 10 Obstacles
// ---------------------------------------------------------------------------------------------

@Composable
private fun ObstaclesScreen(a: OnboardingV2.Answers, onBack: () -> Unit, onChange: (OnboardingV2.Answers) -> Unit, onNext: () -> Unit, section: Int? = OnbStep.OBSTACLES.section) {
    val sel = a.obstacles.orEmpty()
    OnbScaffold(section, onBack, bottom = {
        OnbPrimary("Continue", { if (a.obstacles == null) onChange(a.copy(obstacles = emptyList())); onNext() })
        OnbFootnote("None of these") { onChange(a.copy(obstacles = emptyList())); onNext() }
    }) {
        OnbHeader("Real talk", "What's got in\nyour way before?", "Pick any. We build around the obstacle, not the goal.")
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OnboardingV2.OBSTACLES.forEachIndexed { i, o ->
                val on = o.key in sel
                Entrance(1 + i, key = o.key) { OnbOption(o.label, o.sub, obstacleIcon(o.key), on, { onChange(a.copy(obstacles = if (on) sel - o.key else sel + o.key)) }, multi = true, compact = true) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 11 Coach style (shared with "Tune your plan")
// ---------------------------------------------------------------------------------------------

private const val CALM_SAMPLE = "You skipped the gym today, that's okay. A 20-minute walk after dinner still counts. Tomorrow's a fresh start."
private const val BALANCED_SAMPLE = "You skipped the gym today. It happens. Do a 20-minute walk after dinner and we're back on it tomorrow."
private const val NO_EXCUSES_SAMPLE = "3 skipped sessions this week. You said recomp, not rest. Gym at 6. Shoes by the door tonight. No excuses."

@Composable
fun CoachStyleScreen(current: String?, teen: Boolean, onBack: () -> Unit, section: Int?, onLock: (String) -> Unit) {
    val p = palette
    val keys = listOf("calm", "balanced", "no_excuses")
    var style by remember { mutableStateOf(OnboardingV2.effectiveCoachStyle(current, if (teen) 15 else 30)) }
    val label = OnboardingV2.styleLabel(style)
    OnbScaffold(section, onBack, bottom = { OnbPrimary("Lock in $label", { onLock(style) }) }) {
        OnbHeader("Your coach", "How should we\ntalk to you?", "Some people want a hug. Some want a push. Change it anytime.")
        Entrance(1, key = "slider") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
                Slider(
                    value = keys.indexOf(style).toFloat(),
                    onValueChange = { v -> val k = keys[v.roundToInt().coerceIn(0, 2)]; style = if (teen && k == "no_excuses") "balanced" else k },
                    valueRange = 0f..2f, steps = 1, modifier = Modifier.padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(thumbColor = p.ember, activeTrackColor = p.ember, inactiveTrackColor = p.card2, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    keys.forEach { k ->
                        val on = k == style
                        val locked = teen && k == "no_excuses"
                        Column(Modifier.alpha(if (locked) 0.4f else 1f).clickable(enabled = !locked) { style = k }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CoachMark(k, 26.dp, if (on) p.ember else p.muted)
                            Text(OnboardingV2.styleLabel(k), fontSize = 13.sp, fontWeight = if (on) FontWeight(800) else FontWeight(600), color = if (on) p.ember else p.muted)
                        }
                    }
                }
            }
        }
        val (dimKey, dimText) = if (style == "calm") "no_excuses" to NO_EXCUSES_SAMPLE else "calm" to CALM_SAMPLE
        val mainText = when (style) { "calm" -> CALM_SAMPLE; "no_excuses" -> NO_EXCUSES_SAMPLE; else -> BALANCED_SAMPLE }
        Entrance(2, key = "dim") { IrisBubble(dimText, label = OnboardingV2.styleLabel(dimKey), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp).alpha(0.45f)) }
        Entrance(3, key = "main") { IrisBubble(mainText, label = label, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) }
        Entrance(4, key = "note") {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(OnbIcons.Shield, null, tint = p.muted, modifier = Modifier.size(18.dp))
                Text("Tough love is about your habits, never your body. No comments on looks, ever. Under 18? We keep it at Balanced.", fontSize = 13.sp, lineHeight = 19.sp, color = p.muted)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 12 Buddy / squad
// ---------------------------------------------------------------------------------------------

@Composable
private fun BuddyScreen(signedIn: Boolean, onBack: () -> Unit, onSquadCode: (String) -> Unit, onNext: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var codeOpen by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val anim = rememberAnimationsEnabled()
    val bob = if (anim) rememberInfiniteTransition(label = "bob").animateFloat(-3f, 3f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "bobY").value else 0f
    fun invite() {
        if (!signedIn) { OnbStore.buddyIntent = true; onNext(); return }
        scope.launch {
            busy = true
            try {
                val c = V214Api.buddyInvite()
                shareInvite(ctx, "Be my Locked In buddy: we both log, the streak grows. ${V214Api.buddyLink(c)} (code $c)")
                onNext()
            } catch (e: NotYetAvailable) { note = "Buddy streaks are coming with the next update." }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { note = e.message ?: "Couldn't make an invite right now." }
            finally { busy = false }
        }
    }
    OnbScaffold(OnbStep.BUDDY.section, onBack, bottom = {
        note?.let { Text(it, fontSize = 13.sp, color = p.muted, textAlign = TextAlign.Center) }
        OnbPrimary("Invite a buddy", { invite() }, busy = busy)
        OnbFootnote("I'll do it later", onNext)
    }) {
        OnbHeader("Lock in together", "Bring a buddy.\n", accent = "2× more likely", accentAfter = "\nto stick.")
        Entrance(1, key = "pair") {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp).fillMaxWidth().background(p.card, RoundedCornerShape(24.dp)).padding(22.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(64.dp).background(p.btn, CircleShape), contentAlignment = Alignment.Center) { Icon(OnbIcons.User, null, tint = p.btnInk, modifier = Modifier.size(28.dp)) }
                    Text("You", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(OnbIcons.Flame, null, tint = p.ember, modifier = Modifier.size(34.dp).graphicsLayer { translationY = bob * density })
                    Text("SHARED STREAK", style = EyebrowStyle.copy(fontSize = 10.sp), color = p.muted)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(64.dp).border(2.dp, p.muted, CircleShape), contentAlignment = Alignment.Center) { Icon(OnbIcons.Plus, null, tint = p.muted, modifier = Modifier.size(24.dp)) }
                    Text("Your buddy", fontSize = 14.sp, color = p.muted)
                }
            }
        }
        Entrance(2, key = "rules") {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth().background(p.emberBg, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(OnbIcons.Bell, null, tint = p.ember, modifier = Modifier.size(18.dp))
                Text("Both log = streak grows. One skips = the other gets to nudge. Break it and you both start over.", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(600), color = p.ember)
            }
        }
        Entrance(3, key = "squad") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth().background(p.card, RoundedCornerShape(20.dp)).clickable { codeOpen = !codeOpen }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) { Icon(OnbIcons.Users, null, tint = p.ink, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Or join a squad", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text("Got a 6-letter code? Your crew is waiting.", fontSize = 13.sp, color = p.muted)
                    }
                    Icon(OnbIcons.Chevron, null, tint = p.muted, modifier = Modifier.size(20.dp))
                }
                if (codeOpen) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).height(48.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                        if (code.isEmpty()) Text("ABC123", fontSize = 16.sp, color = p.muted, letterSpacing = 3.sp)
                        BasicTextField(
                            code, { v -> code = v.filter { it.isLetterOrDigit() }.uppercase().take(12) }, singleLine = true,
                            textStyle = TextStyle(fontSize = 16.sp, color = p.ink, letterSpacing = 3.sp, fontWeight = FontWeight(700)), cursorBrush = SolidColor(p.ember),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        )
                    }
                    Box(
                        Modifier.height(48.dp).background(p.btn, CircleShape).clickable(enabled = code.length in 4..12) { onSquadCode(code); onNext() }.padding(horizontal = 18.dp).alpha(if (code.length in 4..12) 1f else 0.4f),
                        contentAlignment = Alignment.Center,
                    ) { Text("Join", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.btnInk) }
                }
            }
        }
        if (!signedIn) Text(
            "We'll share your invite link as soon as your plan is saved.", fontSize = 12.5.sp, color = p.muted,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 13 First challenge
// ---------------------------------------------------------------------------------------------

@Composable
private fun ChallengeScreen(current: String?, onBack: () -> Unit, onPick: (String) -> Unit) {
    val p = palette
    var sel by remember { mutableStateOf(current ?: "protein_7") }
    OnbScaffold(OnbStep.CHALLENGE.section, onBack, bottom = { OnbPrimary("I'm in", { onPick(sel) }) }) {
        OnbHeader("First challenge", "Pick your first\nW.", "Small, winnable, today. Your squad can join.")
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OnboardingV2.CHALLENGES.forEachIndexed { i, c ->
                val on = c.key == sel
                val fg = if (on) p.btnInk else p.ink
                Entrance(1 + i, key = c.key) {
                    Column(
                        Modifier.fillMaxWidth().pressable().background(if (on) p.btn else p.card, RoundedCornerShape(22.dp)).clickable { sel = c.key }.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (i == 0) Box(Modifier.background(p.ember, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("RECOMMENDED", fontSize = 11.sp, fontWeight = FontWeight(800), letterSpacing = 1.sp, color = p.onEmber)
                        }
                        Text(if (c.key == "no_maggi_30" && LocalDate.now(Dates.ZONE).monthValue == 11) "No-Maggi November" else c.title, fontFamily = Bricolage, fontSize = 19.sp, fontWeight = FontWeight(800), color = fg)
                        Text(c.sub, fontSize = 14.sp, color = fg.copy(alpha = 0.7f))
                        Box(Modifier.border(1.dp, fg.copy(alpha = 0.8f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(c.level, fontSize = 11.sp, fontWeight = FontWeight(700), letterSpacing = 1.sp, color = fg.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 14 Building
// ---------------------------------------------------------------------------------------------

@Composable
private fun BuildingScreen(a: OnboardingV2.Answers, hasLog: Boolean, onPlan: (OnboardingV2.Plan?) -> Unit, onDone: () -> Unit) {
    val p = palette
    val anim = rememberAnimationsEnabled()
    val lines = remember(a, hasLog) { OnboardingV2.buildingLines(a, hasLog) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val fetch = async {
            runCatching { V214Api.plan(a) }.getOrNull() ?: OnboardingV2.plan(a)
        }
        progress.animateTo(1f, tween(if (anim) 3600 else 400, easing = LinearEasing))
        onPlan(fetch.await())
        delay(if (anim) 350 else 0)
        onDone()
    }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Building your plan", fontFamily = Bricolage, fontSize = 32.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink)
            Text("just for you…", style = AccentStyle, fontSize = 34.sp, color = p.ember)
        }
        Box(Modifier.fillMaxWidth().padding(top = 22.dp), contentAlignment = Alignment.Center) {
            ThinRing(progress.value, 170.dp, 8.dp, p.ink, p.card2)
            Text(buildAnnotatedString {
                append("${(progress.value * 100).roundToInt()}")
                withStyle(SpanStyle(fontSize = 18.sp, color = p.muted)) { append("%") }
            }, fontFamily = Bricolage, fontSize = 44.sp, fontWeight = FontWeight(800), letterSpacing = (-1.5).sp, color = p.ink)
        }
        Column(Modifier.padding(start = 34.dp, end = 34.dp, top = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            lines.forEachIndexed { i, l ->
                val done = progress.value >= (i + 1f) / (lines.size + 0.5f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmberTick(done)
                    Text(l, fontSize = 15.5.sp, color = if (done) p.ink else p.muted)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 15 Reveal
// ---------------------------------------------------------------------------------------------

@Composable
private fun RevealScreen(a: OnboardingV2.Answers, plan: OnboardingV2.Plan?, onBack: () -> Unit, onPlan: (OnboardingV2.Plan?) -> Unit, onNext: () -> Unit) {
    val p = palette
    val pl = plan ?: remember(a) { OnboardingV2.plan(a) }
    LaunchedEffect(pl) { if (plan == null && pl != null) onPlan(pl) }
    if (pl == null) {
        OnbScaffold(null, onBack, bottom = { OnbPrimary("Go back", onBack) }) {
            OnbHeader("Almost", "We need your\nheight, weight\nand birthday.", "Go back to About you and fill them in, then we'll build the plan.")
        }
        return
    }
    val target = a.goalWeightKg
    OnbScaffold(null, null, bottom = {
        OnbPrimary("Make this plan mine", onNext)
        OnbFootnote(pl.honest)
    }) {
        Entrance(0, key = "head") {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 30.dp)) {
                Text("YOUR PLAN IS READY", style = EyebrowStyle, color = p.ember)
                if (pl.goalDate != null && target != null) {
                    Text("${trimTarget(target)} kg by", fontFamily = Bricolage, fontSize = 36.sp, fontWeight = FontWeight(800), letterSpacing = (-1.2).sp, color = p.ink, modifier = Modifier.padding(top = 8.dp))
                    Text(OnboardingV2.prettyDate(pl.goalDate), style = AccentStyle, fontSize = 52.sp, lineHeight = 54.sp, color = p.ember)
                } else {
                    Text("Your daily plan,", fontFamily = Bricolage, fontSize = 36.sp, fontWeight = FontWeight(800), letterSpacing = (-1.2).sp, color = p.ink, modifier = Modifier.padding(top = 8.dp))
                    Text("from today", style = AccentStyle, fontSize = 52.sp, lineHeight = 54.sp, color = p.ember)
                }
            }
        }
        if (pl.goalDate != null && target != null && a.weightKg != null) Entrance(1, key = "chart") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth().background(p.card, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                PlanLine(a.weightKg, target)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("${fmtKg(a.weightKg)} kg · today", style = EyebrowStyle.copy(fontSize = 10.sp), color = p.muted, modifier = Modifier.weight(1f))
                    Text("${trimTarget(target)} kg · ${pl.weeks ?: "?"} wk", style = EyebrowStyle.copy(fontSize = 10.sp), color = p.muted)
                }
            }
        }
        Entrance(2, key = "targets") {
            // Always an ink card, like the design (bone text in either theme).
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth().background(com.sohum.bandlog.ui.theme.Brand.Ink, RoundedCornerShape(22.dp)).padding(16.dp)) {
                Text("DAILY TARGET", style = EyebrowStyle.copy(fontSize = 11.sp), color = com.sohum.bandlog.ui.theme.Brand.Mute, modifier = Modifier.padding(bottom = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(
                        String.format(Locale.US, "%,d", pl.targets.calories) to "KCAL", "${pl.targets.protein} g" to "PROTEIN", "${pl.targets.carbs} g" to "CARBS",
                        "${pl.targets.fat} g" to "FAT", "${pl.targets.fiber} g" to "FIBRE",
                    ).forEach { (v, l) ->
                        Column {
                            Text(v, fontFamily = Bricolage, fontSize = 20.sp, fontWeight = FontWeight(800), color = com.sohum.bandlog.ui.theme.Brand.Bone)
                            Text(l, style = EyebrowStyle.copy(fontSize = 10.sp), color = com.sohum.bandlog.ui.theme.Brand.Mute)
                        }
                    }
                }
            }
        }
        Entrance(3, key = "why") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pl.reasons.forEach { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("✓", color = p.ember, fontSize = 14.5.sp, fontWeight = FontWeight(700))
                        Text(boldMarked(r), fontSize = 14.5.sp, lineHeight = 20.sp, color = p.ink)
                    }
                }
            }
        }
    }
}

private fun trimTarget(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else fmtKg(v)

/** The reveal's little curve from today's weight to the target, eased like real progress. */
@Composable
private fun PlanLine(from: Double, to: Double) {
    val p = palette
    val t = com.sohum.bandlog.ui.motion.rememberMotion("planline", 300, com.sohum.bandlog.ui.motion.PremiumMotion.DRAW_MS)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        val w = size.width
        val h = size.height
        val down = to < from
        val y0 = if (down) h * 0.15f else h * 0.85f
        val y1 = if (down) h * 0.85f else h * 0.15f
        val path = Path().apply {
            moveTo(0f, y0)
            cubicTo(w * 0.35f, y0 + (y1 - y0) * 0.55f, w * 0.65f, y1 - (y1 - y0) * 0.1f, w, y1)
        }
        drawPathTrimmed(path, com.sohum.bandlog.ui.motion.PremiumMotion.eased(t.value, com.sohum.bandlog.ui.motion.PremiumMotion.EasePen), p.ink, Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(p.ember, 5.dp.toPx(), Offset(w - 1f, y1))
    }
}

// ---------------------------------------------------------------------------------------------
// 16 Hold to lock in (always dark)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PledgeScreen(a: OnboardingV2.Answers, plan: OnboardingV2.Plan?, busy: Boolean, error: String?, onBack: () -> Unit, onLocked: () -> Unit) {
    val p = palette
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val hold = remember { Animatable(0f) }
    var locked by remember { mutableStateOf(false) }
    val anim = rememberAnimationsEnabled()
    val bob = if (anim) rememberInfiniteTransition(label = "bob").animateFloat(-3f, 3f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "bobY").value else 0f
    val target = a.goalWeightKg
    val third = if (plan?.goalDate != null && target != null) "${trimTarget(target)} kg by ${OnboardingV2.prettyDate(plan.goalDate, short = true)}. " to "Locked in."
    else "Every day counts. " to "Locked in."
    val lines = listOf("Log every day. " to "Even the bad ones.", "Show up when week 3 " to "slows down.", third)
    fun done() { if (!locked) { locked = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLocked() } }
    LaunchedEffect(error) { if (error != null) { locked = false; hold.snapTo(0f) } }
    Box(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth()) {
            OnbTopBar(null, onBack.takeIf { !busy })
            Text("THE PLEDGE", style = EyebrowStyle, color = p.muted, modifier = Modifier.padding(start = 26.dp, top = 24.dp))
            Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                lines.forEachIndexed { i, (lead, accent) ->
                    Entrance(1 + i, key = "l$i") {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("0${i + 1}", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted, modifier = Modifier.padding(top = 6.dp))
                            Text(buildAnnotatedString {
                                append(lead)
                                withStyle(SpanStyle(fontFamily = AccentStyle.fontFamily, fontStyle = AccentStyle.fontStyle, fontWeight = FontWeight(300), color = p.ember, fontSize = 25.sp)) { append(accent) }
                            }, fontFamily = Bricolage, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            error?.let { Text(it, fontSize = 13.sp, color = p.red, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 30.dp)) }
            Box(
                Modifier.size(132.dp)
                    .semantics { contentDescription = "Hold to lock in"; onClick(label = "Lock in") { done(); true } }
                    .pointerInput(busy) {
                        if (busy) return@pointerInput
                        detectTapGestures(onPress = {
                            if (locked) return@detectTapGestures
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val job = scope.launch {
                                hold.animateTo(1f, tween(((1f - hold.value) * 1500).toInt().coerceAtLeast(1), easing = LinearEasing))
                                done()
                            }
                            val released = tryAwaitRelease()
                            if (!locked) { job.cancel(); scope.launch { hold.animateTo(0f, tween(250)) } }
                            if (!released) job.cancel()
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                ThinRing(hold.value, 132.dp, 4.dp, p.ember, Color.White.copy(alpha = 0.12f))
                Box(Modifier.size(104.dp).background(p.ember, CircleShape), contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = p.onEmber)
                    else Icon(OnbIcons.Flame, null, tint = p.onEmber, modifier = Modifier.size(40.dp).graphicsLayer { translationY = bob * density })
                }
            }
            Text(if (busy) "Saving your plan…" else "Hold to lock in", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            Text("Only when you mean it.", fontSize = 13.sp, color = p.muted)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 17 Save with email (the account is created here)
// ---------------------------------------------------------------------------------------------

@Composable
private fun SaveScreen(a: OnboardingV2.Answers, plan: OnboardingV2.Plan?, onName: (String) -> Unit, onAccountReady: () -> Unit, onSignIn: () -> Unit, onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var form by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(a.name.orEmpty()) }
    var email by remember { mutableStateOf(OnbStore.pendingEmail.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(OnbStore.pendingEmail != null) }
    val target = a.goalWeightKg
    val planLine = if (plan?.goalDate != null && target != null) "${trimTarget(target)} kg by ${OnboardingV2.prettyDate(plan.goalDate, short = true)}" else plan?.let { "${String.format(Locale.US, "%,d", it.targets.calories)} kcal · ${it.targets.protein} g protein" } ?: "Ready"
    val emailOk = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    fun signUp() {
        if (busy || !emailOk || password.length < 6) return
        focus.clearFocus()
        scope.launch {
            busy = true; error = null
            try {
                if (name.isNotBlank()) onName(name.trim())
                OnbStore.pending = true
                val active = SupabaseAuth.signUp(email.trim(), password, name.trim())
                if (active && Session.signedIn) { OnbStore.pendingEmail = null; onAccountReady() }
                else { OnbStore.pendingEmail = email.trim(); confirm = true }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Couldn't create your account. Try again." }
            finally { busy = false }
        }
    }

    OnbScaffold(null, onBack.takeIf { !busy && !confirm }, bottom = {
        error?.let { Text(it, fontSize = 13.sp, color = p.red, textAlign = TextAlign.Center) }
        when {
            confirm -> {
                OnbPrimary("I've confirmed, sign in", onSignIn)
                OnbFootnote("Wrong email? Change it") { confirm = false; OnbStore.pendingEmail = null; form = true }
            }
            form -> {
                OnbPrimary("Create account", { signUp() }, enabled = emailOk && password.length >= 6, busy = busy)
                OnbFootnote("I already have an account", onSignIn)
            }
            else -> {
                OnbPrimary("Save with email", { form = true })
                OnbFootnote("Takes 20 seconds. No spam, ever.")
            }
        }
    }) {
        Entrance(0, key = "mark") {
            Box(Modifier.fillMaxWidth().padding(top = if (form) 20.dp else 70.dp), contentAlignment = Alignment.Center) { LockedInMark(if (form) 64.dp else 96.dp) }
        }
        Entrance(1, key = "title") {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (confirm) "Check your inbox." else "You're locked in.", fontFamily = Bricolage, fontSize = 40.sp, fontWeight = FontWeight(800), letterSpacing = (-1.5).sp, color = p.ink, textAlign = TextAlign.Center)
                Text(
                    if (confirm) "We sent a link to ${OnbStore.pendingEmail ?: email}. Confirm it, then sign in. Your plan and first meal wait on this phone and save the moment you're in."
                    else "Save your plan to keep your streak, your coach and your squad.",
                    fontSize = 16.sp, lineHeight = 24.sp, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        if (form && !confirm) Entrance(2, key = "form") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SaveField(name, { name = it.take(40) }, "Your name (optional)", KeyboardType.Text, ImeAction.Next)
                SaveField(email, { email = it.trim().take(120) }, "Email", KeyboardType.Email, ImeAction.Next)
                SaveField(password, { password = it.take(72) }, "Password (6+ characters)", KeyboardType.Password, ImeAction.Done, secret = true) { signUp() }
            }
        } else if (!confirm) Entrance(2, key = "summary") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 30.dp).fillMaxWidth().background(p.card, RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryRow("Plan", planLine, p.ink)
                SummaryRow("Streak", "Day 1", p.ember)
                SummaryRow("Coach", OnboardingV2.styleLabel(a.coachStyle), p.ink)
                SummaryRow("Pro", "Free for beta testers", p.ember)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color) {
    val p = palette
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 15.sp, color = p.ink, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight(700), color = color)
    }
}

@Composable
private fun SaveField(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType, ime: ImeAction, secret: Boolean = false, onDone: () -> Unit = {}) {
    val p = palette
    val focus = LocalFocusManager.current
    Box(Modifier.fillMaxWidth().height(56.dp).background(p.card, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(hint, fontSize = 16.sp, color = p.muted)
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = p.ink), cursorBrush = SolidColor(p.ember),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ime, capitalization = if (type == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }, onDone = { onDone() }),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// "Tune your plan" (existing users): coach style + obstacles + training days only
// ---------------------------------------------------------------------------------------------

/**
 * The three new questions for people who signed up before v2.14 (Home's "Tune your plan" card).
 * Saves with /api/onboarding/finish `mode: "tune"`, or straight to the v37 columns.
 */
@Composable
fun TunePlanFlow(profile: com.sohum.bandlog.data.Profile, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val age = profile.age
    val teen = Goals.isTeen(age)
    var a by remember { mutableStateOf(OnboardingV2.Answers(coachStyle = profile.coachStyle, obstacles = profile.obstacles, trainingDays = profile.trainingDays ?: profile.weeklyWorkoutTarget, sports = profile.sports)) }
    var page by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler { if (page > 0) page-- else onClose() }
    fun save() {
        scope.launch {
            busy = true; error = null
            try { OnbSync.tune(a, age); onSaved() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Couldn't save that. Try again." }
            finally { busy = false }
        }
    }
    MotionScreen {
        when (page) {
            0 -> CoachStyleScreen(a.coachStyle, teen, onClose, null) { a = a.copy(coachStyle = it); page = 1 }
            1 -> ObstaclesScreen(a, { page = 0 }, { a = it }, { page = 2 }, section = null)
            else -> Box {
                TrainingScreen(a, teen, { page = 1 }, { a = it }, { save() }, section = null, button = if (busy) "Saving…" else "Save")
                error?.let { Text(it, color = palette.red, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) }
            }
        }
    }
}
