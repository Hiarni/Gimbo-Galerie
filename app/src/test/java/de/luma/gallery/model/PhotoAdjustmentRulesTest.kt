package de.luma.gallery.model

import org.junit.Assert.*
import org.junit.Test

class PhotoAdjustmentRulesTest {
    @Test fun newAdjustmentsRejectNonFiniteAndOutOfRangeValues() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1.01f, 1.01f)) {
            assertThrows(IllegalArgumentException::class.java) { PhotoRecipe(highlights = value) }
            assertThrows(IllegalArgumentException::class.java) { PhotoRecipe(shadows = value) }
        }
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.01f, 1.01f)) {
            assertThrows(IllegalArgumentException::class.java) { PhotoRecipe(sharpness = value) }
        }
        assertEquals(-1f, PhotoRecipe(highlights = -1f).highlights, 0f)
        assertEquals(1f, PhotoRecipe(shadows = 1f, sharpness = 1f).sharpness, 0f)
    }
}
