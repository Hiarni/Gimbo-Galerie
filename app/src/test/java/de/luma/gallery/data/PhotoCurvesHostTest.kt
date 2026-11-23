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
class PhotoCurvesHostTest {
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    @Test fun rgbThenChannelCurvesMapSourcePixelsWithoutMutatingOriginal() {
        val source = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.rgb(32, 64, 96)); setPixel(1, 0, Color.rgb(128, 160, 192)); setPixel(2, 0, Color.WHITE)
        }
        val curves = PhotoCurves(rgb = ToneCurve(midtones = .65f), red = ToneCurve(1f, .75f, .5f, .25f, 0f),
            blue = ToneCurve(0f, .125f, .25f, .375f, .5f))
        val before = pixels(source); val result = PhotoEngine.render(source, PhotoRecipe(curves = curves))
        try {
            val master = ToneCurveSampler(curves.rgb).lookup()
            for (x in 0..2) {
                val pixel = before[x]; val output = result.getPixel(x, 0)
                assertEquals(255 - master[Color.red(pixel)], Color.red(output))
                assertEquals(master[Color.green(pixel)], Color.green(output))
                assertEquals(ToneCurveSampler(curves.blue).lookup()[master[Color.blue(pixel)]], Color.blue(output))
            }
            assertArrayEquals(before, pixels(source)); assertFalse(source.isRecycled)
        } finally { source.recycle(); result.recycle() }
    }

    @Test fun curvesFollowExposureAndToneAdjustmentsAndSurvivePngEncoding() {
        val source = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888).apply {
            for (y in 0..2) for (x in 0..3) setPixel(x, y, Color.rgb(40 + x * 40, 70 + y * 30, 100))
        }
        val recipe = PhotoRecipe(exposure = .3f, highlights = -.4f, shadows = .5f)
        val base = PhotoEngine.render(source, recipe)
        val curve = ToneCurve(shadows = .15f, midtones = .6f, highlights = .9f)
        val result = PhotoEngine.render(source, recipe.copy(curves = PhotoCurves(rgb = curve)))
        val encoded = ByteArrayOutputStream().apply { PhotoEngine.encode(result, PhotoExportOptions(PhotoFormat.PNG), this) }.toByteArray()
        val png = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
        try {
            val lookup = ToneCurveSampler(curve).lookup()
            for (y in 0..2) for (x in 0..3) {
                assertEquals(lookup[Color.red(base.getPixel(x, y))], Color.red(result.getPixel(x, y)))
                assertEquals(lookup[Color.green(base.getPixel(x, y))], Color.green(result.getPixel(x, y)))
            }
            assertArrayEquals(pixels(result), pixels(png))
        } finally { listOf(source, base, result, png).forEach { it.recycle() } }
    }

    @Test fun blackWhiteAndTransparencyWorkForNarrowAndSinglePixelImages() {
        val curve = ToneCurve(1f, .75f, .5f, .25f, 0f)
        for (width in listOf(1, 6)) {
            val source = Bitmap.createBitmap(width, 4, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until width) {
                    setPixel(x, 0, Color.BLACK); setPixel(x, 1, Color.WHITE)
                    setPixel(x, 2, Color.TRANSPARENT); setPixel(x, 3, Color.argb(128, 80, 120, 160))
                }
            }
            val before = pixels(source)
            val result = PhotoEngine.render(source, PhotoRecipe(curves = PhotoCurves(rgb = curve), sharpness = .5f))
            try {
                for (x in 0 until width) {
                    assertEquals(Color.WHITE, result.getPixel(x, 0)); assertEquals(Color.BLACK, result.getPixel(x, 1))
                    assertEquals(0, Color.alpha(result.getPixel(x, 2)))
                    assertEquals(128, Color.alpha(result.getPixel(x, 3)))
                }
                assertArrayEquals(before, pixels(source))
            } finally { source.recycle(); result.recycle() }
        }
        val single = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(80, 120, 160)) }
        val result = PhotoEngine.render(single, PhotoRecipe(curves = PhotoCurves(rgb = curve), sharpness = 1f))
        try { assertEquals(Color.rgb(175, 135, 95), result.getPixel(0, 0)) }
        finally { single.recycle(); result.recycle() }
    }

    @Test fun cancellationStopsCurvesBetweenRowsAndPreservesCallerOwnedSource() {
        val source = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val original = pixels(source)
        try {
            var checks = 0
            assertThrows(CancellationException::class.java) {
                PhotoEngine.render(source, PhotoRecipe(curves = PhotoCurves(rgb = ToneCurve(midtones = .7f)))) {
                    if (++checks == 5) throw CancellationException()
                }
            }
            assertEquals(5, checks); assertFalse(source.isRecycled); assertArrayEquals(original, pixels(source))
        } finally { source.recycle() }
    }

    @Test fun olderRecipesHaveNeutralCurvesAndCurrentRecipesRoundTripAndRejectInvalidCurves() {
        for (version in 1..2) {
            val recipe = RecipeJson.readPhoto("""{"version":$version,"contrast":1.2,"shadows":0.6}""")
            assertEquals(PhotoCurves(), recipe.curves)
            assertEquals(if (version == 1) 0f else .6f, recipe.shadows, 0f)
        }
        val recipe = PhotoRecipe(curves = PhotoCurves(rgb = ToneCurve(midtones = .6f), red = ToneCurve(white = .8f)))
        val json = RecipeJson.photo(recipe)
        assertEquals(7, org.json.JSONObject(json).getInt("version")); assertEquals(recipe, RecipeJson.readPhoto(json))
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.readPhoto("""{"version":8}""") }
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.readPhoto("""{"version":3,"curves":{"red":[0,0.25,2,0.75,1]}}""") }
        assertThrows(IllegalArgumentException::class.java) { RecipeJson.readPhoto("""{"version":3,"curves":{"red":[0,1]}}""") }
        assertThrows(org.json.JSONException::class.java) { RecipeJson.readPhoto("""{"version":3,"curves":"broken"}""") }
    }
}
