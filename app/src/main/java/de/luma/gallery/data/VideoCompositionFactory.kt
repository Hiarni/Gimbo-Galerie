package de.luma.gallery.data

import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import de.luma.gallery.model.*


@androidx.annotation.OptIn(UnstableApi::class)
object VideoCompositionFactory {
    fun outputSize(recipe:VideoRecipe,info:SourceInfo):SourceInfo {
        val crop=recipe.cropBounds
        val croppedWidth=info.width*(crop?.width ?: 1f)
        val croppedHeight=info.height*(crop?.height ?: 1f)
        val ratio=recipe.aspectRatio?.toDouble() ?: if(recipe.quarterTurns%2==0) croppedWidth.toDouble()/croppedHeight else croppedHeight.toDouble()/croppedWidth
        val height=recipe.outputHeight
        val width=(height*ratio/2).toInt().coerceIn(1,4320)*2
        return SourceInfo(width,height)
    }
    fun previewSize(recipe: VideoRecipe, info: SourceInfo): SourceInfo {
        val size = outputSize(recipe, info)
        val scale = minOf(1.0, 1280.0 / maxOf(size.width, size.height))
        fun dimension(value: Int) = (value * scale / 2).toInt().coerceAtLeast(1) * 2
        return SourceInfo(dimension(size.width), dimension(size.height))
    }
    fun create(source: GalleryMedia, recipe: VideoRecipe, info: SourceInfo, context: android.content.Context? = null, preview: Boolean = false): Composition {
        require(recipe.isValid(source.durationMs))
        require(info.width > 0 && info.height > 0)
        val size=if (preview) previewSize(recipe, info) else outputSize(recipe,info)
        val height=size.height
        val width=size.width
        val timeline = recipe.timeline(source)
        val clips = timeline.map { timed ->
            val clip = timed.clip
            val media = MediaItem.Builder().setUri(clip.source.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build()).build()
            val builder = EditedMediaItem.Builder(media).setDurationUs(clip.source.durationMs * 1000)
                .setRemoveAudio(clip.mute).setEffects(VideoEffects.effects(clip, recipe, width, height, timed.startMs, timed.fadeInMs, timed.fadeOutMs))
            if (clip.speed != 1f) builder.setSpeed(object : SpeedProvider {
                override fun getSpeed(timeUs: Long) = clip.speed
                override fun getNextSpeedChangeTimeUs(timeUs: Long) = C.TIME_UNSET
            })
            builder.build()
        }
        val tracks = if (clips.any { !it.removeAudio }) setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO) else setOf(C.TRACK_TYPE_VIDEO)
        val total = recipe.outputDuration(source)
        val sequences = if (recipe.crossfadeMs == 0L || clips.size == 1) mutableListOf(EditedMediaItemSequence.Builder(tracks).addItems(clips).build()) else {
            (0..1).map { channel ->
                val sequence = EditedMediaItemSequence.Builder(tracks)
                var cursor = 0L
                timeline.forEachIndexed { index, timed -> if (index % 2 == channel) {
                    if (timed.startMs > cursor) sequence.addGap((timed.startMs - cursor) * 1000)
                    sequence.addItem(clips[index]); cursor = timed.endMs
                } }
                if (cursor < total) sequence.addGap((total-cursor)*1000)
                sequence.build()
            }.toMutableList()
        }
        recipe.music?.let { music ->

            val duration = minOf(music.durationMs, recipe.outputDuration(source))
            val media = MediaItem.Builder().setUri(music.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setEndPositionMs(duration).build()).build()
            val audio = EditedMediaItem.Builder(media).setDurationUs(music.durationMs * 1000).setRemoveVideo(true)
                .setEffects(Effects(listOf(VolumeAudioProcessor(music.volume, duration)), emptyList())).build()
            sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO)).addItems(audio).setIsLooping(true).build()
        }
        for (track in recipe.audioTracks) {
            val length = minOf(track.lengthMs, total-track.timelineStartMs)
            if (length <= 0) continue
            val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            if (track.timelineStartMs > 0) sequence.addGap(track.timelineStartMs * 1000)
            var cursor = 0L
            val chunk = track.sourceEndMs - track.sourceStartMs
            require(!track.loop || (length + chunk - 1) / chunk <= 2048)
            while (cursor < length) {
                val duration = minOf(chunk, length-cursor)
                val media = MediaItem.Builder().setUri(track.music.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(track.sourceStartMs).setEndPositionMs(track.sourceStartMs+duration).build()).build()
                val start = cursor
                val audio = EditedMediaItem.Builder(media).setDurationUs(track.music.durationMs*1000).setRemoveVideo(true)
                    .setEffects(Effects(listOf(VolumeAudioProcessor(track.music.volume, duration, envelope = { t ->
                        val at = start+t
                        minOf(1f, if(track.fadeInMs==0L) 1f else at.toFloat()/track.fadeInMs,
                            if(track.fadeOutMs==0L) 1f else (length-at).coerceAtLeast(0).toFloat()/track.fadeOutMs)
                    })), emptyList())).build()
                sequence.addItem(audio); cursor += duration
                if (!track.loop) break
            }
            if (track.timelineStartMs+cursor < total) sequence.addGap((total-track.timelineStartMs-cursor)*1000)
            sequences += sequence.build()
        }
        val builder = Composition.Builder(sequences)
        if (recipe.crossfadeMs > 0 && clips.size > 1) builder.setVideoCompositorSettings(CrossfadeCompositor(timeline, width, height))
        val master = if (recipe.keyframes.isEmpty()) emptyList() else {
            val envelope = VideoVolumeEnvelope(recipe.keyframes)
            listOf(VolumeAudioProcessor(1f, total, envelope = envelope::value))
        }
        builder.setEffects(Effects(master, VideoTimelineEffects.create(recipe, width, height, context)))
        return builder.setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
    }
}
