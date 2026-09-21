package com.sohum.bandlog.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Material 3 Expressive motion, expressed with Compose's built-in physics springs (no toolkit
 * upgrade). Spatial specs (position/size/scale) overshoot slightly for a lively, physical feel;
 * Effects specs (opacity/colour) settle with no overshoot. Springs are naturally interruptible.
 */
object Motion {
    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
    fun <T> spatialFast(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
    fun <T> spatialSlow(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)

    fun <T> effects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
    fun <T> effectsFast(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)
}
