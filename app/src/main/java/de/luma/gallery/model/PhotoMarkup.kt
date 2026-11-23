package de.luma.gallery.model

import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage

const val PHOTO_MARKUP_LIMIT = 32
const val PHOTO_MARKUP_POINTS = 256

enum class MarkupKind(val labelRes: Int) {
    BRUSH(R.string.markup_brush), INPAINT(R.string.inpaint_brush), CLONE(R.string.clone_brush), HEAL(R.string.heal_brush), RECTANGLE(R.string.markup_rectangle), ELLIPSE(R.string.markup_ellipse),
    ARROW(R.string.markup_arrow), PIXELATE(R.string.markup_pixelate), COVER(R.string.markup_cover)
}

data class MarkupPoint(val x: Float, val y: Float) {
    init { requireMessage(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f, R.string.invalid_markup) }
}


data class PhotoMarkup(val id: String, val kind: MarkupKind,
    val points: List<MarkupPoint> = listOf(MarkupPoint(.3f, .3f), MarkupPoint(.7f, .7f)),
    val color: Long = 0xFFFF5252L, val width: Float = .015f, val filled: Boolean = false,
    val blockSize: Float = .04f, val visible: Boolean = true, val opacity: Float = 1f, val sample: MarkupPoint = MarkupPoint(.5f, .5f)) {
    init {
        requireMessage(opacity.isFinite() && opacity in 0f..1f, R.string.invalid_layers)
        requireMessage(id.isNotBlank() && id.length <= 80 && points.isNotEmpty() && points.size <= PHOTO_MARKUP_POINTS,
            R.string.invalid_markup)
        requireMessage(kind in listOf(MarkupKind.BRUSH, MarkupKind.CLONE, MarkupKind.HEAL, MarkupKind.INPAINT) || points.size == 2, R.string.invalid_markup)
        requireMessage(color in 0xFF000000L..0xFFFFFFFFL && width.isFinite() && width in .002f.. .1f &&
            blockSize.isFinite() && blockSize in .01f.. .2f, R.string.invalid_markup)
    }
    val stroke get() = kind in listOf(MarkupKind.BRUSH, MarkupKind.CLONE, MarkupKind.HEAL, MarkupKind.INPAINT)
    val left get() = points.minOf { it.x }
    val top get() = points.minOf { it.y }
    val right get() = points.maxOf { it.x }
    val bottom get() = points.maxOf { it.y }
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2

    fun moved(dx: Float, dy: Float): PhotoMarkup {
        val x = dx.coerceIn(-left, 1f - right)
        val y = dy.coerceIn(-top, 1f - bottom)
        return copy(points = points.map { MarkupPoint((it.x + x).coerceIn(0f, 1f), (it.y + y).coerceIn(0f, 1f)) })
    }
    fun resized(width: Float, height: Float): PhotoMarkup {
        requireMessage(!stroke && width.isFinite() && height.isFinite(), R.string.invalid_markup)
        val w = width.coerceIn(.01f, 1f); val h = height.coerceIn(.01f, 1f)
        val x = (centerX - w / 2).coerceIn(0f, 1f - w)
        val y = (centerY - h / 2).coerceIn(0f, 1f - h)
        return copy(points = listOf(MarkupPoint(x, y), MarkupPoint((x + w).coerceAtMost(1f), (y + h).coerceAtMost(1f))))
    }
}

fun PhotoRecipe.withMarkup(markup: PhotoMarkup) = copy(markups = markups.map { if (it.id == markup.id) markup else it })
