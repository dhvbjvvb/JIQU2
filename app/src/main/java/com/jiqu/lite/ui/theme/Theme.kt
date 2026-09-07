package com.jiqu.lite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9FC4C6),
    primaryContainer = Color(0xFF36545A),
    onPrimaryContainer = Color(0xFFD6E8E8),
    secondary = Color(0xFFB7C7C7),
    secondaryContainer = Color(0xFF354247),
    onSecondaryContainer = Color(0xFFDDE5E3),
    tertiary = Color(0xFFD6B98D),
    tertiaryContainer = Color(0xFF514431),
    onTertiaryContainer = Color(0xFFF1DFC3),
    background = Color(0xFF182024),
    surface = Color(0xFF253238),
    surfaceVariant = Color(0xFF34434A),
    surfaceDim = Color(0xFF141C20),
    surfaceBright = Color(0xFF344147),
    surfaceContainerLowest = Color(0xFF11181B),
    surfaceContainerLow = Color(0xFF1D282D),
    surfaceContainer = Color(0xFF253238),
    surfaceContainerHigh = Color(0xFF2D3B41),
    surfaceContainerHighest = Color(0xFF35444A),
    onPrimary = Ink,
    onBackground = Color(0xFFF1F3F2),
    onSurface = Color(0xFFF1F3F2),
    onSurfaceVariant = Color(0xFFC0CAC8),
    outline = Color(0xFF7E8C8D),
    outlineVariant = Color(0xFF46565B),
    scrim = Color(0xFF070A0C)
)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    primaryContainer = AccentSoft,
    onPrimaryContainer = AccentDark,
    secondary = InkMuted,
    secondaryContainer = NeutralSoft,
    onSecondaryContainer = Ink,
    tertiary = Warning,
    tertiaryContainer = WarmSoft,
    onTertiaryContainer = Color(0xFF5B4630),
    background = Canvas,
    surface = Glass,
    surfaceVariant = CanvasDeep,
    surfaceDim = Color(0xFFE3E1DD),
    surfaceBright = Color(0xFFFFFEFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = CanvasSoft,
    surfaceContainer = Color(0xFFF1EFEC),
    surfaceContainerHigh = Color(0xFFEBE9E5),
    surfaceContainerHighest = Color(0xFFE5E3DF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = InkMuted,
    outline = NeutralOutline,
    outlineVariant = Color(0xFFDADDD9),
    scrim = Color(0xFF11171B)
)

@Composable
fun JiquTheme(
    darkTheme: Boolean = false,
    accentHue: Float = 196f,
    content: @Composable () -> Unit
) {
    val hue = accentHue.coerceIn(0f, 360f)
    val accent = Color.hsv(hue, if (darkTheme) 0.42f else 0.68f, if (darkTheme) 0.78f else 0.55f)
    val accentContainer = Color.hsv(hue, if (darkTheme) 0.5f else 0.18f, if (darkTheme) 0.3f else 0.9f)
    val onAccentContainer = if (darkTheme) Color.hsv(hue, 0.12f, 0.94f) else Color.hsv(hue, 0.62f, 0.25f)
    val colorScheme = when {
        darkTheme -> DarkColorScheme.copy(
            primary = accent,
            primaryContainer = accentContainer,
            onPrimaryContainer = onAccentContainer,
            onPrimary = if (accent.luminance() > 0.5f) Ink else Color.White,
            secondary = accent,
            secondaryContainer = accentContainer,
            onSecondaryContainer = onAccentContainer,
            tertiary = accent,
            tertiaryContainer = accentContainer,
            onTertiaryContainer = onAccentContainer
        )
        else -> LightColorScheme.copy(
            primary = accent,
            primaryContainer = accentContainer,
            onPrimaryContainer = onAccentContainer,
            onPrimary = if (accent.luminance() > 0.5f) Ink else Color.White,
            secondary = accent,
            secondaryContainer = accentContainer,
            onSecondaryContainer = onAccentContainer,
            tertiary = accent,
            tertiaryContainer = accentContainer,
            onTertiaryContainer = onAccentContainer,
            onSecondary = if (accent.luminance() > 0.5f) Ink else Color.White,
            onTertiary = if (accent.luminance() > 0.5f) Ink else Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
