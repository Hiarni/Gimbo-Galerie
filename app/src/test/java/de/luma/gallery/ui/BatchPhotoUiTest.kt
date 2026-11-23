package de.luma.gallery.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import de.luma.gallery.R
import de.luma.gallery.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class BatchPhotoUiTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val files = mutableListOf<File>()
    private lateinit var items: List<GalleryMedia>
    private fun text(key: Int, vararg args: Any) = app.getString(key, *args)
    private fun write(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    @Before fun setup() {
        items = listOf("First.png", "Second.png", "Third.png").mapIndexed { index, name ->
            val file = File.createTempFile("batch-ui-", ".png", app.cacheDir); files += file
            write(file, listOf(Color.BLUE, Color.GREEN, Color.RED)[index])
            GalleryMedia("photo-$index", Uri.fromFile(file), name, "Test", 0)
        }
    }
    @After fun cleanup() { files.forEach { it.delete() } }
    private fun awaitImage(index: Int, original: Boolean = false) {
        val description = text(if (original) R.string.batch_preview_original else R.string.batch_preview_edited, items[index].name)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun next() = compose.onNodeWithContentDescription(text(R.string.next_media)).performScrollTo().performClick()
    private fun previous() = compose.onNodeWithContentDescription(text(R.string.previous_media)).performScrollTo().performClick()

    @Test fun everyPhotoIsReachableWithStableEndpointsAndItsOwnRenderedPreview() {
        val bytes = files.map { it.readBytes() }
        compose.setContent { MaterialTheme { BatchPhotoDialog(items, emptyList(), PhotoExportOptions(), {}, { _, _ -> }) } }
        awaitImage(0)
        compose.onNodeWithContentDescription(text(R.string.previous_media)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.batch_preview_position, 1, 3)).assertExists()
        next(); awaitImage(1)
        compose.onNodeWithText(items[1].name).assertExists()
        next(); awaitImage(2)
        compose.onNodeWithContentDescription(text(R.string.next_media)).assertIsNotEnabled()
        previous(); awaitImage(1)
        previous(); awaitImage(0)
        compose.runOnIdle { files.forEachIndexed { index, file -> assertArrayEquals(bytes[index], file.readBytes()) } }
    }

    @Test fun originalComparisonAndPhotoNavigationNeverChangeTheRecipeExportedForTheWholeBatch() {
        var exported: PhotoRecipe? = null
        compose.setContent { MaterialTheme { BatchPhotoDialog(items, emptyList(), PhotoExportOptions(), {}, { recipe, _ -> exported = recipe }) } }
        compose.onNodeWithText(text(R.string.filter_warm)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.original)).performScrollTo().performClick()
        awaitImage(0, true)
        next(); awaitImage(1, true)
        compose.onNodeWithText(text(R.string.export)).performClick()
        compose.onNodeWithText(text(R.string.batch_review, 3, text(R.string.filter_warm))).assertExists()
        compose.onNodeWithText("Kopie speichern").performClick()
        compose.runOnIdle { assertEquals(PhotoRecipe(temperature = .35f), exported) }
    }

    @Test fun failedPhotoCanBeRetriedWithoutBlockingNavigationOrChangingTheBatch() {
        files[0].writeBytes(byteArrayOf())
        compose.setContent { MaterialTheme { BatchPhotoDialog(items, emptyList(), PhotoExportOptions(), {}, { _, _ -> }) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text(R.string.retry_preview)).fetchSemanticsNodes().isNotEmpty() }
        next(); awaitImage(1)
        previous()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text(R.string.retry_preview)).fetchSemanticsNodes().isNotEmpty() }
        write(files[0], Color.BLUE)
        compose.onNodeWithText(text(R.string.retry_preview)).performScrollTo().performClick()
        awaitImage(0)
        compose.onNodeWithText(text(R.string.batch_preview_position, 1, 3)).assertExists()
        compose.onNodeWithText(text(R.string.export)).assertIsEnabled()
    }

    @Test fun projectPresetKeepsItsIdentityWhenTheProjectListChangesOrder() {
        val chosen = PhotoRecipe(quarterTurns = 1)
        val one = EditorProject("one", "Rotate", items[0], chosen, null, 0, 0)
        val two = one.copy(id = "two", name = "Different", photo = PhotoRecipe(saturation = 0f))
        val projects = mutableStateOf(listOf(one, two))
        var exported: PhotoRecipe? = null
        compose.setContent { MaterialTheme { BatchPhotoDialog(items, projects.value, PhotoExportOptions(), {}, { recipe, _ -> exported = recipe }) } }
        compose.onNodeWithText(text(R.string.batch_project_preset, "Rotate")).performScrollTo().performClick()
        compose.runOnIdle { projects.value = listOf(two, one.copy(name = "Renamed")) }
        compose.onNodeWithText(text(R.string.batch_project_preset, "Renamed")).assertExists()
        compose.onNodeWithText(text(R.string.export)).performClick()
        compose.onNodeWithText(text(R.string.batch_review, 3, text(R.string.batch_project_preset, "Renamed"))).assertExists()
        compose.onNodeWithText("Kopie speichern").performClick()
        compose.runOnIdle { assertEquals(chosen, exported) }
    }

    @Test fun removedPresetBlocksExportUntilUserExplicitlyChoosesAnotherPreset() {
        val project = EditorProject("one", "Chosen", items[0], PhotoRecipe(quarterTurns = 1), null, 0, 0)
        val projects = mutableStateOf(listOf(project))
        compose.setContent { MaterialTheme { BatchPhotoDialog(items, projects.value, PhotoExportOptions(), {}, { _, _ -> }) } }
        compose.onNodeWithText(text(R.string.batch_project_preset, "Chosen")).performScrollTo().performClick()
        compose.runOnIdle { projects.value = emptyList() }
        compose.onNodeWithText(text(R.string.batch_preset_missing)).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.export)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.batch_no_edits)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.export)).assertIsEnabled()
        awaitImage(0)
    }

    @Test @Config(qualifiers = "en") fun englishPreviewNavigationAndComparisonAreAvailableOffline() {
        compose.setContent { MaterialTheme { BatchPhotoDialog(items, emptyList(), PhotoExportOptions(), {}, { _, _ -> }) } }
        awaitImage(0)
        compose.onNodeWithText("Photo 1 of 3").assertExists()
        next(); awaitImage(1)
        compose.onNodeWithText("Original").performScrollTo().performClick()
        awaitImage(1, true)
        compose.onNodeWithText("Photo 2 of 3").assertExists()
        compose.onNodeWithText("Edit preview").performScrollTo().performClick()
        awaitImage(1)
        compose.onNodeWithText("Foto 2 von 3").assertDoesNotExist()
    }
}
