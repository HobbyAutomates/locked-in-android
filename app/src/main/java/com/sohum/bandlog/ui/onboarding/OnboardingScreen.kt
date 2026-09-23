package com.sohum.bandlog.ui.onboarding

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Profile
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.GoalSpeedPicker
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.components.PencilIcon
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Ring
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Goals
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Everything the flow collects. Only the first seven fields ever reach the database. */
private data class Answers(
    val gender: String? = null,
    /** Sessions a week, stored as the profile's weekly_workout_target. */
    val workouts: Int? = null,
    val goalType: String? = null,
    val heightCm: String = "170",
    val weightKg: String = "60",
    val dob: String? = null,
    val goalWeightKg: Double? = null,
    val speed: Float = 0.5f,
    val obstacles: Set<String> = emptySet(),
) {
    val weight: Double? get() = weightKg.toDoubleOrNull()?.takeIf { it in 20.0..300.0 }
    val height: Double? get() = heightCm.toDoubleOrNull()?.takeIf { it in 80.0..250.0 }
    val maintaining: Boolean get() = goalType == "maintain"
}

private enum class Step { GENDER, WORKOUTS, GOAL, BODY, DOB, DESIRED, SPEED, OBSTACLES, BUILDING, READY }

private val OBSTACLES = listOf(
    "Lack of consistency" to "We'll keep the streak front and centre",
    "Unhealthy eating habits" to "We'll flag the sugar and the salt for you",
    "Lack of support" to "We'll celebrate every single session",
    "Busy schedule" to "We'll keep meals quick",
    "Lack of meal inspiration" to "We'll lean on the meals you already save",
)

private val PLAN_CHECKS = listOf("Calories", "Carbs", "Protein", "Fats", "Health score")

/**
 * The Cal AI-shaped first run: gender → workouts → goal → body → birthday → target weight →
 * pace → obstacles → a plan being built → the plan itself. Nothing is written until the last
 * screen, where the whole profile (details plus the Auto Generate targets) is saved in one go.
 */
