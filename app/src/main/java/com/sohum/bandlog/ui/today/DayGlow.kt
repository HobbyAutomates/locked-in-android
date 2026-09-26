package com.sohum.bandlog.ui.today

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sohum.bandlog.data.totalsFor
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.rememberAnimationsEnabled
import com.sohum.bandlog.ui.theme.palette

/*
 * v2.14 "The day warms up" (brand v1, "Ember is earned" idea 04): Home starts monochrome and a soft
 * ember glow behind the top ring grows as the day's targets are hit — calories in range, protein,
 * a workout, and anything logged today. Capped at 18 % so it never becomes a background colour.
 * Off when the system "Remove animations" is on or the phone is in battery saver.
 */
object DayGlow {
    const val MAX_ALPHA = 0.18f

    /** 0..1: the share of today's four targets that are hit. */
    fun warmth(calories: Double, budget: Double, protein: Double, proteinTarget: Double, trained: Boolean, logged: Boolean): Float {
        var hit = 0
        if (logged) hit++
        if (budget > 0 && calories > 0 && kotlin.math.abs(calories - budget) <= budget * 0.10) hit++
        if (proteinTarget > 0 && protein >= proteinTarget) hit++
        if (trained) hit++
        return hit / 4f
    }

    fun warmth(vm: AppViewModel): Float {
        val t = vm.today
        val tot = totalsFor(vm.meals, t)
        val trained = vm.workouts.any { it.date == t } || vm.exercises.any { it.date == t && it.source != "workout" }
        return warmth(tot.calories, vm.budgetToday, tot.protein, vm.profile.proteinTargetG.toDouble(), trained, vm.meals.any { it.date == t })
    }

    fun powerSave(ctx: Context): Boolean = runCatching { ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }.getOrDefault(false)
}

/**
 * Draws the glow behind whatever this modifies (the Home list): a radial gradient centred on the
 * calorie ring's usual spot, at [DayGlow.MAX_ALPHA] × [warmth].
 */
@Composable
fun Modifier.dayGlow(warmth: Float): Modifier {
    val ctx = LocalContext.current
    val enabled = rememberAnimationsEnabled() && remember(ctx) { !DayGlow.powerSave(ctx) }
    val target = if (enabled) (warmth.coerceIn(0f, 1f) * DayGlow.MAX_ALPHA) else 0f
    val alpha by animateFloatAsState(target, tween(1400, easing = PremiumMotion.EaseOutSoft), label = "dayGlow")
    val ember = palette.ember
    if (alpha <= 0.001f) return this
    return this.drawBehind {
        val center = Offset(size.width * 0.72f, 250.dp.toPx())
        val radius = maxOf(size.width * 0.95f, 340.dp.toPx())
        drawRect(Brush.radialGradient(listOf(ember.copy(alpha = alpha), ember.copy(alpha = alpha * 0.35f), Color.Transparent), center = center, radius = radius))
    }
}
