package build.bytes.romshifter.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun ROMShifterTheme(
    themeMode: String = "SYSTEM",
    accentColor: String = "BLUE",
    customHex: String = "#0B57D0",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val isAmoled = themeMode.startsWith("AMOLED")
    val useDarkTheme = themeMode.contains("DARK") || isAmoled || (themeMode == "SYSTEM" && systemDark)

    val useDynamicColor = when (themeMode) {
        "LIGHT_DYNAMIC", "DARK_DYNAMIC", "AMOLED_DYNAMIC", "SYSTEM" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        else -> false
    }

    val seed = try { AccentSeed.valueOf(accentColor) } catch (_: Exception) { AccentSeed.BLUE }

    var colorScheme = when {
        useDynamicColor && useDarkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && !useDarkTheme -> dynamicLightColorScheme(context)
        seed == AccentSeed.CUSTOM -> generateCustomScheme(customHex, useDarkTheme)
        else -> getAccentColorScheme(seed, useDarkTheme)
    }

    // MD3 Archive Tune Pitch Black Override
    if (isAmoled && useDarkTheme) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF141414),
            surfaceContainer = Color(0xFF0A0A0A),
            surfaceContainerLow = Color.Black,
            surfaceContainerHigh = Color(0xFF1A1A1A)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}