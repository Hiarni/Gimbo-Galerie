package de.luma.gallery.ui

import android.net.Uri
import android.os.Looper
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.Futures
import de.luma.gallery.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de-w360dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(UnstableApi::class)
class ReviewVideoUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var file: File
    private val players = mutableListOf<ReviewTestPlayer>()
    private val video get() = GalleryMedia("video", Uri.fromFile(file), "Video.mp4", "Camera", 0, true, 3000)
    @Before fun source() {
        file = File.createTempFile("review-video-", ".mp4", RuntimeEnvironment.getApplication().cacheDir)
        File("src/androidTest/assets/fixture.mp4").copyTo(file, overwrite = true)
    }
    @After fun cleanup() { file.delete() }
    private fun open(items: List<GalleryMedia> = listOf(video, video.copy(id = "next")), autoPlay: Boolean = false) {
        val settings = AppSettings(autoPlayVideos = autoPlay, reviewPlayful = false, thumbnailAnimations = false)
        val factory: (android.content.Context) -> Player = { ReviewTestPlayer().also { players += it } }
        compose.setContent { CompositionLocalProvider(LocalReviewVideoPlayerFactory provides factory) {
            LumaTheme(settings) { ReviewScreen(items.map { it.id }, items, settings, {}, {}) }
        } }
    }
    @Test fun videoCanPlayPauseAndEnableSoundWithoutMakingASortingChoice() {
        open()
        compose.onNodeWithContentDescription("Video abspielen").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(players.single().playWhenReady)
            assertEquals(video.uri, players.single().source!!.localConfiguration!!.uri)
            assertEquals(0f, players.single().volume, 0f)
            assertFalse(players.single().audioFocus)
        }
        compose.onNodeWithContentDescription("Video pausieren").performClick()
        compose.runOnIdle { assertFalse(players.single().playWhenReady) }
        compose.onNodeWithContentDescription("Ton einschalten").performClick()
        compose.runOnIdle { assertEquals(1f, players.single().volume, 0f); assertTrue(players.single().audioFocus) }
        compose.onNodeWithText("0 von 2").assertExists()
        compose.runOnIdle {
            fun playerView(view: View): PlayerView? = if (view is PlayerView) view
                else if (view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { playerView(view.getChildAt(it)) } else null
            assertTrue(playerView(compose.activity.window.decorView)!!.videoSurfaceView is TextureView)
        }
    }
    @Test fun swipingPausesAndReleasesVideoAndUndoCreatesAFreshPlayableCard() {
        open(autoPlay = true)
        compose.runOnIdle { assertTrue(players.single().playWhenReady) }
        compose.onNodeWithTag("review_card").performTouchInput { down(center); moveTo(center + Offset(width * .15f, 0f), 200) }
        compose.runOnIdle { assertFalse(players.first().playWhenReady) }
        compose.onNodeWithTag("review_card").performTouchInput { up() }
        compose.runOnIdle { assertTrue(players.first().playWhenReady) }
        compose.onNodeWithContentDescription("Ton einschalten").performClick()
        compose.onNodeWithTag("review_card").performTouchInput { swipeLeft() }
        compose.onNodeWithText("1 von 2").assertExists()
        compose.runOnIdle {
            assertTrue(players.first().wasReleased)
            assertEquals(2, players.size)
            assertEquals(1f, players.last().volume, 0f)
        }
        compose.onNodeWithContentDescription("Rückgängig").performClick()
        compose.onNodeWithText("0 von 2").assertExists()
        compose.runOnIdle { assertTrue(players[1].wasReleased); assertEquals(3, players.size); assertTrue(players.last().playWhenReady) }
    }
    @Test fun summaryAndAppBackgroundPausePlaybackWithoutApplyingChoices() {
        open(autoPlay = true)
        compose.onNodeWithTag("review_card").performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription("Abschließen").performClick()
        compose.runOnIdle { assertFalse(players.last().playWhenReady) }
        compose.onNodeWithText("Zurück").performClick()
        compose.runOnIdle { assertTrue(players.last().playWhenReady) }
        compose.runOnUiThread { (compose.activity.lifecycle as androidx.lifecycle.LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        compose.runOnIdle { assertFalse(players.last().playWhenReady) }
        compose.runOnUiThread { (compose.activity.lifecycle as androidx.lifecycle.LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        compose.onNodeWithContentDescription("Video abspielen").assertExists()
        compose.runOnIdle { assertFalse(players.last().playWhenReady) }
        compose.onNodeWithContentDescription("Video abspielen").performClick()
        compose.runOnIdle { assertTrue(players.last().playWhenReady) }
        compose.onNodeWithText("1 von 2").assertExists()
    }
    @Test fun failedVideoCanRetryOrBeSwipedAndPhotosHaveNoPlaybackControls() {
        open(items = listOf(video, video.copy(id = "photo", isVideo = false)))
        compose.runOnIdle { players.first().fail() }
        compose.onNodeWithText("Vorschau erneut laden").assertExists()
        compose.runOnIdle { assertTrue(players.first().wasReleased) }
        compose.onNodeWithText("Vorschau erneut laden").performClick()
        compose.onNodeWithContentDescription("Video abspielen").assertExists()
        compose.onNodeWithTag("review_card").performTouchInput { swipeRight() }
        compose.onNodeWithText("1 von 2").assertExists()
        compose.onNodeWithTag("review_video_toggle").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, players.size); assertTrue(players.last().wasReleased) }
    }
    private class ReviewTestPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        var source: MediaItem? = null
        var wasReleased = false
        var audioFocus = false
        private var wanted = false
        private var ready = Player.STATE_IDLE
        private var sound = 1f
        private var repeat = Player.REPEAT_MODE_OFF
        private var failure: PlaybackException? = null
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAll(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_PREPARE, Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SET_VOLUME, Player.COMMAND_GET_VOLUME,
                Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_AUDIO_ATTRIBUTES, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE, Player.COMMAND_RELEASE).build())
            .setPlaylist(source?.let { listOf(MediaItemData.Builder("video").setMediaItem(it).setDurationUs(3_000_000).build()) }.orEmpty())
            .setPlayWhenReady(wanted, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).setPlaybackState(ready)
            .setVolume(sound).setRepeatMode(repeat).setPlayerError(failure).build()
        override fun handleSetMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long) = Futures.immediateVoidFuture().also { source = items.first() }
        override fun handlePrepare() = Futures.immediateVoidFuture().also { ready = Player.STATE_READY }
        override fun handleSetPlayWhenReady(playWhenReady: Boolean) = Futures.immediateVoidFuture().also { wanted = playWhenReady }
        override fun handleSetVolume(volume: Float) = Futures.immediateVoidFuture().also { sound = volume }
        override fun handleSetRepeatMode(repeatMode: Int) = Futures.immediateVoidFuture().also { repeat = repeatMode }
        override fun handleSetAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) = Futures.immediateVoidFuture().also { audioFocus = handleAudioFocus }
        override fun handleRelease() = Futures.immediateVoidFuture().also { wasReleased = true }
        fun fail() { ready = Player.STATE_IDLE; failure = PlaybackException("Unavailable", null, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND); invalidateState() }
    }
}
