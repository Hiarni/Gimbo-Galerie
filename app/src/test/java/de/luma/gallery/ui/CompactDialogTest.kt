package de.luma.gallery.ui

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="en-w320dp-h480dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CompactDialogTest {
    @get:Rule val compose=createComposeRule()

    @Test fun photoExportKeepsActionsInsideThePanelWithLargeTextAndScrollableSettings() {
        var saved:PhotoExportOptions?=null
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.7f)) {
            MaterialTheme {PhotoExportDialog(PhotoExportOptions(),{}, {saved=it})}
        }}
        assertContained()
        compose.onNodeWithText("Save copy").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(PhotoExportOptions(),saved)}
        compose.onNodeWithTag("compact_dialog_content").performScrollToNode(hasText("4096 px"))
        compose.onNodeWithText("4096 px").performClick()
        compose.onNodeWithText("8192 px").performClick()
        compose.onNodeWithText("Save copy").performClick()
        compose.runOnIdle {assertEquals(8192,saved?.maxEdge)}
        assertContained()
    }

    @Test fun fileInformationIsBoundedAndKeepsStorageDetailsBehindACategory() {
        val media=GalleryMedia("photo",Uri.EMPTY,"A very long filename with additional words.jpg","A long album name",1,size=12345)
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.7f)) {
            MaterialTheme {MediaDetailsDialog(media,{})}
        }}
        compose.waitUntil(10_000) {compose.onAllNodesWithText(media.name).fetchSemanticsNodes().isNotEmpty()}
        assertContained()
        compose.onNodeWithTag("compact_dialog_content").performScrollToNode(hasText("Storage and file reference"))
        compose.onNodeWithText("Storage and file reference").performClick()
        compose.onNodeWithTag("compact_dialog_content").performScrollToNode(hasText("Storage URI"))
        compose.onNodeWithText("Storage URI").assertExists()
        assertContained()
    }

    @Test
    @Config(qualifiers="en-w640dp-h320dp")
    fun landscapeExportKeepsCopyAndReplacementActionsInsideThePanel() {
        val media=GalleryMedia("photo",Uri.parse("content://media/external/images/media/1"),"Photo.jpg","Camera",0)
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.5f)) {
            MaterialTheme {PhotoExportDialog(PhotoExportOptions(),{}, {},overwriteItem=media,onOverwrite={})}
        }}
        assertContained()
        compose.onNodeWithText("Save copy").assertIsDisplayed()
        val panel=compose.onNodeWithTag("compact_dialog").fetchSemanticsNode().boundsInRoot
        val copy=compose.onNodeWithText("Save copy").fetchSemanticsNode().boundsInRoot
        assertTrue(copy.left>=panel.left && copy.right<=panel.right && copy.bottom<=panel.bottom)
    }

    private fun assertContained() {
        val panel=compose.onNodeWithTag("compact_dialog").fetchSemanticsNode().boundsInRoot
        val content=compose.onNodeWithTag("compact_dialog_content").fetchSemanticsNode().boundsInRoot
        val actions=compose.onNodeWithTag("compact_dialog_actions").fetchSemanticsNode().boundsInRoot
        assertTrue(content.left>=panel.left && content.right<=panel.right)
        assertTrue(actions.left>=panel.left && actions.right<=panel.right)
        assertTrue(content.bottom<=actions.top+1)
        assertTrue(actions.bottom<=panel.bottom+1)
    }

    @Test
    @Config(qualifiers="en-w640dp-h320dp")
    fun replacementReviewCanBeCancelledWithLargeTextWithoutRequestingAWrite() {
        var writes=0
        val media=GalleryMedia("photo",Uri.parse("content://media/external/images/media/1"),"A very long source filename.jpg","Camera",0)
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.7f)) {
            MaterialTheme {OverwriteButton(media,onOverwrite={writes++})}
        }}
        compose.onNodeWithText("Overwrite original").performClick()
        assertContained()
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.onNodeWithTag("compact_dialog").assertDoesNotExist()
        compose.runOnIdle {assertEquals(0,writes)}
    }
}
