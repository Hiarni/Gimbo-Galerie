package de.luma.gallery.ui

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import de.luma.gallery.GalleryViewModel
import de.luma.gallery.data.*
import de.luma.gallery.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoEditorFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun setupMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

    @Test fun newToolsPreviewUndoCompareAndPersistThroughTheActualEditor() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("luma", 0).edit().clear().commit()
        val database = Room.inMemoryDatabaseBuilder(app, ProjectDatabase::class.java).build()
        val projects = ProjectRepository(database)
        val store = ViewModelStore()
        val vm = GalleryViewModel(app, projects)
        ViewModelProvider(store, viewModelFactory { initializer { vm } })[GalleryViewModel::class.java]
        try {
            val file=java.io.File(app.cacheDir,"editor-real-source.png")
            val bitmap=android.graphics.Bitmap.createBitmap(320,240,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.rgb(60,110,180))}
            file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            val source=de.luma.gallery.model.GalleryMedia("real-source",android.net.Uri.fromFile(file),"Source.png","Test",0)
            vm.beginEditor(source)
            compose.setContent {
                val state by vm.state.collectAsState()
                LumaTheme(state.settings) {androidx.compose.material3.Surface(color=androidx.compose.material3.MaterialTheme.colorScheme.background) {PhotoEditor(source, vm, state.editor!!, {})}}
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Bearbeitungsvorschau").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle {
                val view=compose.activity.window.decorView
                val screenshot=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(screenshot))
                val output=java.io.File("build/design19/photo-editor.png")
                output.parentFile!!.mkdirs()
                output.outputStream().use {screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                screenshot.recycle()
            }
            fun previewGreen():Int {
                val bounds=compose.onNodeWithContentDescription("Bearbeitungsvorschau").fetchSemanticsNode().boundsInWindow
                var green=0
                compose.runOnIdle {
                    val view=compose.activity.window.decorView
                    val image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(image));green=android.graphics.Color.green(image.getPixel(bounds.center.x.toInt(),bounds.center.y.toInt()));image.recycle()
                }
                return green
            }
            compose.onNodeWithContentDescription("Helligkeit").performScrollTo()
            val originalGreen=previewGreen()
            compose.onNodeWithContentDescription("Helligkeit").performTouchInput {down(center);moveTo(androidx.compose.ui.geometry.Offset(width*.9f,centerY),100)}
            compose.waitUntil(15_000) {previewGreen()>originalGreen+30}
            compose.runOnIdle {assertEquals(0f,vm.state.value.editor!!.photo!!.brightness,0f)}
            compose.onNodeWithContentDescription("Helligkeit").performTouchInput {up()}
            compose.runOnIdle {assertTrue(vm.state.value.editor!!.photo!!.brightness>0f)}
            compose.onNodeWithContentDescription("Rückgängig").performClick()
            compose.onNodeWithContentDescription("Fotowerkzeuge").performScrollToNode(hasText("Filter"))
            compose.onNodeWithText("Filter").performClick()
            compose.waitUntil(15_000) {compose.onAllNodesWithContentDescription("Filtervorschau: Natürlich").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("Filtervorschauen").performScrollToNode(hasText("Sepia"))
            compose.onNodeWithText("Sepia").performClick()
            compose.onNodeWithContentDescription("Rückgängig").performClick()
            compose.runOnIdle {assertEquals(PhotoRecipe(),vm.state.value.editor!!.photo)}
            compose.onNodeWithContentDescription("Fotowerkzeuge").performScrollToNode(hasText("Licht"))
            compose.onNodeWithText("Licht").performClick()
            compose.onNodeWithContentDescription("Schatten").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(.7f) }
            compose.runOnIdle { assertEquals(.7f, vm.state.value.editor!!.photo!!.shadows, 0f) }
            compose.onNodeWithContentDescription("Rückgängig").performClick()
            compose.runOnIdle { assertEquals(PhotoRecipe(), vm.state.value.editor!!.photo) }
            compose.onNodeWithContentDescription("Wiederholen").performClick()
            compose.runOnIdle { assertEquals(.7f, vm.state.value.editor!!.photo!!.shadows, 0f) }
            compose.onNodeWithContentDescription("Fotowerkzeuge").performScrollToNode(hasText("Details"))
            compose.onNodeWithText("Details").performClick()
            compose.onNodeWithContentDescription("Schärfe").performSemanticsAction(SemanticsActions.SetProgress) { it(.6f) }
            compose.runOnIdle { assertEquals(.6f, vm.state.value.editor!!.photo!!.sharpness, 0f) }
            compose.onNodeWithContentDescription("Fotowerkzeuge").performScrollToNode(hasText("Kurven"))
            compose.onNodeWithText("Kurven").performClick()
            compose.onNodeWithContentDescription("RGB: Mitte").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(.65f) }
            val curves = PhotoCurves(rgb = ToneCurve(midtones = .65f))
            compose.runOnIdle { assertEquals(curves, vm.state.value.editor!!.photo!!.curves) }
            compose.onNodeWithContentDescription("Rückgängig").performClick()
            compose.runOnIdle { assertEquals(PhotoCurves(), vm.state.value.editor!!.photo!!.curves) }
            compose.onNodeWithContentDescription("Wiederholen").performClick()
            compose.runOnIdle { assertEquals(curves, vm.state.value.editor!!.photo!!.curves) }
            compose.onNodeWithText("Vergleichen").performClick()
            compose.onNodeWithContentDescription("Original").assertExists()
            compose.onNodeWithText("Original").performClick()
            compose.onNodeWithContentDescription("Fotowerkzeuge").performScrollToNode(hasText("Text"))
            compose.onNodeWithContentDescription("Fotowerkzeuge").performScrollToNode(hasText("Text"))
            compose.onNodeWithText("Text").performClick()
            compose.onNodeWithText("Text hinzufügen").performScrollTo().performClick()
            compose.onNode(hasSetTextAction()).performTextInput("Sommer")
            compose.onNodeWithText("Übernehmen").performClick()
            val caption = vm.state.value.editor!!.photo!!.texts.single()
            assertEquals("Sommer", caption.text)
            compose.onNodeWithContentDescription("Textposition horizontal (%)").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
            val texts = listOf(caption.copy(x = .75f))
            compose.onNodeWithContentDescription("Rückgängig").performClick()
            compose.runOnIdle { assertEquals(listOf(caption), vm.state.value.editor!!.photo!!.texts) }
            compose.onNodeWithContentDescription("Wiederholen").performClick()
            compose.runOnIdle { assertEquals(texts, vm.state.value.editor!!.photo!!.texts) }
            compose.onNodeWithContentDescription("Fotowerkzeuge").performScrollToNode(hasText("Zeichnen"))
            compose.onNodeWithContentDescription("Fotowerkzeuge").performScrollToNode(hasText("Zeichnen"))
            compose.onNodeWithText("Zeichnen").performClick()
            compose.onNodeWithText("Zeichenobjekt hinzufügen").performScrollTo().performClick()
            val mark = vm.state.value.editor!!.photo!!.markups.single()
            compose.onNodeWithContentDescription("Linienbreite (%)").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
            val markups = listOf(mark.copy(width = .04f))
            compose.onNodeWithContentDescription("Rückgängig").performClick()
            compose.runOnIdle { assertEquals(listOf(mark), vm.state.value.editor!!.photo!!.markups) }
            compose.onNodeWithContentDescription("Wiederholen").performClick()
            compose.runOnIdle { assertEquals(markups, vm.state.value.editor!!.photo!!.markups) }
            compose.onNodeWithContentDescription("Fotowerkzeuge").performScrollToNode(hasText("Filter"))
            compose.onNodeWithContentDescription("Fotowerkzeuge").performScrollToNode(hasText("Filter"))
            compose.onNodeWithText("Filter").performClick()
            compose.onNodeWithText("Natürlich").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(PhotoCurves(), vm.state.value.editor!!.photo!!.curves); assertEquals(texts, vm.state.value.editor!!.photo!!.texts); assertEquals(markups, vm.state.value.editor!!.photo!!.markups) }
            compose.onNodeWithContentDescription("Rückgängig").performClick()
            compose.runOnIdle { assertEquals(curves, vm.state.value.editor!!.photo!!.curves) }
            compose.onNodeWithContentDescription("Projekt speichern").performClick()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput("Detailsprobe")
            compose.onNode(hasText("Projekt speichern") and hasClickAction()).performClick()
            compose.waitUntil(10_000) { vm.state.value.editor?.projectId != null && !vm.state.value.savingProject }
            val project = runBlocking { projects.find(vm.state.value.editor!!.projectId!!)!! }
            assertEquals("Detailsprobe", project.name)
            assertEquals(PhotoRecipe(shadows = .7f, sharpness = .6f, curves = curves, texts = texts, markups = markups), project.photo)
            compose.onNodeWithText("Export").performClick()
            compose.onNodeWithText("PNG").performClick()
            compose.onNodeWithText("Kopie speichern").assertIsEnabled()
            compose.onNodeWithText("Abbrechen").performClick()
            assertEquals(project.photo, vm.state.value.editor!!.photo)
        } finally { compose.runOnIdle { store.clear() }; database.close() }
    }
}
