package de.luma.gallery.model

import de.luma.gallery.localization.*
import de.luma.gallery.R
const val PHOTO_TEXT_LIMIT = 12
const val PHOTO_TEXT_LENGTH = 120


data class PhotoText(val id: String, val text: String, val x: Float = .5f, val y: Float = .5f,
    val size: Float = .06f, val color: Long = 0xFFFFFFFF, val bold: Boolean = false, val background: Boolean = true, val visible: Boolean = true, val opacity: Float = 1f) {
    init {
        requireMessage(opacity.isFinite() && opacity in 0f..1f, R.string.invalid_layers)
        requireMessage(id.isNotBlank() && id.length <= 80, R.string.invalid_text_object)
        requireMessage(text.isNotBlank() && text.length <= PHOTO_TEXT_LENGTH && text.none { it.isISOControl() && it != '\n' } && text.count { it == '\n' } <= 3, R.string.invalid_text_content)
        requireMessage(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f && size.isFinite() && size in .025f.. .2f, R.string.invalid_text_geometry)
        requireMessage(color in 0xFF000000L..0xFFFFFFFFL, R.string.invalid_text_color)
    }
}

fun PhotoRecipe.withText(text: PhotoText) = copy(texts = texts.map { if (it.id == text.id) text else it })