@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit, onSkip: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var a by remember { mutableStateOf(Answers()) }
    var index by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    // Maintaining needs no target weight and no pace, so those two drop out of the flow.
    val steps = remember(a.maintaining, a.goalType) {
        Step.entries.filter { !(a.maintaining && (it == Step.DESIRED || it == Step.SPEED)) }
    }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val back = { if (index > 0) index-- }
    val next = { if (index < steps.lastIndex) index++ }

    BackHandler(enabled = index > 0) { back() }

    // The profile as it would be saved right now — used for the generated targets on the last screen.
    val draft = remember(a, vm.profile) {
        vm.profile.copy(
            gender = a.gender ?: "other",
            weeklyWorkoutTarget = a.workouts ?: 3,
            goalType = a.goalType ?: "maintain",
            heightCm = a.height,
            weightKg = a.weight,
            dob = a.dob,
            goalWeightKg = if (a.maintaining) a.weight else a.goalWeightKg,
            // Kept even when maintaining, so switching to lose/gain later starts from a sane pace.
            goalSpeedKgWk = (a.speed * 10).roundToInt() / 10.0,
        )
    }
    val targets = remember(draft) { Goals.generate(draft) }

    AnimatedContent(
        targetState = step,
        label = "onboarding",
        transitionSpec = {
            val forward = targetState.ordinal >= initialState.ordinal
            val dir = if (forward) 1 else -1
            (slideInHorizontally(Motion.spatial()) { dir * it / 5 } + fadeIn(Motion.effects()))
                .togetherWith(slideOutHorizontally(Motion.spatialFast()) { -dir * it / 5 } + fadeOut(Motion.effectsFast()))
        },
    ) { page ->
        val progress = (steps.indexOf(page) + 1f) / steps.size
        when (page) {
            Step.GENDER -> Chassis(
                progress, back, "Choose your gender",
                ctaEnabled = a.gender != null, onCta = next, onSkip = onSkip,
            ) {
                listOf("male" to "Male", "female" to "Female", "other" to "Other").forEach { (key, label) ->
                    OptionCard(label, selected = a.gender == key) { a = a.copy(gender = key) }
                }
            }

            Step.WORKOUTS -> Chassis(
                progress, back, "How many workouts do you do per week?",
                ctaEnabled = a.workouts != null, onCta = next,
            ) {
                listOf(
                    Triple("0 – 2", "Workouts now and then", 2),
                    Triple("3 – 5", "A few workouts per week", 4),
                    Triple("6+", "Dedicated athlete", 6),
                ).forEach { (label, sub, value) ->
                    OptionCard(label, sub, selected = a.workouts == value) { a = a.copy(workouts = value) }
                }
            }

            Step.GOAL -> Chassis(
                progress, back, "What is your goal?",
                ctaEnabled = a.goalType != null, onCta = next,
            ) {
                listOf("lose" to "Lose weight", "maintain" to "Maintain", "gain" to "Gain weight").forEach { (key, label) ->
                    OptionCard(label, selected = a.goalType == key) {
                        a = a.copy(goalType = key, goalWeightKg = if (key == "maintain") a.weight else a.goalWeightKg)
                    }
                }
            }

            Step.BODY -> Chassis(
                progress, back, "Height & weight",
                ctaEnabled = a.height != null && a.weight != null, onCta = next,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigNumberField("Height", a.heightCm, "cm", Modifier.weight(1f)) { a = a.copy(heightCm = it) }
                    BigNumberField("Weight", a.weightKg, "kg", Modifier.weight(1f)) { a = a.copy(weightKg = it) }
                }
                Spacer(Modifier.height(10.dp))
                Text("Metric only for now — centimetres and kilograms.", fontSize = 12.sp, color = p.muted)
            }

            Step.DOB -> Chassis(
                progress, back, "When were you born?",
                ctaEnabled = a.dob != null, onCta = next,
            ) {
                BirthdayPicker(a.dob) { a = a.copy(dob = it) }
            }

            Step.DESIRED -> {
                val now = a.weight ?: 60.0
                val goal = a.goalWeightKg ?: now
                Chassis(progress, back, "What is your desired weight?", ctaEnabled = true, onCta = next) {
                    Text(
                        "${fmt(goal)} kg",
                        fontSize = 52.sp, fontWeight = FontWeight(800), letterSpacing = (-2).sp, color = p.ink,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    )
                    Text(
                        when {
                            abs(goal - now) < 0.25 -> "Same as today"
                            goal > now -> "${fmt(goal - now)} kg above where you are now"
                            else -> "${fmt(now - goal)} kg below where you are now"
                        },
                        fontSize = 13.sp, color = p.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    TickRuler(goal, (now - 30).coerceAtLeast(25.0), now + 30, 0.5) { a = a.copy(goalWeightKg = it) }
                    Spacer(Modifier.height(8.dp))
                    Text("Drag the ruler", fontSize = 12.sp, color = p.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }

            Step.SPEED -> Chassis(
                progress, back, "How fast do you want to reach your goal?",
                sub = "This changes how many calories we add or subtract each day.",
                ctaEnabled = true, onCta = next,
            ) {
                GoalSpeedPicker(a.speed) { a = a.copy(speed = it) }
            }

            Step.OBSTACLES -> Chassis(
                progress, back, "What's stopping you from reaching your goals?",
                sub = "Pick as many as you like.",
                ctaEnabled = true, onCta = next,
            ) {
                OBSTACLES.forEach { (label, _) ->
                    OptionCard(label, selected = label in a.obstacles) {
                        a = a.copy(obstacles = if (label in a.obstacles) a.obstacles - label else a.obstacles + label)
                    }
                }
            }

            Step.BUILDING -> BuildingScreen(progress) { next() }

            Step.READY -> PlanScreen(
                a = a,
                targets = targets,
                error = vm.error,
                busy = busy,
                onBack = back,
                onStart = {
                    scope.launch {
                        busy = true
                        val saved = draft.let { d -> targets?.let { t -> d.copy(calorieTarget = t.calories, proteinTargetG = t.protein, carbTargetGSet = t.carbs, fatTargetGSet = t.fat) } ?: d }
                        val ok = vm.saveProfile(saved)
                        busy = false
                        if (ok) onDone()
                    }
                },
            )
        }
    }
}

// ---- chassis ----

/**
 * One screen of the flow: back arrow, a thin animated progress bar, a big left-aligned headline,
 * the grey calibration subhead, the body, and a fixed Continue pill that stays disabled until the
 * screen has an answer.
 */
@Composable
private fun Chassis(
    progress: Float,
    onBack: () -> Unit,
    title: String,
    sub: String? = "This will be used to calibrate your custom plan.",
    cta: String = "Continue",
    ctaEnabled: Boolean,
    onCta: () -> Unit,
    onSkip: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).background(p.card2, CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = p.ink, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.width(12.dp))
            ProgressBar(progress, Modifier.weight(1f))
            if (onSkip != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    "Skip for now", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted,
                    modifier = Modifier.clickable(onClick = onSkip),
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp, 28.dp, 24.dp, 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(title, fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-1.2).sp, color = p.ink, lineHeight = 34.sp)
                if (sub != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(sub, fontSize = 14.sp, color = p.muted, lineHeight = 19.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            body()
        }
        Column(Modifier.padding(24.dp, 0.dp, 24.dp, 12.dp).navigationBarsPadding()) {
            PillButton(cta, onCta, enabled = ctaEnabled, height = 54.dp)
        }
    }
}

