package de.luma.gallery.model

import org.junit.Assert.*
import org.junit.Test

class PhotoMarkupTest {
    @Test fun geometryMovesAndResizesWithinTheImageWithoutChangingIdentityOrStyle() {
        val mark = PhotoMarkup("id", MarkupKind.RECTANGLE)
        val moved = mark.moved(2f, -2f)
        assertEquals(.6f, moved.left, .0001f); assertEquals(1f, moved.right, .0001f)
        assertEquals(0f, moved.top, .0001f); assertEquals("id", moved.id)
        val resized = moved.resized(.8f, .9f)
        assertEquals(.8f, resized.right - resized.left, .0001f)
        assertEquals(.9f, resized.bottom - resized.top, .0001f)
        assertTrue(resized.left >= 0 && resized.right <= 1 && resized.top >= 0 && resized.bottom <= 1)
        assertEquals(mark.color, resized.color)
    }

    @Test fun invalidCoordinatesWidthsColorsAndUnboundedObjectsAreRejected() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f))
            assertThrows(IllegalArgumentException::class.java) { MarkupPoint(value, .5f) }
        assertThrows(IllegalArgumentException::class.java) { PhotoMarkup("", MarkupKind.BRUSH) }
        assertThrows(IllegalArgumentException::class.java) { PhotoMarkup("id", MarkupKind.BRUSH, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { PhotoMarkup("id", MarkupKind.RECTANGLE, listOf(MarkupPoint(.5f, .5f))) }
        assertThrows(IllegalArgumentException::class.java) { PhotoMarkup("id", MarkupKind.BRUSH, List(257) { MarkupPoint(.5f, .5f) }) }
        assertThrows(IllegalArgumentException::class.java) { PhotoMarkup("id", MarkupKind.BRUSH, width = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { PhotoMarkup("id", MarkupKind.PIXELATE, blockSize = .001f) }
        assertThrows(IllegalArgumentException::class.java) { PhotoMarkup("id", MarkupKind.COVER, color = 0x80000000) }
        val mark = PhotoMarkup("id", MarkupKind.BRUSH)
        assertThrows(IllegalArgumentException::class.java) { PhotoRecipe(markups = listOf(mark, mark)) }
        assertThrows(IllegalArgumentException::class.java) { PhotoRecipe(markups = List(33) { mark.copy(id = "$it") }) }
    }
}
