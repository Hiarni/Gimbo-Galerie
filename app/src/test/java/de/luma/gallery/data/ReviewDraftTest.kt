package de.luma.gallery.data

import android.app.Application
import de.luma.gallery.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class ReviewDraftTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @After fun cleanup() {ReviewDraft(context).clear()}
    @Test fun decisionsAndUndoSurviveRecreationWithoutApplyingAnyFileAction() {
        val session=ReviewSession(listOf("a","b","c")).choose(ReviewAction.REMOVE).choose(ReviewAction.KEEP)
        ReviewDraft(context).save(session)
        val recreated=ReviewDraft(context)
        assertEquals(session,recreated.read())
        recreated.save(recreated.read()!!.undo())
        assertEquals("b",ReviewDraft(context).read()!!.currentId)
        assertEquals(setOf("a"),ReviewDraft(context).read()!!.selected(ReviewAction.REMOVE))
    }
    @Test fun anExplicitDiscardClearsTheDraft() {
        val draft=ReviewDraft(context);draft.save(ReviewSession(listOf("a")));draft.clear();assertNull(ReviewDraft(context).read())
    }
}
