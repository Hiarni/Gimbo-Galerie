package de.luma.gallery.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import de.luma.gallery.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdvancedEditorsUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun brushSelectionAcceptsGesturePointsAndCommitsOnlyAtGestureEnd(){
        val bitmap=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888)
        val mask=mutableStateOf(PhotoMask("brush",MaskShape.BRUSH,width=1f,height=1f,feather=0f));var commits=0
        try{compose.setContent{MaterialTheme{Box(Modifier.size(300.dp)){PhotoMaskPreview(bitmap,mask.value,{mask.value=it},{commits++})}}}
            compose.onNodeWithContentDescription("Zeichne die gewählte Freihand- oder Pinselmaske auf dieser Vorschau. Positions- und Größenregler stehen darunter zur Verfügung.").performTouchInput{swipe(Offset(width*.2f,height*.5f),Offset(width*.8f,height*.5f))}
            compose.runOnIdle{assertTrue(mask.value.points.size>2);assertEquals(1,commits);assertTrue(mask.value.weight(.5f,.5f)>0);assertEquals(0f,mask.value.weight(.5f,.8f),0f)}
        }finally{bitmap.recycle()}
    }
    @Test fun collapsedVideoToolsExpandAndSaveACameraKeyframe(){
        val source=GalleryMedia("id",Uri.parse("content://media/video/1"),"A.mp4","",0,true,4000)
        val recipe=mutableStateOf(VideoRecipe(0,4000));val failures=mutableListOf<String>()
        compose.setContent{MaterialTheme{Column(Modifier.height(700.dp).verticalScroll(rememberScrollState())){VideoAdvancedControls(source,recipe.value,{recipe.value=it},{},{recipe.value=it},{failures+=it})}}}
        compose.onNodeWithText("Keyframes").assertDoesNotExist()
        compose.onNodeWithText("Erweiterte Schnittwerkzeuge").performClick()
        compose.onNodeWithText("Keyframes").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Zoom").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(2f)}
        compose.onNodeWithText("Keyframe speichern").performScrollTo().performClick()
        compose.runOnIdle{assertEquals(1,recipe.value.keyframes.size);assertEquals(2f,recipe.value.keyframes.single().zoom,0f);assertTrue(failures.isEmpty())}
    }
    @Test fun timedTextOverlayIsAddedEditedAndRemovedThroughTheUi(){
        val source=GalleryMedia("id",Uri.parse("content://media/video/1"),"A.mp4","",0,true,4000)
        val recipe=mutableStateOf(VideoRecipe(0,4000))
        compose.setContent{MaterialTheme{Column(Modifier.height(700.dp).verticalScroll(rememberScrollState())){VideoAdvancedControls(source,recipe.value,{recipe.value=it},{},{recipe.value=it},{})}}}
        compose.onNodeWithText("Erweiterte Schnittwerkzeuge").performClick()
        compose.onNodeWithText("Zeitgesteuerte Überlagerungen").performScrollTo().performClick()
        compose.onNodeWithText("Text hinzufügen").performScrollTo().performClick()
        compose.onNodeWithText("Textinhalt").performTextInput("Hallo")
        compose.onNodeWithText("Übernehmen").performClick()
        compose.runOnIdle{assertEquals("Hallo",recipe.value.overlays.single().text!!.text);assertEquals(4000L,recipe.value.overlays.single().endMs)}
        compose.onNodeWithText("Ausgewähltes Objekt entfernen").performScrollTo().performClick()
        compose.runOnIdle{assertTrue(recipe.value.overlays.isEmpty())}
    }
}
