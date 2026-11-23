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
class PhotoTextHostTest {
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    @Test fun captionIsDrawnLastInItsOwnColorAndSourceRemainsUnchanged() {
        val source = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val before = pixels(source)
        val text = PhotoText("id", "Sommer", size = .18f, color = 0xFFFF0000, background = false)
        val result = PhotoEngine.render(source, PhotoRecipe(saturation = 0f, texts = listOf(text)))
        try {
            assertTrue(pixels(result).any { Color.red(it) > 180 && Color.green(it) < 30 && Color.blue(it) < 30 })
            assertEquals(Color.red(result.getPixel(0, 0)), Color.blue(result.getPixel(0, 0)))
            assertArrayEquals(before, pixels(source)); assertFalse(source.isRecycled)
        } finally { source.recycle(); result.recycle() }
    }

    @Test fun boundsFitLongCaptionsAndEdgePositionsInLandscapePortraitAndTinyImages() {
        for ((width, height) in listOf(800 to 400, 400 to 800, 1 to 1, 1 to 8)) {
            for ((x, y) in listOf(0f to 0f, .5f to .5f, 1f to 1f)) {
                val text = PhotoText("id", "Langer Text mit Umlauten und Schriftzeichen Grüße مرحبا ".repeat(2), x, y, .2f)
                val bounds = PhotoTextRenderer.bounds(width, height, text)
                assertTrue(bounds.left >= -.01f); assertTrue(bounds.top >= -.01f)
                assertTrue(bounds.right <= width + .01f); assertTrue(bounds.bottom <= height + .01f)
                val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val result = PhotoEngine.render(source, PhotoRecipe(texts = listOf(text)))
                try { assertEquals(width, result.width); assertEquals(height, result.height) }
                finally { source.recycle(); result.recycle() }
            }
        }
    }

    @Test fun finalCropAndRotationUseUprightOutputRelativeCaptions() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val text = PhotoText("id", "Hier", x = .75f, y = .25f, size = .15f, background = false)
        val result = PhotoEngine.render(source, PhotoRecipe(quarterTurns = 1, cropBounds = CropBounds(.1f, .1f, .9f, .9f), texts = listOf(text)))
        try {
            assertEquals(240, result.width); assertEquals(320, result.height)
            val bounds = PhotoTextRenderer.bounds(result.width, result.height, text)
            assertEquals(result.width * .75f, bounds.centerX(), .01f)
            assertEquals(result.height * .25f, bounds.centerY(), .01f)
            var count = 0
            for (y in 0 until result.height) for (x in 0 until result.width) if (Color.red(result.getPixel(x, y)) > 40) {
                assertTrue(bounds.contains(x.toFloat(), y.toFloat())); count++
            }
            assertTrue(count > 30)
        } finally { source.recycle(); result.recycle() }
    }

    @Test fun layeringBackgroundAndTransparentPngPreservePixelsAndOriginalAlphaOutsideCaptions() {
        val source = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        val texts = listOf(PhotoText("one", "Label", size = .18f, color = 0xFFFFFFFF),
            PhotoText("two", "Label", size = .18f, color = 0xFF000000, background = true))
        val result = PhotoEngine.render(source, PhotoRecipe(texts = texts))
        val bytes = ByteArrayOutputStream().apply { PhotoEngine.encode(result, PhotoExportOptions(PhotoFormat.PNG), this) }.toByteArray()
        val png = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertEquals(0, Color.alpha(result.getPixel(0, 0)))
            assertTrue(pixels(result).any { Color.alpha(it) == 255 && Color.red(it) < 20 })
            assertTrue(pixels(result).any { Color.alpha(it) > 0 && Color.red(it) > 200 })
            assertArrayEquals(pixels(result), pixels(png)); assertTrue(pixels(source).all { it == 0 })
        } finally { listOf(source, result, png).forEach { it.recycle() } }
    }

    @Test fun cancellationBetweenCaptionsDoesNotRecycleOrMutateSource() {
        val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val before = pixels(source)
        try {
            var checks = 0
            assertThrows(CancellationException::class.java) {
                PhotoEngine.render(source, PhotoRecipe(texts = listOf(PhotoText("a", "A"), PhotoText("b", "B")))) {
                    if (++checks == 3) throw CancellationException()
                }
            }
            assertEquals(3, checks); assertFalse(source.isRecycled); assertArrayEquals(before, pixels(source))
        } finally { source.recycle() }
    }

    @Test fun textRecipesRoundTripAndOlderRecipesHaveNoCaptionsAndInvalidRecordsAreRejected() {
        for (version in 1..3) assertTrue(RecipeJson.readPhoto("""{"version":$version}""").texts.isEmpty())
        val recipe = PhotoRecipe(texts = listOf(PhotoText("a", "Grüße", x = .2f, size = .15f, color = 0xFFFF0000, bold = true), PhotoText("b", "مرحبا")))
        val json = RecipeJson.photo(recipe)
        assertEquals(7, org.json.JSONObject(json).getInt("version")); assertEquals(recipe, RecipeJson.readPhoto(json))
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.readPhoto("""{"version":8}""") }
        val bad = org.json.JSONObject(json).apply { getJSONArray("texts").getJSONObject(0).put("x", 5) }.toString()
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.readPhoto(bad) }
        val duplicate = org.json.JSONObject(json).apply { getJSONArray("texts").getJSONObject(1).put("id", "a") }.toString()
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.readPhoto(duplicate) }
    }
}
