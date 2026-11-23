package de.luma.gallery.model

import org.junit.Assert.*
import org.junit.Test

class EditorRulesTest {
    @Test fun undoRedoRestoresExactRecipes() {
        val initial = PhotoRecipe()
        val brightness = initial.copy(brightness = .2f)
        val rotated = brightness.copy(quarterTurns = 1, cropRatio = 1f)
        val history = History(present = initial).commit(brightness).commit(rotated)
        assertEquals(brightness, history.undo().present)
        assertEquals(initial, history.undo().undo().present)
        assertEquals(rotated, history.undo().undo().redo().redo().present)
    }

    @Test fun newEditAfterUndoDiscardsRedoBranch() {
        val history = History(present = PhotoRecipe()).commit(PhotoRecipe(brightness = .1f))
            .commit(PhotoRecipe(brightness = .3f)).undo().commit(PhotoRecipe(saturation = 0f))
        assertTrue(history.future.isEmpty())
        assertEquals(history, history.redo())
        assertEquals(.1f, history.undo().present.brightness)
    }

    @Test fun duplicateEditsDoNotPolluteUndoHistoryAndHistoryIsBounded() {
        val initial = History(present = PhotoRecipe())
        assertSame(initial, initial.commit(initial.present))
        val history = (1..100).fold(initial) { current, n -> current.commit(PhotoRecipe(brightness = n / 100f)) }
        assertEquals(50, history.past.size)
        assertEquals(initial, initial.undo())
        assertEquals(initial, initial.redo())
    }

    @Test fun videoTrimRejectsInvalidAndOutOfBoundsRanges() {
        assertTrue(VideoRecipe(0, 500).isValid(500))
        assertTrue(VideoRecipe(500, 1500, true).isValid(2000))
        assertFalse(VideoRecipe(-1, 1000).isValid(2000))
        assertFalse(VideoRecipe(1000, 500).isValid(2000))
        assertFalse(VideoRecipe(0, 499).isValid(2000))
        assertFalse(VideoRecipe(0, 2001).isValid(2000))
        assertFalse(VideoRecipe(0, 0).isValid(0))
    }
}
