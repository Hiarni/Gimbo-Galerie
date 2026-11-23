package de.luma.gallery.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoEngineHostTest {
    @Before fun clearPreferences() {
        RuntimeEnvironment.getApplication().getSharedPreferences("luma", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun source() = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) for (x in 0 until width) setPixel(x, y, Color.rgb(x * 25, y * 30, (x + y) * 15))
    }

    @Test fun offCenterCropPicksExactSourcePixelsWithoutMutatingOriginal() {
        val source = source()
        val original = source.getPixel(2, 1)
        val output = PhotoEngine.render(source, PhotoRecipe(cropBounds = CropBounds(.25f, 1f / 6, .75f, 5f / 6)))
        try {
            assertEquals(4, output.width); assertEquals(4, output.height)
            assertEquals(original, output.getPixel(0, 0))
            assertEquals(source.getPixel(5, 4), output.getPixel(3, 3))
            assertEquals(original, source.getPixel(2, 1)); assertFalse(source.isRecycled)
        } finally { output.recycle(); source.recycle() }
    }

    @Test fun rotatedAndMirroredCropKeepsItsSourceRegion() {
        val source = source()
        val recipe = PhotoRecipe(cropBounds = CropBounds(.25f, 1f / 6, .75f, 5f / 6))
        val initial = PhotoEngine.render(source, recipe)
        val mirrored = PhotoEngine.render(source, recipe.mirrored())
        val turned = PhotoEngine.render(source, recipe.rotatedClockwise())
        val mirroredTurned = PhotoEngine.render(source, recipe.mirrored().rotatedClockwise())
        try {
            for (y in 0..3) for (x in 0..3) {
                assertEquals(initial.getPixel(x, y), mirrored.getPixel(3 - x, y))
                assertEquals(initial.getPixel(x, y), turned.getPixel(3 - y, x))
                assertEquals(mirrored.getPixel(x, y), mirroredTurned.getPixel(3 - y, x))
            }
        } finally { listOf(initial, mirrored, turned, mirroredTurned, source).forEach { it.recycle() } }
    }

    @Test fun neutralRecipeCopiesOpaquePixelsExactlyAndPreservesAlpha() {
        val source = source().apply { setPixel(0, 0, Color.argb(128, 100, 100, 100)) }
        val output = PhotoEngine.render(source, PhotoRecipe())
        try {
            assertEquals(128, Color.alpha(output.getPixel(0, 0)))
            for (y in 0 until source.height) for (x in 1 until source.width) assertEquals(source.getPixel(x, y), output.getPixel(x, y))
            assertNotSame(source, output)
        } finally { output.recycle(); source.recycle() }
    }

    @Test fun oneExposureStopDoublesLinearLightAndTemperatureMovesInBothDirections() {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(60, 60, 60)) }
        val bright = PhotoEngine.render(source, PhotoRecipe(exposure = 1f))
        val warm = PhotoEngine.render(source, PhotoRecipe(temperature = 1f))
        val cool = PhotoEngine.render(source, PhotoRecipe(temperature = -1f))
        try {
            assertEquals(85, Color.red(bright.getPixel(0, 0)))
            assertEquals(85, Color.green(bright.getPixel(0, 0)))
            assertTrue(Color.red(warm.getPixel(0, 0)) > Color.blue(warm.getPixel(0, 0)))
            assertTrue(Color.red(cool.getPixel(0, 0)) < Color.blue(cool.getPixel(0, 0)))
        } finally { listOf(bright, warm, cool, source).forEach { it.recycle() } }
    }

    @Test fun tintMovesBetweenGreenAndMagentaWithoutChangingAlpha() {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(100, 100, 100)) }
        val green = PhotoEngine.render(source, PhotoRecipe(tint = -1f))
        val magenta = PhotoEngine.render(source, PhotoRecipe(tint = 1f))
        try {
            assertTrue(Color.green(green.getPixel(0, 0)) > Color.red(green.getPixel(0, 0)))
            assertTrue(Color.green(magenta.getPixel(0, 0)) < Color.red(magenta.getPixel(0, 0)))
            assertEquals(255, Color.alpha(magenta.getPixel(0, 0)))
        } finally { listOf(green, magenta, source).forEach { it.recycle() } }
    }

    @Test fun pngRetainsTransparencyWhileJpegFlattensItOnWhite() {
        val source = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        try {
            for (format in PhotoFormat.entries) {
                val bytes = ByteArrayOutputStream().apply { PhotoEngine.encode(source, PhotoExportOptions(format = format), this) }.toByteArray()
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                try {
                    assertEquals(if (format == PhotoFormat.PNG) 0 else 255, Color.alpha(decoded.getPixel(0, 0)))
                    if (format == PhotoFormat.JPEG) assertEquals(Color.WHITE, decoded.getPixel(0, 0))
                } finally { decoded.recycle() }
            }
            assertFalse(source.isRecycled); assertEquals(0, Color.alpha(source.getPixel(0, 0)))
        } finally { source.recycle() }
    }

    @Test fun previousAlphaRecipesLoadWithNeutralNewAdjustments() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("luma", Context.MODE_PRIVATE).edit()
            .putString("recipe:old", """{"brightness":0.1,"contrast":1.2,"saturation":0.8,"turns":1,"flip":true,"crop":1.0}""").commit()
        val loaded = GalleryRepository(context).loadRecipe("old")
        assertEquals(.1f, loaded.brightness, .00001f); assertEquals(1f, loaded.cropRatio!!, .00001f)
        assertNull(loaded.cropBounds); assertEquals(0f, loaded.exposure, 0f)
        assertEquals(0f, loaded.temperature, 0f); assertEquals(0f, loaded.tint, 0f)
        assertTrue(loaded.flipHorizontal); assertEquals(1, loaded.quarterTurns)
    }

    @Test fun fullRecipeAndExportChoicesSurviveNewRepositoryInstance() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val recipe = PhotoRecipe(.2f, 1.3f, .7f, 2, true, 1f, CropBounds(.1f, .2f, .8f, .9f), -.5f, .4f, -.2f,
            highlights = -.6f, shadows = .5f, sharpness = .7f,
            curves = PhotoCurves(rgb = ToneCurve(shadows = .4f), blue = ToneCurve(white = .8f)),
            texts = listOf(PhotoText("caption", "Luma", x = .7f, color = 0xFFFF0000, background = false)),
            markups = listOf(PhotoMarkup("draw", MarkupKind.BRUSH), PhotoMarkup("pixel", MarkupKind.PIXELATE)))
        val options = PhotoExportOptions(PhotoFormat.PNG, 1920, 87)
        GalleryRepository(context).apply { saveRecipe("new", recipe) }
        SettingsRepository(context).saveExport(options)
        assertEquals(options, SettingsRepository(context).values.first().export)
        GalleryRepository(context).apply { assertEquals(recipe, loadRecipe("new")) }
        Unit
    }
}
