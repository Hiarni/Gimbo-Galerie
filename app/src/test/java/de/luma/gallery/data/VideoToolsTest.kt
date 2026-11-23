package de.luma.gallery.data

import android.net.Uri
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoToolsTest {
    private val source=GalleryMedia("video",Uri.parse("content://media/external/video/media/1"),"Test.mp4","Camera",0,true,10_000)

    @Test fun newGeometryRoundTripsAndLegacyProjectsKeepTheirOriginalOrientation() {
        val recipe=VideoRecipe(500,9000,quarterTurns=3,flipHorizontal=true,flipVertical=true,cropBounds=CropBounds(.1f,.2f,.9f,.8f),aspectRatio=9f/16,fillFrame=true,temperature=.4f,tint=-.1f)
        assertEquals(recipe,RecipeJson.readVideo(RecipeJson.video(recipe)))
        val legacy="""{"version":3,"start":0,"end":10000}"""
        assertEquals(VideoRecipe(0,10_000),RecipeJson.readVideo(legacy))
        assertTrue(runCatching {recipe.copy(quarterTurns=4)}.isFailure)
        assertTrue(runCatching {recipe.copy(aspectRatio=Float.NaN)}.isFailure)
        assertTrue(runCatching {recipe.copy(temperature=2f)}.isFailure)
    }

    @Test fun rotatedAndCroppedCanvasMatchesTheSharedCompositionEffects() {
        val recipe=VideoRecipe(0,10_000,quarterTurns=1,flipHorizontal=true,cropBounds=CropBounds(.25f,0f,.75f,1f))
        val size=VideoCompositionFactory.outputSize(recipe,SourceInfo(1920,1080))
        assertEquals(SourceInfo(1214,1080),size)
        val composition=VideoCompositionFactory.create(source,recipe,SourceInfo(1920,1080))
        val effects=composition.sequences.single().editedMediaItems.single().effects.videoEffects
        assertTrue(effects[0] is Crop)
        assertTrue(effects[1] is ScaleAndRotateTransformation)
        assertTrue(effects[2] is ScaleAndRotateTransformation)
        val presentation=effects.filterIsInstance<Presentation>().single()
        assertEquals(size.width,presentation.configure(1080,960).width)
        assertEquals(size.height,presentation.configure(1080,960).height)
        val portrait=VideoCompositionFactory.outputSize(recipe.copy(aspectRatio=9f/16),SourceInfo(1920,1080))
        assertEquals(SourceInfo(606,1080),portrait)
        assertEquals(VideoEffects.visual(recipe,size.width,size.height).map {it.javaClass},effects.map {it.javaClass})
    }

    @Test fun splittingKeepsTrimSpeedSoundAndOrderAndCanThenBeDuplicated() {
        val extra=VideoClip("extra",source.copy(id="extra"),1000,9000,speed=2f,mute=true,volume=.4f)
        val recipe=VideoRecipe(0,2000,extraClips=listOf(extra),clipOrder=listOf("extra","primary"))
        val split=recipe.splitClip(source,"extra",4000)
        val clips=split.orderedClips(source)
        assertEquals(3,clips.size)
        assertEquals(4000L,clips[0].endMs)
        assertEquals(4000L,clips[1].startMs)
        assertEquals(9000L,clips[1].endMs)
        assertEquals(extra.speed,clips[1].speed,0f)
        assertEquals(extra.mute,clips[1].mute)
        assertEquals(extra.volume,clips[1].volume,0f)
        assertEquals(recipe.outputDuration(source),split.outputDuration(source))
        assertEquals("primary",clips.last().id)
        val duplicate=split.duplicateClip(source,"primary")
        assertEquals(split.outputDuration(source)+2000,duplicate.outputDuration(source))
        assertTrue(duplicate.isValid(source.durationMs))
        assertTrue(runCatching {recipe.splitClip(source,"extra",1200)}.isFailure)
    }

    @Test fun previewLimitsTextureSizesWhileExportKeepsTheChosenResolutionAndTiming() {
        for (ratio in listOf(.25f, 9f / 16, 1f, 16f / 9, 4f)) {
            val recipe = VideoRecipe(1000, 8000, speed = 2f, outputHeight = 2160, aspectRatio = ratio)
            val info = SourceInfo(3840, 2160)
            val size = VideoCompositionFactory.previewSize(recipe, info)
            assertTrue(size.width in 2..1280 && size.height in 2..1280)
            assertEquals(ratio, size.width.toFloat() / size.height, .01f)
            val preview = VideoCompositionFactory.create(source, recipe, info, preview = true)
            val export = VideoCompositionFactory.create(source, recipe, info)
            val previewClip = preview.sequences.single().editedMediaItems.single()
            val exportClip = export.sequences.single().editedMediaItems.single()
            assertEquals(exportClip.mediaItem.clippingConfiguration, previewClip.mediaItem.clippingConfiguration)
            assertEquals(exportClip.durationUs, previewClip.durationUs)
            assertEquals(exportClip.speedProvider.getSpeed(0), previewClip.speedProvider.getSpeed(0), 0f)
            assertEquals(3500L, recipe.outputDuration(source))
            val previewCanvas = previewClip.effects.videoEffects.filterIsInstance<Presentation>().single().configure(3840, 2160)
            val exportCanvas = exportClip.effects.videoEffects.filterIsInstance<Presentation>().single().configure(3840, 2160)
            assertEquals(size.width, previewCanvas.width)
            assertEquals(size.height, previewCanvas.height)
            assertEquals(2160, exportCanvas.height)
        }
    }

    @Test fun removingPrimaryClipDoesNotExposeAnExtraSlotThatWouldOverflowTheRecipe() {
        val clips = List(19) { VideoClip("extra-$it", source) }
        val full = VideoRecipe(0, source.durationMs, extraClips = clips)
        assertEquals(0, full.additionalClipCapacity(source))
        val withoutPrimary = full.copy(includeSource = false)
        assertEquals(19, withoutPrimary.orderedClips(source).size)
        assertEquals(0, withoutPrimary.additionalClipCapacity(source))
        assertTrue(runCatching { withoutPrimary.duplicateClip(source, "extra-0") }.isFailure)
        assertTrue(runCatching { withoutPrimary.splitClip(source, "extra-0", 5000) }.isFailure)
        val available = withoutPrimary.copy(extraClips = clips.dropLast(1))
        assertEquals(1, available.additionalClipCapacity(source))
        assertEquals(0, available.duplicateClip(source, "extra-0").additionalClipCapacity(source))
        assertEquals(0, available.splitClip(source, "extra-0", 5000).additionalClipCapacity(source))
    }

    @Test fun filterStrengthPreservesEditsAndExactTimecodesRejectInvalidInput() {
        val recipe=VideoRecipe(1234,9876,quarterTurns=1,cropBounds=CropBounds(.1f,.1f,.9f,.9f),music=VideoMusic("content://test/audio","Song",5000))
        VIDEO_PRESETS.forEach {preset->
            val applied=recipe.withPreset(preset,.5f)
            assertEquals(recipe.cropBounds,applied.cropBounds)
            assertEquals(recipe.quarterTurns,applied.quarterTurns)
            assertEquals(recipe.music,applied.music)
            assertEquals(recipe.startMs,applied.startMs)
            assertEquals(preset.saturation*.5f,applied.saturation,0f)
            assertEquals(applied,RecipeJson.readVideo(RecipeJson.video(applied)))
        }
        listOf(0L,1234L,3_661_999L,604_799_999L).forEach {assertEquals(it,parseVideoTimecode(videoTimecode(it)))}
        assertEquals(1250L,parseVideoTimecode("00:00:01,25"))
        assertNull(parseVideoTimecode("00:60:00.000"))
        assertNull(parseVideoTimecode("-1"))
    }
}
