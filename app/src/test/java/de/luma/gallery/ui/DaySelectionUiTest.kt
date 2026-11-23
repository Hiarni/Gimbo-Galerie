package de.luma.gallery.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import de.luma.gallery.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File
import java.time.Instant
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="de-w360dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DaySelectionUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var image:File
    private lateinit var originalZone:TimeZone
    @Before fun source() {
        originalZone=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        image=File.createTempFile("day-selection-", ".png",RuntimeEnvironment.getApplication().cacheDir)
        val bitmap=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(92,127,153))}
        image.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @After fun cleanup() {image.delete();TimeZone.setDefault(originalZone)}
    private val photos get()=listOf(
        GalleryMedia("1",Uri.fromFile(image),"Eins.jpg","Camera",Instant.parse("2026-10-12T12:00:00Z").toEpochMilli()),
        GalleryMedia("2",Uri.fromFile(image),"Zwei.jpg","Camera",Instant.parse("2026-10-12T08:00:00Z").toEpochMilli()),
        GalleryMedia("3",Uri.fromFile(image),"Drei.mp4","Camera",Instant.parse("2026-10-11T12:00:00Z").toEpochMilli(),isVideo=true)
    )
    @Test fun partialDaySelectionAddsTheRestAndClearingPreservesOtherDays() {
        var selection by mutableStateOf(setOf("1","3"))
        compose.setContent {LumaTheme(AppSettings()) {Surface(modifier=androidx.compose.ui.Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {MediaGrid(photos,3,emptySet(),selection,{}, {}, {}, {}, {selection=it})}}}
        val day=compose.onNodeWithTag("day_selection:2026-10-12")
        day.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState,ToggleableState.Indeterminate))
        val output=File("build/design84","day-selection.png");output.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view=compose.activity.window.decorView;val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
        day.performTouchInput {click()}
        compose.runOnIdle {assertEquals(setOf("1","2","3"),selection)}
        day.assertIsOn()
        compose.onNodeWithContentDescription("Zwei.jpg").assertIsSelected()
        day.performTouchInput {click()}
        compose.runOnIdle {assertEquals(setOf("3"),selection)}
        day.assertIsOff()
        compose.onNodeWithContentDescription("Drei.mp4").assertIsSelected()
    }
    @Test fun daySelectionUsesOnlyTheVisibleFilteredFiles() {
        var selection by mutableStateOf(emptySet<String>())
        val visible=photos.take(1)
        compose.setContent {LumaTheme(AppSettings()) {MediaGrid(visible,3,emptySet(),selection,{}, {}, {}, {}, {selection=it})}}
        compose.onNodeWithTag("day_selection:2026-10-12").performClick()
        compose.runOnIdle {assertEquals(setOf("1"),selection)}
        compose.onNodeWithContentDescription("Zwei.jpg").assertDoesNotExist()
    }
    @Test fun unknownDatesAreSeparateFromActualEpochDayPhotos() {
        var selection by mutableStateOf(emptySet<String>())
        val media=photos.take(2).mapIndexed {i,item->item.copy(timestamp=i.toLong())}
        compose.setContent {LumaTheme(AppSettings()) {Surface(modifier=androidx.compose.ui.Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {MediaGrid(media,3,emptySet(),selection,{}, {}, {}, {}, {selection=it})}}}
        compose.onNodeWithText("Datum unbekannt").assertExists()
        compose.onNodeWithTag("day_selection:unknown").performClick()
        compose.runOnIdle {assertEquals(setOf("1"),selection)}
        compose.onNodeWithTag("day_selection:1970-01-01").assertIsOff()
    }
    @Test fun pinchingOverADateHeadingChangesTheGridWithoutSelectingItsDay() {
        var selection by mutableStateOf(emptySet<String>());var columns by mutableIntStateOf(3)
        compose.setContent {LumaTheme(AppSettings()) {MediaGrid(photos,columns,emptySet(),selection,{}, {}, {}, {columns=it}, {selection=it})}}
        compose.onNodeWithTag("day_selection:2026-10-12").performTouchInput {
            down(0,center+androidx.compose.ui.geometry.Offset(-30f,0f));down(1,center+androidx.compose.ui.geometry.Offset(30f,0f))
            for(i in 1..5) {updatePointerTo(0,center+androidx.compose.ui.geometry.Offset(-30f-i*12,0f));updatePointerTo(1,center+androidx.compose.ui.geometry.Offset(30f+i*12,0f));move();advanceEventTime(20)}
            up(0);up(1)
        }
        compose.runOnIdle {assertEquals(2,columns);assertTrue(selection.isEmpty())}
    }
}
