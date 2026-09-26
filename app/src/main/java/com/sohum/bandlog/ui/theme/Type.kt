package com.sohum.bandlog.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font as GoogleFontFont
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.R

// v2.14 brand v1 type (LockedIn-backups/brand-system-v1.md):
//   Display  Bricolage Grotesque 700–800 — headlines, big numbers
//   Accent   Fraunces italic 300 — at most one phrase per screen
//   UI/body  Geist 400/500/600 (bundled)
//   Labels   Geist Mono 500, UPPERCASE, wide tracking (bundled)
// Bricolage and Fraunces come from Google Fonts through Play services (downloadable fonts), so the
// APK doesn't grow. Every family lists the bundled Geist after the Google font: on a phone without
// Play services (or offline on first run) Compose falls back to it instead of the system font.
@OptIn(ExperimentalTextApi::class)
private fun geist(weight: Int, style: FontStyle = FontStyle.Normal) =
    Font(R.font.geist, FontWeight(weight), style, variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

@OptIn(ExperimentalTextApi::class)
private fun geistMono(weight: Int) =
    Font(R.font.geistmono, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private val Geist = FontFamily(geist(400), geist(500), geist(600), geist(700), geist(800))

private val googleFonts = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val BricolageFont = GoogleFont("Bricolage Grotesque")
private val FrauncesFont = GoogleFont("Fraunces")

/** Bricolage Grotesque (display), Geist as the fallback. */
val Bricolage = FontFamily(
    GoogleFontFont(BricolageFont, googleFonts, FontWeight(600)), GoogleFontFont(BricolageFont, googleFonts, FontWeight(700)), GoogleFontFont(BricolageFont, googleFonts, FontWeight(800)),
    geist(600), geist(700), geist(800),
)

/** Fraunces italic 300: the one accent phrase per screen. */
val Fraunces = FontFamily(
    GoogleFontFont(FrauncesFont, googleFonts, FontWeight(300), FontStyle.Italic), GoogleFontFont(FrauncesFont, googleFonts, FontWeight(400), FontStyle.Italic),
    geist(300, FontStyle.Italic), geist(400, FontStyle.Italic),
)

/** Headlines and big numbers. Was Geist until v2.13. */
val Display = Bricolage
val Body = Geist
/** Geist Mono — used for eyebrow labels and countdowns (uppercase, letter-spaced). */
val Mono = FontFamily(geistMono(400), geistMono(500), geistMono(600))

val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontFamily = Bricolage, fontWeight = FontWeight(800), letterSpacing = (-1).sp),
        headlineMedium = headlineMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight(700), letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight(700), letterSpacing = (-0.4).sp),
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

/** v2.14 eyebrow: Geist Mono 500, uppercase, +14% tracking. */
val EyebrowStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight(500), fontSize = 12.sp, letterSpacing = 1.7.sp)

/** v2.14 headline: Bricolage 800, tight tracking (−3.5%). */
val HeadlineStyle = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(800), fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-1.2).sp)

/** v2.14 accent phrase: Fraunces italic 300. */
val AccentStyle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight(300), fontStyle = FontStyle.Italic)
