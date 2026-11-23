package de.luma.gallery.ui

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import de.luma.gallery.GalleryViewModel
import de.luma.gallery.data.ProjectDatabase
import de.luma.gallery.data.ProjectRepository
import de.luma.gallery.model.GalleryMedia
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class VideoEditorFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun completeEditorOpensWithTimelinePreviewAndCanClose() {
        withEditor { vm, _, _ ->
            compose.onNodeWithText("Video bearbeiten").assertExists()
            compose.onNodeWithText("Projekt speichern").assertExists()
            compose.onNodeWithText("Clip-Vorschau").performClick()
            compose.onAllNodes(hasText("Zeitleiste abspielen") and hasClickAction()).onFirst().performClick()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.runOnIdle { Assert.assertNull(vm.state.value.editor) }
        }
    }

    @Test fun missingSourceShowsRetryAndKeepsEditorToolsAndDraftAvailable() {
        withEditor(missing = true) { vm, source, file ->
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Vorschau erneut laden").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Projekt speichern").assertExists()
            compose.onNodeWithText("Vorschau erneut laden").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Vorschau erneut laden").fetchSemanticsNodes().isNotEmpty() }
            Assert.assertEquals(source.durationMs, vm.state.value.editor!!.video!!.endMs)
            File("src/androidTest/assets/fixture.mp4").copyTo(file, overwrite = true)
            compose.onNodeWithText("Vorschau erneut laden").performClick()
            compose.onNodeWithText("Video bearbeiten").assertExists()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.runOnIdle { Assert.assertNull(vm.state.value.editor) }
        }
    }

    @Test fun unknownDurationDoesNotSeekAnUnconfiguredPlayerOrEnableExport() {
        withEditor(duration = 0) { vm, _, _ ->
            compose.onNodeWithText("Video bearbeiten").assertExists()
            compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(2)
            compose.onNode(hasText("Export") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertIsNotEnabled()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.runOnIdle { Assert.assertNull(vm.state.value.editor) }
        }
    }

    private fun withEditor(missing: Boolean = false, duration: Long = 3000,
        action: (GalleryViewModel, GalleryMedia, File) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("luma", 0).edit().clear().commit()
        val database = Room.inMemoryDatabaseBuilder(app, ProjectDatabase::class.java).build()
        val store = ViewModelStore()
        val vm = GalleryViewModel(app, ProjectRepository(database))
        ViewModelProvider(store, viewModelFactory { initializer { vm } })[GalleryViewModel::class.java]
        val file = File(app.cacheDir, "video-editor-entry.mp4")
        if (missing) file.delete() else File("src/androidTest/assets/fixture.mp4").copyTo(file, overwrite = true)
        val source = GalleryMedia("entry", Uri.fromFile(file), "Video.mp4", "Camera", 0, true, duration)
        val dataSource = DataSource.toDataSource(app, source.uri)
        ShadowMediaMetadataRetriever.addMetadata(dataSource, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "320")
        ShadowMediaMetadataRetriever.addMetadata(dataSource, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "240")
        try {
            vm.beginEditor(source)
            compose.setContent {
                val state by vm.state.collectAsState()
                LumaTheme(state.settings) { Surface {
                    state.editor?.let { VideoEditor(source, vm, it, vm::closeEditor) }
                } }
            }
            action(vm, source, file)
        } finally { compose.runOnIdle { vm.closeEditor(); store.clear() }; database.close(); file.delete() }
    }
}
