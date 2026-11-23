package de.luma.gallery.data

import android.graphics.Bitmap
import android.graphics.Color
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoAdjustmentsHostTest {
    @Test fun softMaskChangesOnlyItsRegionAndHslTargetsSelectedHue() {
        val source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(80, 80, 80)) }
        val mask = PhotoMask("m", x = .25f, y = .5f, width = .4f, height = .8f, exposure = 1f, feather = .5f)
        val recipe = PhotoRecipe(masks = listOf(mask))
        val out = PhotoEngine.render(source, recipe)
        try {
            assertTrue(Color.red(out.getPixel(20, 20)) > 100)
            assertEquals(source.getPixel(65, 20), out.getPixel(65, 20))
            assertEquals(80, Color.red(source.getPixel(20, 20)))
            assertEquals(recipe, RecipeJson.readPhoto(RecipeJson.photo(recipe)))
        } finally { out.recycle(); source.recycle() }
        val colors = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply { setPixel(0, 0, Color.RED); setPixel(1, 0, Color.BLUE) }
        val adjusted = PhotoEngine.render(colors, PhotoRecipe(hsl = PhotoHsl(listOf(HslBand(saturation = -1f)) + List(5) { HslBand() })))
        try { assertEquals(Color.BLUE, adjusted.getPixel(1, 0)); assertEquals(Color.red(adjusted.getPixel(0, 0)), Color.green(adjusted.getPixel(0, 0))) }
        finally { colors.recycle(); adjusted.recycle() }
    }
}
