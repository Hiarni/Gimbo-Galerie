package de.luma.gallery.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import de.luma.gallery.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoMarkupHostTest {
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    @Test fun shapesAndBrushUseTheirOwnColorsAfterFiltersAndTextIsAboveThem() {
        val source = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val before = pixels(source)
        val marks = listOf(PhotoMarkup("rect", MarkupKind.RECTANGLE, color = 0xFFFF0000, filled = true),
            PhotoMarkup("oval", MarkupKind.ELLIPSE, color = 0xFF00FF00, filled = true),
            PhotoMarkup("brush", MarkupKind.BRUSH, listOf(MarkupPoint(.1f, .1f), MarkupPoint(.9f, .1f)), color = 0xFFFF0000, width = .04f))
        val result = PhotoEngine.render(source, PhotoRecipe(saturation = 0f, markups = marks,
            texts = listOf(PhotoText("text", "AAA", size = .2f, background = false))))
        try {
            assertEquals(Color.RED, result.getPixel(150, 20))
            assertEquals(Color.RED, result.getPixel(91, 61))
            assertEquals(Color.GREEN, result.getPixel(150, 75))
            assertTrue(pixels(result).any { it == Color.WHITE })
            assertArrayEquals(before, pixels(source)); assertFalse(source.isRecycled)
        } finally { result.recycle(); source.recycle() }
    }

    @Test fun pixelationAveragesBlocksInsideTheRegionAndLeavesOutsideUnchanged() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) for (x in 0 until 100) source.setPixel(x, y, if (x % 2 == 0) Color.BLACK else Color.WHITE)
        val before = pixels(source)
        val mark = PhotoMarkup("pixel", MarkupKind.PIXELATE, listOf(MarkupPoint(.2f, .2f), MarkupPoint(.8f, .8f)), blockSize = .1f)
        val result = PhotoEngine.render(source, PhotoRecipe(markups = listOf(mark)))
        try {
            for (y in 20 until 80) for (x in 20 until 80) assertEquals(Color.rgb(127, 127, 127), result.getPixel(x, y))
            assertEquals(source.getPixel(19, 50), result.getPixel(19, 50))
            assertEquals(source.getPixel(80, 50), result.getPixel(80, 50))
            assertArrayEquals(before, pixels(source))
        } finally { source.recycle(); result.recycle() }
    }

    @Test fun pixelationPreservesAlphaAndDoesNotMixInvisibleColorsIntoVisiblePixels() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) for (x in 0 until 100) source.setPixel(x, y, if (x % 2 == 0) Color.RED else Color.TRANSPARENT)
        val mark = PhotoMarkup("pixel", MarkupKind.PIXELATE, listOf(MarkupPoint(0f, 0f), MarkupPoint(1f, 1f)), blockSize = .1f)
        val result = PhotoEngine.render(source, PhotoRecipe(markups = listOf(mark)))
        try {
            assertEquals(127, Color.alpha(result.getPixel(5, 5)))
            assertTrue(Color.red(result.getPixel(5, 5)) >= 253)
            assertEquals(0, Color.green(result.getPixel(5, 5)))
        } finally { source.recycle(); result.recycle() }
    }

    @Test fun coverOnFinalCropIsOpaqueAndSurvivesPngAndJpegEncoding() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val mark = PhotoMarkup("cover", MarkupKind.COVER, color = 0xFF000000)
        val result = PhotoEngine.render(source, PhotoRecipe(quarterTurns = 1, cropBounds = CropBounds(.1f, .1f, .9f, .9f), markups = listOf(mark)))
        try {
            assertEquals(240, result.width); assertEquals(320, result.height)
            assertEquals(Color.BLACK, result.getPixel(120, 160))
            assertEquals(0, Color.alpha(result.getPixel(0, 0)))
            for (format in PhotoFormat.entries) {
                val bytes = ByteArrayOutputStream().apply { PhotoEngine.encode(result, PhotoExportOptions(format), this) }.toByteArray()
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                try {
                    assertEquals(255, Color.alpha(decoded.getPixel(120, 160)))
                    assertTrue(Color.red(decoded.getPixel(120, 160)) < 5)
                    if (format == PhotoFormat.PNG) assertArrayEquals(pixels(result), pixels(decoded))
                    else assertTrue(Color.red(decoded.getPixel(0, 0)) > 250)
                } finally { decoded.recycle() }
            }
            assertTrue(pixels(source).all { it == 0 })
        } finally { source.recycle(); result.recycle() }
    }

    @Test fun pixelationAlwaysPrecedesPaintAndCancellationPreservesSource() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val marks = listOf(PhotoMarkup("cover", MarkupKind.COVER, color = 0xFF000000), PhotoMarkup("pixel", MarkupKind.PIXELATE))
        val result = PhotoEngine.render(source, PhotoRecipe(markups = marks))
        try {
            assertEquals(Color.BLACK, result.getPixel(50, 50))
            var checks = 0
            assertThrows(CancellationException::class.java) {
                PhotoEngine.render(source, PhotoRecipe(markups = marks)) { if (++checks == 4) throw CancellationException() }
            }
            assertFalse(source.isRecycled); assertTrue(pixels(source).all { it == Color.WHITE })
        } finally { source.recycle(); result.recycle() }
    }

    @Test fun recipesPreserveAllKindsAndReadOldVersionsWithoutMarkupsButRejectCorruptObjects() {
        val recipe = PhotoRecipe(markups = MarkupKind.entries.map { PhotoMarkup(it.name, it, width = .04f, filled = true, blockSize = .12f) })
        val json = RecipeJson.photo(recipe)
        assertEquals(7, JSONObject(json).getInt("version")); assertEquals(recipe, RecipeJson.readPhoto(json))
        for (version in 1..4) assertTrue(RecipeJson.readPhoto("""{"version":$version}""").markups.isEmpty())
        for (change in listOf<(JSONObject) -> Unit>({ it.put("kind", "UNKNOWN") }, { it.put("width", -1) },
            { it.getJSONArray("points").getJSONObject(0).put("x", 2) }, { it.put("color", 0) })) {
            val bad = JSONObject(json).apply { change(getJSONArray("markups").getJSONObject(0)) }
            assertThrows(IllegalArgumentException::class.java) { RecipeJson.readPhoto(bad.toString()) }
        }
        val duplicate = JSONObject(json).apply { getJSONArray("markups").getJSONObject(1).put("id", "BRUSH") }
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.readPhoto(duplicate.toString()) }
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.readPhoto("""{"version":8}""") }
    }
}
