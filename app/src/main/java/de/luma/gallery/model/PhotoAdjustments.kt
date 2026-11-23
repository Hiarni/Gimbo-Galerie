package de.luma.gallery.model

import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage

val HSL_LABELS = listOf(R.string.red, R.string.yellow, R.string.green, R.string.cyan, R.string.blue, R.string.magenta)
data class HslBand(val hue: Float = 0f, val saturation: Float = 0f, val lightness: Float = 0f) {
    init { requireMessage(hue.isFinite() && hue in -45f..45f && saturation.isFinite() && saturation in -1f..1f && lightness.isFinite() && lightness in -1f..1f, R.string.invalid_photo_details) }
    val neutral get() = hue == 0f && saturation == 0f && lightness == 0f
}
data class PhotoHsl(val bands: List<HslBand> = List(6) { HslBand() }) {
    init { requireMessage(bands.size == 6, R.string.invalid_photo_details) }
    val neutral get() = bands.all { it.neutral }
}
enum class MaskShape { ELLIPSE, RECTANGLE, FREEHAND, BRUSH, LUMINANCE, COLOR }
data class PhotoMask(val id: String, val shape: MaskShape = MaskShape.ELLIPSE, val x: Float = .5f, val y: Float = .5f,
    val width: Float = .5f, val height: Float = .5f, val feather: Float = .2f, val exposure: Float = 0f, val saturation: Float = 1f, val points: List<MarkupPoint> = emptyList(), val inverted: Boolean = false,
    val rangeStart: Float = 0f, val rangeEnd: Float = 1f, val brushSize: Float = .05f) {
    init { requireMessage(id.isNotBlank() && id.length <= 80 && listOf(x, y, width, height, feather, exposure, saturation).all { it.isFinite() } &&
        x in 0f..1f && y in 0f..1f && width in .01f..1f && height in .01f..1f && feather in 0f..1f && exposure in -2f..2f && saturation in 0f..2f, R.string.invalid_mask) }
    init { requireMessage(points.size <= 256 && rangeStart.isFinite() && rangeEnd.isFinite() && rangeStart in 0f..1f && rangeEnd in rangeStart..1f && brushSize.isFinite() && brushSize in .005f.. .25f, R.string.invalid_mask) }
    fun weight(px: Float, py: Float, pixel: Int = 0): Float {
        val result = rawWeight(px, py, pixel)
        return if (inverted) 1f - result else result
    }
    private fun rawWeight(px: Float, py: Float, pixel: Int): Float {
        if (shape == MaskShape.LUMINANCE || shape == MaskShape.COLOR) {
            val red = (pixel ushr 16 and 255) / 255f; val green = (pixel ushr 8 and 255) / 255f; val blue = (pixel and 255) / 255f
            val value = if (shape == MaskShape.LUMINANCE) .2126f * red + .7152f * green + .0722f * blue else {
                val max = maxOf(red, green, blue); val delta = max - minOf(red, green, blue)
                if (delta == 0f) return 0f
                val hue = when (max) { red -> (green - blue) / delta; green -> (blue - red) / delta + 2f; else -> (red - green) / delta + 4f }
                ((hue / 6f) + 1f) % 1f
            }
            if (value < rangeStart || value > rangeEnd) return 0f
            val edge = minOf(if (rangeStart == 0f) 1f else value - rangeStart, if (rangeEnd == 1f) 1f else rangeEnd - value)
            return if (feather == 0f) 1f else (edge / (feather * .1f)).coerceIn(0f, 1f)
        }
        if (shape == MaskShape.FREEHAND || shape == MaskShape.BRUSH) {
            if (points.isEmpty() || shape == MaskShape.FREEHAND && points.size < 3) return 0f
            val tx = (px - x) / width + .5f; val ty = (py - y) / height + .5f
            fun distance(a: MarkupPoint, b: MarkupPoint): Float {
                val dx = b.x - a.x; val dy = b.y - a.y
                val t = if (dx * dx + dy * dy == 0f) 0f else (((tx - a.x) * dx + (ty - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
                return kotlin.math.hypot(tx - a.x - t * dx, ty - a.y - t * dy)
            }
            var nearest = Float.MAX_VALUE; var inside = false
            for (i in points.indices) {
                val a = points[i]; val b = points[if (shape == MaskShape.FREEHAND) (i + 1) % points.size else minOf(i + 1, points.lastIndex)]
                nearest = minOf(nearest, distance(a, b))
                if ((a.y > ty) != (b.y > ty) && tx < (b.x - a.x) * (ty - a.y) / (b.y - a.y) + a.x) inside = !inside
            }
            if (shape == MaskShape.FREEHAND) return if (!inside) 0f else if (feather == 0f) 1f else (nearest / (feather * .05f)).coerceIn(0f, 1f)
            val radius = brushSize / 2
            return if (nearest >= radius) 0f else if (feather == 0f) 1f else ((radius - nearest) / (radius * feather)).coerceIn(0f, 1f)
        }
        val dx = kotlin.math.abs(px - x) / (width / 2); val dy = kotlin.math.abs(py - y) / (height / 2)
        val distance = if (shape == MaskShape.ELLIPSE) kotlin.math.sqrt(dx * dx + dy * dy) else maxOf(dx, dy)
        return if (distance >= 1f) 0f else if (feather == 0f || distance <= 1f - feather) 1f else ((1f - distance) / feather).coerceIn(0f, 1f)
    }
}
