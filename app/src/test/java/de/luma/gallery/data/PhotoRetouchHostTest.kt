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
class PhotoRetouchHostTest {
    @Test fun cloneCopiesChosenTextureAndPerspectiveLeavesTransparentEdgesWithoutChangingInput() {
        val source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
            for (y in 0 until 40) for (x in 0 until 40) setPixel(x, y, Color.RED)
        }
        val mark = PhotoMarkup("clone", MarkupKind.CLONE, points = listOf(MarkupPoint(.75f, .5f)), width = .1f, sample = MarkupPoint(.25f, .5f))
        val recipe = PhotoRecipe(markups = listOf(mark), perspectiveY = .2f)
        val clone = PhotoEngine.render(source, PhotoRecipe(markups = listOf(mark)))
        val perspective = PhotoEngine.render(source, PhotoRecipe(perspectiveY = .2f))
        try {
            assertEquals(Color.RED, clone.getPixel(60, 20))
            assertEquals(Color.BLUE, source.getPixel(60, 20))
            assertEquals(0, Color.alpha(perspective.getPixel(0, 0)))
            assertEquals(255, Color.alpha(perspective.getPixel(40, 20)))
            assertEquals(recipe, RecipeJson.readPhoto(RecipeJson.photo(recipe)))
        } finally { source.recycle(); clone.recycle(); perspective.recycle() }
    }
}
