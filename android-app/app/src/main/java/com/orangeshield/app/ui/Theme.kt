package com.orangeshield.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ORANGE SHIELD — tema Material3 escuro com identidade laranja.
 * Primária #F97316, fundo #09090B, superfície #18181B ( Zinc dark ).
 */

val OrangePrimary = Color(0xFFF97316)
val OrangeSecondary = Color(0xFFFDBA74)
val DarkBackground = Color(0xFF09090B)
val DarkSurface = Color(0xFF18181B)
val DarkSurfaceVariant = Color(0xFF27272A)
val LightOnDark = Color(0xFFFAFAFA)
val MutedOnDark = Color(0xFFA1A1AA)

// Cores semânticas de decisão (bandeira do motor)
val EmeraldOk = Color(0xFF10B981) // ALLOW
val AmberWarn = Color(0xFFF59E0B) // WARN / QUARANTINE
val ZincNeutral = Color(0xFF71717A) // SILENCE / info
val RedBlock = Color(0xFFEF4444) // BLOCK

private val OrangeShieldColorScheme = darkColorScheme(
    primary = OrangePrimary,
    onPrimary = Color(0xFF180A02),
    primaryContainer = Color(0xFF7C2D12),
    onPrimaryContainer = Color(0xFFFFEDD5),
    secondary = OrangeSecondary,
    onSecondary = Color(0xFF431407),
    secondaryContainer = Color(0xFF9A3412),
    onSecondaryContainer = Color(0xFFFFEDD5),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    background = DarkBackground,
    onBackground = LightOnDark,
    surface = DarkSurface,
    onSurface = LightOnDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFD4D4D8),
    outline = Color(0xFF52525B),
    error = RedBlock,
    onError = Color(0xFFFFF1F1),
    inverseSurface = Color(0xFFE4E4E7),
    inverseOnSurface = Color(0xFF18181B)
)

@Composable
fun OrangeShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OrangeShieldColorScheme,
        content = content
    )
}
