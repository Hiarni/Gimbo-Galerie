package de.luma.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PhotoMarkupUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shapeControlsUpdateAndDeleteOnlyTheSelectedObject() {
        val first = PhotoMarkup("one", MarkupKind.BRUSH)
        val second = PhotoMarkup("two", MarkupKind.RECTANGLE)
        val state = mutableStateOf(listOf(first, second)); var finished = 0
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            PhotoMarkupControls(state.value, state.value.find { it.id == "two" }, MarkupKind.BRUSH, {}, {}, {},
                { mark -> state.value = state.value.map { if (it.id == mark.id) mark else it } }, { finished++ }, { state.value = it })
        } } }
        compose.onNodeWithContentDescription("Objektbreite (%)").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        compose.onNodeWithContentDescription("Gefüllte Form").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(first, state.value.first()); assertTrue(state.value.last().filled)
            assertEquals(.6f, state.value.last().right - state.value.last().left, .0001f); assertEquals(1, finished) }
        compose.onNodeWithText("Ausgewähltes Objekt entfernen").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(first), state.value) }
    }

    @Test fun dragCreatesAStrokeWithThePreviousBrushStyleAndCommitsOnlyOnce() {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        var created: PhotoMarkup? = null; var finishes = 0
        val template = PhotoMarkup("old", MarkupKind.BRUSH, color = 0xFF448AFF, width = .04f)
        try {
            compose.setContent { MaterialTheme { Box(Modifier.size(300.dp)) {
                PhotoMarkupPreview(bitmap, null, MarkupKind.BRUSH, template, true, {}, { finishes++ }, { created = it })
            } } }
            compose.onNodeWithContentDescription("Zeichenfläche").performTouchInput {
                swipe(Offset(width * .2f, height * .2f), Offset(width * .8f, height * .7f))
            }
            compose.runOnIdle {
                val mark = created!!
                assertTrue(mark.points.size > 2); assertTrue(mark.points.size <= PHOTO_MARKUP_POINTS)
                assertEquals(.8f, mark.points.last().x, .02f); assertEquals(.7f, mark.points.last().y, .02f)
                assertEquals(template.color, mark.color); assertEquals(template.width, mark.width)
                assertNotEquals(template.id, mark.id); assertEquals(0, finishes)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun movingASelectedObjectClampsAtTheEdgeAndNeverCreatesAnotherOne() {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val state = mutableStateOf(PhotoMarkup("id", MarkupKind.COVER)); var finishes = 0; var creates = 0
        try {
            compose.setContent { MaterialTheme { Box(Modifier.size(300.dp)) {
                PhotoMarkupPreview(bitmap, state.value, MarkupKind.BRUSH, null, true,
                    { state.value = it }, { finishes++ }, { creates++ })
            } } }
            compose.onNodeWithContentDescription("Zeichenfläche").performTouchInput { swipe(center, center + Offset(width * .48f, 0f)) }
            compose.runOnIdle { assertEquals(1f, state.value.right, .0001f); assertEquals("id", state.value.id)
                assertEquals(.4f, state.value.right - state.value.left, .0001f); assertEquals(1, finishes); assertEquals(0, creates) }
        } finally { bitmap.recycle() }
    }

    @Test @Config(qualifiers = "en") fun englishPixelationControlsAndLimitAreAvailable() {
        val pixel = PhotoMarkup("pixel", MarkupKind.PIXELATE)
        val state = mutableStateOf(List(PHOTO_MARKUP_LIMIT) { pixel.copy(id = "$it") })
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            PhotoMarkupControls(state.value, state.value.first(), MarkupKind.PIXELATE, {}, {}, {},
                { mark -> state.value = state.value.map { if (it.id == mark.id) mark else it } }, {}, { state.value = it })
        } } }
        compose.onNodeWithText("Add drawing object").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Block size (%)").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        compose.onNodeWithText("Pixelation may leave details visible. Use an opaque cover for sensitive areas.").assertExists()
        compose.runOnIdle { assertEquals(.12f, state.value.first().blockSize, .0001f); assertEquals(.04f, state.value.last().blockSize, 0f) }
    }
}
