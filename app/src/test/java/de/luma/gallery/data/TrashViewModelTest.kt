package de.luma.gallery.data

import android.app.Application
import android.content.pm.ProviderInfo
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import de.luma.gallery.GalleryViewModel
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {
    private lateinit var database: ProjectDatabase
    private lateinit var store: ViewModelStore
    private lateinit var vm: GalleryViewModel
    private lateinit var provider: TrashRepositoryTest.TestMediaProvider
    private val item = GalleryMedia("content://media/external/images/media/1", Uri.parse("content://media/external/images/media/1"), "Foto.jpg", "Test", 0)

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("luma", 0).edit().clear().commit()
        provider = TrashRepositoryTest.TestMediaProvider()
        provider.attachInfo(app, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        database = Room.inMemoryDatabaseBuilder(app, ProjectDatabase::class.java).build()
        store = ViewModelStore()
        vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                GalleryViewModel(app, ProjectRepository(database)) as T
        })[GalleryViewModel::class.java]
    }

    @After fun cleanup() { store.clear(); database.close(); Dispatchers.resetMain() }

    private suspend fun await(condition: () -> Boolean) = withTimeout(10_000) {
        while (!condition()) delay(10)
    }

    @Test fun canceledSystemRequestKeepsMediaAndProjectsAndBlocksDuplicateRequests() = runBlocking {
        provider.rows += TrashRepositoryTest.Row(1, trashed = false)
        val projectId = ProjectRepository(database).save(item, "Bleibt erhalten", photo = PhotoRecipe(exposure = 1f))
        await { !vm.state.value.loading }
        vm.favorite(item.id)
        assertNotNull(vm.prepareMediaAction(MediaAction.TRASH, listOf(item)))
        assertEquals(MediaAction.TRASH, vm.state.value.pendingMediaAction?.action)
        assertNull(vm.prepareMediaAction(MediaAction.DELETE, listOf(item)))
        assertEquals(1, provider.requests.size)
        vm.completeMediaAction(false)
        assertNull(vm.state.value.pendingMediaAction)
        await { !vm.state.value.loading && vm.state.value.media.any { it.uri == item.uri } }
        assertTrue(item.id in vm.state.value.favorites)
        assertNotNull(ProjectRepository(database).find(projectId))
        assertEquals(0, provider.directMutations)
    }

    @Test fun successfulRestoreRefreshesLibraryAndTrashAndLaunchFailureReleasesPendingState() = runBlocking {
        val row = TrashRepositoryTest.Row(1); provider.rows += row
        vm.refreshTrash()
        await { !vm.state.value.trashLoading && vm.state.value.trash.size == 1 }
        assertNotNull(vm.prepareMediaAction(MediaAction.RESTORE, listOf(item)))

        row.trashed = false
        vm.completeMediaAction(true)
        await { !vm.state.value.loading && !vm.state.value.trashLoading &&
            vm.state.value.trash.isEmpty() && vm.state.value.media.any { it.uri == item.uri } }
        assertNull(vm.state.value.pendingMediaAction)
        assertNotNull(vm.prepareMediaAction(MediaAction.TRASH, listOf(item)))
        vm.completeMediaAction(false, launchFailed = true)
        assertNull(vm.state.value.pendingMediaAction)
        assertEquals(0, provider.directMutations)
    }
}
