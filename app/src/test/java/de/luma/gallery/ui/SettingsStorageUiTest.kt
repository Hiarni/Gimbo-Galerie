package de.luma.gallery.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import de.luma.gallery.GalleryState
import de.luma.gallery.localization.AppLanguage
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
class SettingsStorageUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unreadableSettingsDisableAppearanceButPermitLanguageAndExplicitRetry() {
        var retries = 0; var selected: AppLanguage? = null
        compose.setContent { MaterialTheme {
            SettingsScreen(GalleryState(settingsReady = false, settingsError = "Ladefehler"), {}, {}, {}, {},
                onLanguage = { selected = it }, onRetrySettings = { retries++ })
        } }
        compose.onNodeWithContentDescription("Einstellungen").performScrollToNode(hasText("Ladefehler"))
        compose.onNodeWithText("Ladefehler").assertExists()
        compose.onNodeWithText("Hell").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Einstellungen").performScrollToNode(hasContentDescription("Sprache auswählen"))
        compose.onNodeWithContentDescription("Sprache auswählen").performClick()
        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einstellungen").performScrollToNode(hasText("Einstellungen erneut laden"))
        compose.onNodeWithText("Einstellungen erneut laden").performClick()
        compose.runOnIdle { assertEquals(AppLanguage.ENGLISH, selected); assertEquals(1, retries) }
    }
}
