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
class ProjectsUiTest {
    @get:Rule val compose = createComposeRule()
    private val project = EditorProject("id", "Urlaub", GalleryMedia("source", Uri.EMPTY, "Original.jpg", "Test", 0), PhotoRecipe(), null, 0, 0)

    @Test fun saveDialogRejectsBlankNameAndCreatesIndependentCopyWhenSelected() {
        var saved: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme { SaveProjectDialog("Urlaub", true, {}, { name, copy -> saved = name to copy }) } }
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasText("Projekt speichern") and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("  Variante  ")
        compose.onNode(isToggleable()).performClick()
        compose.onNode(hasText("Projekt speichern") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals("Variante" to true, saved) }
    }

    @Test fun projectRowOpensProjectAndRenameTargetsItsId() {
        var opened: String? = null
        var renamed: Pair<String, String>? = null
        compose.setContent { MaterialTheme { ProjectsScreen(listOf(project), false, null, {}, { opened = it }, { id, name -> renamed = id to name }, {}) } }
        compose.onNodeWithText("Urlaub").performClick()
        compose.runOnIdle { assertEquals("id", opened) }
        compose.onNodeWithContentDescription("Aktionen für Urlaub").performClick()
        compose.onNodeWithText("Umbenennen").performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithText("Umbenennen").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Neue Idee")
        compose.onNodeWithText("Umbenennen").performClick()
        compose.runOnIdle { assertEquals("id" to "Neue Idee", renamed) }
    }

    @Test fun deletingProjectRequiresConfirmationAndCancelLeavesItAlone() {
        var deleted: String? = null
        compose.setContent { MaterialTheme { ProjectsScreen(listOf(project), false, null, {}, {}, { _, _ -> }, { deleted = it }) } }
        compose.onNodeWithContentDescription("Aktionen für Urlaub").performClick()
        compose.onNodeWithText("Projekt entfernen").performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithText("Abbrechen").performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithContentDescription("Aktionen für Urlaub").performClick()
        compose.onNodeWithText("Projekt entfernen").performClick()
        compose.onNodeWithText("Projekt entfernen").performClick()
        compose.runOnIdle { assertEquals("id", deleted) }
    }
    @Test fun unavailablePreviewKeepsTheProjectRowUsable() {
        var opened: String? = null
        compose.setContent { MaterialTheme { ProjectsScreen(listOf(project), false, null, {}, { opened = it }, { _, _ -> }, {}) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Vorschau nicht verfügbar. Die Quelle muss lesbar bleiben.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Urlaub").performClick()
        compose.runOnIdle { assertEquals("id", opened) }
    }

    @Test @Config(qualifiers = "en") fun englishPreviewFallbackKeepsItsSourceExplanation() {
        compose.setContent { MaterialTheme { ProjectsScreen(listOf(project), false, null, {}, {}, { _, _ -> }, {}) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Preview unavailable. The source must remain readable.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Photo · Original.jpg").assertExists()
    }

}
