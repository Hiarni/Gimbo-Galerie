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
class PhotoLayersHostTest {
    @Test fun orderVisibilityAndOpacityChangeActualExportPixelsAndKeepInput() {
        val source = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val a = PhotoMarkup("a", MarkupKind.RECTANGLE, color = 0xFFFF0000, filled = true)
        val b = a.copy(id = "b", color = 0xFF0000FF)
        fun pixel(recipe: PhotoRecipe): Int = PhotoEngine.render(source, recipe).let { out -> try { out.getPixel(20, 20) } finally { out.recycle() } }
        try {
            val recipe = PhotoRecipe(markups = listOf(a, b))
            assertEquals(Color.BLUE, pixel(recipe))
            assertEquals(Color.RED, pixel(recipe.copy(layerOrder = listOf("mark:b", "mark:a"))))
            assertEquals(Color.RED, pixel(recipe.withLayer("mark:b", visible = false)))
            val blended = pixel(recipe.withLayer("mark:b", opacity = .5f))
            assertTrue(Color.red(blended) in 126..129 && Color.blue(blended) in 126..129)
            assertEquals(Color.BLACK, source.getPixel(20, 20))
            assertEquals(recipe, RecipeJson.readPhoto(RecipeJson.photo(recipe)))
        } finally { source.recycle() }
    }
    @Test fun multilineAndLayerSettingsSurviveRecipeRoundTrip() {
        val recipe = PhotoRecipe(texts = listOf(PhotoText("text", "One\nTwo\nThree", visible = false, opacity = .4f)),
            markups = listOf(PhotoMarkup("arrow", MarkupKind.ARROW, opacity = .8f)), layerOrder = listOf("text:text", "mark:arrow"))
        assertEquals(recipe, RecipeJson.readPhoto(RecipeJson.photo(recipe)))
        assertEquals(listOf("mark:arrow"), recipe.copy(texts = emptyList()).layerKeys())
    }
}