/** The thin black bar across the top; animates as the flow advances. */
@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val p = palette
    val a by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.spatialSlow(), label = "progress")
    Box(modifier.height(5.dp).background(p.track, CircleShape)) {
        Box(Modifier.fillMaxWidth(a).height(5.dp).background(p.ink, CircleShape))
    }
}

/** An answer tile: solid black with white text when chosen, soft grey when not. */
@Composable
private fun OptionCard(title: String, sub: String? = null, selected: Boolean, onClick: () -> Unit) {
    val p = palette
    val bg = if (selected) p.btn else p.card2
    val fg = if (selected) p.btnInk else p.ink
    Column(
        Modifier.fillMaxWidth().pressable().background(bg, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(18.dp, 16.dp),
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight(700), color = fg)
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 13.sp, color = if (selected) fg.copy(alpha = 0.72f) else p.muted)
        }
    }
}

/** A big tappable number with its unit, used for height and weight side by side. */
@Composable
private fun BigNumberField(label: String, value: String, unit: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val p = palette
    Column(modifier.background(p.card2, RoundedCornerShape(18.dp)).padding(16.dp, 14.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value,
                { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(5)) },
                Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
                textStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight(800), letterSpacing = (-1.4).sp, color = p.ink),
                cursorBrush = SolidColor(p.ink),
            )
            Text(unit, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

/** Birthday via the platform date picker, defaulting to 1 January 2009. */
@Composable
private fun BirthdayPicker(dob: String?, onPick: (String) -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val shown = dob?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)) }.getOrNull() }
    Column(
        Modifier.fillMaxWidth().pressable().background(p.card2, RoundedCornerShape(18.dp)).clickable {
            val start = runCatching { LocalDate.parse(dob) }.getOrDefault(LocalDate.of(2009, 1, 1))
            runCatching {
                DatePickerDialog(
                    ctx,
                    { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d).toString()) },
                    start.year, start.monthValue - 1, start.dayOfMonth,
                ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
            }
        }.padding(18.dp, 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(shown ?: "1 January 2009", fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = if (shown == null) p.muted else p.ink)
        Spacer(Modifier.height(4.dp))
        Text(if (shown == null) "Tap to pick your birthday" else "Tap to change", fontSize = 12.sp, color = p.muted)
    }
}

/**
 * The draggable target-weight ruler: ticks every 0.5 kg, a taller tick every kilo, a label every
 * five, and a black needle down the middle that the number above is pinned to.
 */
@Composable
private fun TickRuler(value: Double, min: Double, max: Double, step: Double, onChange: (Double) -> Unit) {
    val p = palette
    val measurer = rememberTextMeasurer()
    val pxPerStep = with(LocalDensity.current) { 13.dp.toPx() }
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    var carry by remember { mutableStateOf(0f) }

    Box(
        Modifier.fillMaxWidth().height(104.dp).pointerInput(min, max, step, pxPerStep) {
            detectHorizontalDragGestures(
                onDragStart = { carry = 0f },
                onDragEnd = { carry = 0f },
            ) { drag, dx ->
                drag.consume()
                carry -= dx // dragging left walks the ruler up
                val notches = (carry / pxPerStep).toInt()
                if (notches != 0) {
                    carry -= notches * pxPerStep
                    val next = ((current + notches * step) / step).roundToInt() * step
                    val clamped = next.coerceIn(min, max)
                    if (abs(clamped - current) > 1e-6) change(clamped)
                }
            }
        },
    ) {
        val ink = p.ink
        val muted = p.muted
        val hair = p.hair
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val top = size.height * 0.30f
            val span = (cx / pxPerStep).toInt() + 3
            val centreIdx = (current / step).roundToInt()
            for (i in -span..span) {
                val idx = centreIdx + i
                val v = idx * step
                if (v < min - step || v > max + step) continue
                val x = cx + ((v - current) / step).toFloat() * pxPerStep
                if (x < -20f || x > size.width + 20f) continue
                val major = idx % 2 == 0          // every whole kilo
                val labelled = idx % 10 == 0      // every five kilos
                val h = if (labelled) size.height * 0.34f else if (major) size.height * 0.24f else size.height * 0.14f
                drawLine(
                    if (labelled) muted else hair,
                    Offset(x, top), Offset(x, top + h),
                    strokeWidth = if (labelled) 3f else 2f, cap = StrokeCap.Round,
                )
                if (labelled) {
                    val label = fmt(v)
                    val laid = measurer.measure(label, TextStyle(fontSize = 11.sp, color = muted, fontWeight = FontWeight(600)))
                    drawText(laid, topLeft = Offset(x - laid.size.width / 2f, top + h + 6f))
                }
            }
            // The needle the number above is read against.
            drawLine(ink, Offset(cx, top - size.height * 0.16f), Offset(cx, top + size.height * 0.42f), strokeWidth = 6f, cap = StrokeCap.Round)
        }
    }
}

