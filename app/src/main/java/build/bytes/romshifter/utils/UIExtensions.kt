package build.bytes.romshifter.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap
import kotlin.math.absoluteValue

fun Drawable.toSafeBitmap(): Bitmap {
    if (this is BitmapDrawable && this.bitmap != null) return this.bitmap

    val targetWidth = if (intrinsicWidth > 0) intrinsicWidth else 120
    val targetHeight = if (intrinsicHeight > 0) intrinsicHeight else 120

    val bitmap = createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

fun getAvatarColor(name: String): Color {
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
        Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
        Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
        Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFF8A65)
    )
    return colors[name.hashCode().absoluteValue % colors.size]
}