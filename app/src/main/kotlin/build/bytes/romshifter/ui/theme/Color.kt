package build.bytes.romshifter.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

val PrimaryBlueLight = Color(0xFF2D5BD0)
val PrimaryBlueDark = Color(0xFF8EABFF)

fun Color.toColorScheme(isDark: Boolean, isAmoled: Boolean = false): ColorScheme {
    val argb = this.toArgb()
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)

    if (hsl[1] < 0.4f) hsl[1] = 0.5f

    if (isDark) {
        hsl[2] = 0.75f
    } else {
        hsl[2] = 0.40f
    }

    val primaryColorArgb = ColorUtils.HSLToColor(hsl)
    val primary = Color(primaryColorArgb)

    val onPrimary =
        if (ColorUtils.calculateLuminance(primaryColorArgb) > 0.5) Color.Black else Color.White

    val containerHsl = hsl.copyOf()
    if (isDark) {
        containerHsl[2] = 0.15f
    } else {
        containerHsl[2] = 0.90f
    }
    val containerColor = Color(ColorUtils.HSLToColor(containerHsl))

    val onContainerHsl = hsl.copyOf()
    if (isDark) {
        onContainerHsl[2] = 0.85f
    } else {
        onContainerHsl[2] = 0.15f
    }
    val onContainerColor = Color(ColorUtils.HSLToColor(onContainerHsl))

    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = containerColor,
            onPrimaryContainer = onContainerColor,
            secondaryContainer = containerColor,
            onSecondaryContainer = onContainerColor,

            background = if (isAmoled) Color.Black else Color(0xFF0B0D11),
            surface = if (isAmoled) Color.Black else Color(0xFF0B0D11),
            surfaceVariant = if (isAmoled) Color.Black else Color(0xFF1A1D26),

            surfaceContainerLowest = if (isAmoled) Color.Black else Color(0xFF0F1218),
            surfaceContainerLow = if (isAmoled) Color.Black else Color(0xFF14171E),
            surfaceContainer = if (isAmoled) Color.Black else Color(0xFF1A1D26),
            surfaceContainerHigh = if (isAmoled) Color.Black else Color(0xFF21252F),
            surfaceContainerHighest = if (isAmoled) Color.Black else Color(0xFF282D38)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = containerColor,
            onPrimaryContainer = onContainerColor,
            secondaryContainer = containerColor,
            onSecondaryContainer = onContainerColor,

            background = Color(0xFFFCFCFC),
            surface = Color(0xFFFCFCFC),
            surfaceVariant = Color(0xFFE5E7EB),

            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF8FAFF),
            surfaceContainer = Color(0xFFF1F5FF),
            surfaceContainerHigh = Color(0xFFE9F0FF),
            surfaceContainerHighest = Color(0xFFE2EAFF)
        )
    }
}

val LightColorScheme = lightColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),

    background = Color(0xFFFCFCFC),
    surface = Color(0xFFFCFCFC),
    surfaceVariant = Color(0xFFE5E7EB),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFF),
    surfaceContainer = Color(0xFFF1F5FF),
    surfaceContainerHigh = Color(0xFFE9F0FF),
    surfaceContainerHighest = Color(0xFFE2EAFF)
)

val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color(0xFF00144B),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDBEAFE),

    background = Color(0xFF0B0D11),
    surface = Color(0xFF0B0D11),
    surfaceVariant = Color(0xFF44474E),

    surfaceContainerLowest = Color(0xFF0F1218),
    surfaceContainerLow = Color(0xFF14171E),
    surfaceContainer = Color(0xFF1A1D26),
    surfaceContainerHigh = Color(0xFF21252F),
    surfaceContainerHighest = Color(0xFF282D38)
)

val AmoledAccentColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color(0xFF00144B),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDBEAFE),

    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF000000),

    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF000000),
    surfaceContainer = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF000000),
    surfaceContainerHighest = Color(0xFF000000)
)