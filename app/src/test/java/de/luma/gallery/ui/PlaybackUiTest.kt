package de.luma.gallery.ui

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.ui.PlayerView
import de.luma.gallery.GalleryState
import de.luma.gallery.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun gallerySettingsAllowDisablingAutomaticVideoPlayback() {
        var settings by mutableStateOf(AppSettings())
        compose.setContent {LumaTheme(settings) {SettingsScreen(GalleryState(settings=settings),{}, {settings=it},{},{})}}
        compose.onNodeWithContentDescription("Einstellungen").performScrollToNode(hasText("Galerie & Sortierung"))
        compose.onNodeWithText("Galerie & Sortierung").performClick()
        compose.onNodeWithContentDescription("Videos automatisch abspielen").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithContentDescription("Videos automatisch abspielen").assertIsOff()
        compose.runOnIdle {assertFalse(settings.autoPlayVideos)}
    }

    @Test fun playerAppliesPlaybackIntentAndControlsVisibilityWithoutResettingManualPlayback() {
        var automatic by mutableStateOf(true)
        var controls by mutableStateOf(false)
        val source=GalleryMedia("video",Uri.parse("file:///nonexistent-video.mp4"),"Video.mp4","Camera",0,true,4000)
        compose.setContent {LumaTheme(AppSettings()) {VideoPlayer(source,autoPlay=automatic,showControls=controls,controlInsetPx=112)}}
        lateinit var view:PlayerView
        compose.runOnIdle {
            view=findPlayer(compose.activity.window.decorView)!!
            assertTrue(view.player!!.playWhenReady)
            assertFalse(view.useController)
            assertEquals(112,(view.findViewById<View>(androidx.media3.ui.R.id.exo_controller).layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
            automatic=false
        }
        compose.runOnIdle {assertFalse(view.player!!.playWhenReady);view.player!!.play();controls=true}
        compose.runOnIdle {assertTrue(view.player!!.playWhenReady);assertTrue(view.useController)}
    }

    private fun findPlayer(root:View):PlayerView? {
        if(root is PlayerView) return root
        if(root is ViewGroup) for(index in 0 until root.childCount) findPlayer(root.getChildAt(index))?.let {return it}
        return null
    }
}
