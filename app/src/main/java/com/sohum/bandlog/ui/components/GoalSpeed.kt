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
 * The 0.1–1.5 kg/week pace control: sloth / rabbit / cheetah above a notched slider, with the
 * big number and a "Slow and steady / Recommended / Aggressive" chip below. Shared by the
 * Goal & current weight page and the onboarding flow so both always read the same.
 */
@Composable
fun GoalSpeedPicker(speed: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    val p = palette
    val speedKg = kotlin.math.round(speed * 10) / 10.0
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SpeedAnimal(SlothIcon, speedKg < 0.5, p.green)
            SpeedAnimal(RabbitIcon, speedKg in 0.5..0.8, p.orange)
            SpeedAnimal(CheetahIcon, speedKg > 0.8, p.red)
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = speed,
            onValueChange = onChange,
            valueRange = 0.1f..1.5f,
            steps = 13, // 0.1 … 1.5 in 0.1 kg notches
            colors = SliderDefaults.colors(thumbColor = p.ink, activeTrackColor = p.ink, inactiveTrackColor = p.track),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0.1 kg", fontSize = 11.sp, color = p.muted)
            Text("1.5 kg", fontSize = 11.sp, color = p.muted)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${fmt(speedKg)} kg / week", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
            Spacer(Modifier.size(10.dp))
            val label = Goals.speedLabel(speedKg)
            val tint = when (label) { "Recommended" -> p.green; "Aggressive" -> p.red; else -> p.orange }
            Box(Modifier.background(tint.copy(alpha = 0.16f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight(700), color = tint)
            }
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
