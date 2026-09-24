package com.sohum.bandlog.ui.today

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ConfettiBurst
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.WaterPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/** Water blue, same in both themes. */
val WaterBlue = Color(0xFF4FA3F7)
private val WaterDeep = Color(0xFF2F86F0)
private val WaterLight = Color(0xFF8CCBFF)

/** v2.6 vessels: one water_log row per tap of the + under each (vessel = key). */
private data class Vessel(val key: String, val label: String, val ml: Int?)

private val VESSELS = listOf(
    Vessel("glass", "Glass", 250),
    Vessel("bottle", "Bottle", 500),
    Vessel("large", "Large bottle", 1000),
    Vessel("custom", "Custom", null),
)

private fun lastLoggedLabel(createdAt: String?): String? = createdAt?.let {
    runCatching {
        OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    }.getOrNull()
}

/**
 * v2.6 Water page (Fittr bottle + Sohum's vessel row): the goal in glasses on the left, a bottle
 * that fills beside + / − on the right, the four vessels, and the reminder window + frequency.
 */
@Composable
fun WaterScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prof = vm.profile
    val glass = prof.waterGlassMl.coerceAtLeast(50)
    val goal = prof.waterGoalMl.coerceAtLeast(glass)
    val total = vm.waterToday
    val done = total >= goal
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var customSheet by remember { mutableStateOf(false) }
    var goalSheet by remember { mutableStateOf(false) }

    fun add(ml: Int, vessel: String) {
        if (busy) return
        scope.launch { busy = true; error = null; if (!vm.logWater(ml, vessel = vessel, quiet = true)) error = vm.error ?: "Couldn't log water"; busy = false }
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = p.ink, modifier = Modifier.size(18.dp)) }
            Text("Water", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
            Box(Modifier.size(40.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(PaddingValues(20.dp, 6.dp, 20.dp, 40.dp)).navigationBarsPadding(),
        ) {
            Text("Your daily water intake", fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = p.ink, lineHeight = 30.sp)
            Text("today", fontSize = 14.sp, color = p.muted)
            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // Left: the goal in glasses (tap to edit), and the congratulations once it's met.
                Column(Modifier.weight(1f)) {
                    Text("Goal", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.heightIn(min = 60.dp).width(76.dp).pressable().border(1.5.dp, p.hair, RoundedCornerShape(14.dp)).clickable { goalSheet = true },
                            contentAlignment = Alignment.Center,
                        ) { Text("${(goal + glass / 2) / glass}", fontSize = 32.sp, fontWeight = FontWeight(800), color = p.ink, maxLines = 1) }
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.background(p.card2, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("gl", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink)
                        }
                    }
                    Text("= ${WaterPrefs.litres(goal)}", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(top = 6.dp))
                    Text("1 gl = $glass mL", fontSize = 12.sp, color = p.muted)
                    Text("Tap to change", fontSize = 12.sp, fontWeight = FontWeight(600), color = WaterBlue, modifier = Modifier.clickable { goalSheet = true }.padding(top = 2.dp))
                    if (done) {
                        Spacer(Modifier.height(18.dp))
                        Text("🎉", fontSize = 30.sp)
                        Text("Congratulations!", fontSize = 16.sp, fontWeight = FontWeight(800), color = p.ink)
                        Text("You are done with your water goal for the day", fontSize = 13.sp, color = p.muted, lineHeight = 17.sp)
                    }
                }
                // Right: the bottle between its + / − buttons.
                Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Consumed", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text(
                        lastLoggedLabel(vm.waterTodayRows.firstOrNull()?.createdAt)?.let { "Last logged $it" } ?: "Nothing yet today",
                        fontSize = 11.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp), maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WaterBottle(total.toFloat() / goal, WaterPrefs.litres(total), Modifier.size(96.dp, 214.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(56.dp)) {
                            RoundStep("+", "Add a glass", enabled = !busy) { add(glass, "glass") }
                            RoundStep("−", "Remove the last one", enabled = !busy && vm.waterTodayRows.isNotEmpty()) {
                                scope.launch { busy = true; error = null; if (!vm.undoWater()) error = vm.error; busy = false }
                            }
                        }
                    }
                }
            }
            ErrorNote(error, Modifier.padding(top = 10.dp))

            Spacer(Modifier.height(22.dp))
            Card(padding = 16.dp) {
                Text("Add a drink", fontSize = 16.sp, fontWeight = FontWeight(800), color = p.ink)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    VESSELS.forEach { v ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            VesselArt(v.key, Modifier.size(44.dp, 58.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(v.ml?.let { "$it ml" } ?: "Custom", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, softWrap = false)
                            Text(if (v.ml == null) "amount" else v.label, fontSize = 11.sp, color = p.muted, maxLines = 1, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.size(48.dp).pressable().background(p.card2, CircleShape).clickable(enabled = !busy) {
                                    if (v.ml == null) customSheet = true else add(v.ml, v.key)
                                },
                                contentAlignment = Alignment.Center,
                            ) { Text("+", fontSize = 26.sp, fontWeight = FontWeight(500), color = WaterBlue) }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Total ${WaterPrefs.litres(total)} of ${WaterPrefs.litres(goal)}",
                    fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(26.dp))
            WaterReminderBlock(vm)
        }
    }

    if (customSheet) CustomAmountSheet(onDismiss = { customSheet = false }) { ml -> customSheet = false; add(ml, "custom") }
    if (goalSheet) GoalSheet(goal, glass, onDismiss = { goalSheet = false }) { ml -> goalSheet = false; vm.setWaterGoal(ml) }
}

