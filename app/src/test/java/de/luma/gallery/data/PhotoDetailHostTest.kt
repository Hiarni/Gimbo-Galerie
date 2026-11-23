package de.luma.gallery.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoDetailHostTest {
    private fun grays(vararg values: Int) = Bitmap.createBitmap(values.size, 1, Bitmap.Config.ARGB_8888).apply {
        values.forEachIndexed { x, value -> setPixel(x, 0, Color.rgb(value, value, value)) }
    }
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    @Test fun shadowsTargetDarkAreasInBothDirectionsAndPreserveBlackAndWhite() {
        val source = grays(0, 64, 192, 255)
        val lighter = PhotoEngine.render(source, PhotoRecipe(shadows = 1f))
        val darker = PhotoEngine.render(source, PhotoRecipe(shadows = -1f))
        try {
            assertTrue(Color.red(lighter.getPixel(1, 0)) - 64 > 3 * (Color.red(lighter.getPixel(2, 0)) - 192))
            assertTrue(64 - Color.red(darker.getPixel(1, 0)) > 3 * (192 - Color.red(darker.getPixel(2, 0))))
            for (bitmap in listOf(lighter, darker)) {
                assertEquals(Color.BLACK, bitmap.getPixel(0, 0)); assertEquals(Color.WHITE, bitmap.getPixel(3, 0))
            }
            assertEquals(Color.rgb(64, 64, 64), source.getPixel(1, 0)); assertFalse(source.isRecycled)
        } finally { listOf(source, lighter, darker).forEach { it.recycle() } }
    }

    @Test fun highlightsTargetBrightAreasInBothDirections() {
        val source = grays(0, 64, 192, 255)
        val brighter = PhotoEngine.render(source, PhotoRecipe(highlights = 1f))
        val darker = PhotoEngine.render(source, PhotoRecipe(highlights = -1f))
        try {
            assertTrue(Color.red(brighter.getPixel(2, 0)) - 192 > 3 * (Color.red(brighter.getPixel(1, 0)) - 64))
            assertTrue(192 - Color.red(darker.getPixel(2, 0)) > 3 * (64 - Color.red(darker.getPixel(1, 0))))
            assertEquals(Color.BLACK, darker.getPixel(0, 0)); assertEquals(Color.WHITE, brighter.getPixel(3, 0))
        } finally { listOf(source, brighter, darker).forEach { it.recycle() } }
    }

    @Test fun sharpnessIncreasesEdgeContrastWithoutChangingFlatAreasOrTheSource() {
        val source = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) {
                val gray = if (x < 4) 80 else 160
                setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }
        val original = pixels(source)
        val output = PhotoEngine.render(source, PhotoRecipe(sharpness = 1f))
        try {
            for (y in 0 until 8) {
                assertEquals(80, Color.red(output.getPixel(0, y)))
                assertEquals(160, Color.red(output.getPixel(7, y)))
                assertTrue(Color.red(output.getPixel(3, y)) < 80)
                assertTrue(Color.red(output.getPixel(4, y)) > 160)
            }
            assertArrayEquals(original, pixels(source))
        } finally { source.recycle(); output.recycle() }
    }

    @Test fun sharpeningUsesOriginalNeighborsForEveryRowAndRotatesWithTheImage() {
        val source = Bitmap.createBitmap(5, 7, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) {
                val gray = if (y < 3) 70 else 170
                setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }
        val output = PhotoEngine.render(source, PhotoRecipe(sharpness = .7f))
        val rotated = PhotoEngine.render(source, PhotoRecipe(sharpness = .7f, quarterTurns = 1))
        try {
            assertEquals(70, Color.red(output.getPixel(2, 0)))
            assertTrue(Color.red(output.getPixel(2, 2)) < 70)
            assertTrue(Color.red(output.getPixel(2, 3)) > 170)
            assertEquals(170, Color.red(output.getPixel(2, 4)))
            for (y in 0 until source.height) for (x in 0 until source.width)
                assertEquals(output.getPixel(x, y), rotated.getPixel(source.height - 1 - y, x))
        } finally { listOf(source, output, rotated).forEach { it.recycle() } }
    }

    @Test fun onePixelAndFlatImagesStayUnchangedWhenSharpened() {
        for ((width, height) in listOf(1 to 1, 1 to 8, 8 to 1, 8 to 8)) {
            val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(80, 120, 160)) }
            val output = PhotoEngine.render(source, PhotoRecipe(sharpness = 1f))
            try { assertArrayEquals(pixels(source), pixels(output)) }
            finally { source.recycle(); output.recycle() }
        }
    }

    @Test fun transparentNeighborsDoNotDarkenSharpEdgesAndAlphaIsPreserved() {
        val source = Bitmap.createBitmap(6, 4, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width)
                setPixel(x, y, Color.argb(listOf(0, 0, 64, 128, 255, 255)[x], 100, 100, 100))
        }
        val baseline = PhotoEngine.render(source, PhotoRecipe())
        val sharpened = PhotoEngine.render(source, PhotoRecipe(sharpness = 1f))
        val toned = PhotoEngine.render(source, PhotoRecipe(highlights = -.5f, shadows = .8f, sharpness = 1f))
        try {
            for (y in 0 until 4) for (x in 0 until 6) {
                assertEquals(Color.alpha(baseline.getPixel(x, y)), Color.alpha(sharpened.getPixel(x, y)))
                assertEquals(Color.alpha(baseline.getPixel(x, y)), Color.alpha(toned.getPixel(x, y)))
                assertTrue(kotlin.math.abs(Color.red(baseline.getPixel(x, y)) - Color.red(sharpened.getPixel(x, y))) <= 1)
            }
            assertEquals(100, Color.red(sharpened.getPixel(4, 2)))
        } finally { listOf(source, baseline, sharpened, toned).forEach { it.recycle() } }
    }

    @Test fun combinedToolsSurvivePngEncodingAndKeepTheOriginalPixels() {
        val source = grays(0, 48, 80, 144, 192, 255)
        val original = pixels(source)
        val edited = PhotoEngine.render(source, PhotoRecipe(exposure = .2f, highlights = -.4f, shadows = .6f, sharpness = .3f))
        val bytes = ByteArrayOutputStream().apply { PhotoEngine.encode(edited, PhotoExportOptions(PhotoFormat.PNG), this) }.toByteArray()
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertArrayEquals(pixels(edited), pixels(decoded))
            assertArrayEquals(original, pixels(source)); assertFalse(source.isRecycled)
            assertFalse(original.contentEquals(pixels(edited)))
        } finally { listOf(source, edited, decoded).forEach { it.recycle() } }
    }

    @Test fun cancellationStopsScanlineProcessingWithoutRecyclingOrMutatingSource() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val original = pixels(source)
        try {
            for (recipe in listOf(PhotoRecipe(exposure = 1f), PhotoRecipe(shadows = .5f), PhotoRecipe(sharpness = 1f))) {
                var checks = 0
                assertThrows(CancellationException::class.java) { PhotoEngine.render(source, recipe) {
                    if (++checks == 5) throw CancellationException()
                } }
                assertEquals(5, checks); assertFalse(source.isRecycled); assertArrayEquals(original, pixels(source))
            }
        } finally { source.recycle() }
    }

    @Test fun olderRecipesRenderIdenticallyAndNewRecipesRoundTripWithVersionProtection() {
        val old = """{"version":1,"brightness":0.1,"contrast":1.2,"saturation":0.8,"exposure":0.2}"""
        val recipe = RecipeJson.readPhoto(old)
        assertEquals(0f, recipe.highlights, 0f); assertEquals(0f, recipe.shadows, 0f); assertEquals(0f, recipe.sharpness, 0f)
        val source = grays(30, 100, 200)
        val output = PhotoEngine.render(source, recipe)
        val expected = PhotoEngine.render(source, PhotoRecipe(brightness = .1f, contrast = 1.2f, saturation = .8f, exposure = .2f))
        try { assertArrayEquals(pixels(expected), pixels(output)) }
        finally { listOf(source, output, expected).forEach { it.recycle() } }
        val new = recipe.copy(highlights = -.8f, shadows = .7f, sharpness = .6f)
        val json = RecipeJson.photo(new)
        assertEquals(7, org.json.JSONObject(json).getInt("version")); assertEquals(new, RecipeJson.readPhoto(json))
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.readPhoto("""{"version":8}""") }
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.readPhoto("""{"version":2,"sharpness":5}""") }
        assertEquals(4, org.json.JSONObject(RecipeJson.video(VideoRecipe(0, 1000))).getInt("version"))
    }
}
