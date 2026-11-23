package de.luma.gallery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import de.luma.gallery.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val SETTINGS_KEYS = setOf("theme", "accent", "columns", "newestFirst", "exportFormat", "exportSize", "exportQuality")
private val Context.lumaSettings by preferencesDataStore(name = "luma-settings",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "luma", keysToMigrate = SETTINGS_KEYS)) })

data class StoredSettings(val appearance: AppSettings = AppSettings(), val export: PhotoExportOptions = PhotoExportOptions())


class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.lumaSettings)
    val values: Flow<StoredSettings> = store.data.map(::decode)

    suspend fun saveAppearance(value: AppSettings) {
        requireMessage(value.columns in 2..8 && value.albumColumns in 2..6 && value.trashDays in 0..30 && value.accent in 0xFF000000L..0xFFFFFFFFL, R.string.invalid_settings)
        store.edit {
            it[THEME] = value.theme.name; it[ACCENT] = value.accent
            requireMessage(value.toolOrder.size == DEFAULT_PHOTO_TOOLS.size && value.toolOrder.toSet() == DEFAULT_PHOTO_TOOLS.toSet(), R.string.invalid_settings)
            it[SORT] = value.sort.name; it[GROUP] = value.groupByDay; it[ALBUM_ORDER] = org.json.JSONArray(value.albumOrder.take(5000)).toString()
            it[ALBUM_COLUMNS] = value.albumColumns; it[COLUMNS] = value.columns; it[NEWEST] = value.newestFirst
            it[REVIEW_GESTURES]=value.reviewGestures;it[REVIEW_LEFT]=value.reviewLeft.name;it[REVIEW_RIGHT]=value.reviewRight.name;it[REVIEW_PLAYFUL]=value.reviewPlayful;it[AUTO_PLAY] = value.autoPlayVideos; it[REMEMBER_GRID] = value.rememberGrid; it[TRASH_DAYS] = value.trashDays
            it[ANIMATIONS] = value.thumbnailAnimations; it[TOOLS] = value.toolOrder.joinToString(",")
        }
    }
    suspend fun saveExport(value: PhotoExportOptions) { store.edit {
        it[FORMAT] = value.format.name; it[SIZE] = value.maxEdge; it[QUALITY] = value.quality; it[METADATA] = value.metadata.name
    } }

    private fun decode(prefs: Preferences): StoredSettings {

        val raw = prefs.asMap()
        val theme = (raw[THEME] as? String)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK
        val accent = (raw[ACCENT] as? Long)?.takeIf { it in 0xFF000000L..0xFFFFFFFFL } ?: AppSettings().accent
        val columns = (raw[COLUMNS] as? Int)?.coerceIn(2, 8) ?: 3
        val newest = raw[NEWEST] as? Boolean ?: true
        val format = (raw[FORMAT] as? String)?.let { runCatching { PhotoFormat.valueOf(it) }.getOrNull() } ?: PhotoFormat.JPEG
        val size = (raw[SIZE] as? Int)?.takeIf { it in listOf(1280, 1920, 2560, 4096, 8192, 16384) } ?: 4096
        val quality = (raw[QUALITY] as? Int)?.takeIf { it in 60..100 } ?: 95
        val animations = raw[ANIMATIONS] as? Boolean ?: true
        val order = (raw[TOOLS] as? String)?.split(",").orEmpty().filter { it in DEFAULT_PHOTO_TOOLS }.distinct()
        val tools = order + DEFAULT_PHOTO_TOOLS.filter { it !in order }
        val metadata = (raw[METADATA] as? String)?.let { runCatching { MetadataMode.valueOf(it) }.getOrNull() } ?: MetadataMode.NONE
        return StoredSettings(AppSettings(theme, accent, columns, newest, animations, tools, raw[REMEMBER_GRID] as? Boolean ?: true, (raw[TRASH_DAYS] as? Int)?.coerceIn(0,30) ?: 30,
            (raw[SORT] as? String)?.let {runCatching {MediaSort.valueOf(it)}.getOrNull()} ?: MediaSort.DATE,
            raw[GROUP] as? Boolean ?: true,
            (raw[ALBUM_ORDER] as? String)?.let {runCatching {val array=org.json.JSONArray(it); (0 until minOf(array.length(),5000)).map {i->array.getString(i)}.distinct()}.getOrNull()}.orEmpty(), (raw[ALBUM_COLUMNS] as? Int)?.coerceIn(2,6) ?: 2, raw[AUTO_PLAY] as? Boolean ?: true, raw[REVIEW_GESTURES] as? Boolean ?: true,
            (raw[REVIEW_LEFT] as? String)?.let {runCatching {ReviewAction.valueOf(it)}.getOrNull()} ?: ReviewAction.REMOVE,
            (raw[REVIEW_RIGHT] as? String)?.let {runCatching {ReviewAction.valueOf(it)}.getOrNull()} ?: ReviewAction.KEEP, raw[REVIEW_PLAYFUL] as? Boolean ?: true), PhotoExportOptions(format, size, quality, metadata))
    }
    private companion object {
        val SORT=stringPreferencesKey("mediaSort"); val GROUP=booleanPreferencesKey("groupByDay"); val ALBUM_ORDER=stringPreferencesKey("albumOrder")
        val REMEMBER_GRID = booleanPreferencesKey("rememberGrid"); val TRASH_DAYS = intPreferencesKey("trashDays")
        val ANIMATIONS = booleanPreferencesKey("thumbnailAnimations"); val TOOLS = stringPreferencesKey("toolOrder")
        val THEME = stringPreferencesKey("theme"); val ACCENT = longPreferencesKey("accent")
        val REVIEW_GESTURES=booleanPreferencesKey("reviewGestures");val REVIEW_LEFT=stringPreferencesKey("reviewLeft");val REVIEW_RIGHT=stringPreferencesKey("reviewRight")
        val REVIEW_PLAYFUL=booleanPreferencesKey("reviewPlayful")
        val AUTO_PLAY = booleanPreferencesKey("autoPlayVideos")
        val ALBUM_COLUMNS = intPreferencesKey("albumColumns")
        val COLUMNS = intPreferencesKey("columns"); val NEWEST = booleanPreferencesKey("newestFirst")
        val METADATA = stringPreferencesKey("exportMetadata")
        val FORMAT = stringPreferencesKey("exportFormat"); val SIZE = intPreferencesKey("exportSize"); val QUALITY = intPreferencesKey("exportQuality")
    }
}
