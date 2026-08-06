package build.bytes.romshifter.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF001D35),
    secondaryContainer = Color(0xFFD3E3FD),
    onSecondaryContainer = Color(0xFF001D35)
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondaryContainer = Color(0xFF004A77),
    onSecondaryContainer = Color(0xFFD3E3FD),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E)
)