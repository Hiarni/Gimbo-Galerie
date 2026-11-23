package de.luma.gallery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import de.luma.gallery.model.*
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
class PhotoAdjustmentsUiTest {
    @get:Rule val compose = createComposeRule()
    private val initial = PhotoRecipe(exposure = .2f, temperature = .4f, cropBounds = CropBounds(.1f, .2f, .7f, .8f),
        highlights = -.3f, shadows = .5f, sharpness = .4f, curves = PhotoCurves(red = ToneCurve(midtones = .7f)))

    @Test fun lightSlidersCommitAndResetOnlyLightAdjustments() {
        val state = mutableStateOf(initial)
        var finishes = 0
        var committed: PhotoRecipe? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            PhotoAdjustmentControls(PhotoToolPage.LIGHT, state.value, { state.value = it },
                { finishes++; committed = state.value }, { state.value = it; committed = it })
        } } }
        compose.onNodeWithContentDescription("Schatten").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.8f) }
        compose.runOnIdle { assertEquals(.8f, committed!!.shadows, 0f); assertEquals(1, finishes) }
        compose.onNodeWithContentDescription("Lichter").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(-.7f) }
        compose.runOnIdle { assertEquals(-.7f, committed!!.highlights, 0f); assertEquals(2, finishes) }
        compose.onNodeWithText("Licht zurücksetzen").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(initial.copy(exposure = 0f, highlights = 0f, shadows = 0f), committed) }
    }

    @Test fun sharpnessCanBeSetAndResetWithoutLosingCropOrToneAdjustments() {
        val state = mutableStateOf(initial)
        var committed: PhotoRecipe? = null
        compose.setContent { MaterialTheme {
            PhotoAdjustmentControls(PhotoToolPage.DETAILS, state.value, { state.value = it },
                { committed = state.value }, { state.value = it; committed = it })
        } }
        compose.onNodeWithContentDescription("Schärfe").performSemanticsAction(SemanticsActions.SetProgress) { it(.75f) }
        compose.runOnIdle { assertEquals(initial.copy(sharpness = .75f), committed) }
        compose.onNodeWithText("Details zurücksetzen").performClick()
        compose.runOnIdle { assertEquals(initial.copy(sharpness = 0f), committed) }
    }

    @Test fun colorCategoryContainsOnlyColorToolsAndResetKeepsNewToolsAndGeometry() {
        var reset: PhotoRecipe? = null
        compose.setContent { MaterialTheme { Column {
            PhotoAdjustmentControls(PhotoToolPage.COLOR, initial, {}, {}, { reset = it })
        } } }
        compose.onNodeWithContentDescription("Sättigung").assertExists()
        compose.onNodeWithContentDescription("Lichter").assertDoesNotExist()
        compose.onNodeWithContentDescription("Schärfe").assertDoesNotExist()
        compose.onNodeWithText("Farbe zurücksetzen").performClick()
        compose.runOnIdle { assertEquals(initial.copy(temperature = 0f), reset) }
    }
}
