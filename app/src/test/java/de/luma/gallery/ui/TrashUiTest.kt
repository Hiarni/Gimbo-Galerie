package de.luma.gallery.ui

import android.net.Uri
import androidx.compose.material3.MaterialTheme
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
class TrashUiTest {
    @get:Rule val compose = createComposeRule()
    private val photo = GalleryMedia("1", Uri.EMPTY, "Foto.jpg", "Urlaub", 0)
    private val video = GalleryMedia("2", Uri.EMPTY, "Video.mp4", "Urlaub", 0, true)

    @Test fun selectionTargetsChosenMediaAndCanBeCleared() {
        var requested: PendingMediaAction? = null
        compose.setContent { MaterialTheme { TrashScreen(listOf(TrashMedia(photo), TrashMedia(video)), false, null,
            false, true, false, {}, {}, {}, { action, items -> requested = PendingMediaAction(action, items) }) } }
        compose.onNodeWithText("Wiederherstellen").assertIsNotEnabled()
        compose.onNodeWithText("Foto.jpg").performClick()
        compose.onNodeWithText("Wiederherstellen").performClick()
        compose.runOnIdle { assertEquals(PendingMediaAction(MediaAction.RESTORE, listOf(photo)), requested) }
        compose.onNodeWithText("Auswahl aufheben").performClick()
        compose.onNodeWithText("Endgültig löschen").assertIsNotEnabled()
        compose.onNodeWithText("Alle auswählen").performClick()
        compose.onNodeWithText("Endgültig löschen").performClick()
        compose.runOnIdle { assertEquals(PendingMediaAction(MediaAction.DELETE, listOf(photo, video)), requested) }
    }

    @Test fun deletionPreviewLetsUsersReconsiderAndOnlyConfirmsRemainingItems() {
        var result=emptyList<GalleryMedia>()
        compose.setContent {MaterialTheme {MediaActionDialog(PendingMediaAction(MediaAction.TRASH,listOf(photo,video)),{}, {result=it})}}
        compose.onNodeWithContentDescription("Medium 1 von 2",substring=true).performClick()
        compose.onNodeWithText("1 Medium").assertExists()
        compose.onNodeWithText("In den Papierkorb").performClick()
        compose.runOnIdle {assertEquals(listOf(video),result)}
    }
    @Test fun aCompletelyDeselectedPreviewDisablesDestructiveConfirmation() {
        var confirmed=false
        compose.setContent {MaterialTheme {MediaActionDialog(PendingMediaAction(MediaAction.DELETE,listOf(photo)),{}, {confirmed=true})}}
        compose.onNodeWithContentDescription("Medium 1 von 1",substring=true).performClick()
        compose.onNodeWithText("Endgültig löschen").assertIsNotEnabled()
        compose.runOnIdle {assertFalse(confirmed)}
    }

    @Test fun irreversibleDeleteNeedsExplicitConfirmationAndCancelDoesNothing() {
        var confirmed = 0; var dismissed = 0
        compose.setContent { MaterialTheme { MediaActionDialog(PendingMediaAction(MediaAction.DELETE, listOf(photo)),
            { dismissed++ }, { confirmed++ }) } }
        compose.onNodeWithContentDescription("Medium 1 von 1",substring=true).assertExists()
        compose.runOnIdle { assertEquals(0, confirmed) }
        compose.onNodeWithText("Abbrechen").performClick()
        compose.runOnIdle { assertEquals(1, dismissed); assertEquals(0, confirmed) }
        compose.onNodeWithText("Endgültig löschen").performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun missingAccessIsNotPresentedAsKnownEmptySystemTrash() {
        var access = 0
        compose.setContent { MaterialTheme { TrashScreen(emptyList(), false, null, true, true, false,
            {}, {}, { access++ }, { _, _ -> }) } }
        compose.onNodeWithText("Keine Einträge sichtbar").assertExists()
        compose.onNodeWithText("Medienzugriff verwalten").performClick()
        compose.runOnIdle { assertEquals(1, access) }
        compose.onNodeWithText("Wiederherstellen").assertDoesNotExist()
        compose.onNodeWithText("Endgültig löschen").assertDoesNotExist()
    }

    @Test fun android10DisplaysUnsupportedStateWithoutDeleteControls() {
        compose.setContent { MaterialTheme { TrashScreen(listOf(TrashMedia(photo)), false, null, false, false, false,
            {}, {}, {}, { _, _ -> fail("Unsupported action") }) } }
        compose.onNodeWithText("Ab Android 11 verfügbar").assertExists()
        compose.onNodeWithText("Endgültig löschen").assertDoesNotExist()
    }

    @Test fun pendingSystemActionPreventsRepeatedRequests() {
        compose.setContent { MaterialTheme { TrashScreen(listOf(TrashMedia(photo)), false, null, false, true, true,
            {}, {}, {}, { _, _ -> fail("Repeated request") }) } }
        compose.onNodeWithText("Alle auswählen").assertIsNotEnabled()
        compose.onNodeWithText("Wiederherstellen").assertIsNotEnabled()
        compose.onNodeWithText("Endgültig löschen").assertIsNotEnabled()
    }
}
