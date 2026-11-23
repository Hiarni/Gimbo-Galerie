package de.luma.gallery.model

import de.luma.gallery.localization.*
import de.luma.gallery.R
import java.text.Normalizer
import java.util.Locale

data class VirtualAlbum(val id: String, val name: String, val mediaIds: Set<String>, val createdAt: Long, val updatedAt: Long)

fun albumName(value: String): String = value.trim().also {
    requireMessage(it.isNotEmpty() && it.length <= 80 && it.none { char -> char.isISOControl() }, R.string.invalid_album_name)
}

fun albumNameKey(value: String): String = Normalizer.normalize(albumName(value), Normalizer.Form.NFKC).lowercase(Locale.ROOT)

fun albumMedia(album: VirtualAlbum, library: List<GalleryMedia>): List<GalleryMedia> = library.filter { it.id in album.mediaIds }
