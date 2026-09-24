package com.sohum.bandlog.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BottleIcon
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.GlassIcon
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import kotlinx.coroutines.launch
import java.util.Locale

/** Water blue, same in both themes. */
val WaterBlue = Color(0xFF4FA3F7)

private data class Pour(val label: String, val ml: Int, val icon: ImageVector)

private val POURS = listOf(
    Pour("Glass", 250, GlassIcon),
    Pour("Bottle", 500, BottleIcon),
    Pour("Large bottle", 750, BottleIcon),
)

/**
 * v2.3 Log water: a big number in mL, three quick cards (+1 Glass 250 / +1 Bottle 500 /
 * +1 Large bottle 750) that add to it, and one Log button → bandlog.water_log.
 */
@Composable
fun LogWaterSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var ml by remember { mutableStateOf("250") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val amount = ml.toIntOrNull() ?: 0
    BottomSheet(
        title = "Log water",
        subtitle = "${vm.waterToday} of ${vm.profile.waterGoalMl} mL today",
        onDismiss = onDismiss,
        primary = if (busy) "Logging…" else "Log",
        primaryEnabled = !busy && amount in 1..5000,
        onPrimary = {
            scope.launch {
                busy = true; error = null
                if (vm.logWater(amount)) onDismiss() else { error = vm.error ?: "Couldn't log water"; busy = false }
            }
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                ml, { ml = it.filter(Char::isDigit).take(4) }, Modifier.width(150.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                textStyle = TextStyle(fontSize = 52.sp, fontWeight = FontWeight(800), color = p.ink, textAlign = TextAlign.End, letterSpacing = (-1.5).sp),
                cursorBrush = SolidColor(p.ink),
            )
            Text(" mL", fontSize = 18.sp, fontWeight = FontWeight(700), color = p.muted, modifier = Modifier.padding(bottom = 12.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            POURS.forEach { pour ->
                Column(
                    Modifier.weight(1f).pressable().background(p.card2, RoundedCornerShape(18.dp))
                        .clickable { ml = ((ml.toIntOrNull() ?: 0) + pour.ml).coerceAtMost(5000).toString() }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(pour.icon, null, tint = WaterBlue, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("+1 ${pour.label}", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, textAlign = TextAlign.Center)
                    Text("${pour.ml} mL", fontSize = 11.sp, color = p.muted)
                }
            }
        }
        if (amount > 0) Text(
            "Tap a card to add it · clear the number to start over",
            fontSize = 11.sp, color = p.muted, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = TextAlign.Center,
        )
        ErrorNote(error, Modifier.padding(top = 8.dp))
    }
}

/** Home's small water card: today vs goal, a bar, and a one-tap "+ Glass". Tapping the card opens the sheet. */
@Composable
fun WaterCard(vm: AppViewModel, onOpen: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val total = vm.waterToday
    val goal = vm.profile.waterGoalMl.coerceAtLeast(1)
    Card(onClick = onOpen, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(WaterBlue.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(GlassIcon, null, tint = WaterBlue, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(String.format(Locale.US, "%,d", total), fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
                    Text(" / ${String.format(Locale.US, "%,d", goal)} mL", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(bottom = 2.dp))
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(p.track, CircleShape)) {
                    val f = (total.toFloat() / goal).coerceIn(0f, 1f)
                    if (f > 0f) Box(Modifier.fillMaxWidth(f).height(6.dp).background(WaterBlue, CircleShape))
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.height(36.dp).pressable().background(p.btn, CircleShape).clickable(enabled = !busy) {
                    scope.launch { busy = true; vm.logWater(250); busy = false }
                }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (busy) "…" else "+ Glass", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.btnInk, maxLines = 1) }
        }
    }
}
