package com.sohum.bandlog.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * v2.14 brand v1 palette (LockedIn-backups/brand-system-v1.md, docs/v214-spec.md in the web repo):
 * ink · bone · surf · surf2 · mute · line · ember · iris · mint. The body is monochrome; ember is
 * rationed (the + button, the streak flame, "you" in squad lists, the one primary action per
 * screen), iris means the coach is talking, mint means "on track".
 *
 * The field names are the pre-v2.14 ones so every screen keeps compiling: [bg] is the page, [card]
 * surf, [card2] surf2, [ink] the text colour, [muted] mute, [hair] line. The old macro colours
 * ([orange], [blue], [purple]) are mono shades now; [red] stays a real danger colour for errors and
 * destructive actions, and [green] is mint ("on track"). [flame] is ember.
 */
data class Palette(
    val bg: Color, val card: Color, val card2: Color, val ink: Color, val muted: Color, val hair: Color, val track: Color,
    val btn: Color, val btnInk: Color,
    val red: Color, val redBg: Color, val orange: Color, val orangeBg: Color, val blue: Color, val blueBg: Color,
    val green: Color, val greenBg: Color, val purple: Color, val purpleBg: Color, val flame: Color,
    val shadow: Color,
    // ---- v2.14 brand tokens ----
    /** Brand orange: you, your streak, the next action. */
    val ember: Color,
    /** Ember's soft tint (chips, the buddy explainer). */
    val emberBg: Color,
    /** Text / icons on a solid ember fill. */
    val onEmber: Color,
    /** The coach's colour, and nothing else. */
    val iris: Color,
    val irisBg: Color,
    /** "On track" only. */
    val mint: Color,
    /** The hatch lines for "still to go" on rings and bars. */
    val hatch: Color,
    val dark: Boolean,
)

// Bone page, white cards (light theme swaps ink and bone; ember stays the same).
val LightPalette = Palette(
    bg = Color(0xFFF4F1EA), card = Color(0xFFFFFFFF), card2 = Color(0xFFEAE6DD), ink = Color(0xFF0B0B0C), muted = Color(0xFF6E6962),
    hair = Color(0x1A0B0B0C), track = Color(0xFFEAE6DD), btn = Color(0xFF0B0B0C), btnInk = Color(0xFFF4F1EA),
    red = Color(0xFFD23A2A), redBg = Color(0xFFF8DFDA), orange = Color(0xFF3D3A36), orangeBg = Color(0xFFEAE6DD), blue = Color(0xFF8F8A82), blueBg = Color(0xFFEAE6DD),
    green = Color(0xFF1F9D6B), greenBg = Color(0x241F9D6B), purple = Color(0xFF57534D), purpleBg = Color(0xFFEAE6DD), flame = Color(0xFFFF5B1F),
    shadow = Color(0x1F0B0B0C),
    ember = Color(0xFFFF5B1F), emberBg = Color(0xFFFBE3D8), onEmber = Color(0xFF0B0B0C),
    iris = Color(0xFF6B5CE6), irisBg = Color(0x126B5CE6), mint = Color(0xFF1F9D6B), hatch = Color(0x400B0B0C), dark = false,
)

// Ink page, near-black surfaces.
val DarkPalette = Palette(
    bg = Color(0xFF0B0B0C), card = Color(0xFF141416), card2 = Color(0xFF1D1D20), ink = Color(0xFFF4F1EA), muted = Color(0xFF8F8A82),
    hair = Color(0x17F4F1EA), track = Color(0xFF1D1D20), btn = Color(0xFFF4F1EA), btnInk = Color(0xFF0B0B0C),
    red = Color(0xFFFF6B5B), redBg = Color(0x29FF6B5B), orange = Color(0xFFC9C4BA), orangeBg = Color(0xFF1D1D20), blue = Color(0xFF8F8A82), blueBg = Color(0xFF1D1D20),
    green = Color(0xFF59E3A7), greenBg = Color(0x2459E3A7), purple = Color(0xFFB3AEA5), purpleBg = Color(0xFF1D1D20), flame = Color(0xFFFF5B1F),
    shadow = Color(0xE6000000),
    ember = Color(0xFFFF5B1F), emberBg = Color(0x29FF5B1F), onEmber = Color(0xFF0B0B0C),
    iris = Color(0xFF8B7BFF), irisBg = Color(0x1F8B7BFF), mint = Color(0xFF59E3A7), hatch = Color(0x4DF4F1EA), dark = true,
)

/** Brand constants that don't change with the theme. */
object Brand {
    val Ink = Color(0xFF0B0B0C)
    val Bone = Color(0xFFF4F1EA)
    val Ember = Color(0xFFFF5B1F)
    val EmberLight = Color(0xFFFF8B5E)
    val Mute = Color(0xFF8F8A82)
}

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Shorthand used across screens. */
val palette: Palette @Composable get() = LocalPalette.current

fun Palette.toScheme(dark: Boolean) = if (dark) darkColorScheme(
    primary = ink, onPrimary = btnInk, secondary = muted, onSecondary = btnInk, tertiary = mint, onTertiary = btnInk,
    background = bg, onBackground = ink, surface = card, onSurface = ink, surfaceVariant = card2, onSurfaceVariant = muted,
    surfaceContainer = card, surfaceContainerHigh = card2, outline = hair, outlineVariant = hair, error = red,
) else lightColorScheme(
    primary = ink, onPrimary = btnInk, secondary = muted, onSecondary = btnInk, tertiary = mint, onTertiary = btnInk,
    background = bg, onBackground = ink, surface = card, onSurface = ink, surfaceVariant = card2, onSurfaceVariant = muted,
    surfaceContainer = card, surfaceContainerHigh = card2, outline = hair, outlineVariant = hair, error = red,
)
