package de.luma.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
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
class PhotoTextUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dialogRejectsBlankAndReturnsTrimmedText() {
        var saved: String? = null
        compose.setContent { MaterialTheme { PhotoTextDialog("", false, {}, { saved = it }) } }
        compose.onNodeWithText("Übernehmen").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("  Sommer  ")
        compose.onNodeWithText("Übernehmen").performClick()
        compose.runOnIdle { assertEquals("Sommer", saved) }
    }

    @Test fun addingEditingAndRemovingCaptionsKeepsTheOtherObject() {
        val first = PhotoText("first", "Erster")
        val state = mutableStateOf(listOf(first)); val selected = mutableStateOf("first")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            PhotoTextControls(state.value, state.value.find { it.id == selected.value }, { selected.value = it },
                { text -> state.value = state.value.map { if (it.id == text.id) text else it } }, {}, { state.value = it })
        } } }
        compose.onNodeWithText("Text hinzufügen").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Zweiter")
        compose.onNodeWithText("Übernehmen").performClick()
        compose.runOnIdle { assertEquals(2, state.value.size); assertEquals(first, state.value.first()); assertEquals(state.value.last().id, selected.value) }
        compose.onNodeWithText("Inhalt bearbeiten").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("Geändert")
        compose.onNodeWithText("Übernehmen").performClick()
        compose.runOnIdle { assertEquals("Geändert", state.value.last().text); assertEquals(first, state.value.first()) }
        compose.onNodeWithText("Text entfernen").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(first), state.value) }
    }

    @Test fun slidersAndStyleControlsChangeOnlySelectedCaption() {
        val first = PhotoText("one", "A"); val second = PhotoText("two", "B")
        val state = mutableStateOf(listOf(first, second)); var finished = 0
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            PhotoTextControls(state.value, state.value.last(), {}, { text -> state.value = listOf(first, text) }, { finished++ }, { state.value = it })
        } } }
        compose.onNodeWithContentDescription("Schriftgröße (%)").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }
        compose.onNodeWithContentDescription("Textposition horizontal (%)").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        compose.onNodeWithContentDescription("Fettschrift").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Kontrasthintergrund").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(first, state.value.first())
            assertEquals(second.copy(size = .15f, x = .75f, bold = true, background = false), state.value.last())
            assertEquals(2, finished)
        }
    }

    @Test fun draggingSelectedFrameMovesCaptionAndCommitsOnce() {
        val state = mutableStateOf(PhotoText("id", "A", size = .15f))
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        var finishes = 0
        compose.setContent { MaterialTheme { Box(Modifier.size(300.dp)) {
            PhotoTextPreview(bitmap, state.value, { state.value = it }, { finishes++ })
        } } }
        compose.onNodeWithContentDescription("Textrahmen. Gewählten Text verschieben oder Positionsregler verwenden.").performTouchInput {
            swipe(center, center + Offset(width * .2f, height * .1f))
        }
        compose.runOnIdle {
            assertTrue(state.value.x > .65f); assertTrue(state.value.y > .55f)
            assertEquals("A", state.value.text); assertEquals(.15f, state.value.size, 0f); assertEquals(1, finishes)
        }
        bitmap.recycle()
    }

    @Test fun objectLimitDisablesFurtherAddition() {
        val texts = (0..11).map { PhotoText("$it", "Text $it") }
        compose.setContent { MaterialTheme { PhotoTextControls(texts, null, {}, {}, {}, {}) } }
        compose.onNodeWithText("Text hinzufügen").assertIsNotEnabled()
    }
}
