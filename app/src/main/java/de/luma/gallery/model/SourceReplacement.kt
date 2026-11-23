package de.luma.gallery.model


data class SourceInfo(val width: Int, val height: Int)
data class SourceReplacement(val project: EditorProject, val source: GalleryMedia,
    val previousInfo: SourceInfo?, val replacementInfo: SourceInfo)
