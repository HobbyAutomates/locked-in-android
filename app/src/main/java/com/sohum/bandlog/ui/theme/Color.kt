package com.sohum.bandlog.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Dark-first, matching the web app's tokens: near-black blue background, coral accent, green "ok".
val Accent = Color(0xFFF0595E)
val Ok = Color(0xFF22C55E)
val Warn = Color(0xFFF59E0B)
val Muted = Color(0xFF8A97A6)
val Line = Color(0xFF243040)

val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF7C5CFC),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Ok,
    onTertiary = Color(0xFF06220F),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFEEF2F6),
    surface = Color(0xFF131922),
    onSurface = Color(0xFFEEF2F6),
    surfaceVariant = Color(0xFF1B2430),
    onSurfaceVariant = Muted,
    surfaceContainer = Color(0xFF10151D),
    surfaceContainerHigh = Color(0xFF1B2430),
    outline = Color(0xFF34435A),
    outlineVariant = Line,
    error = Accent,
)
