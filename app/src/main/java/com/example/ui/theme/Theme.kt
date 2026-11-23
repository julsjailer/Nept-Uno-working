package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NeptunoDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = AbyssalNavy,
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = IceWhite,
    secondary = BioluminescentTurquoise,
    onSecondary = AbyssalNavy,
    secondaryContainer = TurquoiseContainer,
    onSecondaryContainer = TurquoiseSoft,
    tertiary = SonarCyan,
    onTertiary = AbyssalNavy,
    tertiaryContainer = BathyalSurface,
    onTertiaryContainer = IceWhite,
    background = AbyssalNavy,
    onBackground = IceWhite,
    surface = DeepOcean,
    onSurface = IceWhite,
    surfaceVariant = TrenchBlue,
    onSurfaceVariant = MistBlue,
    surfaceContainerLowest = AbyssalNavy,
    surfaceContainerLow = DeepOcean,
    surfaceContainer = TrenchBlue,
    surfaceContainerHigh = BathyalSurface,
    surfaceContainerHighest = Color(0xFF173861),
    outline = PelagicBorder,
    outlineVariant = Color(0xFF153152),
    error = CoralAlert,
    onError = AbyssalNavy,
    errorContainer = CoralAlertContainer,
    onErrorContainer = Color(0xFFFFD6DD)
)

private val NeptunoLightColorScheme = lightColorScheme(
    primary = LightElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E9FF),
    onPrimaryContainer = Color(0xFF001C3B),
    secondary = LightTurquoise,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4F5F0),
    onSecondaryContainer = Color(0xFF00201E),
    tertiary = Color(0xFF006782),
    onTertiary = Color.White,
    background = LightOceanBg,
    onBackground = LightNavyText,
    surface = LightOceanSurface,
    onSurface = LightNavyText,
    surfaceVariant = LightOceanVariant,
    onSurfaceVariant = LightMutedText,
    outline = Color(0xFF9EB9D4),
    outlineVariant = Color(0xFFC8DCEE),
    error = Color(0xFFBA1A3A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF41000B)
)

val NeptunoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NeptunoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NeptunoDarkColorScheme else NeptunoLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NeptunoTypography,
        shapes = NeptunoShapes,
        content = content
    )
}
