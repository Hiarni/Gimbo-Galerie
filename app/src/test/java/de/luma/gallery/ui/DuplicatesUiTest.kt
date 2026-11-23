package de.luma.gallery.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import de.luma.gallery.GalleryState
import de.luma.gallery.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="de-w360dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DuplicatesUiTest {
    @get:Rule val compose=createComposeRule()
    private val photos=(1..2).map {GalleryMedia("$it",Uri.parse("content://media/external/images/media/$it"),"$it.jpg","Camera",0,size=100)}
    private fun report(kind:DuplicateKind)=DuplicateReport(listOf(DuplicateGroup("group",kind,photos.map {DuplicateFile(it,"hash",100)})),2,0)
    @Test fun selectingCopiesKeepsOneUncheckedAndRequiresAReviewBeforeVerification() {
        var selected by mutableStateOf(emptySet<String>())
        var removal:Set<String>?=null
        compose.setContent {LumaTheme(AppSettings()) {DuplicatesScreen(GalleryState(media=photos,duplicateReport=report(DuplicateKind.EXACT)),selected,{selected=it},{},{_,_->},{},{},{_,_->},{removal=it})}}
        assertTrue(selected.isEmpty());assertNull(removal)
        compose.onNodeWithContentDescription("2.jpg zum Entfernen markieren").performScrollTo().performClick()
        assertEquals(setOf("2"),selected)
        compose.onNodeWithContentDescription("1.jpg zum Entfernen markieren").performScrollTo().assertIsNotEnabled().assertIsOff()
        compose.onNodeWithText("Diese Datei bleibt erhalten").assertExists();assertNull(removal)
        compose.onNodeWithText("Entfernen prüfen").performClick()
        assertNull(removal)
        compose.onNodeWithText("Markierte Dateien prüfen").performClick()
        assertEquals(setOf("2"),removal)
    }
    @Test fun similarPhotosHaveNoAutomaticExtraCopySelection() {
        var selected by mutableStateOf(emptySet<String>())
        compose.setContent {LumaTheme(AppSettings()) {DuplicatesScreen(GalleryState(media=photos,duplicateReport=report(DuplicateKind.SIMILAR)),selected,{selected=it},{},{_,_->},{},{},{_,_->},{})}}
        compose.onNodeWithText("Zusätzliche identische Kopien markieren").assertDoesNotExist()
        assertTrue(selected.isEmpty())
    }
}
