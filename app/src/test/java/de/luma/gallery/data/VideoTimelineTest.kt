package de.luma.gallery.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@androidx.annotation.OptIn(UnstableApi::class)
class VideoTimelineTest {
    private val source=GalleryMedia("first",Uri.parse("content://media/first"),"One.mp4","",0,true,4000)
    private fun recipe()=VideoRecipe(0,4000,extraClips=listOf(VideoClip("second",source.copy(uri=Uri.parse("content://media/second")),speed=2f),VideoClip("third",source.copy(uri=Uri.parse("content://media/third")))),crossfadeMs=1500)
    @Test fun overlapsUseOutputDurationsAndNeverCreateThreeConcurrentClips(){
        val recipe=recipe();val timeline=recipe.timeline(source)
        assertEquals(listOf(0L,3000L,4000L),timeline.map{it.startMs});assertEquals(8000L,recipe.outputDuration(source))
        assertEquals(1000L,timeline[0].fadeOutMs);assertEquals(1000L,timeline[1].fadeInMs)
        for(time in 0L..8000L step 10)assertTrue(timeline.count{time>=it.startMs && time<it.endMs}<=2)
    }
    @Test fun compositorBlendsBothDirectionsAndHidesGapFrames(){
        val timeline=recipe().timeline(source);val compositor=CrossfadeCompositor(timeline,1280,720)
        assertEquals(.5f,compositor.getOverlaySettings(0,3_500_000).alphaScale,.001f)
        assertEquals(1f,compositor.getOverlaySettings(1,3_500_000).alphaScale,0f)
        assertEquals(.5f,compositor.getOverlaySettings(0,4_500_000).alphaScale,.001f)
        assertEquals(0f,compositor.getOverlaySettings(1,1_000_000).alphaScale,0f)
        assertEquals(1f,compositor.getOverlaySettings(0,7_000_000).alphaScale,0f)
    }
    @Test fun graphAlternatesTwoVideoSequencesAndAddsCorrectGaps(){
        val graph=VideoCompositionFactory.create(source,recipe(),SourceInfo(1920,1080))
        assertEquals(2,graph.sequences.size)
        val first=graph.sequences[0].editedMediaItems;val second=graph.sequences[1].editedMediaItems
        assertEquals(2,first.size);assertEquals(3,second.size)
        assertEquals(3_000_000L,second[0].durationUs)
        assertEquals(2f,second[1].speedProvider.getSpeed(0),0f)
        assertEquals(3_000_000L,second[2].durationUs)
        assertTrue(graph.videoCompositorSettings is CrossfadeCompositor)
    }
    @Test fun keyframesInterpolateCameraBrightnessAndVolumeWithBoundedEndpoints(){
        val recipe=VideoRecipe(0,4000,keyframes=listOf(VideoKeyframe(4000,3f,1f,0f,.6f,.5f),VideoKeyframe(0)))
        val frame=recipe.frameAt(2000);assertEquals(2f,frame.zoom,0f);assertEquals(.75f,frame.x,0f);assertEquals(.25f,frame.y,0f);assertEquals(.3f,frame.brightness,0f);assertEquals(.75f,frame.volume,0f)
        assertEquals(1f,recipe.frameAt(0).zoom,0f);assertEquals(3f,recipe.frameAt(8000).zoom,0f)
        assertEquals(.75f,VideoVolumeEnvelope(recipe.keyframes).value(2000),0f)
        assertEquals(1,VideoCompositionFactory.create(source,recipe,SourceInfo(1920,1080)).effects.audioProcessors.size)
        assertTrue(runCatching{recipe.copy(keyframes=listOf(VideoKeyframe(0),VideoKeyframe(0)))}.isFailure)
    }
    @Test fun audioTracksHaveIndependentPlacementSourceTrimAndFiniteLoops(){
        val track=VideoAudioTrack("track",VideoMusic("content://audio/song","Song",5000,.4f),timelineStartMs=1000,sourceStartMs=500,sourceEndMs=1500,lengthMs=2500,loop=true,fadeInMs=500,fadeOutMs=500)
        val graph=VideoCompositionFactory.create(source,VideoRecipe(0,4000,audioTracks=listOf(track)),SourceInfo(1920,1080))
        val audio=graph.sequences[1];assertEquals(setOf(C.TRACK_TYPE_AUDIO),audio.trackTypes);assertFalse(audio.isLooping)
        val items=audio.editedMediaItems;assertEquals(5,items.size);assertEquals(1_000_000L,items.first().durationUs);assertEquals(500_000L,items.last().durationUs)
        assertEquals(500L,items[1].mediaItem.clippingConfiguration.startPositionMs);assertEquals(1000L,items[3].mediaItem.clippingConfiguration.endPositionMs)
    }
    @Test fun completeRecipeRoundTripsWithTimedTextImageAudioAndKeyframes(){
        val recipe=recipe().copy(keyframes=listOf(VideoKeyframe(0),VideoKeyframe(8000,zoom=2f)),audioTracks=listOf(VideoAudioTrack("song",VideoMusic("content://audio/song","Song",5000),timelineStartMs=2000,lengthMs=3000)),
            overlays=listOf(VideoOverlay("text",text=PhotoText("t","Hallo"),startMs=2000,endMs=3000),VideoOverlay("image",imageUri="content://images/sticker",startMs=1000,endMs=5000,opacity=.7f,rotation=25f)))
        assertEquals(recipe,RecipeJson.readVideo(RecipeJson.video(recipe)))
        assertTrue(runCatching{recipe.copy(overlays=List(17){VideoOverlay("$it",text=PhotoText("x","X"))})}.isFailure)
        assertTrue(runCatching{VideoAudioTrack("bad",VideoMusic("content://audio/song","Song",5000),sourceStartMs=4000,sourceEndMs=3000)}.isFailure)
    }
}