// ---- the last two screens ----

/** "Building your plan": a ring counting to 100% while the five lines tick off underneath. */
@Composable
private fun BuildingScreen(progress: Float, onDone: () -> Unit) {
    val p = palette
    var pct by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (pct < 1f) {
            withFrameMillis { now ->
                pct = ((now - start) / 2500f).coerceIn(0f, 1f)
            }
        }
        onDone()
    }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 16.dp, 0.dp)) { ProgressBar(progress, Modifier.weight(1f)) }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Ring(pct, p.ink, 148.dp, 12.dp) {
                Text("${(pct * 100).roundToInt()}%", fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink)
            }
            Spacer(Modifier.height(24.dp))
            Text("Building your plan", fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink)
            Spacer(Modifier.height(4.dp))
            Text("Crunching your numbers — a few seconds.", fontSize = 13.sp, color = p.muted)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PLAN_CHECKS.forEachIndexed { i, label ->
                    val done = pct >= (i + 1) / PLAN_CHECKS.size.toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(24.dp).background(if (done) p.green else p.card2, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (done) Icon(CheckIcon, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Text(label, fontSize = 15.sp, fontWeight = FontWeight(600), color = if (done) p.ink else p.muted)
                    }
                }
            }
        }
    }
}

/** "Your custom plan is ready": the goal chip, the four daily targets, then into the app. */
@Composable
private fun PlanScreen(
    a: Answers,
    targets: Goals.Targets?,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val p = palette
    val current = a.weight ?: 60.0
    val goal = if (a.maintaining) current else (a.goalWeightKg ?: current)
    val chip = remember(a) {
        val delta = abs(goal - current)
        val pace = ((a.speed * 10).roundToInt() / 10.0).coerceAtLeast(0.1)
        when {
            a.maintaining || delta < 0.25 -> "Maintain ${fmt(current)} kg"
            else -> {
                val days = ceil(delta / pace * 7.0).toLong().coerceIn(7, 3650)
                val by = LocalDate.now().plusDays(days).format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))
                "${if (goal > current) "Gain" else "Lose"} ${fmt(delta)} kg by $by"
            }
        }
    }
    val obstacleLine = OBSTACLES.firstOrNull { it.first in a.obstacles }?.second

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).background(p.card2, CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = p.ink, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.width(12.dp))
            ProgressBar(1f, Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp, 24.dp, 24.dp, 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Rise(0) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).background(p.btn, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(CheckIcon, null, tint = p.btnInk, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Your custom plan is ready",
                        fontSize = 28.sp, fontWeight = FontWeight(800), letterSpacing = (-1.2).sp, color = p.ink,
                        textAlign = TextAlign.Center, lineHeight = 32.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.background(p.card2, CircleShape).padding(14.dp, 8.dp)) {
                        Text(chip, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                    }
                }
            }
            Rise(1) {
                Column {
                    Text("Daily recommendation", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text("You can edit this anytime.", fontSize = 12.sp, color = p.muted)
                }
            }
            if (targets == null) {
                Rise(2) {
                    Card { Text("We'll set your targets once your details are in — open Profile → Edit Nutrition Goals.", fontSize = 13.sp, color = p.muted) }
                }
            } else {
                Rise(2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TargetCard("Calories", targets.calories, "", p.ink, Modifier.weight(1f))
                        TargetCard("Carbs", targets.carbs, "g", p.orange, Modifier.weight(1f))
                    }
                }
                Rise(3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TargetCard("Protein", targets.protein, "g", p.red, Modifier.weight(1f))
                        TargetCard("Fats", targets.fat, "g", p.blue, Modifier.weight(1f))
                    }
                }
            }
            if (obstacleLine != null) {
                Rise(4) {
                    Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(16.dp)).padding(16.dp, 14.dp)) {
                        Text(obstacleLine, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink)
                    }
                }
            }
            Rise(5) { ErrorNote(error) }
        }
        Column(Modifier.padding(24.dp, 0.dp, 24.dp, 12.dp).navigationBarsPadding()) {
            PillButton(if (busy) "Saving…" else "Let's get started!", onStart, enabled = !busy, height = 54.dp)
        }
    }
}

/** One of the four daily targets: a coloured ring, the number, and the pencil that hints at editing. */
@Composable
private fun TargetCard(label: String, value: Int, unit: String, color: Color, modifier: Modifier = Modifier) {
    val p = palette
    Card(modifier, padding = 14.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Ring(1f, color, 34.dp, 5.dp) { Box(Modifier.size(8.dp).background(color, CircleShape)) }
            Icon(PencilIcon, null, tint = p.muted, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("$value$unit", fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
    }
}
