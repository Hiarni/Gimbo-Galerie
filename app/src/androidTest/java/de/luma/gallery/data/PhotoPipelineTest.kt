package de.luma.gallery.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.luma.gallery.model.GalleryMedia
import de.luma.gallery.model.PhotoRecipe
import de.luma.gallery.model.PhotoExportOptions
import de.luma.gallery.model.PhotoFormat
import de.luma.gallery.model.PhotoCurves
import de.luma.gallery.model.ToneCurve
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoPipelineTest {
    @Test fun rotateThenCropAndAdjustDoesNotMutateSource() {
        val source = Bitmap.createBitmap(6, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val output = PhotoEngine.render(source, PhotoRecipe(quarterTurns = 1, cropRatio = 1f, saturation = 0f))
        try {
            assertEquals(4, output.width)
            assertEquals(4, output.height)
            val pixel = output.getPixel(0, 0)
            assertEquals(Color.red(pixel), Color.green(pixel))
            assertEquals(Color.green(pixel), Color.blue(pixel))
            assertEquals(Color.RED, source.getPixel(0, 0))
            assertFalse(source.isRecycled)
        } finally { output.recycle(); source.recycle() }
    }

    @Test fun flipPreservesAlphaAndCorrectlyMovesPixels() {
        val source = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.RED)
        source.setPixel(1, 0, Color.TRANSPARENT)
        val output = PhotoEngine.render(source, PhotoRecipe(flipHorizontal = true))
        try {
            assertEquals(0, Color.alpha(output.getPixel(0, 0)))
            assertEquals(Color.RED, output.getPixel(1, 0))
            assertEquals(Color.RED, source.getPixel(0, 0))
        } finally { output.recycle(); source.recycle() }
    }

    @Test fun realMediaStoreExportCreatesReadableCopyAndKeepsOriginalBytes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = File.createTempFile("luma_source_", ".png", context.cacheDir)
        val source = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        sourceFile.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        source.recycle()
        val originalBytes = sourceFile.readBytes()
        var exported: Uri? = null
        try {
            val item = GalleryMedia("test", Uri.fromFile(sourceFile), "Original.png", "Test", 0)
            val result = GalleryRepository(context).exportPhoto(item, PhotoRecipe(quarterTurns = 1), PhotoExportOptions(format = PhotoFormat.PNG))
            exported = result
            assertNotEquals(item.uri, result)
            assertArrayEquals(originalBytes, sourceFile.readBytes())
            val decoded = PhotoEngine.decode(context, result, 4096)
            try {
                assertEquals(8, decoded.width)
                assertEquals(12, decoded.height)
                assertEquals(Color.BLUE, decoded.getPixel(0, 0))
            } finally { decoded.recycle() }
        } finally {
            exported?.let { context.contentResolver.delete(it, null, null) }
            sourceFile.delete()
        }
        Unit
    }

    @Test fun toneCurvesAndSharpnessRecipeReachesMediaStorePngWithoutChangingOriginal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) {
                val gray = if (x < 8) 64 else 192
                setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }
        val file = File.createTempFile("luma_details_", ".png", context.cacheDir)
        var exported: Uri? = null
        val recipe = PhotoRecipe(shadows = .8f, highlights = -.4f, sharpness = .5f, quarterTurns = 1,
            curves = PhotoCurves(rgb = ToneCurve(midtones = .6f), blue = ToneCurve(highlights = .8f)))
        val expected = PhotoEngine.render(source, recipe)
        try {
            file.outputStream().use { check(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val bytes = file.readBytes()
            val item = GalleryMedia("details-test", Uri.fromFile(file), "Original.png", "Test", 0)
            val result = GalleryRepository(context).exportPhoto(item, recipe, PhotoExportOptions(PhotoFormat.PNG))
            exported = result
            val actual = PhotoEngine.decode(context, result, 4096)
            try {
                assertEquals(expected.width, actual.width); assertEquals(expected.height, actual.height)
                for (y in 0 until expected.height) for (x in 0 until expected.width)
                    assertEquals(expected.getPixel(x, y), actual.getPixel(x, y))
                assertArrayEquals(bytes, file.readBytes())
            } finally { actual.recycle() }
        } finally { source.recycle(); expected.recycle(); exported?.let { context.contentResolver.delete(it, null, null) }; file.delete() }
        Unit
    }
}
