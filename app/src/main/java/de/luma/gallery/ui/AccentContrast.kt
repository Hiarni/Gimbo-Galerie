package de.luma.gallery.ui

import androidx.core.graphics.ColorUtils


internal fun readableAccent(accent: Int, background: Int): Int {
    if (ColorUtils.calculateContrast(accent, background) >= 4.5) return accent
    val target = if (ColorUtils.calculateLuminance(background) < .5) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    for (step in 1..100) {
        val value = ColorUtils.blendARGB(accent, target, step / 100f) or 0xFF000000.toInt()
        if (ColorUtils.calculateContrast(value, background) >= 4.5) return value
    }
    return target
}
internal fun textOnAccent(accent: Int): Int = if (ColorUtils.calculateContrast(android.graphics.Color.BLACK, accent) >=
    ColorUtils.calculateContrast(android.graphics.Color.WHITE, accent)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
