package de.luma.gallery.data

import de.luma.gallery.localization.*
import de.luma.gallery.R
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import de.luma.gallery.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class TrashRepository(private val context: Context) {
    private val strings get() = de.luma.gallery.localization.localizedResources(context)
    private val resolver = context.contentResolver

    suspend fun load(): TrashSnapshot = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) throw MessageStateException(R.string.trash_platform_unavailable)
        val items = mutableListOf<TrashMedia>()
        var limited = false
        for (video in listOf(false, true)) {
            val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = listOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED,
                if (video) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN,
                if (video) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_EXPIRES) + if (video) listOf(MediaStore.Video.Media.DURATION) else emptyList()
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
            }
            try {
                val cursor = resolver.query(collection, projection.toTypedArray(), args, null)
                    ?: messageError(R.string.trash_read_failed)
                cursor.use {
                    while (it.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val uri = ContentUris.withAppendedId(collection, it.getLong(0))
                        val taken = it.getLong(4).takeIf { time -> time > 0 } ?: it.getLong(3) * 1000
                        val media = GalleryMedia(uri.toString(), uri, it.getString(1) ?: strings.getString(R.string.unnamed),
                            it.getString(5) ?: strings.getString(R.string.media), taken, video, if (video) it.getLong(7) else 0, it.getLong(2))
                        val expires = if (it.isNull(6)) null else it.getLong(6).takeIf { time -> time > 0 }?.let { time -> time * 1000 }
                        items += TrashMedia(media, expires)
                    }
                }
            } catch (_: SecurityException) { limited = true }
        }
        TrashSnapshot(items.sortedWith(compareBy<TrashMedia> { it.expiresAt ?: Long.MAX_VALUE }.thenBy { it.media.name }), limited)
    }


    fun request(action: MediaAction, items: List<GalleryMedia>): IntentSender {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) throw MessageStateException(R.string.trash_platform_unavailable)
        val checked = checkedMediaAction(action, items)
        val uris = checked.items.map { it.uri }
        return when (action) {
            MediaAction.TRASH -> MediaStore.createTrashRequest(resolver, uris, true)
            MediaAction.RESTORE -> MediaStore.createTrashRequest(resolver, uris, false)
            MediaAction.DELETE -> MediaStore.createDeleteRequest(resolver, uris)
        }.intentSender
    }
}
