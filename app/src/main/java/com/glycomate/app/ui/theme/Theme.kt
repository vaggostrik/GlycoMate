package com.glycomate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand colors ──────────────────────────────────────────────────────────────
val GlycoBlue   = Color(0xFF58A6FF)
val GlycoGreen  = Color(0xFF3FB950)
val GlycoAmber  = Color(0xFFE3B341)
val GlycoRed    = Color(0xFFF85149)
val GlycoPurple = Color(0xFFA371F7)

private val DarkScheme = darkColorScheme(
    primary            = GlycoBlue,
    onPrimary          = Color(0xFF003060),
    primaryContainer   = Color(0xFF0C447C),
    onPrimaryContainer = Color(0xFFD0E4FF),

    secondary            = GlycoGreen,
    onSecondary          = Color(0xFF003919),
    secondaryContainer   = Color(0xFF1E5228),
    onSecondaryContainer = Color(0xFFAAF0B4),

    tertiary            = GlycoPurple,
    onTertiary          = Color(0xFF2B0060),
    tertiaryContainer   = Color(0xFF4A2080),
    onTertiaryContainer = Color(0xFFE8D5FF),

    // Backgrounds — slightly lighter than pitch black for readability
    background       = Color(0xFF121212),
    onBackground     = Color(0xFFE8E8E8),

    surface          = Color(0xFF1E1E1E),
    onSurface        = Color(0xFFE8E8E8),
    surfaceVariant   = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceTint      = GlycoBlue,

    outline          = Color(0xFF444444),
    outlineVariant   = Color(0xFF333333),

    error            = GlycoRed,
    onError          = Color(0xFF5A0000),
    errorContainer   = Color(0xFF8B0000),
    onErrorContainer = Color(0xFFFFCDD2),
)

private val LightScheme = lightColorScheme(
    primary            = Color(0xFF185FA5),
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF042C53),

    secondary            = Color(0xFF2A6B18),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFCCF0BE),
    onSecondaryContainer = Color(0xFF082200),

    tertiary            = Color(0xFF6B3DB5),
    onTertiary          = Color(0xFFFFFFFF),
    tertiaryContainer   = Color(0xFFEFD9FF),
    onTertiaryContainer = Color(0xFF2B0060),

    background       = Color(0xFFF6F8FA),
    onBackground     = Color(0xFF1A1A1A),

    surface          = Color(0xFFFFFFFF),
    onSurface        = Color(0xFF1A1A1A),
    surfaceVariant   = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF555555),

    outline          = Color(0xFFCCCCCC),
    outlineVariant   = Color(0xFFE0E0E0),

    error            = Color(0xFFA32D2D),
    onError          = Color(0xFFFFFFFF),
    errorContainer   = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun GlycoMateTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content     = content
    )
}