@Composable
private fun RoundStep(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier.size(44.dp).pressable().border(1.5.dp, if (enabled) p.ink else p.hair, CircleShape).clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 22.sp, fontWeight = FontWeight(600), color = if (enabled) p.ink else p.muted) }
}

/** Fittr-style tall bottle: capsule body with a cap, blue liquid with a soft moving wave and rising bubbles. */
@Composable
fun WaterBottle(fraction: Float, label: String?, modifier: Modifier = Modifier) {
    val p = palette
    val dark = p.bg.luminance() < 0.5f
    val level by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.7f, stiffness = 60f), label = "waterLevel")
    val waves = rememberInfiniteTransition(label = "wave")
    val phase by waves.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "wavePhase")
    val glassBody = if (dark) Color(0xFF1B2640) else Color(0xFFE4EEFA)
    val capColor = if (dark) Color(0xFF2C3A5C) else Color(0xFFC9D8EC)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val capH = h * 0.06f
            val neckTop = capH; val bodyTop = h * 0.1f
            // cap + neck
            drawRoundRect(capColor, topLeft = Offset(w * 0.3f, 0f), size = Size(w * 0.4f, capH), cornerRadius = CornerRadius(w * 0.06f))
            drawRoundRect(glassBody, topLeft = Offset(w * 0.36f, neckTop - 1f), size = Size(w * 0.28f, bodyTop - neckTop + w * 0.2f), cornerRadius = CornerRadius(w * 0.04f))
            val r = w * 0.42f
            val body = Path().apply { addRoundRect(RoundRect(0f, bodyTop, w, h, CornerRadius(r))) }
            drawPath(body, glassBody)
            clipPath(body) {
                if (level > 0.001f) {
                    val top = bodyTop + (1f - level) * (h - bodyTop)
                    val amp = if (level >= 0.995f) 0f else 3.5f * density
                    val wave = Path().apply {
                        moveTo(0f, top)
                        var x = 0f
                        while (x <= w) { lineTo(x, top + amp * sin(2f * PI.toFloat() * x / w * 1.2f + phase)); x += 3f }
                        lineTo(w, h); lineTo(0f, h); close()
                    }
                    drawPath(wave, Brush.verticalGradient(listOf(WaterLight, WaterDeep), startY = top, endY = h))
                    // bubbles rising through the water
                    val span = h - top
                    listOf(0.22f to 0.1f, 0.62f to 0.45f, 0.4f to 0.8f, 0.76f to 0.25f, 0.3f to 0.6f).forEach { (fx, seed) ->
                        val prog = ((seed + phase / (2f * PI.toFloat())) % 1f)
                        val y = h - prog * span
                        if (y > top + 4f) drawCircle(Color.White.copy(alpha = 0.55f * (1f - prog)), radius = 1.8f * density, center = Offset(w * fx, y))
                    }
                }
                // glass highlight
                drawRoundRect(Color.White.copy(alpha = if (dark) 0.07f else 0.35f), topLeft = Offset(w * 0.1f, bodyTop + r * 0.45f), size = Size(w * 0.07f, (h - bodyTop) * 0.3f), cornerRadius = CornerRadius(w * 0.05f))
            }
            drawPath(body, if (dark) Color.White.copy(alpha = 0.06f) else Color(0x14000000), style = Stroke(width = 1.dp.toPx()))
        }
        if (label != null) Text(
            label, fontSize = 17.sp, fontWeight = FontWeight(800), color = if (level > 0.28f) Color.White else p.ink, maxLines = 1,
            modifier = Modifier.padding(top = 70.dp),
        )
    }
}

