package de.luma.gallery.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoCompositionTest {
    private val source = GalleryMedia("v", Uri.parse("content://media/external/video/media/1"), "A.mp4", "Camera", 0, true, 10_000)
    @Test fun completeTimelineRoundTripsWithOrderTimingAndOldProjectsRemainReadable() {
        val extra = VideoClip("extra", source.copy(id = "second", uri = Uri.parse("content://test/second")), 1000, 5000, speed = 2f, volume = .3f)
        val recipe = VideoRecipe(500, 3500, speed = .5f, brightness = .2f, contrast = .3f, saturation = -.2f,
            caption = PhotoText("caption", "Two\nlines"), extraClips = listOf(extra), clipOrder = listOf("extra", "primary"),
            music = VideoMusic("content://test/audio", "Song", 4000, .4f), fadeInMs = 1000, fadeOutMs = 2000, outputHeight = 720, bitrate = 3_000_000, transitionMs = 800)
        assertEquals(recipe, RecipeJson.readVideo(RecipeJson.video(recipe)))
        assertEquals(listOf("extra", "primary"), recipe.orderedClips(source).map { it.id })
        assertEquals(8000L, recipe.outputDuration(source))
        assertTrue(recipe.isValid(source.durationMs))
        assertFalse(recipe.copy(extraClips = listOf(extra.copy(endMs = 20_000))).isValid(source.durationMs))
        assertEquals(VideoRecipe(0, 2000, true), RecipeJson.readVideo("""{"version":1,"start":0,"end":2000,"mute":true}"""))
        assertTrue(runCatching { RecipeJson.readVideo("""{"version":3}""") }.isFailure)
    }
    @Test fun pcmVolumeAndFadesPreserveStereoTimingAndClampAmplifiedFloatSamples() {
        val processor = VolumeAudioProcessor(.5f, 2000, 1000, 1000)
        processor.configure(AudioProcessor.AudioFormat(1000, 2, C.ENCODING_PCM_16BIT)); processor.flush()
        val input = ByteBuffer.allocateDirect(8000).order(ByteOrder.nativeOrder())
        repeat(4000) { input.putShort(10_000) }; input.flip(); processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(0, output.getShort(0).toInt())
        assertEquals(2500, output.getShort(500 * 4).toInt())
        assertEquals(output.getShort(500 * 4), output.getShort(500 * 4 + 2))
        assertEquals(5000, output.getShort(1000 * 4).toInt())
        assertEquals(2500, output.getShort(1500 * 4).toInt())
        val float = VolumeAudioProcessor(2f, 1000)
        float.configure(AudioProcessor.AudioFormat(1000, 1, C.ENCODING_PCM_FLOAT)); float.flush()
        val samples = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).putFloat(.8f).putFloat(-.8f).apply { flip() }
        float.queueInput(samples)
        val actual = float.output.order(ByteOrder.nativeOrder())
        assertEquals(1f, actual.float, 0f); assertEquals(-1f, actual.float, 0f)
    }
    @Test fun sharedCompositionKeepsClippedDurationsAndBoundsMusicToTimeline() {
        val extra = VideoClip("second", source.copy(uri = Uri.parse("content://test/second")), 1000, 5000, speed = 2f)
        val recipe = VideoRecipe(0, 3000, extraClips = listOf(extra),
            music = VideoMusic("content://test/song", "Long song", 60_000), transitionMs = 500)
        val composition = VideoCompositionFactory.create(source, recipe, SourceInfo(1920, 1080))
        assertEquals(2, composition.sequences.size)
        val clips = composition.sequences[0].editedMediaItems
        assertEquals(2, clips.size)
        assertEquals(2f, clips[1].speedProvider.getSpeed(0), 0f)
        assertEquals(10_000_000L, clips[0].durationUs)
        assertEquals(3000L, clips[0].mediaItem.clippingConfiguration.endPositionMs)
        assertEquals(1000L, clips[1].mediaItem.clippingConfiguration.startPositionMs)
        assertTrue(clips.all { clip -> clip.effects.videoEffects.any { it is VideoFade } })
        val audio = composition.sequences[1]
        assertTrue(audio.isLooping)
        assertEquals(setOf(C.TRACK_TYPE_AUDIO), audio.trackTypes)
        assertTrue(audio.editedMediaItems.single().removeVideo)
        assertEquals(5000L, audio.editedMediaItems.single().mediaItem.clippingConfiguration.endPositionMs)
        assertEquals(60_000_000L, audio.editedMediaItems.single().durationUs)
    }
    @Test fun transitionUsesOutputTimeAndDoesNotConsumeTheMiddleOfShortClips() {
        val fade = VideoFade(1000, 2000)
        assertEquals(0f, fade.gain(0), 0f)
        assertEquals(.5f, fade.gain(250_000), .001f)
        assertEquals(1f, fade.gain(500_000), 0f)
        assertEquals(.5f, fade.gain(750_000), .001f)
        assertEquals(0f, fade.gain(1_000_000), 0f)
        val matrix = fade.getMatrix(250_000, false)
        assertEquals(.5f, matrix[0], 0f); assertEquals(.5f, matrix[5], 0f)
        assertEquals(.5f, matrix[10], 0f); assertEquals(1f, matrix[15], 0f)
        assertEquals(1f, VideoFade(1000, 0).gain(0), 0f)
        assertEquals(80_000_012L, VideoClip("long", source.copy(durationMs = 60_000_000), endMs = 20_000_003, speed = .25f).outputDurationMs)
        assertFalse(VideoRecipe(0, 100_000_000).isValid(100_000_000))
        assertTrue(runCatching { VideoMusic("content://test/music", "overflow", Long.MAX_VALUE) }.isFailure)
    }
    @Test fun unknownVideoDurationCanBeDisplayedWithoutMakingAnExportableTimeline() {
        val unknown = source.copy(durationMs = 0)
        val recipe = VideoRecipe(0, 0)
        val clips = recipe.orderedClips(unknown)
        assertEquals(1, clips.size)
        assertEquals(0L, clips.single().outputDurationMs)
        assertFalse(clips.single().valid())
        assertFalse(recipe.isValid(0))
        assertFalse(recipe.copy(endMs = 1000).isValid(Long.MAX_VALUE))
    }
    @Test fun exportJournalPreservesBatchAndResumePositionWithoutFutureVersionFallback() {
        val photo = source.copy(isVideo = false, durationMs = 0)
        val request = ExportRequest(items = listOf(photo, photo.copy(id = "two")), photo = PhotoRecipe(exposure = .5f), completed = 1)
        assertEquals(request, ExportRequest.read(request.json()))
        assertTrue(runCatching { ExportRequest.read(request.json().replace("\"version\":1", "\"version\":2")) }.isFailure)
        assertTrue(runCatching { request.copy(completed = 3) }.isFailure)
        assertTrue(runCatching { ExportRequest(items = listOf(source), photo = PhotoRecipe()) }.isFailure)
    }
}
