package com.sohum.bandlog.ui.platform

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.alarm.RestTimerAlarm
import com.sohum.bandlog.data.Lift
import com.sohum.bandlog.data.LiftSet
import com.sohum.bandlog.notify.PlatformNotifications
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.theme.MonoStyle
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Lifts
import com.sohum.bandlog.util.Routines
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Starts a live workout pre-filled from a routine day: last time's top kg per lift, the planned reps. */
fun startLive(vm: AppViewModel, pvm: PlatformViewModel, day: Routines.Day, title: String) {
    pvm.live = LiveWorkout(
        title = title,
        startedAt = System.currentTimeMillis(),
        exercises = day.exercises.map { e ->
            val last = vm.lastLift(e.name)?.sets?.mapNotNull { it.kg }?.maxOrNull()
            LiveExercise(e.name, e.restS, List(e.sets) { LiveSet(kg = last?.let { num1(it) }.orEmpty(), reps = "${e.reps}") })
        },
    )
    PlatformNav.open(PlatformPage.WORKOUT)
}

private fun buzz(ctx: Context) = runCatching {
    val v = (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
    android.media.RingtoneManager.getRingtone(ctx, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))?.play()
}

private fun mmss(sec: Long): String = String.format(java.util.Locale.US, "%d:%02d", sec.coerceAtLeast(0) / 60, sec.coerceAtLeast(0) % 60)

/**
 * v2.13 live workout mode (spec §12): a set-by-set checklist. Ticking a set starts the rest timer
 * (the exercise's rest, 90 s by default) with an ongoing countdown notification; at zero the
 * phone vibrates and plays the notification sound. Finish saves a normal gym workout.
 */