/** Small drawings for the vessel row: a glass, a bottle, a large bottle with ticks, and a grey "custom" bottle. */
@Composable
private fun VesselArt(kind: String, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        when (kind) {
            "glass" -> {
                val cup = Path().apply { moveTo(w * 0.12f, h * 0.3f); lineTo(w * 0.88f, h * 0.3f); lineTo(w * 0.78f, h); lineTo(w * 0.22f, h); close() }
                drawPath(cup, Color(0xFFCFE6FF))
                val water = Path().apply { moveTo(w * 0.16f, h * 0.5f); lineTo(w * 0.84f, h * 0.5f); lineTo(w * 0.78f, h); lineTo(w * 0.22f, h); close() }
                drawPath(water, WaterLight)
                drawCircle(Color(0xFFD7E84A), radius = w * 0.14f, center = Offset(w * 0.8f, h * 0.3f))
            }
            "bottle", "large" -> {
                val big = kind == "large"
                val bw = if (big) w * 0.78f else w * 0.58f
                val left = (w - bw) / 2
                val top = if (big) h * 0.2f else h * 0.34f
                drawRoundRect(Color(0xFF34C77B), topLeft = Offset(left + bw * 0.25f, top - h * 0.12f), size = Size(bw * 0.5f, h * 0.12f), cornerRadius = CornerRadius(3f))
                drawRoundRect(Color(0xFFCFE6FF), topLeft = Offset(left, top), size = Size(bw, h - top), cornerRadius = CornerRadius(bw * 0.2f))
                val fillTop = top + (h - top) * 0.35f
                drawRoundRect(Brush.verticalGradient(listOf(WaterLight, WaterDeep), startY = fillTop, endY = h), topLeft = Offset(left, fillTop), size = Size(bw, h - fillTop), cornerRadius = CornerRadius(bw * 0.2f))
                if (big) for (i in 1..5) {
                    val y = top + (h - top) * i / 6.5f
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(left + bw * 0.72f, y), Offset(left + bw * 0.9f, y), strokeWidth = 1.5f * density)
                }
            }
            else -> {
                val bw = w * 0.52f
                val left = (w - bw) / 2
                drawRoundRect(Color(0xFFB9BCC6), topLeft = Offset(left + bw * 0.3f, h * 0.06f), size = Size(bw * 0.4f, h * 0.12f), cornerRadius = CornerRadius(3f))
                val b = Path().apply {
                    moveTo(left + bw * 0.3f, h * 0.18f); lineTo(left + bw * 0.7f, h * 0.18f)
                    cubicTo(left + bw * 0.7f, h * 0.3f, left + bw, h * 0.32f, left + bw, h * 0.45f)
                    lineTo(left + bw, h * 0.92f); quadraticBezierTo(left + bw, h, left + bw * 0.88f, h)
                    lineTo(left + bw * 0.12f, h); quadraticBezierTo(left, h, left, h * 0.92f)
                    lineTo(left, h * 0.45f); cubicTo(left, h * 0.32f, left + bw * 0.3f, h * 0.3f, left + bw * 0.3f, h * 0.18f); close()
                }
                drawPath(b, Color(0xFFD9DBE2))
            }
        }
    }
}

