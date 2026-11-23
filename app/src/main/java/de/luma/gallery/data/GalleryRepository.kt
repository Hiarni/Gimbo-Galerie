package de.luma.gallery.data

import de.luma.gallery.localization.*
import de.luma.gallery.R
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.edit
import androidx.core.net.toUri
import de.luma.gallery.model.GalleryMedia
import de.luma.gallery.model.PhotoRecipe
import de.luma.gallery.model.CropBounds
import de.luma.gallery.model.PhotoExportOptions
import de.luma.gallery.model.PhotoFormat
import de.luma.gallery.model.VideoRecipe
import de.luma.gallery.model.canManageMedia
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryRepository(private val context: Context) {
    private val resolver = context.contentResolver
    private val strings get() = de.luma.gallery.localization.localizedResources(context)
    private val prefs = context.getSharedPreferences("luma", Context.MODE_PRIVATE)

    fun archived(): Set<String> = prefs.getStringSet("archived", emptySet())!!.toSet()
    fun saveArchived(value: Set<String>) { prefs.edit { putStringSet("archived", value) } }
    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet())!!.toSet()
    fun saveFavorites(value: Set<String>) { prefs.edit { putStringSet("favorites", value) } }

    suspend fun loadLibrary(): List<GalleryMedia> = withContext(Dispatchers.IO) {
        val items = mutableListOf<GalleryMedia>()
        for (video in listOf(false, true)) {
            val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = mutableListOf(
                MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (video) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN,
                if (video) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            )
            if (video) projection += MediaStore.Video.Media.DURATION
            val selection = if (Build.VERSION.SDK_INT >= 30) "is_pending = 0 AND is_trashed = 0" else "is_pending = 0"

            try {
                var beforeAdded: Long? = null
                var beforeId: Long? = null
                val seen = mutableSetOf<String>()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val boundary = beforeAdded
                    val args = Bundle().apply {
                        putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                            selection + if (boundary != null) " AND (date_added < ? OR (date_added = ? AND _id < ?))" else "")
                        if (boundary != null) putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                            arrayOf(boundary.toString(), boundary.toString(), beforeId.toString()))
                        putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "date_added DESC, _id DESC")
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 500)
                    }
                    var rows = 0
                    var added = 0
                    resolver.query(collection, projection.toTypedArray(), args, null)?.use { cursor ->
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            rows++
                            val id = cursor.getLong(0)
                            val uri = ContentUris.withAppendedId(collection, id)
                            beforeAdded = cursor.getLong(2); beforeId = id
                            if (!seen.add(uri.toString())) continue
                            added++
                            val taken = cursor.getLong(5).takeIf { it > 0 } ?: cursor.getLong(2) * 1000
                            items += GalleryMedia(uri.toString(), uri, cursor.getString(1) ?: strings.getString(R.string.unnamed),
                                cursor.getString(6) ?: strings.getString(R.string.pictures), taken, video,
                                if (video) cursor.getLong(7) else 0, cursor.getLong(3))
                        }
                    }


                    if (rows != 500 || added == 0) break
                    kotlinx.coroutines.yield()
                }
            } catch (_: SecurityException) {   }
        }
        val imports = prefs.getStringSet("imports", emptySet())!!.toSet()
        imports.forEach { value ->
            val uri = value.toUri()
            runCatching { describe(uri) }.getOrNull()?.let { item ->
                if (items.none { it.uri == uri }) items += item
            }
        }
        items.sortedByDescending { it.timestamp }
    }

    fun importedReferences():Set<String> = prefs.getStringSet("imports",emptySet())!!.toSet()

    suspend fun forgetReferences(ids:Set<String>)=withContext(Dispatchers.IO) {importsMutex.withLock {
        val existing=importedReferences()
        check(prefs.edit().putStringSet("imports",existing-ids).commit())
    }}

    suspend fun import(uris: List<Uri>) = withContext(Dispatchers.IO) {importsMutex.withLock {
        val persistent = prefs.getStringSet("imports", emptySet())!!.toMutableSet()
        uris.forEach { uri ->
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            describe(uri)
            persistent += uri.toString()
        }
        check(prefs.edit().putStringSet("imports",persistent).commit())
    }}


    internal suspend fun registerGranted(uris: List<Uri>) = withContext(Dispatchers.IO) {importsMutex.withLock {
        val persistent = prefs.getStringSet("imports", emptySet())!!.toMutableSet()
        uris.forEach { describe(it); persistent += it.toString() }
        check(prefs.edit().putStringSet("imports",persistent).commit())
    }}

    internal fun describe(uri: Uri): GalleryMedia {
        val mime = resolver.getType(uri).orEmpty()
        requireMessage(mime.startsWith("image/") || mime.startsWith("video/"), R.string.not_media_file)
        var name = strings.getString(R.string.import_label)
        var size = 0L
        resolver.query(uri, arrayOf("_display_name", "_size"), Bundle(), null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0) ?: name
                size = cursor.getLong(1)
            }
        }
        val video = mime.startsWith("video/")
        val duration = if (video) MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } else 0
        val item = GalleryMedia(uri.toString(), uri, name, strings.getString(R.string.imported_media_collection), 0, video, duration, size,isImported=true)
        requireMessage(!isTrashed(item), R.string.file_in_trash)
        return item
    }

    suspend fun inspectSource(uri: Uri): GalleryMedia = withContext(Dispatchers.IO) { describe(uri) }

    suspend fun clearDraft(id:String)=withContext(Dispatchers.IO) {check(prefs.edit().remove("recipe:$id").remove("videoRecipe:$id").commit())}
    fun loadRecipe(id: String): PhotoRecipe = runCatching { RecipeJson.readPhoto(prefs.getString("recipe:$id", "{}")!!) }.getOrDefault(PhotoRecipe())

    fun saveRecipe(id: String, recipe: PhotoRecipe) {
        prefs.edit { putString("recipe:$id", RecipeJson.photo(recipe)) }
    }

    fun loadVideoRecipe(item: GalleryMedia): VideoRecipe = runCatching {
        RecipeJson.readVideo(prefs.getString("videoRecipe:${item.id}", "")!!).takeIf { it.isValid(item.durationMs) }
    }.getOrNull() ?: VideoRecipe(0, item.durationMs)

    fun saveVideoRecipe(id: String, recipe: VideoRecipe) { prefs.edit { putString("videoRecipe:$id", RecipeJson.video(recipe)) } }

    suspend fun checkSource(item: GalleryMedia) = withContext(Dispatchers.IO) {
        requireMessage(!isTrashed(item), R.string.source_in_trash)
        val stream = if (item.isDemo) context.assets.open(item.uri.path!!.removePrefix("/android_asset/"))
            else resolver.openInputStream(item.uri)
        requireNotNull(stream).use { require(it.read() != -1) }
    }

    private fun isTrashed(item: GalleryMedia): Boolean {
        if (Build.VERSION.SDK_INT < 30 || !canManageMedia(item)) return false
        val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
        return resolver.query(item.uri, arrayOf(MediaStore.MediaColumns.IS_TRASHED), args, null)?.use {
            it.moveToFirst() && it.getInt(0) == 1
        } ?: false
    }

    suspend fun exportPhoto(item: GalleryMedia, recipe: PhotoRecipe, options: PhotoExportOptions, outputName: String? = null): Uri = withContext(Dispatchers.IO) {
        outputName?.let { exportExists(false, "$it.${options.format.extension}")?.let { uri -> return@withContext uri } }
        if (options.maxEdge >= 8192) return@withContext writeMedia(false, options.format.mime, options.format.extension, outputName) { uri ->
            val size = resolver.openOutputStream(uri)?.use { TiledPhotoExporter(context).render(item, recipe, options, it) }
                ?: messageError(R.string.storage_unavailable)
            if (options.format == PhotoFormat.JPEG) PhotoMetadata.copy(context, item, uri, options.metadata, size.first, size.second)
        }
        val renderContext = currentCoroutineContext()
        val source = PhotoEngine.decode(context, item.uri, options.maxEdge)
        val edited = try { PhotoEngine.render(source, recipe) { renderContext.ensureActive() } } finally { source.recycle() }
        try {
            writeMedia(false, options.format.mime, options.format.extension, outputName) { uri ->
                resolver.openOutputStream(uri)?.use { stream ->
                    PhotoEngine.encode(edited, options, stream)
                } ?: messageError(R.string.storage_unavailable)
                if (options.format == PhotoFormat.JPEG) PhotoMetadata.copy(context, item, uri, options.metadata, edited.width, edited.height)
            }
        } finally { edited.recycle() }
    }

    suspend fun exportCollage(items:List<GalleryMedia>,spec:de.luma.gallery.model.CollageSpec,options:PhotoExportOptions,outputName:String?=null):Uri=withContext(Dispatchers.IO) {
        outputName?.let {exportExists(false,"$it.${options.format.extension}")?.let {uri->return@withContext uri}}
        val bitmap=CollageEngine.render(context,items,spec,options.maxEdge)
        try {writeMedia(false,options.format.mime,options.format.extension,outputName) {uri->
            resolver.openOutputStream(uri)?.use {PhotoEngine.encode(bitmap,options,it)} ?: messageError(R.string.storage_unavailable)
        }} finally {bitmap.recycle()}
    }

    suspend fun saveVideoFile(file: java.io.File, outputName: String? = null): Uri = withContext(Dispatchers.IO) {
        writeMedia(true, "video/mp4", "mp4", outputName) { uri ->
            resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: messageError(R.string.storage_unavailable)
        }
    }

    suspend fun exportExists(video: Boolean, name: String): Uri? = withContext(Dispatchers.IO) {
        val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?"
        for(path in if(video) listOf("Movies/Gimbo Galerie/","Movies/Gimbo/","Movies/Luma/") else listOf("Pictures/Gimbo Galerie/","Pictures/Gimbo/","Pictures/Luma/")) {
            resolver.query(collection,arrayOf(MediaStore.MediaColumns._ID,MediaStore.MediaColumns.IS_PENDING),selection,arrayOf(name,path,context.packageName),null)?.use {cursor->
                while(cursor.moveToNext()) {
                    val uri=ContentUris.withAppendedId(collection,cursor.getLong(0))
                    if(cursor.getInt(1)==0) return@withContext uri
                    check(resolver.delete(uri,null,null)==1)
                }
            }
        }
        null
    }

    private suspend fun writeMedia(video: Boolean, mime: String, extension: String, outputName: String? = null, write: suspend (Uri) -> Unit): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${outputName ?: "Gimbo-Galerie_$stamp"}.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/Gimbo Galerie" else "Pictures/Gimbo Galerie")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: messageError(R.string.file_create_failed)
        try {
            write(uri)
            currentCoroutineContext().ensureActive()
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
            return uri
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
    private companion object {val importsMutex=Mutex()}
}
