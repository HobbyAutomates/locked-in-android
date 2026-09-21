package com.sohum.bandlog.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Cal AI-style palette: neutral ground, white/charcoal cards, black ink, macro colours. */
data class Palette(
    val bg: Color, val card: Color, val card2: Color, val ink: Color, val muted: Color, val hair: Color, val track: Color,
    val btn: Color, val btnInk: Color,
    val red: Color, val redBg: Color, val orange: Color, val orangeBg: Color, val blue: Color, val blueBg: Color,
    val green: Color, val greenBg: Color, val purple: Color, val purpleBg: Color, val flame: Color,
    val shadow: Color,
)

val LightPalette = Palette(
    bg = Color(0xFFF4F4F6), card = Color(0xFFFFFFFF), card2 = Color(0xFFF2F2F4), ink = Color(0xFF111111), muted = Color(0xFF7C7C82),
    hair = Color(0xFFE9E9EC), track = Color(0xFFECECEF), btn = Color(0xFF111111), btnInk = Color(0xFFFFFFFF),
    red = Color(0xFFE9573F), redBg = Color(0xFFFDE9E5), orange = Color(0xFFF09A2A), orangeBg = Color(0xFFFDF0DC),
    blue = Color(0xFF3E86E4), blueBg = Color(0xFFE3EEFB), green = Color(0xFF2FB35E), greenBg = Color(0xFFE2F5E8),
    purple = Color(0xFF7A5AF8), purpleBg = Color(0xFFECE7FE), flame = Color(0xFFFF6A00), shadow = Color(0x33111111),
)

val DarkPalette = Palette(
    bg = Color(0xFF0B0B0C), card = Color(0xFF1A1A1C), card2 = Color(0xFF26262A), ink = Color(0xFFF5F5F7), muted = Color(0xFF9A9AA0),
    hair = Color(0xFF2C2C30), track = Color(0xFF2C2C30), btn = Color(0xFFF5F5F7), btnInk = Color(0xFF0B0B0C),
    red = Color(0xFFFF6B6B), redBg = Color(0x29FF6B6B), orange = Color(0xFFF5A623), orangeBg = Color(0x29F5A623),
    blue = Color(0xFF5B9BF0), blueBg = Color(0x295B9BF0), green = Color(0xFF4CD964), greenBg = Color(0x294CD964),
    purple = Color(0xFFA78BFA), purpleBg = Color(0x29A78BFA), flame = Color(0xFFFF7A1A), shadow = Color(0xE6000000),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Shorthand used across screens. */
val palette: Palette @Composable get() = LocalPalette.current

fun Palette.toScheme(dark: Boolean) = if (dark) darkColorScheme(
    primary = ink, onPrimary = btnInk, secondary = purple, onSecondary = btnInk, tertiary = green, onTertiary = btnInk,
    background = bg, onBackground = ink, surface = card, onSurface = ink, surfaceVariant = card2, onSurfaceVariant = muted,
    surfaceContainer = card, surfaceContainerHigh = card2, outline = hair, outlineVariant = hair, error = red,
) else lightColorScheme(
    primary = ink, onPrimary = btnInk, secondary = purple, onSecondary = btnInk, tertiary = green, onTertiary = btnInk,
    background = bg, onBackground = ink, surface = card, onSurface = ink, surfaceVariant = card2, onSurfaceVariant = muted,
    surfaceContainer = card, surfaceContainerHigh = card2, outline = hair, outlineVariant = hair, error = red,
)
