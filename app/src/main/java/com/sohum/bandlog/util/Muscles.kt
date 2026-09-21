package com.sohum.bandlog.util

import androidx.compose.ui.graphics.Color

object Muscles {
    val ALL = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Forearms", "Core", "Glutes", "Quads", "Hamstrings", "Calves", "Other")

    private val colors = mapOf(
        "Chest" to Color(0xFFE76F51), "Back" to Color(0xFF2A9D8F), "Shoulders" to Color(0xFFE0A100),
        "Biceps" to Color(0xFF7C5CFC), "Triceps" to Color(0xFF3B82F6), "Forearms" to Color(0xFF0EA5A4),
        "Core" to Color(0xFFE0559A), "Glutes" to Color(0xFFF97316), "Quads" to Color(0xFF43A047),
        "Hamstrings" to Color(0xFF84CC16), "Calves" to Color(0xFFA16207), "Other" to Color(0xFF94A3B8),
    )
    fun color(m: String): Color = colors[m] ?: colors.getValue("Other")

    val BAND_LEVELS = listOf("Light", "Medium", "Heavy")
    fun bandColor(level: String): Color = when (level) {
        "Light" -> Color(0xFFF2C230)
        "Heavy" -> Color(0xFF64748B)
        else -> Color(0xFFE5484D)
    }
}
