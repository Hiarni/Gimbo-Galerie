package de.luma.gallery.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import de.luma.gallery.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="de-w360dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CreativeFlowTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var files:List<File>
    private lateinit var photos:List<GalleryMedia>
    @Before fun source() {
        val context=RuntimeEnvironment.getApplication()
        files=listOf(Color.RED,Color.BLUE).mapIndexed {index,color->File.createTempFile("creative-$index", ".png",context.cacheDir).apply {
            val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply {eraseColor(color)}
            outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }}
        photos=files.mapIndexed {i,file->GalleryMedia("$i",Uri.fromFile(file),"Photo-$i.png","Camera",LocalDate.now().minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())}
    }
    @After fun cleanup() {files.forEach {it.delete()}}
    @Test fun memoriesOpenTheirCollectionAndManualCollageChoicePreservesSelectionOrder() {
        var collection:List<GalleryMedia>?=null;var selected:List<GalleryMedia>?=null
        compose.setContent {LumaTheme(AppSettings()) {CreativeScreen(photos,{}, {media,_->selected=media},{media,_->collection=media})}}
        compose.onNodeWithText("An diesem Tag · ${LocalDate.now().year-1}").performClick()
        assertEquals(photos,collection)
        compose.onAllNodesWithText("Collage erstellen").onFirst().performClick()
        compose.onNode(hasContentDescription(photos[1].name) and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(hasContentDescription(photos[0].name) and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Weiter").performClick()
        assertEquals(listOf(photos[1],photos[0]),selected)
    }
    @Test fun collagePreviewControlsAndExportUseTheCurrentLayoutAndTransforms() {
        var exported:CollageSpec?=null;var exportSources:List<GalleryMedia>?=null
        compose.setContent {LumaTheme(AppSettings()) {Surface(color=MaterialTheme.colorScheme.background) {CollageEditor(photos,photos,0xFFF8F7FA,false,{}, {items,spec,_->exportSources=items;exported=spec})}}}
        compose.waitUntil(15_000) {compose.onAllNodesWithContentDescription("Collagevorschau").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Hauptbild").performClick()
        compose.onNodeWithContentDescription("Abstand").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(.03f)}
        compose.onNodeWithContentDescription("Collagevorschau").assertExists()
        compose.onNodeWithText("Speichern").performClick()
        compose.onNodeWithText("PNG · 2560px").performScrollTo().performClick()
        assertEquals(CollageLayout.HERO,exported!!.layout);assertEquals(.03f,exported!!.gap,0f);assertEquals(photos,exportSources)
        val output=File("build/design-beta","collage-editor.png");output.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view=compose.activity.window.decorView;val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap));output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
}
