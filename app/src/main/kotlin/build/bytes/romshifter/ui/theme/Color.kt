package build.bytes.romshifter.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val PrimaryBlueLight = Color(0xFF3B82F6)
val PrimaryBlueDark = Color(0xFF60A5FA)

val LightColorScheme = lightColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),

    background = Color(0xFFFCFCFC),
    surface = Color(0xFFFCFCFC),
    surfaceVariant = Color(0xFFE5E7EB),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F8FD),
    surfaceContainer = Color(0xFFF0F4FC),
    surfaceContainerHigh = Color(0xFFE9EEF8),
    surfaceContainerHighest = Color(0xFFE2E8F4)
)

val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDBEAFE),

    background = Color(0xFF0F1115),
    surface = Color(0xFF0F1115),
    surfaceVariant = Color(0xFF44474E),

    surfaceContainerLowest = Color(0xFF14171C),
    surfaceContainerLow = Color(0xFF181B22),
    surfaceContainer = Color(0xFF1E222A),
    surfaceContainerHigh = Color(0xFF232832),
    surfaceContainerHighest = Color(0xFF292E39)
)

val AmoledAccentColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color(0xFF0F172A),
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