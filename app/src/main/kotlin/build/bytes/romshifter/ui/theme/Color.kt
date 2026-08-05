package build.bytes.romshifter.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class AccentSeed(val displayName: String, val primaryColor: Color) {
    BLUE("Ocean Blue", Color(0xFF0B57D0)),
    RED("Crimson Red", Color(0xFFBA1A1A)),
    GREEN("Emerald Green", Color(0xFF006A5F)),
    YELLOW("Sunflower Yellow", Color(0xFF825500)),
    PURPLE("Deep Purple", Color(0xFF6750A4)),
    ORANGE("Vibrant Orange", Color(0xFF8A5100)),
    PINK("Rose Pink", Color(0xFFAD3270)),
    TEAL("Teal", Color(0xFF006D77)),
    CYAN("Nordic Cyan", Color(0xFF006688)),
    MONOCHROME("Monochrome", Color(0xFF5F5F5F)),
    CUSTOM("Custom Hex", Color.Transparent)
}

fun generateCustomScheme(hexString: String, isDark: Boolean): ColorScheme {
    val color = try {
        Color(android.graphics.Color.parseColor(if (!hexString.startsWith("#")) "#$hexString" else hexString))
    } catch (e: Exception) {
        Color(0xFF0B57D0)
    }

    return if (isDark) {
        darkColorScheme(
            primary = color,
            onPrimary = Color.Black,
            primaryContainer = color.copy(alpha = 0.3f),
            onPrimaryContainer = color.copy(alpha = 0.9f),
            secondaryContainer = color.copy(alpha = 0.2f),
            onSecondaryContainer = color.copy(alpha = 0.8f)
        )
    } else {
        lightColorScheme(
            primary = color,
            onPrimary = Color.White,
            primaryContainer = color.copy(alpha = 0.2f),
            onPrimaryContainer = color,
            secondaryContainer = color.copy(alpha = 0.15f),
            onSecondaryContainer = color
        )
    }
}

