package de.luma.gallery.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import de.luma.gallery.GalleryState
import de.luma.gallery.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryInteractionTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var file:File
    @Before fun source() {
        file=File.createTempFile("gallery-interaction-", ".png",RuntimeEnvironment.getApplication().cacheDir)
        val bitmap=Bitmap.createBitmap(100,80,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.CYAN)}
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @After fun cleanup() {file.delete()}
    private fun media(i:Int)=GalleryMedia("photo-$i",Uri.fromFile(file),"Photo-$i.png","Camera",1000)
    @Test fun swipingViewerNavigatesAndDoubleTapDoesNotNavigate() {
        var index by mutableIntStateOf(1)
        compose.setContent {MaterialTheme {MediaViewer(media(index),GalleryState(),{}, {}, {}, {index+=it}, {}, {},true,true)}}
        compose.onNodeWithContentDescription("Photo-1.png").performTouchInput {swipeLeft()}
        compose.runOnIdle {assertEquals(2,index)}
        compose.onNodeWithContentDescription("Photo-2.png").performTouchInput {doubleClick()}
        compose.onNodeWithContentDescription("Photo-2.png").performTouchInput {swipeLeft()}
        compose.runOnIdle {assertEquals(2,index)}
    }
    @Test fun pinchingGridChangesColumnCountAndLongPressStartsSelection() {
        var columns by mutableIntStateOf(3);var selection by mutableStateOf(emptySet<String>())
        val media=(1..9).map(::media)
        compose.setContent {MaterialTheme {MediaGrid(media,columns,emptySet(),selection,onOpen={},onSelect={selection=selection+it.id},
            onAtTop={},onColumns={columns=it},onSelection={selection=it})}}
        compose.onNodeWithContentDescription("Photo-1.png").performTouchInput {longClick()}
        compose.runOnIdle {assertEquals(setOf("photo-1"),selection)}
        compose.onRoot().performTouchInput {
            down(0,center+Offset(-30f,0f));down(1,center+Offset(30f,0f))
            for(i in 1..5) {updatePointerTo(0,center+Offset(-30f-i*12,0f));updatePointerTo(1,center+Offset(30f+i*12,0f));move();advanceEventTime(20)}
            up(0);up(1)
        }
        compose.runOnIdle {assertEquals(2,columns);assertEquals(setOf("photo-1"),selection)}
    }
    @Test fun draggingAfterLongPressSelectsARange() {
        var selection by mutableStateOf(emptySet<String>())
        val media=(1..12).map(::media)
        compose.setContent {MaterialTheme {MediaGrid(media,3,emptySet(),selection,onOpen={},onSelect={selection=selection+it.id},
            onAtTop={},onColumns={},onSelection={selection=it})}}
        val grid=compose.onNodeWithContentDescription("Medienraster").fetchSemanticsNode().boundsInRoot
        val start=compose.onNodeWithContentDescription("Photo-1.png").fetchSemanticsNode().boundsInRoot.center-grid.topLeft
        val end=compose.onNodeWithContentDescription("Photo-3.png").fetchSemanticsNode().boundsInRoot.center-grid.topLeft
        compose.onNodeWithContentDescription("Medienraster").performTouchInput {
            down(start);advanceEventTime(700);moveTo(end,200);up()
        }
        compose.runOnIdle {assertEquals(setOf("photo-1","photo-2","photo-3"),selection)}
        compose.onNodeWithContentDescription("Photo-2.png").assertIsSelected()
    }
    @Test fun zoomingOutPastFitReturnsToOverview() {
        var returned=0
        compose.setContent {MaterialTheme {MediaViewer(media(1),GalleryState(),{returned++},{},{},{},{},{},false,false)}}
        compose.onNodeWithContentDescription("Photo-1.png").performTouchInput {
            down(0,center+Offset(-100f,0f));down(1,center+Offset(100f,0f))
            for(i in 1..6) {updatePointerTo(0,center+Offset(-100f+i*10,0f));updatePointerTo(1,center+Offset(100f-i*10,0f));move();advanceEventTime(20)}
            up(0);up(1)
        }
        compose.runOnIdle {assertEquals(1,returned)}
    }
    @Test fun rangeSelectionReversesCleanlyAndFiltersPreserveGeometryAndMarkup() {
        val ids=listOf("a","b","c","d")
        assertEquals(setOf("z","a","b","c"),rangeSelection(setOf("z"),ids,"c","a",true))
        assertEquals(setOf("d"),rangeSelection(ids.toSet(),ids,"a","c",false))
        assertEquals(setOf("z"),rangeSelection(setOf("z"),ids,"missing","a",true))
        val recipe=PhotoRecipe(quarterTurns=1,flipHorizontal=true,cropBounds=CropBounds(.1f,.1f,.8f,.8f),
            texts=listOf(PhotoText("text","Test")),masks=listOf(PhotoMask("mask")))
        PHOTO_PRESETS.forEach {preset->
            assertEquals(recipe,recipe.withPreset(preset.recipe,0f))
            val applied=recipe.withPreset(preset.recipe,1f)
            assertEquals(recipe.cropBounds,applied.cropBounds);assertEquals(recipe.quarterTurns,applied.quarterTurns)
            assertEquals(recipe.texts,applied.texts);assertEquals(recipe.masks,applied.masks)
        }
        assertNotSame(LumaIcons.FavoriteBorder,LumaIcons.Favorite)
    }
}
