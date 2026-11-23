package de.luma.gallery.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
class SettingsRepositoryTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var directory: File
    private lateinit var file: File
    private lateinit var legacyName: String
    private lateinit var job: Job
    private lateinit var repository: SettingsRepository
    private val legacy get() = app.getSharedPreferences(legacyName, 0)

    @Before fun setup() {
        directory = File(app.cacheDir, "settings-${UUID.randomUUID()}").apply { mkdirs() }
        file = File(directory, "settings.preferences_pb")
        legacyName = "legacy-${UUID.randomUUID()}"
        open()
    }
    private fun open() {
        job = SupervisorJob()
        repository = SettingsRepository(PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(app, legacyName, keysToMigrate = SETTINGS_KEYS)),
            scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file }))
    }
    @After fun cleanup() = runBlocking { job.cancelAndJoin(); directory.deleteRecursively(); app.deleteSharedPreferences(legacyName); Unit }

    @Test fun migrationCopiesSevenSettingsAndRetainsFavoritesImportsAndDrafts() = runBlocking {
        legacy.edit().putString("theme", "LIGHT").putLong("accent", 0xFFA5D6BE).putInt("columns", 4).putBoolean("newestFirst", false)
            .putString("exportFormat", "PNG").putInt("exportSize", 1920).putInt("exportQuality", 80)
            .putStringSet("favorites", setOf("photo")).putStringSet("imports", setOf("content://provider/image"))
            .putString("recipe:photo", "{\"version\":5}").putString("videoRecipe:clip", "video").commit()
        val expected = StoredSettings(AppSettings(ThemeMode.LIGHT, 0xFFA5D6BE, 4, false), PhotoExportOptions(PhotoFormat.PNG, 1920, 80))
        assertEquals(expected, repository.values.first())
        assertTrue(SETTINGS_KEYS.none { legacy.contains(it) })
        assertEquals(setOf("photo"), legacy.getStringSet("favorites", emptySet()))
        assertEquals(setOf("content://provider/image"), legacy.getStringSet("imports", emptySet()))
        assertEquals("{\"version\":5}", legacy.getString("recipe:photo", null))
        assertEquals("video", legacy.getString("videoRecipe:clip", null))
        job.cancelAndJoin(); open()
        assertEquals(expected, repository.values.first())
    }

    @Test fun sortingAndManualAlbumOrderSurviveReopening()=runBlocking {
        val expected=AppSettings(sort=MediaSort.NAME,newestFirst=false,groupByDay=false,albumOrder=listOf("virtual:b","virtual:a","folder:Camera"))
        repository.saveAppearance(expected);job.cancelAndJoin();open()
        assertEquals(expected,repository.values.first().appearance)
    }
    @Test fun existingDataStoreValuesWinOverReintroducedLegacyValues() = runBlocking {
        val appearance = AppSettings(ThemeMode.LIGHT, columns = 4)
        repository.saveAppearance(appearance)
        job.cancelAndJoin()
        legacy.edit().putString("theme", "DARK").putInt("columns", 2).putString("exportFormat", "PNG").commit()
        open()
        val saved = repository.values.first()
        assertEquals(appearance, saved.appearance)
        assertEquals(PhotoFormat.PNG, saved.export.format)
        assertTrue(SETTINGS_KEYS.none { legacy.contains(it) })
    }

    @Test fun invalidIndividualLegacyTypesAndValuesDoNotResetOtherSettings() = runBlocking {
        legacy.edit().putInt("theme", 3).putLong("accent", 42).putInt("columns", 99).putBoolean("newestFirst", false)
            .putString("exportFormat", "TIFF").putInt("exportSize", 15).putInt("exportQuality", 80).commit()
        val saved = repository.values.first()
        assertEquals(AppSettings(columns = 8, newestFirst = false), saved.appearance)
        assertEquals(PhotoExportOptions(quality = 80), saved.export)
    }

    @Test fun concurrentAppearanceAndExportWritesPersistIndependentlyAcrossReopen() = runBlocking {
        val appearance = AppSettings(ThemeMode.SYSTEM, 0xFF9DCBEA, 5, false, albumColumns=4,autoPlayVideos=false,reviewGestures=false,reviewLeft=ReviewAction.ARCHIVE,reviewRight=ReviewAction.FAVORITE,reviewPlayful=false)
        val export = PhotoExportOptions(PhotoFormat.PNG, 2560, 65)
        coroutineScope { awaitAll(async { repository.saveAppearance(appearance) }, async { repository.saveExport(export) }) }
        job.cancelAndJoin(); open()
        assertEquals(StoredSettings(appearance, export), repository.values.first())
        assertTrue(runCatching { repository.saveAppearance(appearance.copy(columns = 9)) }.isFailure)
        assertEquals(StoredSettings(appearance, export), repository.values.first())
    }

    @Test fun corruptedStoreIsNeverReplacedAndLegacyValuesRemainAvailable() = runBlocking {
        val bytes = byteArrayOf(-1, -2, 0)
        file.writeBytes(bytes)
        legacy.edit().putString("theme", "LIGHT").commit()
        assertTrue(runCatching { repository.values.first() }.exceptionOrNull() is CorruptionException)
        assertTrue(runCatching { repository.saveAppearance(AppSettings()) }.isFailure)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals("LIGHT", legacy.getString("theme", null))
    }
}