@Composable
private fun CustomAmountSheet(onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    val p = palette
    val focus = LocalFocusManager.current
    var ml by remember { mutableStateOf("") }
    val amount = ml.toIntOrNull() ?: 0
    BottomSheet(title = "Custom amount", subtitle = "Logged as one drink", onDismiss = onDismiss, primary = "Add", primaryEnabled = amount in 1..5000, onPrimary = { onAdd(amount) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                ml, { ml = it.filter(Char::isDigit).take(4) }, Modifier.width(150.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                textStyle = TextStyle(fontSize = 52.sp, fontWeight = FontWeight(800), color = p.ink, textAlign = TextAlign.End, letterSpacing = (-1.5).sp),
                cursorBrush = SolidColor(p.ink),
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { if (ml.isEmpty()) Text("330", fontSize = 52.sp, fontWeight = FontWeight(800), color = p.muted.copy(alpha = 0.4f)); inner() } },
            )
            Text(" mL", fontSize = 18.sp, fontWeight = FontWeight(700), color = p.muted, modifier = Modifier.padding(bottom = 12.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(150, 200, 330, 750).forEach { v ->
                Box(Modifier.weight(1f).height(40.dp).background(p.card2, CircleShape).clickable { ml = v.toString() }, contentAlignment = Alignment.Center) {
                    Text("$v", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                }
            }
        }
    }
}

@Composable
private fun GoalSheet(goalMl: Int, glassMl: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val p = palette
    var glasses by remember { mutableIntStateOf(((goalMl + glassMl / 2) / glassMl).coerceIn(1, 40)) }
    BottomSheet(title = "Daily water goal", subtitle = "In glasses of $glassMl mL", onDismiss = onDismiss, primary = "Save", onPrimary = { onSave(glasses * glassMl) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            RoundStep("−", "Fewer glasses", glasses > 1) { glasses-- }
            Column(Modifier.width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$glasses", fontSize = 52.sp, fontWeight = FontWeight(800), color = p.ink)
                Text("glasses · ${WaterPrefs.litres(glasses * glassMl)}", fontSize = 13.sp, color = p.muted)
            }
            RoundStep("+", "More glasses", glasses < 40) { glasses++ }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The reminder block: from–to wheels and the frequency list (Never … Every 4 hours). Saved to the
 * local mirror (the alarm reads it) and to the profile; picking a frequency asks for notifications.
 */
@Composable
private fun WaterReminderBlock(vm: AppViewModel) {
    val p = palette
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(WaterPrefs.load(ctx)) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun commit(n: WaterPrefs.Settings) {
        s = n
        WaterPrefs.save(ctx, n)
        com.sohum.bandlog.alarm.WaterAlarms.reschedule(ctx)
        vm.setWaterReminder(n.from, n.to, n.every)
    }
    Text("Water reminder", fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
    Text("Timings", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        TimeWheel(s.from) { commit(s.copy(from = it)) }
        Text("to", fontSize = 14.sp, color = p.muted)
        TimeWheel(s.to) { commit(s.copy(to = it)) }
    }
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WaterPrefs.FREQUENCIES.forEach { every ->
            val sel = s.every == every
            Box(
                Modifier.fillMaxWidth().heightIn(min = 54.dp).pressable()
                    .border(if (sel) 2.dp else 1.dp, if (sel) p.ink else p.hair, RoundedCornerShape(14.dp))
                    .clickable {
                        commit(s.copy(every = every))
                        if (every > 0 && android.os.Build.VERSION.SDK_INT >= 33 &&
                            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(WaterPrefs.label(every), fontSize = 15.sp, fontWeight = if (sel) FontWeight(700) else FontWeight(500), color = p.ink)
            }
        }
    }
    Text(
        if (s.every > 0) "A nudge ${WaterPrefs.label(s.every).lowercase()} between ${s.from} and ${s.to}, until you hit your goal. The notification's +1 glass logs without opening the app."
        else "Pick a frequency to get nudged inside the window above.",
        fontSize = 12.sp, color = p.muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp),
    )
}

/** "HH : MM" with a scrolling wheel for each part (the value in the middle, neighbours faded). */
@Composable
private fun TimeWheel(time: String, onChange: (String) -> Unit) {
    val p = palette
    val (h, m) = WaterPrefs.hm(time)
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(128.dp, 46.dp).border(1.5.dp, p.hair, RoundedCornerShape(12.dp)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WheelColumn(24, h, 46.dp) { hh -> onChange("%02d:%02d".format(hh, m)) }
            Text(":", fontSize = 18.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.padding(horizontal = 6.dp))
            WheelColumn(60, m, 46.dp) { onChange("%02d:%02d".format(h, it)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(count: Int, selected: Int, rowH: Dp, onSelected: (Int) -> Unit) {
    val p = palette
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    val center by remember { derivedStateOf { (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0).coerceIn(0, count - 1) } }
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) { delay(60); if (center != selected) onSelected(center) }
    }
    LazyColumn(
        Modifier.width(44.dp).height(rowH * 3), state = state, flingBehavior = fling,
        contentPadding = PaddingValues(vertical = rowH), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count) { i ->
            Box(Modifier.height(rowH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val on = i == center
                Text("%02d".format(i), fontSize = if (on) 20.sp else 14.sp, fontWeight = if (on) FontWeight(700) else FontWeight(500), color = if (on) p.ink else p.muted.copy(alpha = 0.5f))
            }
        }
    }
}

/** Home's water tile: a mini bottle, "Water · 1.75 L / 2.5 L", and a one-tap "+ Glass". Tapping the tile opens the Water page. */
@Composable
fun WaterCard(vm: AppViewModel, onOpen: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val total = vm.waterToday
    val glass = vm.profile.waterGlassMl.coerceAtLeast(50)
    val goal = vm.profile.waterGoalMl.coerceAtLeast(1)
    Card(onClick = onOpen, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaterBottle(total.toFloat() / goal, null, Modifier.size(26.dp, 50.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Water", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(WaterPrefs.litres(total), fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink, maxLines = 1)
                    Text(" / ${WaterPrefs.litres(goal)}", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(bottom = 2.dp), maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).background(p.track, CircleShape)) {
                    val f = (total.toFloat() / goal).coerceIn(0f, 1f)
                    if (f > 0f) Box(Modifier.fillMaxWidth(f).height(5.dp).background(WaterBlue, CircleShape))
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.height(36.dp).pressable().background(p.btn, CircleShape).clickable(enabled = !busy) {
                    scope.launch { busy = true; vm.logWater(glass, vessel = "glass"); busy = false }
                }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (busy) "…" else "+ Glass", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.btnInk, maxLines = 1) }
        }
    }
}

/** Full-screen confetti + the Fittr line, shown once a day when an add crosses the goal. Tap or wait to dismiss. */
@Composable
fun WaterGoalParty(onDone: () -> Unit) {
    val p = palette
    LaunchedEffect(Unit) { delay(3800); onDone() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp).shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = p.shadow, spotColor = p.shadow).background(p.card, RoundedCornerShape(28.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎉", fontSize = 44.sp)
            Text("Congratulations!", fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink, modifier = Modifier.padding(top = 6.dp))
            Text("You are done with your water goal for the day", fontSize = 14.sp, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
        ConfettiBurst(key = "water-goal")
    }
}
