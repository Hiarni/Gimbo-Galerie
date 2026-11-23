package de.luma.gallery.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import de.luma.gallery.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatchPreviewRendererTest {
    private val files = mutableListOf<File>()
    private fun photo(width: Int, height: Int, color: Int): GalleryMedia {
        val file = File.createTempFile("batch-render-", ".png", RuntimeEnvironment.getApplication().cacheDir)
        files += file
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return GalleryMedia(file.name, Uri.fromFile(file), file.name, "Test", 0)
    }
    @After fun cleanup() { files.forEach { it.delete() } }

    @Test fun sameProjectFramesDifferentSourceShapesWhileOriginalComparisonBypassesAllEdits() = runBlocking {
        val wide = photo(1024, 256, Color.BLUE)
        val tall = photo(256, 1024, Color.GREEN)
        val bytes = files.map { it.readBytes() }
        val recipe = PhotoRecipe(quarterTurns = 1, cropBounds = CropBounds(.25f, .25f, .75f, .75f),
            markups = listOf(PhotoMarkup("cover", MarkupKind.COVER, color = 0xFFFF0000L)))
        val renderer = BatchPreviewRenderer(RuntimeEnvironment.getApplication())
        val first = renderer.render(wide, recipe, false)
        val second = renderer.render(tall, recipe, false)
        val original = renderer.render(wide, recipe, true)
        try {
            assertEquals(64, first.width); assertEquals(256, first.height)
            assertEquals(256, second.width); assertEquals(64, second.height)
            assertEquals(Color.RED, first.getPixel(first.width / 2, first.height / 2))
            assertEquals(Color.RED, second.getPixel(second.width / 2, second.height / 2))
            assertEquals(512, original.width); assertEquals(128, original.height)
            assertEquals(Color.BLUE, original.getPixel(256, 64))
            files.forEachIndexed { index, file -> assertArrayEquals(bytes[index], file.readBytes()) }
        } finally { first.recycle(); second.recycle(); original.recycle() }
    }

    @Test fun outputOwnershipIsIndependentAcrossSourcesAndNoEditPreviewRetainsTransparency() = runBlocking {
        val first = photo(40, 20, Color.TRANSPARENT)
        val second = photo(20, 40, Color.GREEN)
        val renderer = BatchPreviewRenderer(RuntimeEnvironment.getApplication())
        val transparent = renderer.render(first, PhotoRecipe(), false)
        val green = renderer.render(second, PhotoRecipe(), false)
        try {
            assertEquals(0, Color.alpha(transparent.getPixel(20, 10)))
            transparent.recycle()
            assertFalse(green.isRecycled)
            assertEquals(Color.GREEN, green.getPixel(10, 20))
            assertEquals(20, green.width); assertEquals(40, green.height)
        } finally { if (!transparent.isRecycled) transparent.recycle(); green.recycle() }
    }
}
