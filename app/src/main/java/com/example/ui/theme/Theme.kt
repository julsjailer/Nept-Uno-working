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

/** Paleta completa a partir de un tono (croma) elegido por el usuario. [hue] en grados 0..360. */
fun accentColorScheme(hue: Float, dark: Boolean): androidx.compose.material3.ColorScheme {
    fun c(h: Float, s: Float, v: Float) = Color.hsv(((h % 360f) + 360f) % 360f, s, v)
    val h = hue
    return if (dark) {
        darkColorScheme(
            primary = c(h, .55f, 1f), onPrimary = c(h, .65f, .12f),
            primaryContainer = c(h, .65f, .40f), onPrimaryContainer = c(h, .12f, 1f),
            secondary = c(h + 30, .45f, .95f), onSecondary = c(h + 30, .6f, .12f),
            secondaryContainer = c(h + 30, .55f, .32f), onSecondaryContainer = c(h + 30, .15f, .96f),
            tertiary = c(h + 180, .40f, .95f), onTertiary = c(h + 180, .6f, .12f),
            tertiaryContainer = c(h + 180, .50f, .30f), onTertiaryContainer = c(h + 180, .15f, .96f),
            background = c(h, .62f, .10f), onBackground = c(h, .05f, .96f),
            surface = c(h, .58f, .14f), onSurface = c(h, .05f, .96f),
            surfaceVariant = c(h, .45f, .22f), onSurfaceVariant = c(h, .15f, .80f),
            surfaceContainerLowest = c(h, .65f, .08f), surfaceContainerLow = c(h, .60f, .12f),
            surfaceContainer = c(h, .55f, .17f), surfaceContainerHigh = c(h, .50f, .21f),
            surfaceContainerHighest = c(h, .45f, .25f),
            outline = c(h, .30f, .50f), outlineVariant = c(h, .45f, .28f),
            error = CoralAlert, onError = AbyssalNavy,
            errorContainer = CoralAlertContainer, onErrorContainer = Color(0xFFFFD6DD)
        )
    } else {
        lightColorScheme(
            primary = c(h, .85f, .60f), onPrimary = Color.White,
            primaryContainer = c(h, .18f, 1f), onPrimaryContainer = c(h, .90f, .18f),
            secondary = c(h + 30, .80f, .50f), onSecondary = Color.White,
            secondaryContainer = c(h + 30, .16f, .98f), onSecondaryContainer = c(h + 30, .90f, .16f),
            tertiary = c(h + 180, .80f, .45f), onTertiary = Color.White,
            background = c(h, .04f, .99f), onBackground = c(h, .60f, .14f),
            surface = c(h, .03f, 1f), onSurface = c(h, .60f, .14f),
            surfaceVariant = c(h, .10f, .93f), onSurfaceVariant = c(h, .35f, .35f),
            outline = c(h, .25f, .60f), outlineVariant = c(h, .15f, .85f),
            error = Color(0xFFBA1A3A), onError = Color.White,
            errorContainer = Color(0xFFFFDAD9), onErrorContainer = Color(0xFF41000B)
        )
    }
}

/** Tono (0..360) de un color ARGB guardado como acento. */
fun accentHue(argb: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv[0]
}

@Composable
fun NeptunoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentArgb: Int = 0,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        accentArgb != 0 -> accentColorScheme(accentHue(accentArgb), darkTheme)
        darkTheme -> NeptunoDarkColorScheme
        else -> NeptunoLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NeptunoTypography,
        shapes = NeptunoShapes,
        content = content
    )
}
