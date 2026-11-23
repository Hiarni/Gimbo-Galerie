package de.luma.gallery.model

import de.luma.gallery.localization.*
import de.luma.gallery.R
data class EditorProject(
    val id: String,
    val name: String,
    val source: GalleryMedia,
    val photo: PhotoRecipe?,
    val video: VideoRecipe?,
    val createdAt: Long,
    val updatedAt: Long,
)


data class EditorSession(
    val token: String,
    val source: GalleryMedia,
    val projectId: String? = null,
    val photo: PhotoRecipe? = null,
    val video: VideoRecipe? = null,
)

fun projectName(value: String): String = value.trim().also {
    requireMessage(it.isNotEmpty() && it.length <= 80 && it.none { char -> char.isISOControl() }, R.string.invalid_project_name)
}
