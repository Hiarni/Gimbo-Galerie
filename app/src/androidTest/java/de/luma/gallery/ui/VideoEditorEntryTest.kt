package de.luma.gallery.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.luma.gallery.GalleryViewModel
import de.luma.gallery.data.ProjectDatabase
import de.luma.gallery.data.ProjectRepository
import de.luma.gallery.model.AppSettings
import de.luma.gallery.model.GalleryMedia
import org.junit.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoEditorEntryTest {
    @Test fun sortingCardPlaysMutedVideoAndReleasesItsTexturePlayerOnAndroid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val sourceFile = File.createTempFile("gimbo-review-video-", ".mp4", app.cacheDir)
        instrumentation.context.assets.open("fixture.mp4").use { input -> sourceFile.outputStream().use { input.copyTo(it) } }
        val source = GalleryMedia(sourceFile.name, Uri.fromFile(sourceFile), "Video.mp4", "Test", 0, true, 3000)
        var open by mutableStateOf(true)
        var player: androidx.media3.exoplayer.ExoPlayer? = null
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    LumaTheme(AppSettings()) { Surface {
                        if (open) ReviewScreen(listOf(source.id), listOf(source), AppSettings(autoPlayVideos = true), { open = false }, { open = false })
                    } }
                } }
                var playing = false
                val deadline = SystemClock.uptimeMillis() + 10_000
                while (!playing && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        fun viewPlayer(view: android.view.View): androidx.media3.ui.PlayerView? =
                            if (view is androidx.media3.ui.PlayerView) view
                            else if (view is android.view.ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { viewPlayer(view.getChildAt(it)) }
                            else null
                        val view = viewPlayer(activity.window.decorView)
                        if (view != null) {
                            Assert.assertTrue(view.videoSurfaceView is android.view.TextureView)
                            player = view.player as? androidx.media3.exoplayer.ExoPlayer
                            playing = player?.isPlaying == true
                        }
                    }
                    if (!playing) SystemClock.sleep(25)
                }
                Assert.assertTrue("Sorting video never started", playing)
                scenario.onActivity { Assert.assertEquals(0f, player!!.volume, 0f); open = false }
                var released = false
                val releaseDeadline = SystemClock.uptimeMillis() + 10_000
                while (!released && SystemClock.uptimeMillis() < releaseDeadline) {
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { released = player!!.isReleased }
                    if (!released) SystemClock.sleep(25)
                }
                Assert.assertTrue("Sorting player never released", released)
            }
        } finally { sourceFile.delete() }
    }

    @Test fun videoEditorInitializesAndReleasesPreviewOnAndroid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val database = Room.inMemoryDatabaseBuilder(app, ProjectDatabase::class.java).build()
        val store = ViewModelStore()
        val sourceFile = File.createTempFile("gimbo-editor-entry-", ".mp4", app.cacheDir)
        instrumentation.context.assets.open("fixture.mp4").use { input -> sourceFile.outputStream().use { input.copyTo(it) } }
        val original = sourceFile.readBytes()
        val source = GalleryMedia(sourceFile.name, Uri.fromFile(sourceFile), "Video.mp4", "Test", 0, true, 3000)
        val vm = GalleryViewModel(app, ProjectRepository(database))
        ViewModelProvider(store, viewModelFactory { initializer { vm } })[GalleryViewModel::class.java]
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    vm.beginEditor(source)
                    activity.setContent {
                        val state by vm.state.collectAsState()
                        LumaTheme(AppSettings()) { Surface {
                            state.editor?.let { VideoEditor(source, vm, it, vm::closeEditor) }
                        } }
                    }
                }
                var initialized = false
                val deadline = SystemClock.uptimeMillis() + 10_000
                while (!initialized && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        fun boundPlayer(view: android.view.View): Boolean =
                            if (view is androidx.media3.ui.PlayerView) view.player != null
                            else view is android.view.ViewGroup && (0 until view.childCount).any { boundPlayer(view.getChildAt(it)) }
                        initialized = boundPlayer(activity.window.decorView)
                    }
                    if (!initialized) SystemClock.sleep(25)
                }
                Assert.assertTrue("Timeline preview never initialized", initialized)
                scenario.onActivity { vm.closeEditor() }
                instrumentation.waitForIdleSync()
                Assert.assertNull(vm.state.value.editor)
                Assert.assertArrayEquals(original, sourceFile.readBytes())
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            database.close()
            sourceFile.delete()
        }
    }
}
