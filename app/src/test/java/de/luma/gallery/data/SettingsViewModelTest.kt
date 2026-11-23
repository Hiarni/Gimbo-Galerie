package de.luma.gallery.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import de.luma.gallery.GalleryViewModel
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private lateinit var database: ProjectDatabase
    private lateinit var holder: ViewModelStore
    private lateinit var fake: TestStore
    private lateinit var vm: GalleryViewModel
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(app, ProjectDatabase::class.java).build()
        holder = ViewModelStore(); fake = TestStore()
        vm = GalleryViewModel(app, ProjectRepository(database), settingsRepository = SettingsRepository(fake))
        ViewModelProvider(holder, viewModelFactory { initializer { vm } })[GalleryViewModel::class.java]
    }
    @After fun cleanup() { holder.clear(); database.close(); Dispatchers.resetMain() }

    @Test fun rapidChangesStayVisibleInOrderAndLastValuesArePersisted() {
        fake.gate = CompletableDeferred()
        vm.settings(vm.state.value.settings.copy(theme = ThemeMode.LIGHT))
        vm.settings(vm.state.value.settings.copy(columns = 5))
        vm.settings(vm.state.value.settings.copy(newestFirst = false))
        val expected = AppSettings(ThemeMode.LIGHT, columns = 5, newestFirst = false)
        assertEquals(expected, vm.state.value.settings)
        assertEquals(0, fake.writes)
        fake.gate!!.complete(Unit)
        assertEquals(3, fake.writes)
        assertEquals(expected, vm.state.value.settings)
        assertEquals("LIGHT", fake.value.value[stringPreferencesKey("theme")])
        assertEquals(5, fake.value.value[intPreferencesKey("columns")])
        assertEquals(false, fake.value.value[booleanPreferencesKey("newestFirst")])
    }

    @Test fun overwritingRemembersExportChoicesForTheNextEdit()=runBlocking {
        val app=RuntimeEnvironment.getApplication()
        ExportController.update(ExportSnapshot());ExportController.remove(app)
        val options=PhotoExportOptions(PhotoFormat.PNG,1280,80)
        val source=GalleryMedia("source",android.net.Uri.parse("content://media/external/images/media/1"),"Source.png","Camera",0)
        try {
            vm.overwritePhoto(source,PhotoRecipe(),options)
            assertEquals(options,vm.state.value.exportOptions)
            assertEquals(options,SettingsRepository(fake).values.first().export)
            withTimeout(5000) {ExportController.state.first {it.running}}
            val task=ExportController.read(app)!!
            assertTrue(task.overwrite);assertEquals(options,task.options)
            assertEquals(listOf(source),task.items)
        } finally {ExportController.remove(app);ExportController.update(ExportSnapshot())}
    }

    @Test fun failedSaveRestoresActualSavedStateAndReportsTheFailure() = runBlocking {
        val messages = mutableListOf<String>()
        val listener = launch(UnconfinedTestDispatcher()) { vm.messages.collect { messages += it } }
        try {
            vm.settings(AppSettings(theme = ThemeMode.LIGHT))
            fake.failWrite = true
            vm.settings(vm.state.value.settings.copy(columns = 5))
            assertEquals(AppSettings(theme = ThemeMode.LIGHT), vm.state.value.settings)
            assertEquals(1, messages.size)
            assertTrue(messages.single().startsWith("Einstellungen konnten nicht gespeichert werden."))
            assertTrue(vm.state.value.settingsReady)
        } finally { listener.cancelAndJoin() }
    }

    @Test fun readFailureBlocksChangesAndExplicitRetryLoadsTheExistingValues() {
        fake.failRead = true
        vm.reloadSettings()
        assertFalse(vm.state.value.settingsReady); assertFalse(vm.state.value.settingsLoading)
        assertNotNull(vm.state.value.settingsError)
        vm.settings(AppSettings(theme = ThemeMode.LIGHT))
        assertEquals(0, fake.writes)
        fake.value.value = preferencesOf(intPreferencesKey("columns") to 4, stringPreferencesKey("theme") to "LIGHT")
        fake.failRead = false
        vm.reloadSettings()
        assertTrue(vm.state.value.settingsReady); assertNull(vm.state.value.settingsError)
        assertEquals(AppSettings(theme = ThemeMode.LIGHT, columns = 4), vm.state.value.settings)
    }

    private class TestStore : DataStore<Preferences> {
        val value = MutableStateFlow<Preferences>(emptyPreferences())
        var failRead = false; var failWrite = false; var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        override val data: Flow<Preferences> get() = flow { if (failRead) throw IOException(); emitAll(value) }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            gate?.await()
            if (failWrite) { failWrite = false; throw IOException() }
            return transform(value.value).also { writes++; value.value = it }
        }
    }
}
