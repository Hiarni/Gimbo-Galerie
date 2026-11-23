package de.luma.gallery.ui

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
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
@Config(sdk=[29],qualifiers="de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AlbumFolderInteractionTest {
    @get:Rule val compose=createComposeRule()
    private val media=listOf("Kamera","Screenshots").mapIndexed {i,name->GalleryMedia("photo$i",Uri.EMPTY,"Photo$i.jpg",name,0)}

    @Test fun foldersAreSelectedInTheOverviewAndSingleFolderActionsTargetTheFolder() {
        var opened:String?=null
        var action:Pair<String,FolderAction>?=null
        compose.setContent {MaterialTheme {AlbumsScreen(emptyList(),media,false,null,false,{},{opened=it},{},{_,_->},{},onFolderAction={name,a->action=name to a})}}
        compose.onNodeWithContentDescription("Albumvorschau Kamera").performTouchInput {longClick()}
        compose.onNodeWithText("1 ausgewählt").assertExists()
        compose.onNodeWithText("Umbenennen").performClick()
        compose.runOnIdle {assertNull(opened);assertEquals("Kamera" to FolderAction.RENAME,action)}
        compose.onNodeWithText("Screenshots").performClick()
        compose.onNodeWithText("2 ausgewählt").assertExists()
        compose.onNodeWithText("Eigene Alben").assertExists()
        compose.onNodeWithText("Album entfernen").assertDoesNotExist()
        compose.runOnIdle {assertNull(opened)}
    }

    @Test fun albumPinchResizesTheGridAndDraggingSelectsFoldersWithoutOpeningThem() {
        var columns by mutableIntStateOf(3)
        var opened=0
        val folders=(1..6).map {GalleryMedia("photo$it",Uri.EMPTY,"Photo$it.jpg","Folder$it",0)}
        compose.setContent {MaterialTheme {AlbumsScreen(emptyList(),folders,false,null,false,{},{opened++},{},{_,_->},{},columns=columns,onColumns={columns=it})}}
        compose.onNodeWithContentDescription("Albumraster").performTouchInput {
            down(0,center+Offset(-30f,0f));down(1,center+Offset(30f,0f))
            for(i in 1..5) {updatePointerTo(0,center+Offset(-30f-i*12,0f));updatePointerTo(1,center+Offset(30f+i*12,0f));move();advanceEventTime(20)}
            up(0);up(1)
        }
        compose.runOnIdle {assertEquals(2,columns);assertEquals(0,opened)}
        val grid=compose.onNodeWithContentDescription("Albumraster").fetchSemanticsNode().boundsInRoot
        val a=compose.onNodeWithContentDescription("Albumvorschau Folder1").fetchSemanticsNode().boundsInRoot.center-grid.topLeft
        val b=compose.onNodeWithContentDescription("Albumvorschau Folder2").fetchSemanticsNode().boundsInRoot.center-grid.topLeft
        compose.onNodeWithContentDescription("Albumraster").performTouchInput {down(a);advanceEventTime(700);moveTo(b,200);up()}
        compose.onNodeWithText("2 ausgewählt").assertExists()
        compose.runOnIdle {assertEquals(0,opened)}
    }

    @Test fun scrollingDuringFolderSelectionKeepsHeaderNotificationsStable() {
        val events=mutableListOf<Boolean>()
        val folders=(1..40).map {GalleryMedia("photo$it",Uri.EMPTY,"Photo$it.jpg","Folder%02d".format(it),0)}
        compose.setContent {MaterialTheme {AlbumsScreen(emptyList(),folders,false,null,false,{},{},{},{_,_->},{},onAtTop={events+=it})}}
        compose.onNodeWithContentDescription("Albumvorschau Folder01").performTouchInput {longClick()}
        compose.runOnIdle {events.clear()}
        compose.onNodeWithContentDescription("Albumraster").performScrollToNode(hasText("Folder35"))
        compose.onNodeWithText("1 ausgewählt").assertExists()
        compose.runOnIdle {assertTrue(events.isEmpty())}
    }

    @Test fun importedCollectionIsSeparateFromDeviceFoldersAndCanRemoveItsReferences() {
        val imported=GalleryMedia("reference",Uri.EMPTY,"Imported.jpg","Importiert",0,isImported=true)
        val native=GalleryMedia("native",Uri.EMPTY,"Local.jpg","Kamera",0)
        var removed=emptySet<String>()
        compose.setContent {MaterialTheme {AlbumsScreen(emptyList(),listOf(imported,native),false,null,false,{},{},{},{_,_->},{},
            importedReferences=setOf("reference"),onRemoveImports={removed=it})}}
        compose.onNodeWithText("Sammlung von Dateiverweisen").assertExists()
        compose.onNodeWithText("Importiert").assertDoesNotExist()
        compose.onNodeWithText("Kamera").assertExists()
        compose.onNodeWithContentDescription("Albumaktionen für Importierte Dateien").performClick()
        compose.onNodeWithText("Aus Galerie entfernen").performClick()
        compose.runOnIdle {assertEquals(setOf("reference"),removed)}
    }
}
