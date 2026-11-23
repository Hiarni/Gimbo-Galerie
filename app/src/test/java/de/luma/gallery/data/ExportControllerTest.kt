package de.luma.gallery.data

import android.net.Uri
import de.luma.gallery.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ExportControllerTest {
    @Test fun committedJournalCanResumeAfterProcessStateLossAndDiscardKeepsMediaReferences() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        ExportController.update(ExportSnapshot()); ExportController.remove(context)
        val photo = GalleryMedia("ref", Uri.parse("content://test/image"), "Original", "", 0)
        val request = ExportRequest(items = listOf(photo, photo.copy(id = "second")), photo = PhotoRecipe(), completed = 1)
        try {
            ExportController.write(context, request)
            ExportController.inspect(context)
            assertTrue(ExportController.state.value.interrupted)
            assertEquals(1, ExportController.state.value.completed)
            assertEquals(request, ExportController.read(context))
            ExportController.resume(context)
            assertTrue(ExportController.state.value.running)
            assertEquals("de.luma.gallery.EXPORT", shadowOf(context).nextStartedService.action)
            ExportController.update(ExportSnapshot(interrupted = true))
            ExportController.discard(context)
            assertNull(ExportController.read(context))
            assertFalse(ExportController.state.value.interrupted)
        } finally { ExportController.remove(context); ExportController.update(ExportSnapshot()) }
    }
}
