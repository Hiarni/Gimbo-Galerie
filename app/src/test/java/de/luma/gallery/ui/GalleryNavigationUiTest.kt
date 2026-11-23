package de.luma.gallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import de.luma.gallery.model.AppSettings
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="de-w320dp-h480dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class GalleryNavigationUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun narrowNavigationShowsCompleteNamesAndPreservesMinimumTouchTargets() {
        var page by mutableIntStateOf(0)
        compose.setContent {LumaTheme(AppSettings()) {Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {GalleryNavigation(page) {page=it}}}}
        listOf("Fotos","Videos","Aussortieren","Alben","Suche").forEach {label->
            val bounds=compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width>=48f && bounds.height>=48f)
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label,useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
            assertEquals(1,layouts.single().lineCount);assertFalse("layout: "+layouts.single().layoutInput.text+" size="+layouts.single().size+" overflowWidth="+layouts.single().didOverflowWidth+" overflowHeight="+layouts.single().didOverflowHeight,layouts.single().hasVisualOverflow)
        }
        compose.onNodeWithContentDescription("Aussortieren").performClick()
        compose.runOnIdle {assertEquals(2,page)}
        compose.onNodeWithContentDescription("Alben").performClick().assertIsSelected()
        compose.runOnIdle {assertEquals(3,page)}
    }
    @Test fun largeFontsGiveTheSelectedTabEnoughSpaceAndKeepAllDestinationsAccessible() {
        var page by mutableIntStateOf(0)
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.7f)) {LumaTheme(AppSettings()) {Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {GalleryNavigation(page) {page=it}}}}}
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Fotos",useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertFalse("layout: "+layouts.single().layoutInput.text+" size="+layouts.single().size+" overflowWidth="+layouts.single().didOverflowWidth+" overflowHeight="+layouts.single().didOverflowHeight,layouts.single().hasVisualOverflow)
        listOf("Fotos","Videos","Aussortieren","Alben","Suche").forEach {label->
            val bounds=compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width>=48f && bounds.height>=48f)
        }
        compose.onNodeWithContentDescription("Videos").performClick().assertIsSelected()
        compose.onNodeWithText("Videos").assertIsDisplayed()
        compose.runOnIdle {assertEquals(1,page)}
    }
}
