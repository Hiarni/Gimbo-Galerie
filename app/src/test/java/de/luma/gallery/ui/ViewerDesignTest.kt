package de.luma.gallery.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.Density
import de.luma.gallery.GalleryState
import de.luma.gallery.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="en-w360dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ViewerDesignTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var file:File
    private lateinit var media:GalleryMedia

    @Before fun source() {
        file=File.createTempFile("viewer-design-", ".png",RuntimeEnvironment.getApplication().cacheDir)
        val bitmap=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(0f,0f,600f,900f,Paint().apply {
            shader=LinearGradient(0f,0f,600f,900f,intArrayOf(0xFF40536D.toInt(),0xFFF2B790.toInt(),0xFF1E5555.toInt()),null,Shader.TileMode.CLAMP)
        })
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        media=GalleryMedia("photo",Uri.fromFile(file),"Sommerabend.jpg","Kamera",0,size=file.length())
    }
    @After fun cleanup() {file.delete()}

    @Test fun floatingActionsPutDeleteFirstAndMoreMenuPreservesAlbums() {
        var album=0;var deleted=0
        compose.setContent {LumaTheme(AppSettings()) {
            ImmersiveMedia(true)
            MediaViewer(media,GalleryState(),{},{},{},{},{deleted++},{album++},false,false)
        }}
        compose.onNodeWithTag("viewer_actions").assertDoesNotExist()
        compose.waitForIdle()
        Thread.sleep(250)
        compose.mainClock.advanceTimeBy(400)
        capture("viewer-content-first")
        showControls()
        assertDockBounds()
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Album").performClick()
        compose.runOnIdle {assertEquals(1,album)}
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle {assertEquals(1,deleted)}
        assertDockBounds()
        val deleteBounds=compose.onNodeWithText("Delete").fetchSemanticsNode().boundsInRoot
        val editBounds=compose.onNodeWithText("Edit").fetchSemanticsNode().boundsInRoot
        assertTrue(deleteBounds.left<editBounds.left)
        capture("viewer-dark")
    }

    @Test
    @Config(qualifiers="en-w320dp-h480dp")
    fun smallScreenAndLargeTextKeepEveryPrimaryActionInsideTheDock() {
        val settings=AppSettings(theme=ThemeMode.LIGHT,accent=0xFF0064FF)
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.7f)) {
            LumaTheme(settings) {ImmersiveMedia(true);MediaViewer(media,GalleryState(settings=settings,favorites=setOf(media.id)),{},{},{},{},{},{},false,false)}
        }}
        showControls()
        assertDockBounds()
        compose.onNodeWithContentDescription("Favorite").assertIsSelected()
        capture("viewer-large-text")
    }

    private fun showControls() {
        compose.onNodeWithContentDescription(media.name).performTouchInput {click()}
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
    }

    private fun assertDockBounds() {
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        val dock=compose.onNodeWithTag("viewer_actions").fetchSemanticsNode().boundsInRoot
        assertTrue(dock.left>root.left && dock.right<root.right && dock.bottom<root.bottom)
        listOf("Delete","Edit","Share","More").forEach {label->
            val button=compose.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(label,button.left>=dock.left && button.right<=dock.right && button.top>=dock.top && button.bottom<=dock.bottom)
        }
    }

    private fun capture(name:String) {
        val output=File("build/design89", "$name.png")
        output.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
}
