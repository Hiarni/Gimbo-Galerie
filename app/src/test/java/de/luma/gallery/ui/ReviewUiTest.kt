package de.luma.gallery.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class ReviewUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var image:java.io.File
    @Before fun source() {
        image=java.io.File.createTempFile("review-test-", ".png",org.robolectric.RuntimeEnvironment.getApplication().cacheDir)
        val bitmap=android.graphics.Bitmap.createBitmap(600,900,android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).drawRect(0f,0f,600f,900f,android.graphics.Paint().apply {
            shader=android.graphics.LinearGradient(0f,0f,600f,900f,intArrayOf(0xFF477A83.toInt(),0xFFF2B790.toInt(),0xFF2C405B.toInt()),null,android.graphics.Shader.TileMode.CLAMP)
        })
        image.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @After fun cleanup() {image.delete()}
    private val photos get()=(1..2).map {GalleryMedia("$it",Uri.fromFile(image),"$it.jpg","Camera",0)}
    @Test fun swipingDoesNotApplyAnythingUntilTheSummaryIsConfirmedAndUndoWorks() {
        var applied:ReviewSession?=null
        compose.setContent {LumaTheme(AppSettings()) {ReviewScreen(photos.map {it.id},photos,AppSettings(),{}, {applied=it})}}
        compose.onNodeWithTag("review_card").performTouchInput {swipeLeft()}
        compose.onNodeWithText("1 von 2").assertExists();assertNull(applied)
        compose.onNodeWithContentDescription("Rückgängig").performClick()
        compose.onNodeWithText("0 von 2").assertExists()
        compose.onNodeWithTag("review_card").performTouchInput {swipeRight()}
        compose.onNodeWithTag("review_card").performTouchInput {swipeLeft()}
        compose.onNodeWithText("Alles durchgesehen").assertExists();assertNull(applied)
        compose.onNodeWithText("Entscheidungen prüfen").performClick()
        compose.onNodeWithText("Entscheidungen übernehmen").performClick()
        assertEquals(setOf("1"),applied!!.selected(ReviewAction.KEEP));assertEquals(setOf("2"),applied!!.selected(ReviewAction.REMOVE))
    }
    @Test fun disablingGesturesKeepsTheMenuAvailableWithoutFooterButtons() {
        val settings=AppSettings(reviewGestures=false,reviewLeft=ReviewAction.ARCHIVE,reviewRight=ReviewAction.FAVORITE)
        compose.setContent {LumaTheme(settings) {ReviewScreen(photos.map {it.id},photos,settings,{}, {})}}
        compose.onNodeWithTag("review_card").performTouchInput {swipeLeft()}
        compose.onNodeWithText("0 von 2").assertExists()
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("Favorit").performClick();compose.onNodeWithText("1 von 2").assertExists()
    }

    @Test fun dragStampUpdatesBeforeReleaseAndShortSwipesDoNotMakeAChoice() {
        compose.setContent {LumaTheme(AppSettings()) {androidx.compose.material3.Surface(color=androidx.compose.material3.MaterialTheme.colorScheme.background) {ReviewScreen(photos.map {it.id},photos,AppSettings(),{}, {})}}}
        compose.onNodeWithTag("review_card").performTouchInput {down(center);moveTo(center+androidx.compose.ui.geometry.Offset(-width*.17f,0f),200)}
        compose.onNodeWithTag("review_drag_stamp").assertExists()
        compose.onNodeWithText("Weg damit").assertExists()
        compose.onNodeWithText("Weiterwischen zum Auswählen").assertDoesNotExist()
        compose.onNodeWithText("0 von 2").assertExists()
        compose.onNodeWithTag("review_card").performTouchInput {up()}
        compose.onNodeWithText("0 von 2").assertExists()
        compose.onNodeWithTag("review_drag_stamp").assertDoesNotExist()
        compose.onNodeWithTag("review_card").performTouchInput {down(center);moveTo(center+androidx.compose.ui.geometry.Offset(-width*.4f,0f),200)}
        compose.onNodeWithText("Loslassen").assertExists()
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        val stamp=compose.onNodeWithTag("review_drag_stamp").fetchSemanticsNode().boundsInRoot
        assertTrue(stamp.left>=root.left && stamp.right<=root.right)
        capture("review-swiping")
        compose.onNodeWithTag("review_card").performTouchInput {up()}
        compose.onNodeWithText("1 von 2").assertExists()
        compose.onNodeWithContentDescription("Rückgängig").performClick()
        compose.onNodeWithText("0 von 2").assertExists()
    }
    @Test fun customRightSwipeShowsArchiveAndMissingSourcesOnlySkip() {
        var applied:ReviewSession?=null
        val settings=AppSettings(reviewLeft=ReviewAction.FAVORITE,reviewRight=ReviewAction.ARCHIVE)
        compose.setContent {LumaTheme(settings) {ReviewScreen(listOf("gone","1"),photos,settings,{}, {applied=it})}}
        compose.onNodeWithText("Datei nicht verfügbar").assertExists()
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("Überspringen").performClick()
        compose.onNodeWithTag("review_card").performTouchInput {down(center);moveTo(center+androidx.compose.ui.geometry.Offset(width*.4f,0f),200)}
        compose.onNodeWithText("Ab ins Archiv").assertExists()
        compose.onNodeWithTag("review_card").performTouchInput {up()}
        compose.onNodeWithText("Entscheidungen prüfen").performClick()
        compose.onNodeWithText("Entscheidungen übernehmen").performClick()
        assertEquals(setOf("gone"),applied!!.selected(ReviewAction.SKIP))
        assertEquals(setOf("1"),applied!!.selected(ReviewAction.ARCHIVE))
        assertTrue(applied!!.selected(ReviewAction.REMOVE).isEmpty())
    }
    @Test @Config(qualifiers="de-w320dp-h480dp")
    fun largeTextKeepsHeaderActionsReachableAndCalmModeHasNoFooterButtons() {
        val settings=AppSettings(reviewPlayful=false,thumbnailAnimations=false)
        compose.setContent {androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density,1.5f)) {
            LumaTheme(settings) {androidx.compose.material3.Surface(color=androidx.compose.material3.MaterialTheme.colorScheme.background) {ReviewScreen(photos.map {it.id},photos,settings,{}, {})}}
        }}
        compose.onNodeWithTag("review_card").performTouchInput {swipeRight()}
        compose.onNodeWithText("Bleibt!").assertDoesNotExist()
        compose.onNodeWithContentDescription("Rückgängig").assertIsDisplayed().performClick()
        compose.onNodeWithText("0 von 2").assertExists()
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        listOf("review_left_action","review_right_action","review_skip_action").forEach {compose.onNodeWithTag(it).assertDoesNotExist()}
        val hints=compose.onNodeWithTag("review_directions").fetchSemanticsNode().boundsInRoot
        val photo=compose.onNodeWithTag("review_card").fetchSemanticsNode().boundsInRoot
        assertTrue(hints.left>=root.left && hints.right<=root.right && hints.bottom<=root.bottom && hints.top>=photo.bottom)
        capture("review-large-text")
    }
    @Test fun finishingEarlyExplainsUnreviewedFilesAndLeavingNeedsConfirmation() {
        var applied:ReviewSession?=null;var left=false
        compose.setContent {LumaTheme(AppSettings()) {ReviewScreen(photos.map {it.id},photos,AppSettings(),{left=true}, {applied=it})}}
        compose.onNodeWithTag("review_card").performTouchInput {swipeRight()}
        compose.onNodeWithContentDescription("Abschließen").performClick()
        compose.onNodeWithText("1 noch nicht durchgesehen; diese bleiben unverändert.").assertExists()
        compose.onNodeWithText("Abbrechen").assertDoesNotExist()
        compose.onNodeWithText("Zurück").performClick()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Ohne Übernehmen verlassen?").assertExists()
        assertFalse(left);assertNull(applied)
        compose.onNodeWithText("Entscheidungen verwerfen").performClick()
        assertTrue(left);assertNull(applied)
    }
    @Test fun leavingDuringTheCardAnimationReturnsToTheSamePhotoWhenCanceled() {
        compose.setContent {LumaTheme(AppSettings()) {ReviewScreen(photos.map {it.id},photos,AppSettings(),{}, {})}}
        compose.onNodeWithTag("review_card").performTouchInput {swipeRight()}
        compose.onNodeWithText("1 von 2").assertExists()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("review_card").performTouchInput {swipeLeft()}
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Abbrechen").performClick()
        compose.mainClock.autoAdvance=true
        compose.onNodeWithText("1 von 2").assertExists()
        val card=compose.onNodeWithTag("review_card").fetchSemanticsNode().boundsInRoot
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(card.center.x>=root.left && card.center.x<=root.right)
        compose.onNodeWithTag("review_card").performTouchInput {swipeRight()}
        compose.onNodeWithText("Alles durchgesehen").assertExists()
    }
    @Test @Config(qualifiers="de-w640dp-h360dp")
    fun landscapeKeepsThePhotoLargeAndDirectionsBelowIt() {
        compose.setContent {LumaTheme(AppSettings()) {androidx.compose.material3.Surface(color=androidx.compose.material3.MaterialTheme.colorScheme.background) {ReviewScreen(photos.map {it.id},photos,AppSettings(),{}, {})}}}
        val photo=compose.onNodeWithTag("review_card").fetchSemanticsNode().boundsInRoot
        val hints=compose.onNodeWithTag("review_directions").fetchSemanticsNode().boundsInRoot
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(photo.width>=root.width*.75f && photo.left>=root.left && photo.right<=root.right)
        assertTrue(hints.top>=photo.bottom && hints.bottom<=root.bottom)
        capture("review-landscape")
        compose.onNodeWithTag("review_card").performTouchInput {swipeRight()}
        compose.onNodeWithContentDescription("Rückgängig").performClick()
        compose.onNodeWithText("0 von 2").assertExists()
    }
    @Test @Config(qualifiers="de-w640dp-h740dp")
    fun resizingDuringASwipeKeepsGesturesAndUndoWorking() {
        var landscape by mutableStateOf(false)
        compose.setContent {LumaTheme(AppSettings()) {androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.requiredSize(640.dp,if(landscape) 360.dp else 740.dp)) {ReviewScreen(photos.map {it.id},photos,AppSettings(),{}, {})}}}
        compose.onNodeWithTag("review_card").assertExists()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("review_card").performTouchInput {swipeLeft()}
        compose.runOnUiThread {landscape=true}
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithText("1 von 2").assertExists()
        compose.onNodeWithContentDescription("Rückgängig").assertIsEnabled().performClick()
        compose.onNodeWithTag("review_card").performTouchInput {swipeRight()}
        compose.onNodeWithText("1 von 2").assertExists()
        compose.onNodeWithContentDescription("Rückgängig").performClick()
        compose.onNodeWithText("0 von 2").assertExists()
    }
    @Test fun repeatedChoicesShowDifferentBubblesAndDoNotApplyAnything() {
        val items=(1..4).map {GalleryMedia("$it",Uri.fromFile(image),"$it.jpg","Camera",0)}
        var applied:ReviewSession?=null
        compose.setContent {LumaTheme(AppSettings()) {ReviewScreen(items.map {it.id},items,AppSettings(),{}, {applied=it})}}
        listOf("Guter Fang!","Das darf bleiben","Passt zu dir").forEachIndexed {index,title->
            compose.onNodeWithTag("review_card").performTouchInput {swipeRight()}
            compose.onNodeWithText("${index+1} von 4").assertExists()
            compose.onNodeWithText(title).assertExists()
            assertNull(applied)
        }
        capture("review-bubble")
    }
    @Test fun screenReaderCanMakeAChoiceAndRapidGesturesCommitOnlyOnce() {
        compose.setContent {LumaTheme(AppSettings()) {ReviewScreen(photos.map {it.id},photos,AppSettings(),{}, {})}}
        val actions=compose.onNodeWithTag("review_card").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        compose.runOnIdle {assertTrue(actions.single {it.label=="Behalten"}.action())}
        compose.onNodeWithText("1 von 2").assertExists()
        compose.onNodeWithContentDescription("Rückgängig").performClick()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("review_card").performTouchInput {swipeLeft();swipeRight()}
        compose.mainClock.advanceTimeBy(700)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithText("1 von 2").assertExists()
        compose.onNodeWithContentDescription("Rückgängig").performClick()
        compose.onNodeWithText("0 von 2").assertExists()
    }
    @Test fun rtlKeepsPhysicalDirectionsAndStaleMenuClicksCannotChooseTheNextFile() {
        var applied:ReviewSession?=null
        compose.setContent {CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            LumaTheme(AppSettings()) {ReviewScreen(photos.map {it.id},photos,AppSettings(),{}, {applied=it})}
        }}
        val left=compose.onNodeWithTag("review_direction_left",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val right=compose.onNodeWithTag("review_direction_right",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(left.right<=right.left)
        compose.onNodeWithTag("review_card").performTouchInput {swipeLeft()}
        compose.onNodeWithText("1 von 2").assertExists()
        compose.onNodeWithContentDescription("Rückgängig").performClick()
        compose.onNodeWithContentDescription("Mehr").performClick()
        val click=compose.onNodeWithText("Löschen vormerken").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        compose.runOnIdle {click();click()}
        compose.onNodeWithText("1 von 2").assertExists();assertNull(applied)
        compose.onNodeWithContentDescription("Abschließen").performClick()
        compose.onNodeWithText("Entscheidungen übernehmen").performClick()
        assertEquals(setOf("1"),applied!!.selected(ReviewAction.REMOVE))
    }
    private fun capture(name:String) {
        val output=java.io.File("build/design89","$name.png");output.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view=compose.activity.window.decorView;val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));output.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
}
