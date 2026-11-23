package de.luma.gallery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import de.luma.gallery.GalleryState
import de.luma.gallery.localization.AppLanguage
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class EnglishUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsOfferAllLanguagesAndSelectionAndEnglishAppearanceControlsAreAccessible() {
        val language = mutableStateOf(AppLanguage.SYSTEM)
        var settings = AppSettings()
        compose.setContent { MaterialTheme {
            SettingsScreen(GalleryState(), {}, { settings = it }, {}, {}, language.value, { language.value = it })
        } }
        fun choose(name:String) {
            compose.onNodeWithContentDescription("Settings").performScrollToNode(hasContentDescription("Choose language"))
            compose.onNodeWithContentDescription("Choose language").performClick()
            compose.onNodeWithText(name).performScrollTo().performClick()
        }
        choose("Deutsch")
        compose.runOnIdle {assertEquals(AppLanguage.GERMAN,language.value)}
        choose("English")
        choose("System language")
        compose.onNodeWithContentDescription("Settings").performScrollToNode(hasText("Light"))
        compose.onNodeWithText("Light").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AppLanguage.SYSTEM, language.value); assertEquals(ThemeMode.LIGHT, settings.theme) }
        compose.onNodeWithContentDescription("Accent color #B9A7FF").performScrollTo().assertExists()
        compose.onNodeWithText("Einstellungen").assertDoesNotExist()
    }

    @Test fun englishPhotoTextControlsKeepUserContentAndObjectIdentity() {
        val state = mutableStateOf(emptyList<PhotoText>()); val selected = mutableStateOf<String?>(null)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            PhotoTextControls(state.value, state.value.find { it.id == selected.value }, { selected.value = it },
                { text -> state.value = state.value.map { if (it.id == text.id) text else it } }, {}, { state.value = it })
        } } }
        compose.onNodeWithText("Add text").performClick()
        compose.onNodeWithText("Text content").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Grüße")
        compose.onNodeWithText("Apply").performClick()
        val id = state.value.single().id
        compose.onNodeWithContentDescription("Bold").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(id, state.value.single().id); assertEquals("Grüße", state.value.single().text); assertTrue(state.value.single().bold) }
        compose.onNodeWithContentDescription("Font size (%)").performScrollTo().assertExists()
        compose.onNodeWithText("Remove text").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(state.value.isEmpty()) }
    }

    @Test fun englishDeleteDialogUsesSingularAndRequiresExplicitAction() {
        val item = GalleryMedia("a", android.net.Uri.parse("content://media/external/images/media/1"), "Grüße.jpg", "Familie", 0)
        var confirmed = false; var canceled = false
        compose.setContent { MaterialTheme { MediaActionDialog(PendingMediaAction(MediaAction.DELETE, listOf(item)), { canceled = true }, { confirmed = true }) } }
        compose.onNodeWithText("Delete permanently?").assertExists()
        compose.onNodeWithText("1 item").assertExists()
        compose.onNodeWithContentDescription("Item 1 of 1",substring=true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(canceled); assertFalse(confirmed) }
    }

    @Test fun englishCurvesExposeTranslatedChannelAndSliderLabels() {
        var result = PhotoCurves()
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            PhotoCurvesControls(result, { result = it }, {}, { result = it })
        } } }
        compose.onNodeWithText("Red").performClick()
        compose.onNodeWithContentDescription("Red: Midtones").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Tone curve Red").assertExists()
        compose.onNodeWithText("Reset channel").performScrollTo().performClick()
        assertEquals(PhotoCurves(), result)
    }
}
