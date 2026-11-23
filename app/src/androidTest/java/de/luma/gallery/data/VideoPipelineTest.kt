package de.luma.gallery.data

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.luma.gallery.model.GalleryMedia
import de.luma.gallery.model.VideoRecipe
import de.luma.gallery.model.VideoClip
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VideoPipelineTest {
    @Test fun trimmedMutedVideoIsReadableAndOriginalRemainsUnchanged() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File.createTempFile("luma_video_test_", ".mp4", context.cacheDir)
        instrumentation.context.assets.open("fixture.mp4").use { input -> source.outputStream().use { input.copyTo(it) } }
        val bytes = source.readBytes()
        var output: File? = null
        try {
            val item = GalleryMedia("video-test", Uri.fromFile(source), "Video.mp4", "Test", 0, true, 3000)
            val result = withTimeout(60_000) { VideoExporter(context).export(item, VideoRecipe(500, 2000, mute = true)) }
            output = result
            assertTrue(result.length() > 0)
            assertArrayEquals(bytes, source.readBytes())
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(result.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Unexpected duration: $duration", duration in 1250..1750)
                assertNotEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            }
        } finally { output?.delete(); source.delete() }
        Unit
    }
    @Test fun multipleClipsWithSpeedCaptionAndTransitionExportAtExpectedDuration() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File.createTempFile("luma_video_test_", ".mp4", context.cacheDir)
        instrumentation.context.assets.open("fixture.mp4").use { input -> source.outputStream().use { input.copyTo(it) } }
        val original = source.readBytes(); var output: File? = null
        try {
            val item = GalleryMedia("video-test", Uri.fromFile(source), "Video.mp4", "Test", 0, true, 3000)
            val recipe = VideoRecipe(0, 1500, mute = true, extraClips = listOf(VideoClip("second", item, 1000, 3000, mute = true, speed = 2f)),
                caption = de.luma.gallery.model.PhotoText("caption", "Luma"), transitionMs = 250, outputHeight = 720, bitrate = 3_000_000)
            val result = withTimeout(90_000) { VideoExporter(context).export(item, recipe) }; output = result
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(result.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Unexpected duration: $duration", duration in 2250..2750)
                assertEquals("720", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                assertNotEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            }
            assertArrayEquals(original, source.readBytes())
        } finally { output?.delete(); source.delete() }
        Unit
    }

    @Test fun realOverlapKeyframesAndTimedTextExportWithShorterTimeline() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val source=File.createTempFile("luma_video_test_", ".mp4",context.cacheDir)
        instrumentation.context.assets.open("fixture.mp4").use { input -> source.outputStream().use { input.copyTo(it) } }
        var output: File?=null
        try {
            val item=GalleryMedia("video-test",Uri.fromFile(source),"Video.mp4","Test",0,true,3000)
            val recipe=VideoRecipe(0,1500,mute=true,extraClips=listOf(VideoClip("second",item,1000,3000,mute=true,speed=2f)),crossfadeMs=250,
                keyframes=listOf(de.luma.gallery.model.VideoKeyframe(0),de.luma.gallery.model.VideoKeyframe(2250,zoom=1.5f)),
                overlays=listOf(de.luma.gallery.model.VideoOverlay("text",text=de.luma.gallery.model.PhotoText("t","Luma"),startMs=1000,endMs=2000)),outputHeight=720,bitrate=3_000_000)
            val result=withTimeout(90_000){VideoExporter(context).export(item,recipe)};output=result
            MediaMetadataRetriever().use { reader ->reader.setDataSource(result.path)
                val duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Unexpected overlap duration: $duration",duration in 2000..2500)
                assertEquals("720",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                val frame=reader.getFrameAtTime(1_250_000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                assertNotNull(frame);frame?.recycle()
            }
        }finally{output?.delete();source.delete()}
        Unit
    }

}
