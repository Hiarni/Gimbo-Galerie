package de.luma.gallery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class PhotoCurvesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun accessibleSliderTargetsSelectedChannelAndKeepsOtherChannels() {
        val state = mutableStateOf(PhotoCurves(rgb = ToneCurve(midtones = .6f)))
        var committed: PhotoCurves? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            PhotoCurvesControls(state.value, { state.value = it }, { committed = state.value }, { state.value = it })
        } } }
        compose.onNodeWithText("Rot").performClick()
        compose.onNodeWithContentDescription("Rot: Mitte").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.75f) }
        compose.runOnIdle {
            assertEquals(.75f, committed!!.red.midtones, 0f)
            assertEquals(.6f, committed!!.rgb.midtones, 0f)
            assertEquals(ToneCurve(), committed!!.green); assertEquals(ToneCurve(), committed!!.blue)
        }
    }

    @Test fun draggingTheGraphChangesOnlyOnePointAndCommitsOnceInsideScrollingTools() {
        val state = mutableStateOf(PhotoCurves())
        var commits = 0
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            PhotoCurvesControls(state.value, { state.value = it }, { commits++ }, { state.value = it })
        } } }
        compose.onNodeWithContentDescription("Tonwertkurve RGB").performScrollTo().performTouchInput {
            swipe(center, center - Offset(0f, height * .25f))
        }
        compose.runOnIdle {
            assertTrue(state.value.rgb.midtones > .65f)
            assertEquals(0f, state.value.rgb.black, 0f); assertEquals(.25f, state.value.rgb.shadows, 0f)
            assertEquals(.75f, state.value.rgb.highlights, 0f); assertEquals(1f, state.value.rgb.white, 0f)
            assertEquals(ToneCurve(), state.value.red); assertEquals(1, commits)
        }
    }

    @Test fun channelResetAndAllResetAreSeparateAndPresetsTargetOnlyCurrentChannel() {
        val initial = PhotoCurves(rgb = ToneCurve(shadows = .3f), red = ToneCurve(midtones = .7f))
        val state = mutableStateOf(initial)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            PhotoCurvesControls(state.value, { state.value = it }, {}, { state.value = it })
        } } }
        compose.onNodeWithText("Blau").performScrollTo().performClick()
        compose.onNodeWithText("Kontrastkurve").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(initial.copy(blue = ToneCurve(shadows = .18f, highlights = .82f)), state.value) }
        compose.onNodeWithText("Kanal zurücksetzen").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(initial, state.value) }
        compose.onNodeWithText("Kurven zurücksetzen").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(PhotoCurves(), state.value) }
    }

    @Test fun selectableEndpointsCanLiftBlackAndLowerWhite() {
        val state = mutableStateOf(PhotoCurves())
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            PhotoCurvesControls(state.value, { state.value = it }, {}, { state.value = it })
        } } }
        compose.onNodeWithText("Schwarz").performScrollTo().performClick()
        compose.onNodeWithContentDescription("RGB: Schwarz").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.2f) }
        compose.onNodeWithContentDescription("Tonwertpunkte").performScrollToNode(hasText("Weiß"))
        compose.onNodeWithText("Weiß").performScrollTo().performClick()
        compose.onNodeWithContentDescription("RGB: Weiß").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.8f) }
        compose.runOnIdle { assertEquals(ToneCurve(black = .2f, white = .8f), state.value.rgb) }
    }
}
