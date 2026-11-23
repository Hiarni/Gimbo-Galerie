package de.luma.gallery.model

import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage

data class VideoClip(val id: String, val source: GalleryMedia, val startMs: Long = 0, val endMs: Long = source.durationMs,
    val mute: Boolean = false, val speed: Float = 1f, val volume: Float = 1f) {
    init { requireMessage(id.isNotBlank() && id.length <= 80 && source.isVideo && speed.isFinite() && speed in .25f..4f && volume.isFinite() && volume in 0f..2f, R.string.invalid_video_clip) }
    fun valid() = source.durationMs in 1..604_800_000 && startMs >= 0 && endMs <= source.durationMs && endMs - startMs >= 500
    val outputDurationMs get() = ((endMs - startMs).toDouble() / speed).toLong()
}
data class VideoMusic(val uri: String, val name: String, val durationMs: Long, val volume: Float = .5f) {
    init { requireMessage(uri.startsWith("content://") && name.length <= 4096 && durationMs in 1..604_800_000 && volume.isFinite() && volume in 0f..2f, R.string.invalid_video_clip) }
}
fun VideoRecipe.orderedClips(source: GalleryMedia): List<VideoClip> {
    val clips = (if (includeSource) listOf(VideoClip("primary", source, startMs, endMs, mute, speed, volume)) else emptyList()) + extraClips
    return clipOrder.mapNotNull { key -> clips.find { it.id == key } } + clips.filter { it.id !in clipOrder }
}
fun VideoRecipe.outputDuration(source: GalleryMedia) = timeline(source).lastOrNull()?.endMs ?: 0L
