package com.sohum.bandlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Goals

/**
 * The weekly pace control: sloth / rabbit / cheetah above a notched slider, the big number and a
 * "Slow and steady / Recommended / Max safe pace" chip, then the one-line why and the ⓘ sheet.
 * v2.10: the slider only reaches this person's safe max — 1 % of body weight a week for loss
 * (never over 1 kg), 0.5 % for gain. Shared by Goal & current weight and onboarding (GoalSpeedPicker.tsx).
 */
@Composable
fun GoalSpeedPicker(speed: Float, goal: String, weightKg: Double?, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    val p = palette
    val max = Goals.speedMax(goal, weightKg)
    val speedKg = Goals.roundSpeed(speed.toDouble(), max)
    val label = Goals.speedLabel(speedKg, max)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SpeedAnimal(SlothIcon, label == "Slow and steady", p.green)
            SpeedAnimal(RabbitIcon, label == "Recommended", p.green)
            SpeedAnimal(CheetahIcon, label == "Max safe pace", p.orange)
        }
        Spacer(Modifier.height(4.dp))
        val notches = kotlin.math.round((max - 0.1) * 10).toInt()
        Slider(
            value = speedKg.toFloat(),
            onValueChange = { onChange(Goals.roundSpeed(it.toDouble(), max).toFloat()) },
            valueRange = 0.1f..max.toFloat().coerceAtLeast(0.11f),
            steps = (notches - 1).coerceAtLeast(0), // 0.1 … max in 0.1 kg notches
            enabled = max > 0.1,
            colors = SliderDefaults.colors(thumbColor = p.ink, activeTrackColor = p.ink, inactiveTrackColor = p.track),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0.1 kg", fontSize = 11.sp, color = p.muted)
            Text("${fmt(max)} kg", fontSize = 11.sp, color = p.muted)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${fmt(speedKg)} kg / week", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
            Spacer(Modifier.size(10.dp))
            val tint = if (label == "Max safe pace") p.orange else p.green
            Box(Modifier.background(tint.copy(alpha = 0.16f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight(700), color = tint)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(Goals.speedWhy(goal, weightKg), fontSize = 12.sp, color = p.muted, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(8.dp))
            ScienceButton()
        }
    }
}

/** One of the three pace animals; the active band lights up in its colour. */
@Composable
private fun SpeedAnimal(icon: ImageVector, active: Boolean, color: Color) {
    val p = palette
    Box(
        Modifier.size(52.dp).background(if (active) color.copy(alpha = 0.16f) else p.card2, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (active) color else p.muted, modifier = Modifier.size(26.dp)) }
}
