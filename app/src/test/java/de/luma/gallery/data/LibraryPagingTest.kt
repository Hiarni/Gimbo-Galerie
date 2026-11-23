package de.luma.gallery.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LibraryPagingTest {
    @Test fun pagedEnumerationKeepsDateTiesDistinctAndClosesEachCursor() = runBlocking {
        val provider = MediaProvider()
        ShadowContentResolver.registerProviderInternal("media", provider)
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("luma", 0).edit().remove("imports").commit()
        val items = GalleryRepository(context).loadLibrary()
        assertEquals(1003, items.size)
        assertEquals(items.size, items.map { it.id }.distinct().size)
        assertEquals(4, provider.calls)
        assertTrue(provider.maxRows <= 500)
        assertTrue(provider.cursors.all { it.isClosed })
    }
    @Test fun providerIgnoringPageLimitDoesNotRepeatAnUnboundedQuery() = runBlocking {
        val provider = MediaProvider().apply { ignoreLimit = true }
        ShadowContentResolver.registerProviderInternal("media", provider)
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("luma", 0).edit().remove("imports").commit()
        assertEquals(1003, GalleryRepository(context).loadLibrary().size)
        assertEquals(2, provider.calls)
    }
    class MediaProvider : ContentProvider() {
        var ignoreLimit = false
        var calls = 0
        var maxRows = 0
        val cursors = mutableListOf<MatrixCursor>()
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, args: Bundle?, signal: CancellationSignal?): Cursor {
            calls++
            val video = uri.path.orEmpty().contains("video")
            val before = args?.getStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)
            val count = if (video) 3 else 1000
            val ids = (count downTo 1).filter { id -> before == null || id / 5L < before[0].toLong() || id / 5L == before[0].toLong() && id < before[2].toLong() }
                .let { if (ignoreLimit) it else it.take(requireNotNull(args).getInt(android.content.ContentResolver.QUERY_ARG_LIMIT)) }
            maxRows = maxOf(maxRows, ids.size)
            return MatrixCursor(requireNotNull(projection)).apply {
                ids.forEach { id -> addRow(projection.map<String, Any?> { column -> when (column) {
                    "_id" -> id; "_display_name" -> "$id.${if (video) "mp4" else "jpg"}"; "date_added" -> id / 5L
                    "_size" -> 42; "relative_path" -> "DCIM/"; "datetaken" -> 0; "bucket_display_name" -> "Camera"; "duration" -> 1000
                    else -> 0
                } }.toTypedArray()) }
                cursors += this
            }
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException()
        override fun getType(uri: Uri) = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