@Composable
fun LiveWorkoutScreen(vm: AppViewModel, pvm: PlatformViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val live = pvm.live
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }

    // The rest buzz is done here while the screen shows; the alarm covers the background.
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) RestTimerAlarm.foreground = true
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) RestTimerAlarm.foreground = false
        }
        owner.lifecycle.addObserver(obs)
        RestTimerAlarm.foreground = true
        onDispose { owner.lifecycle.removeObserver(obs); RestTimerAlarm.foreground = false }
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val restLeft = live?.restEndsAt?.let { ((it - now) / 1000.0).let { s -> kotlin.math.ceil(s).toLong() } }
    LaunchedEffect(live?.restEndsAt, restLeft != null && restLeft <= 0) {
        val l = pvm.live ?: return@LaunchedEffect
        if (l.restEndsAt != null && System.currentTimeMillis() >= l.restEndsAt) {
            // In the background the alarm posts "Rest's up" (replacing the countdown); here we only buzz.
            if (RestTimerAlarm.foreground) { RestTimerAlarm.cancel(ctx); PlatformNotifications.cancelRest(ctx); buzz(ctx) }
            pvm.live = l.copy(restEndsAt = null)
        }
    }

    fun update(block: (LiveWorkout) -> LiveWorkout) { pvm.live?.let { pvm.live = block(it) } }
    fun startRest(ex: LiveExercise, next: String) {
        if (ex.restS <= 0) return
        val end = System.currentTimeMillis() + ex.restS * 1000L
        update { it.copy(restEndsAt = end, restTotalS = ex.restS, restLabel = ex.name, restNext = next) }
        PlatformNotifications.postRest(ctx, ex.name, next, end)
        RestTimerAlarm.schedule(ctx, end, next)
        if (!PlatformNotifications.permitted(ctx)) PlatformNav.askNotificationsTick++
    }
    fun stopRest() { update { it.copy(restEndsAt = null) }; RestTimerAlarm.cancel(ctx); PlatformNotifications.cancelRest(ctx) }

    fun finish() {
        val l = pvm.live ?: return
        val lifts = l.exercises.mapNotNull { e ->
            val sets = e.sets.filter { it.done }.map { s -> LiftSet(s.kg.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }, s.reps.toIntOrNull()?.takeIf { it > 0 }) }
            if (sets.isEmpty()) null else Lift(e.name, sets)
        }
        if (lifts.isEmpty()) { err = "Tick at least one set before finishing."; return }
        busy = true
        val names = lifts.map { it.name }
        val bodyweight = names.all { Lifts.find(it)?.bodyweight == true }
        val minutes = ((System.currentTimeMillis() - l.startedAt) / 60_000L).toInt().coerceIn(1, 600)
        scope.launch {
            val ok = vm.saveWorkout(
                null, Dates.today(), Lifts.musclesFor(names), "Medium", null, minutes, "", l.title,
                kind = if (bodyweight) "bodyweight" else "gym", lifts = lifts,
            )
            busy = false
            if (ok) { stopRest(); pvm.live = null; onBack() } else err = vm.error ?: "Couldn't save the workout"
        }
    }

    PageFrame(live?.title ?: "Workout", onBack) {
        if (live == null) {
            Column(Modifier.padding(16.dp)) { Text("No workout in progress.", color = p.muted) }
            return@PageFrame
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, 220.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "head") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(mmss((now - live.startedAt) / 1000), style = MonoStyle.copy(fontSize = 22.sp), color = p.ink)
                        Spacer(Modifier.width(8.dp))
                        Text("elapsed", fontSize = 13.sp, color = p.muted)
                        Spacer(Modifier.weight(1f))
                        val done = live.exercises.sumOf { e -> e.sets.count { it.done } }
                        Text("$done / ${live.exercises.sumOf { it.sets.size }} sets", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink)
                    }
                }
                itemsIndexed(live.exercises, key = { i, e -> "$i-${e.name}" }) { ei, ex ->
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ex.name, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
                            Text("rest ${ex.restS}s", fontSize = 12.sp, color = p.muted)
                        }
                        Spacer(Modifier.height(8.dp))
                        ex.sets.forEachIndexed { si, s ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${si + 1}", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.muted, modifier = Modifier.width(24.dp))
                                com.sohum.bandlog.ui.log.NumberField(s.kg, { v -> update { w -> w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i != ei) e else e.copy(sets = e.sets.mapIndexed { j, x -> if (j == si) x.copy(kg = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6)) else x }) }) } }, "kg")
                                Spacer(Modifier.width(10.dp))
                                com.sohum.bandlog.ui.log.NumberField(s.reps, { v -> update { w -> w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i != ei) e else e.copy(sets = e.sets.mapIndexed { j, x -> if (j == si) x.copy(reps = v.filter { c -> c.isDigit() }.take(3)) else x }) }) } }, "reps")
                                Spacer(Modifier.weight(1f))
                                Box(
                                    Modifier.size(40.dp).background(if (s.done) accentColor else Color.Transparent, CircleShape).border(1.5.dp, if (s.done) accentColor else p.hair, CircleShape)
                                        .clickable(onClickLabel = if (s.done) "Untick set ${si + 1}" else "Done set ${si + 1}") {
                                            val nowDone = !s.done
                                            update { w -> w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i != ei) e else e.copy(sets = e.sets.mapIndexed { j, x -> if (j == si) x.copy(done = nowDone) else x }) }) }
                                            if (nowDone) {
                                                val next = when {
                                                    si + 1 < ex.sets.size -> "Next: ${ex.name}, set ${si + 2}"
                                                    ei + 1 < live.exercises.size -> "Next: ${live.exercises[ei + 1].name}"
                                                    else -> "Last set done. Finish when ready."
                                                }
                                                if (si + 1 < ex.sets.size || ei + 1 < live.exercises.size) startRest(ex, next)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(CheckIcon, null, tint = if (s.done) Color.White else p.muted, modifier = Modifier.size(18.dp)) }
                            }
                        }
                        Text(
                            "+ Add set", fontSize = 14.sp, fontWeight = FontWeight(600), color = accentColor,
                            modifier = Modifier.heightIn(min = 44.dp).clickable {
                                update { w -> w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i != ei) e else e.copy(sets = e.sets + (e.sets.lastOrNull()?.copy(done = false) ?: LiveSet())) }) }
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
                item(key = "add") {
                    PillButton("Add exercise", { picker = true }, bg = p.card2, fg = p.ink, icon = LineIcons.Plus)
                }
                item(key = "end") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ErrorNote(err)
                        PillButton(if (busy) "Saving…" else "Finish workout", { confirmFinish = true }, enabled = !busy)
                        Text(
                            "Discard", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.red,
                            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 44.dp).clickable { confirmDiscard = true }.padding(12.dp),
                        )
                    }
                }
            }
            // The rest timer, docked at the bottom while resting.
            if (restLeft != null && restLeft > 0) RestPanel(live, restLeft, Modifier.align(Alignment.BottomCenter), onAdjust = { d ->
                update { w -> w.restEndsAt?.let { e -> w.copy(restEndsAt = (e + d * 1000L).coerceAtLeast(System.currentTimeMillis() + 1000)) } ?: w }
                pvm.live?.restEndsAt?.let { e -> PlatformNotifications.postRest(ctx, live.restLabel, live.restNext, e); RestTimerAlarm.schedule(ctx, e, live.restNext) }
            }, onSkip = { stopRest() })
        }
    }

    if (picker) ExercisePicker(onDismiss = { picker = false }) { name ->
        picker = false
        val last = vm.lastLift(name)?.sets?.mapNotNull { it.kg }?.maxOrNull()
        update { w -> w.copy(exercises = w.exercises + LiveExercise(name, Routines.DEFAULT_REST_S, List(3) { LiveSet(kg = last?.let { num1(it) }.orEmpty(), reps = "10") })) }
    }
    if (confirmFinish) ConfirmDialog(
        "Finish and save?", "Ticked sets are saved as today's workout and count toward your streak.", "Save workout",
        onConfirm = { confirmFinish = false; finish() }, onDismiss = { confirmFinish = false },
    )
    if (confirmDiscard) ConfirmDialog(
        "Discard this workout?", "Nothing from this session will be saved.", "Discard", danger = true,
        onConfirm = { confirmDiscard = false; stopRest(); pvm.live = null; onBack() }, onDismiss = { confirmDiscard = false },
    )
}

@Composable
private fun RestPanel(live: LiveWorkout, left: Long, modifier: Modifier, onAdjust: (Int) -> Unit, onSkip: () -> Unit) {
    val p = palette
    val a = accentColor
    val frac = (left.toFloat() / live.restTotalS.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(
        modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = p.shadow, spotColor = p.shadow)
            .background(p.btn, RoundedCornerShape(24.dp)).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("REST", style = MonoStyle, color = p.btnInk.copy(alpha = 0.7f))
                Text(mmss(left), style = MonoStyle.copy(fontSize = 40.sp), color = p.btnInk)
            }
            RestButton("−15", p.btnInk) { onAdjust(-15) }
            Spacer(Modifier.width(8.dp))
            RestButton("+15", p.btnInk) { onAdjust(15) }
            Spacer(Modifier.width(8.dp))
            RestButton("Skip", p.btnInk, onSkip)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(p.btnInk.copy(alpha = 0.18f), CircleShape)) {
            Box(Modifier.fillMaxWidth(frac).height(6.dp).background(a, CircleShape))
        }
        Text(live.restNext, fontSize = 13.sp, color = p.btnInk.copy(alpha = 0.75f), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun RestButton(text: String, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).border(1.dp, ink.copy(alpha = 0.35f), CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight(700), color = ink) }
}
