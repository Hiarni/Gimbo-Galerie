package de.luma.gallery.model

import org.junit.Assert.*
import org.junit.Test

class PhotoTextTest {
    @Test fun captionsRejectBlankControlCharactersAndExcessiveLength() {
        for (text in listOf("", " ", "A\nB\nC\nD\nE", "A\tB", "a".repeat(121)))
            assertThrows(IllegalArgumentException::class.java) { PhotoText("id", text) }
        assertEquals(120, PhotoText("id", "a".repeat(120)).text.length)
        assertEquals("Grüße 🌞 مرحبا", PhotoText("id", "Grüße 🌞 مرحبا").text)
    }

    @Test fun positionsSizeAndColorMustBeValidAndFinite() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f)) {
            assertThrows(IllegalArgumentException::class.java) { PhotoText("id", "A", x = value) }
            assertThrows(IllegalArgumentException::class.java) { PhotoText("id", "A", y = value) }
        }
        for (value in listOf(Float.NaN, .024f, .201f))
            assertThrows(IllegalArgumentException::class.java) { PhotoText("id", "A", size = value) }
        assertThrows(IllegalArgumentException::class.java) { PhotoText("id", "A", color = 0x00FFFFFF) }
        assertThrows(IllegalArgumentException::class.java) { PhotoText("", "A") }
        PhotoText("id", "A", x = 0f, y = 1f, size = .2f, color = 0xFF000000)
    }

    @Test fun duplicateIdsAndTooManyCaptionsCannotEnterRecipes() {
        val text = PhotoText("id", "A")
        assertThrows(IllegalArgumentException::class.java) { PhotoRecipe(texts = listOf(text, text)) }
        assertThrows(IllegalArgumentException::class.java) { PhotoRecipe(texts = (0..12).map { text.copy(id = "$it") }) }
        assertEquals(12, PhotoRecipe(texts = (0..11).map { text.copy(id = "$it") }).texts.size)
    }

    @Test fun captionEditsAndImageGeometryPreserveOtherObjectsAndSettings() {
        val first = PhotoText("first", "A"); val second = PhotoText("second", "B", x = .8f)
        val recipe = PhotoRecipe(exposure = .7f, texts = listOf(first, second))
        val changed = recipe.withText(second.copy(text = "Neu", bold = true))
        assertEquals(first, changed.texts.first()); assertEquals("Neu", changed.texts.last().text)
        assertEquals(.7f, changed.exposure, 0f)
        assertEquals(changed.texts, changed.rotatedClockwise().mirrored().texts)
        val history = History(present = recipe).commit(changed)
        assertEquals(recipe, history.undo().present); assertEquals(changed, history.undo().redo().present)
    }
}
