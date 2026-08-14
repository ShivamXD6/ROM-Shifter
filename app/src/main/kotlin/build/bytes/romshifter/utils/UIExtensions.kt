package build.bytes.romshifter.utils

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue


fun getAvatarColor(name: String): Color {
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
        Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
        Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
        Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFF8A65)
    )
    return colors[name.hashCode().absoluteValue % colors.size]
}