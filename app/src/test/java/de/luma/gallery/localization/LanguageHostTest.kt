package de.luma.gallery.localization

import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.appcompat.app.AppCompatActivity
import de.luma.gallery.R
import de.luma.gallery.data.SettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import de.luma.gallery.data.GalleryRepository
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.Locale

class LanguageTestActivity : AppCompatActivity() {

    override fun recreate() = Unit
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_GimboGalerie)
        super.onCreate(savedInstanceState)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@LooperMode(LooperMode.Mode.PAUSED)
class LanguageHostTest {
    private fun resources(tags: String) = RuntimeEnvironment.getApplication().let { app ->
        app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tags))
        }).resources
    }

    @Test fun germanEnglishAndFallbackResourcesHaveCorrectTextNumbersAndSingularForms() {
        val de = resources("de"); val en = resources("en"); val fr = resources("fr")
        assertEquals("Fotowerkzeuge", de.getString(R.string.photo_tools))
        assertEquals("Photo tools", en.getString(R.string.photo_tools))
        assertEquals("Photo tools", fr.getString(R.string.photo_tools))
        assertEquals(Locale.FRENCH, fr.appLocale)
        assertEquals("Größe: 1,50 MB", de.getString(R.string.file_size, 1.5))
        assertEquals("Size: 1.50 MB", en.getString(R.string.file_size, 1.5))
        assertEquals("1 Medium", de.getQuantityString(R.plurals.media_count, 1, 1))
        assertEquals("1 item", en.getQuantityString(R.plurals.media_count, 1, 1))
        assertEquals("2 items", en.getQuantityString(R.plurals.media_count, 2, 2))
        val mixed = RuntimeEnvironment.getApplication().createConfigurationContext(Configuration().apply {
            setLocales(LocaleList.forLanguageTags("fr,de,en"))
        })
        assertEquals("Photos", localizedResources(mixed).getString(R.string.photos))
        assertEquals("fr", resources("fr,de,en").appLocale.language)
    }

    @Test fun validationMessagesAreResolvedInTheCurrentLanguageAndUnknownErrorsGetFriendlyFallback() {
        val error = runCatching { albumName("") }.exceptionOrNull()!!
        assertEquals("Bitte einen Albumnamen mit 1 bis 80 Zeichen eingeben.", error.userMessage(resources("de"), R.string.album_save_failed))
        assertEquals("Enter an album name with 1 to 80 characters.", error.userMessage(resources("en"), R.string.album_save_failed))
        assertEquals("The album could not be saved.", IllegalStateException("Provider failed").userMessage(resources("en"), R.string.album_save_failed))
        val limit = MessageArgumentException(R.string.invalid_media_limit, 200)
        assertEquals("Select no more than 200 items per action.", limit.userMessage(resources("en"), R.string.action_unavailable))
    }

    @Test fun monthSearchUsesTheAppLanguageAndRetainsNamesFiltersAndNumericDateSearch() {
        val source = GalleryMedia("one", android.net.Uri.EMPTY, "Grüße.jpg", "Familie", 1791633600000L)
        assertEquals(listOf(source), selectMedia(listOf(source), MediaFilter.ALL, emptySet(), "Oktober 2026", locale = Locale.GERMAN))
        assertEquals(listOf(source), selectMedia(listOf(source), MediaFilter.ALL, emptySet(), "October 2026", locale = Locale.ENGLISH))
        assertEquals(listOf(source), selectMedia(listOf(source), MediaFilter.ALL, emptySet(), "2026-10", locale = Locale.ENGLISH))
        assertEquals(listOf(source), selectMedia(listOf(source), MediaFilter.FAVORITES, setOf("one"), "grusse familie", locale = Locale.ENGLISH))
        assertTrue(selectMedia(listOf(source), MediaFilter.VIDEOS, emptySet(), locale = Locale.ENGLISH).isEmpty())
    }

    @Test fun legacyAndroidLanguageChoiceSurvivesActivityReplacementAndDoesNotChangeDrafts() = checkActivityLanguageSwitch()

    @Test @Config(sdk = [35], qualifiers = "de")
    fun platformAndroidLanguageChoiceUsesTheSameSettingsAndDrafts() = checkActivityLanguageSwitch(platform = true)

    private fun checkActivityLanguageSwitch(platform: Boolean = false) {
        val app = RuntimeEnvironment.getApplication()
        val repository = GalleryRepository(app)
        app.getSharedPreferences("luma", 0).edit().clear().commit()
        val recipe = PhotoRecipe(exposure = .5f, texts = listOf(PhotoText("id", "Grüße")))
        repository.saveRecipe("source", recipe)
        runBlocking { SettingsRepository(app).saveAppearance(AppSettings(theme = ThemeMode.LIGHT, columns = 4)) }
        repository.saveFavorites(setOf("source"))
        var controller = Robolectric.buildActivity(LanguageTestActivity::class.java).setup()
        try {
            AppLanguage.ENGLISH.select()
            controller.pause().stop().destroy()
            controller = Robolectric.buildActivity(LanguageTestActivity::class.java).setup()
            assertEquals(AppLanguage.ENGLISH, AppLanguage.current())
            if (platform) assertEquals("en", app.getSystemService(android.app.LocaleManager::class.java).applicationLocales.toLanguageTags())
            else assertEquals("Photo tools", controller.get().getString(R.string.photo_tools))
            assertEquals("Photo tools", localizedResources(app).getString(R.string.photo_tools))
            assertEquals(recipe, GalleryRepository(app).loadRecipe("source"))
            assertEquals(4, runBlocking { SettingsRepository(app).values.first().appearance }.columns)
            assertEquals(ThemeMode.LIGHT, runBlocking { SettingsRepository(app).values.first().appearance }.theme)
            assertEquals(setOf("source"), GalleryRepository(app).favorites())
            AppLanguage.GERMAN.select()
            controller.pause().stop().destroy()
            controller = Robolectric.buildActivity(LanguageTestActivity::class.java).setup()
            assertEquals(AppLanguage.GERMAN, AppLanguage.current())
            if (platform) assertEquals("de", app.getSystemService(android.app.LocaleManager::class.java).applicationLocales.toLanguageTags())
            else assertEquals("Fotowerkzeuge", controller.get().getString(R.string.photo_tools))
            assertEquals("Fotowerkzeuge", localizedResources(app).getString(R.string.photo_tools))
            AppLanguage.SYSTEM.select()
            assertEquals(AppLanguage.SYSTEM, AppLanguage.current())
        } finally {
            AppLanguage.SYSTEM.select()
            controller.pause().stop().destroy()
        }
    }
}
