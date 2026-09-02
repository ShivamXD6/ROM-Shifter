package build.bytes.romshifter.ui.theme

import android.app.WallpaperManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun ROMShifterTheme(
    themeMode: Int = 0,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current

    fun getLegacySeedColor(): Color? {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val colors = wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            if (colors != null) {
                return Color(colors.primaryColor.toArgb())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    val colorScheme = when (themeMode) {
        0 -> {
            if (dynamicColor) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(
                        context
                    )
                } else {
                    val seed = getLegacySeedColor()
                    seed?.toColorScheme(systemDark)
                        ?: if (systemDark) DarkColorScheme else LightColorScheme
                }
            } else {
                if (systemDark) DarkColorScheme else LightColorScheme
            }
        }

        1 -> {
            if (dynamicColor) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicLightColorScheme(context)
                } else {
                    val seed = getLegacySeedColor()
                    seed?.toColorScheme(false) ?: LightColorScheme
                }
            } else {
                LightColorScheme
            }
        }

        2 -> {
            if (dynamicColor) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicDarkColorScheme(context)
                } else {
                    val seed = getLegacySeedColor()
                    seed?.toColorScheme(true) ?: DarkColorScheme
                }
            } else {
                DarkColorScheme
            }
        }

        3, 4 -> {
            if (dynamicColor) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicDarkColorScheme(context).copy(
                        background = Color(0xFF000000),
                        surface = Color(0xFF000000),
                        surfaceVariant = Color(0xFF000000),
                        surfaceContainerLowest = Color(0xFF000000),
                        surfaceContainerLow = Color(0xFF000000),
                        surfaceContainer = Color(0xFF000000),
                        surfaceContainerHigh = Color(0xFF000000),
                        surfaceContainerHighest = Color(0xFF000000)
                    )
                } else {
                    val seed = getLegacySeedColor()
                    seed?.toColorScheme(isDark = true, isAmoled = true) ?: AmoledAccentColorScheme
                }
            } else {
                AmoledAccentColorScheme
            }
        }

        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ExpressiveShapes,
        content = content
    )
}