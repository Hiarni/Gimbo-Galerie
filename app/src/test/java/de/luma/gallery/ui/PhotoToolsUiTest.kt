package de.luma.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import de.luma.gallery.model.CropBounds
import de.luma.gallery.model.PhotoExportOptions
import de.luma.gallery.model.PhotoFormat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PhotoToolsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun draggingFrameMovesCropAndCommitsOnce() {
        val state = mutableStateOf(CropBounds(.2f, .2f, .8f, .8f))
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        var commits = 0
        var touchSlop = 0f
        compose.setContent { Box(Modifier.size(300.dp).testTag("crop")) {
            touchSlop = LocalViewConfiguration.current.touchSlop
            CropPreview(bitmap, state.value, false, { state.value = it }, { commits++ })
        } }
        val width = compose.onNodeWithTag("crop").fetchSemanticsNode().size.width
        compose.onNodeWithTag("crop").performTouchInput { swipe(center, center + Offset(width * .1f, 0f)) }
        compose.runOnIdle {

            assertEquals(.3f - touchSlop / width, state.value.left, .0001f)
            assertEquals(.6f, state.value.width, .00001f)
            assertEquals(.2f, state.value.top, .00001f)
            assertEquals(1, commits)
        }
    }

    @Test fun draggingLockedCornerKeepsRatioAndOppositeAnchor() {
        val state = mutableStateOf(CropBounds(.2f, .3f, .8f, .6f))
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        compose.setContent { Box(Modifier.size(300.dp).testTag("crop")) {
            CropPreview(bitmap, state.value, true, { state.value = it }, {})
        } }
        compose.onNodeWithTag("crop").performTouchInput {
            swipe(Offset(width * .8f, height * .6f), Offset(width * .6f, height * .5f))
        }
        compose.runOnIdle {
            assertTrue("Corner did not resize far enough: ${state.value}", state.value.width < .5f)
            assertEquals(2f, state.value.width / state.value.height, .0001f)
            assertEquals(.2f, state.value.left, .00001f)
            assertEquals(.3f, state.value.top, .00001f)
        }
    }

    @Test fun exportDialogChangesFormatAndSizeAndReturnsSelectedOptions() {
        var selected: PhotoExportOptions? = null
        compose.setContent { MaterialTheme {
            PhotoExportDialog(PhotoExportOptions(), {}, { selected = it })
        } }
        compose.onNodeWithContentDescription("JPEG-Qualität").assertExists()
        compose.onNodeWithText("PNG").performClick()
        compose.onNodeWithContentDescription("JPEG-Qualität").assertDoesNotExist()
        compose.onNodeWithText("4096 px").performScrollTo().performClick()
        compose.onNodeWithText("1280 px").performClick()
        compose.onNodeWithText("Kopie speichern").performClick()
        compose.runOnIdle { assertEquals(PhotoExportOptions(PhotoFormat.PNG, 1280, 95), selected) }
    }
    @Test fun jpegMetadataChoiceIsReturnedAndPngExplicitlyOmitsIt() {
        var selected: PhotoExportOptions? = null
        compose.setContent { MaterialTheme {
            PhotoExportDialog(PhotoExportOptions(), {}, { selected = it })
        } }
        compose.onNodeWithText("Metadaten entfernen").performScrollTo().performClick()
        compose.onNodeWithText("Kamera- und Aufnahmedaten behalten").performClick()
        compose.onNodeWithText("Kopie speichern").performClick()
        compose.runOnIdle { assertEquals(de.luma.gallery.model.MetadataMode.CAMERA, selected!!.metadata) }
        compose.onNodeWithText("PNG").performScrollTo().performClick()
        compose.onNodeWithText("Kopie speichern").performClick()
        compose.runOnIdle { assertEquals(PhotoFormat.PNG, selected!!.format); assertEquals(de.luma.gallery.model.MetadataMode.NONE, selected!!.metadata) }
    }

}
