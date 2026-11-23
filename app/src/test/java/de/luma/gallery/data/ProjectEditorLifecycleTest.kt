package de.luma.gallery.data

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import de.luma.gallery.GalleryViewModel
import de.luma.gallery.model.GalleryMedia
import de.luma.gallery.model.PhotoRecipe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectEditorLifecycleTest {
    @Before fun setupMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }
    @Test fun editorAutosavesIndependentCopiesAndRestoresAfterViewModelAndDatabaseRestart() = runBlocking {
        val application = RuntimeEnvironment.getApplication()
        val name = "editor-lifecycle-${UUID.randomUUID()}.db"
        var database = Room.databaseBuilder(application, ProjectDatabase::class.java, name).build()
        var store = ViewModelStore()
        val file = File.createTempFile("editor_original_", ".jpg", application.cacheDir).apply { writeBytes(byteArrayOf(11, 22, 33)) }
        val bytes = file.readBytes()
        try {
            var repository = ProjectRepository(database)
            val vm = GalleryViewModel(application, repository)
            ViewModelProvider(store, viewModelFactory { initializer { vm } })[GalleryViewModel::class.java]
            val source = GalleryMedia(UUID.randomUUID().toString(), Uri.fromFile(file), "Original.jpg", "Test", 0)
            vm.beginEditor(source)
            val initial = PhotoRecipe(exposure = .5f)
            vm.photoDraft(vm.state.value.editor!!, initial)
            vm.saveProject("Erste Idee", false, photo = initial)
            await { vm.state.value.editor?.projectId != null && !vm.state.value.savingProject }
            val firstId = vm.state.value.editor!!.projectId!!
            val edited = initial.copy(temperature = .4f, contrast = 1.2f, highlights = -.3f, shadows = .5f, sharpness = .4f)
            vm.photoDraft(vm.state.value.editor!!, edited)
            vm.saveProject("Variante", true, photo = edited)
            await { vm.state.value.editor?.projectId != firstId && !vm.state.value.savingProject }
            val copyId = vm.state.value.editor!!.projectId!!
            val copyRecipe = edited.copy(saturation = 0f)
            vm.photoDraft(vm.state.value.editor!!, copyRecipe)
            vm.closeEditor()
            await { repository.find(copyId)?.photo == copyRecipe }
            assertEquals(edited, repository.find(firstId)!!.photo)
            assertArrayEquals(bytes, file.readBytes())

            store.clear(); database.close()
            database = Room.databaseBuilder(application, ProjectDatabase::class.java, name).build()
            repository = ProjectRepository(database)
            store = ViewModelStore()
            val restoredVm = GalleryViewModel(application, repository)
            ViewModelProvider(store, viewModelFactory { initializer { restoredVm } })[GalleryViewModel::class.java]
            restoredVm.openProject(firstId)
            await { restoredVm.state.value.editor?.projectId == firstId }
            assertEquals(edited, restoredVm.state.value.editor!!.photo)
            assertEquals(source.uri, restoredVm.state.value.editor!!.source.uri)
            restoredVm.openProject(copyId)
            await { restoredVm.state.value.editor?.projectId == copyId }
            assertEquals(copyRecipe, restoredVm.state.value.editor!!.photo)
            assertArrayEquals(bytes, file.readBytes())
        } finally { store.clear(); database.close(); application.deleteDatabase(name); file.delete() }
    }

    private suspend fun await(condition: suspend () -> Boolean) = withTimeout(10_000) {
        while (!condition()) { delay(10) }
    }
}
