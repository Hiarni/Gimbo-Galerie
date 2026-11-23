package de.luma.gallery.ui

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
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
class AlbumsUiTest {
    @get:Rule val compose = createComposeRule()
    private val album = VirtualAlbum("album-id", "Eigen", setOf("photo", "missing"), 0, 0)
    private val photo = GalleryMedia("photo", Uri.EMPTY, "Foto.jpg", "Kamera", 0)

    @Test fun albumsCanBeReorderedSelectedAndRemovedAsCollections() {
        val second=album.copy(id="second",name="Zwei")
        var order by mutableStateOf(emptyList<String>())
        var removed=emptySet<String>()
        compose.setContent {MaterialTheme {AlbumsScreen(listOf(album,second),listOf(photo),false,null,false,{},{},{},{_,_->},{},
            order=order,onOrder={order=it},onDeleteAlbums={removed=it})}}
        compose.onNodeWithContentDescription("Albumaktionen für Eigen").performClick()
        compose.onNodeWithText("Nach unten").performClick()
        compose.runOnIdle {assertEquals(listOf("virtual:second","virtual:album-id"),order)}
        compose.onNodeWithContentDescription("Albumvorschau Eigen").performTouchInput {longClick()}
        compose.onNodeWithText("1 ausgewählt").assertExists()
        compose.onNodeWithText("Album entfernen").performClick()
        compose.onNode(hasText("Album entfernen") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle {assertEquals(setOf("album-id"),removed)}
    }
    @Test fun newAlbumDialogRejectsBlankAndDuplicateNamesAndTrimsTheResult() {
        var saved: String? = null
        compose.setContent { MaterialTheme { AlbumNameDialog("", false, setOf(albumNameKey("Eigen")), false, {}, { saved = it }) } }
        compose.onNodeWithText("Album anlegen").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("EIGEN")
        compose.onNodeWithText("Album anlegen").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("  Neu  ")
        compose.onNodeWithText("Album anlegen").performClick()
        compose.runOnIdle { assertEquals("Neu", saved) }
    }

    @Test fun overviewSeparatesFoldersAndAlbumsAndTargetsRenameById() {
        var opened: String? = null; var renamed: Pair<String, String>? = null
        compose.setContent { MaterialTheme { AlbumsScreen(listOf(album), listOf(photo), false, null, false,
            { opened = it }, {}, {}, { id, name -> renamed = id to name }, {}) } }
        compose.onNodeWithText("Eigene Alben").assertExists()
        compose.onNodeWithText("Ordner auf dem Gerät").assertExists()
        compose.onNodeWithText("1 sichtbar · 2 zugeordnet").assertExists()
        compose.onNodeWithText("Eigen").performClick()
        compose.runOnIdle { assertEquals("album-id", opened) }
        compose.onNodeWithContentDescription("Albumaktionen für Eigen").performClick()
        compose.onNodeWithText("Umbenennen").performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("Neue Sammlung")
        compose.onNodeWithText("Umbenennen").performClick()
        compose.runOnIdle { assertEquals("album-id" to "Neue Sammlung", renamed) }
    }

    @Test fun removingAnAlbumRequiresConfirmationAndCancelDoesNothing() {
        var removed: String? = null
        compose.setContent { MaterialTheme { AlbumsScreen(listOf(album), emptyList(), false, null, false,
            {}, {}, {}, { _, _ -> }, { removed = it }) } }
        compose.onNodeWithContentDescription("Albumaktionen für Eigen").performClick()
        compose.onNodeWithText("Album entfernen").performClick()
        compose.runOnIdle { assertNull(removed) }
        compose.onNodeWithText("Abbrechen").performClick()
        compose.runOnIdle { assertNull(removed) }
        compose.onNodeWithContentDescription("Albumaktionen für Eigen").performClick()
        compose.onNodeWithText("Album entfernen").performClick()
        compose.onNodeWithText("Album entfernen").performClick()
        compose.runOnIdle { assertEquals("album-id", removed) }
    }

    @Test fun pickerCanTargetAnExistingAlbumOrCreateOneForTheSelection() {
        var target: String? = null; var created: String? = null
        compose.setContent { MaterialTheme { AlbumPickerDialog(listOf(album), 2, false, null, false, {}, { target = it }, { created = it }) } }
        compose.onNodeWithText("Eigen").performClick()
        compose.runOnIdle { assertEquals("album-id", target) }
        compose.onNodeWithText("Neues Album für Auswahl").performClick()
        compose.onNodeWithText("Anlegen und hinzufügen").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Urlaub")
        compose.onNodeWithText("Anlegen und hinzufügen").performClick()
        compose.runOnIdle { assertEquals("Urlaub", created) }
    }

    @Test fun pendingWriteDisablesAlbumPickerAndNewAlbumCreation() {
        compose.setContent { MaterialTheme { AlbumPickerDialog(listOf(album), 2, false, null, true, {}, { fail("Duplicate action") }, {}) } }
        compose.onNodeWithText("Eigen").assertIsNotEnabled()
        compose.onNodeWithText("Neues Album für Auswahl").assertIsNotEnabled()
    }
}