fun getAccentColorScheme(seed: AccentSeed, isDark: Boolean): ColorScheme {
    return when (seed) {
        AccentSeed.BLUE -> if (isDark) darkColorScheme(
            primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF003062),
            primaryContainer = Color(0xFF004A77), onPrimaryContainer = Color(0xFFD3E3FD),
            secondaryContainer = Color(0xFF004A77), onSecondaryContainer = Color(0xFFD3E3FD)
        ) else lightColorScheme(
            primary = Color(0xFF0B57D0), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD3E3FD), onPrimaryContainer = Color(0xFF001D35),
            secondaryContainer = Color(0xFFD3E3FD), onSecondaryContainer = Color(0xFF001D35)
        )
        AccentSeed.RED -> if (isDark) darkColorScheme(
            primary = Color(0xFFFFB4AB), onPrimary = Color(0xFF690005),
            primaryContainer = Color(0xFF93000A), onPrimaryContainer = Color(0xFFFFDAD6),
            secondaryContainer = Color(0xFF93000A), onSecondaryContainer = Color(0xFFFFDAD6)
        ) else lightColorScheme(
            primary = Color(0xFFBA1A1A), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDAD6), onPrimaryContainer = Color(0xFF410002),
            secondaryContainer = Color(0xFFFFDAD6), onSecondaryContainer = Color(0xFF410002)
        )
        AccentSeed.GREEN -> if (isDark) darkColorScheme(
            primary = Color(0xFF80D5C4), onPrimary = Color(0xFF003731),
            primaryContainer = Color(0xFF005047), onPrimaryContainer = Color(0xFF9CF1E0),
            secondaryContainer = Color(0xFF005047), onSecondaryContainer = Color(0xFF9CF1E0)
        ) else lightColorScheme(
            primary = Color(0xFF006A5F), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF72F8E5), onPrimaryContainer = Color(0xFF00201C),
            secondaryContainer = Color(0xFFCCF8F1), onSecondaryContainer = Color(0xFF00201C)
        )
        AccentSeed.YELLOW -> if (isDark) darkColorScheme(
            primary = Color(0xFFFFB951), onPrimary = Color(0xFF452B00),
            primaryContainer = Color(0xFF633F00), onPrimaryContainer = Color(0xFFFFDDB3),
            secondaryContainer = Color(0xFF633F00), onSecondaryContainer = Color(0xFFFFDDB3)
        ) else lightColorScheme(
            primary = Color(0xFF825500), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDDB3), onPrimaryContainer = Color(0xFF2A1800),
            secondaryContainer = Color(0xFFFFDDB3), onSecondaryContainer = Color(0xFF2A1800)
        )
        AccentSeed.PURPLE -> if (isDark) darkColorScheme(
            primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
            secondaryContainer = Color(0xFF4F378B), onSecondaryContainer = Color(0xFFEADDFF)
        ) else lightColorScheme(
            primary = Color(0xFF6750A4), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
            secondaryContainer = Color(0xFFEADDFF), onSecondaryContainer = Color(0xFF21005D)
        )
        AccentSeed.ORANGE -> if (isDark) darkColorScheme(
            primary = Color(0xFFFFB870), onPrimary = Color(0xFF4A2800),
            primaryContainer = Color(0xFF693F00), onPrimaryContainer = Color(0xFFFFDCC2),
            secondaryContainer = Color(0xFF693F00), onSecondaryContainer = Color(0xFFFFDCC2)
        ) else lightColorScheme(
            primary = Color(0xFF8A5100), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDCC2), onPrimaryContainer = Color(0xFF2C1600),
            secondaryContainer = Color(0xFFFFDCC2), onSecondaryContainer = Color(0xFF2C1600)
        )
        AccentSeed.PINK -> if (isDark) darkColorScheme(
            primary = Color(0xFFFFAFD1), onPrimary = Color(0xFF63003F),
            primaryContainer = Color(0xFF871457), onPrimaryContainer = Color(0xFFFFD8E6),
            secondaryContainer = Color(0xFF871457), onSecondaryContainer = Color(0xFFFFD8E6)
        ) else lightColorScheme(
            primary = Color(0xFFAD3270), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFD8E6), onPrimaryContainer = Color(0xFF3D0024),
            secondaryContainer = Color(0xFFFFD8E6), onSecondaryContainer = Color(0xFF3D0024)
        )
        AccentSeed.TEAL -> if (isDark) darkColorScheme(
            primary = Color(0xFF4FD8EB), onPrimary = Color(0xFF00363D),
            primaryContainer = Color(0xFF004F56), onPrimaryContainer = Color(0xFF83F3FF),
            secondaryContainer = Color(0xFF004F56), onSecondaryContainer = Color(0xFF83F3FF)
        ) else lightColorScheme(
            primary = Color(0xFF006D77), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF83F3FF), onPrimaryContainer = Color(0xFF002023),
            secondaryContainer = Color(0xFF83F3FF), onSecondaryContainer = Color(0xFF002023)
        )
        AccentSeed.CYAN -> if (isDark) darkColorScheme(
            primary = Color(0xFF7CD2F6), onPrimary = Color(0xFF003547),
            primaryContainer = Color(0xFF004D65), onPrimaryContainer = Color(0xFFC2E8FF),
            secondaryContainer = Color(0xFF004D65), onSecondaryContainer = Color(0xFFC2E8FF)
        ) else lightColorScheme(
            primary = Color(0xFF006688), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFC2E8FF), onPrimaryContainer = Color(0xFF001E2B),
            secondaryContainer = Color(0xFFC2E8FF), onSecondaryContainer = Color(0xFF001E2B)
        )
        AccentSeed.MONOCHROME -> if (isDark) darkColorScheme(
            primary = Color(0xFFC6C6C6), onPrimary = Color(0xFF303030),
            primaryContainer = Color(0xFF474747), onPrimaryContainer = Color(0xFFE2E2E2),
            secondaryContainer = Color(0xFF474747), onSecondaryContainer = Color(0xFFE2E2E2)
        ) else lightColorScheme(
            primary = Color(0xFF5F5F5F), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE2E2E2), onPrimaryContainer = Color(0xFF1A1A1A),
            secondaryContainer = Color(0xFFE2E2E2), onSecondaryContainer = Color(0xFF1A1A1A)
        )
        AccentSeed.CUSTOM -> {
            if (isDark) darkColorScheme() else lightColorScheme()
        }
    }
}