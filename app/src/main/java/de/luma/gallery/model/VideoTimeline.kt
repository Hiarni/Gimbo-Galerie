package de.luma.gallery.model

import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage


data class VideoKeyframe(val timeMs: Long, val zoom: Float = 1f, val x: Float = .5f, val y: Float = .5f,
    val brightness: Float = 0f, val volume: Float = 1f) {
    init { requireMessage(timeMs in 0..86_400_000 && listOf(zoom,x,y,brightness,volume).all { it.isFinite() } && zoom in 1f..4f && x in 0f..1f && y in 0f..1f && brightness in -1f..1f && volume in 0f..2f, R.string.invalid_video_clip) }
}
data class VideoAudioTrack(val id: String, val music: VideoMusic, val timelineStartMs: Long = 0,
    val sourceStartMs: Long = 0, val sourceEndMs: Long = music.durationMs, val lengthMs: Long = music.durationMs,
    val loop: Boolean = false, val fadeInMs: Long = 0, val fadeOutMs: Long = 0) {
    init { requireMessage(id.length in 1..80 && timelineStartMs in 0..86_400_000 && sourceStartMs >= 0 && sourceEndMs <= music.durationMs && sourceEndMs > sourceStartMs &&
        lengthMs in 1..86_400_000 && fadeInMs in 0..10_000 && fadeOutMs in 0..10_000 && (if(loop) (lengthMs+sourceEndMs-sourceStartMs-1)/(sourceEndMs-sourceStartMs)<=2048 else lengthMs <= sourceEndMs-sourceStartMs), R.string.invalid_video_clip) }
}
data class VideoOverlay(val id: String, val text: PhotoText? = null, val imageUri: String? = null,
    val startMs: Long = 0, val endMs: Long = 86_400_000, val x: Float = .5f, val y: Float = .5f,
    val scale: Float = .25f, val opacity: Float = 1f, val rotation: Float = 0f) {
    init { requireMessage(id.length in 1..80 && (text != null) != (imageUri != null) && (imageUri == null || imageUri.length <= 8192 && imageUri.startsWith("content://")) &&
        startMs in 0..86_400_000 && endMs in (startMs+1)..86_400_000 && listOf(x,y,scale,opacity,rotation).all { it.isFinite() } && x in 0f..1f && y in 0f..1f && scale in .025f..2f && opacity in 0f..1f && rotation in -180f..180f, R.string.invalid_video_clip) }
}
data class TimedVideoClip(val clip: VideoClip, val startMs: Long, val fadeInMs: Long, val fadeOutMs: Long) {
    val endMs get() = startMs + clip.outputDurationMs
}
fun VideoRecipe.timeline(source: GalleryMedia): List<TimedVideoClip> {
    val clips = orderedClips(source)
    val overlaps = clips.zipWithNext { a,b -> minOf(crossfadeMs, a.outputDurationMs/2, b.outputDurationMs/2) }
    var start=0L
    return clips.mapIndexed { index, clip ->
        val before = overlaps.getOrElse(index-1) { 0L }; val after = overlaps.getOrElse(index) { 0L }
        val timed = TimedVideoClip(clip,start,before,after); start=timed.endMs-after; timed
    }
}
fun VideoRecipe.frameAt(timeMs: Long): VideoKeyframe {
    if(keyframes.isEmpty()) return VideoKeyframe(timeMs.coerceIn(0,86_400_000))
    val ordered=keyframes.sortedBy { it.timeMs }
    if(timeMs<=ordered.first().timeMs) return ordered.first().copy(timeMs=timeMs.coerceAtLeast(0))
    if(timeMs>=ordered.last().timeMs) return ordered.last().copy(timeMs=timeMs.coerceAtMost(86_400_000))
    val next=ordered.indexOfFirst { it.timeMs>timeMs }; val a=ordered[next-1]; val b=ordered[next]
    val f=(timeMs-a.timeMs).toFloat()/(b.timeMs-a.timeMs)
    fun mix(a: Float,b: Float)=a+(b-a)*f
    return VideoKeyframe(timeMs,mix(a.zoom,b.zoom),mix(a.x,b.x),mix(a.y,b.y),mix(a.brightness,b.brightness),mix(a.volume,b.volume))
}
