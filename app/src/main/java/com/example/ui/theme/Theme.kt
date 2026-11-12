package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF00222B),
    primaryContainer = Color(0xFF003745),
    onPrimaryContainer = Color(0xFF9CF3FF),

    secondary = ElectricBlue,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDBEAFE),

    tertiary = HologramBlue,
    onTertiary = Color(0xFF001E3D),
    tertiaryContainer = Color(0xFF00325B),
    onTertiaryContainer = Color(0xFFCCE5FF),

    background = CyberBackground,
    onBackground = TextPrimary,

    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    outline = CyberBorder,
    error = StateError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Dedicated futuristic dark theme as requested for "AI BABY"
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
