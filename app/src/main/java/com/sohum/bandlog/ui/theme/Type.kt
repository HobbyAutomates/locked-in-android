package com.sohum.bandlog.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.R

// Single clean typeface (Geist) for a minimal, modern look. `Display` and `Body` both map to it so
// existing call sites keep working; titles just use heavier weights + tighter tracking.
@OptIn(ExperimentalTextApi::class)
private fun geist(weight: Int) =
    Font(R.font.geist, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

@OptIn(ExperimentalTextApi::class)
private fun geistMono(weight: Int) =
    Font(R.font.geistmono, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private val Geist = FontFamily(geist(400), geist(500), geist(600), geist(700), geist(800))

val Display = Geist
val Body = Geist
/** Geist Mono — used for eyebrow labels and countdowns (uppercase, letter-spaced). */
val Mono = FontFamily(geistMono(400), geistMono(500), geistMono(600))

val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontFamily = Geist, fontWeight = FontWeight(800), letterSpacing = (-1).sp),
        headlineMedium = headlineMedium.copy(fontFamily = Geist, fontWeight = FontWeight(700), letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontFamily = Geist, fontWeight = FontWeight(700), letterSpacing = (-0.4).sp),
        titleMedium = titleMedium.copy(fontFamily = Geist, fontWeight = FontWeight(600)),
        bodyLarge = bodyLarge.copy(fontFamily = Geist, fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = bodyMedium.copy(fontFamily = Geist),
        bodySmall = bodySmall.copy(fontFamily = Geist),
        labelLarge = labelLarge.copy(fontFamily = Geist, fontWeight = FontWeight(600)),
        labelMedium = labelMedium.copy(fontFamily = Geist, fontWeight = FontWeight(600)),
        labelSmall = labelSmall.copy(fontFamily = Geist, fontWeight = FontWeight(600), letterSpacing = 1.2.sp),
    )
}

val OverlineStyle = TextStyle(
    fontFamily = FontFamily(geistMono(600)), fontWeight = FontWeight(600), fontSize = 11.sp, letterSpacing = 1.5.sp,
)

/** Monospace style for countdowns / numeric chips. */
val MonoStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight(600), fontSize = 12.sp, letterSpacing = 0.5.sp)
