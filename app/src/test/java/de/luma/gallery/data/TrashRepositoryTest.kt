package de.luma.gallery.data

import android.app.Application
import android.app.PendingIntent
import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.core.os.BundleCompat
import de.luma.gallery.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de")
class TrashRepositoryTest {
    private lateinit var app: Application
    private lateinit var provider: TestMediaProvider
    private lateinit var repository: TrashRepository
    private fun media(id: Long, video: Boolean = false) = GalleryMedia("$id:$video",
        Uri.parse("content://media/external/${if (video) "video" else "images"}/media/$id"), "$id.jpg", "Test", 0, video)

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("luma", 0).edit().clear().commit()
        provider = TestMediaProvider()
        provider.attachInfo(app, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        repository = TrashRepository(app)
    }

    @Test fun queryIncludesOnlyTrashedPublishedPhotosAndVideosAndKeepsExpiryAndMetadata() = runBlocking {
        provider.rows += listOf(Row(1, expires = 200), Row(2, video = true, expires = 100),
            Row(3, trashed = false), Row(4, pending = true), Row(5, expires = null))
        val snapshot = repository.load()
        assertFalse(snapshot.accessLimited)
        assertEquals(listOf(2L, 1L, 5L), snapshot.items.map { ContentUris.parseId(it.media.uri) })
        assertEquals(100_000L, snapshot.items[0].expiresAt)
        assertEquals(4_000L, snapshot.items[0].media.durationMs)
        assertEquals(10_000L, snapshot.items[0].media.timestamp)
        assertEquals("Urlaub", snapshot.items[0].media.album)
        assertNull(snapshot.items.last().expiresAt)
        assertEquals(2, provider.args.size)
        provider.args.forEach {
            assertEquals(MediaStore.MATCH_ONLY, it.getInt(MediaStore.QUERY_ARG_MATCH_TRASHED))
            assertEquals(MediaStore.MATCH_EXCLUDE, it.getInt(MediaStore.QUERY_ARG_MATCH_PENDING))
        }
        assertTrue(provider.cursors.all { it.isClosed })
    }

    @Test fun deniedCollectionDoesNotHideAuthorizedVideos() = runBlocking {
        provider.denyImages = true
        provider.rows += Row(2, video = true)
        val snapshot = repository.load()
        assertTrue(snapshot.accessLimited)
        assertEquals(listOf(2L), snapshot.items.map { ContentUris.parseId(it.media.uri) })
    }

    @Test fun deniedPhotosAndVideosProduceLimitedAccessRatherThanClaimingEmptyTrash() = runBlocking {
        provider.denyImages = true; provider.denyVideos = true
        val snapshot = repository.load()
        assertTrue(snapshot.items.isEmpty()); assertTrue(snapshot.accessLimited)
    }

    @Test fun providerFailureIsReportedRatherThanReturningAnEmptySuccessfulResult() {
        provider.failQueries = true
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.load() } }
        provider.failQueries = false; provider.nullCursor = true
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.load() } }
    }

    @Test fun systemRequestsHaveCorrectUrisAndModesAndNeverDirectlyDeleteOrUpdate() {
        val files = listOf(media(1), media(2, true))
        for (action in MediaAction.entries) repository.request(action, files)
        assertEquals(listOf("create_trash_request", "create_trash_request", "create_delete_request"), provider.requests.map { it.first })
        provider.requests.forEach { (_, bundle) ->
            val clip = BundleCompat.getParcelable(bundle, "clip_data", ClipData::class.java)!!
            assertEquals(files.map { it.uri }, (0 until clip.itemCount).map { clip.getItemAt(it).uri })
        }
        assertEquals(1, BundleCompat.getParcelable(provider.requests[0].second, "content_values", ContentValues::class.java)!!.getAsInteger("is_trashed"))
        assertEquals(0, BundleCompat.getParcelable(provider.requests[1].second, "content_values", ContentValues::class.java)!!.getAsInteger("is_trashed"))
        assertNull(BundleCompat.getParcelable(provider.requests[2].second, "content_values", ContentValues::class.java))
        assertEquals(0, provider.directMutations)
    }

    @Test fun invalidOrDuplicateMediaAndOversizedRequestsNeverReachTheProvider() {
        val source = media(1)
        val invalid = listOf("file:///tmp/image.jpg", "content://cloud/document/1", "content://media/external/images/media",
            "content://media/external/images/media/0", "content://media/external/file/1",
            "content://media/internal/images/media/1", "content://media/external/images/media/1?x=1")
        for (uri in invalid) assertThrows(IllegalArgumentException::class.java) {
            repository.request(MediaAction.DELETE, listOf(source.copy(uri = Uri.parse(uri))))
        }
        for (items in listOf(emptyList(), listOf(source, source), (1..201).map { media(it.toLong()) },
            listOf(source.copy(isDemo = true)), listOf(source.copy(isVideo = true)))) {
            assertThrows(IllegalArgumentException::class.java) { repository.request(MediaAction.TRASH, items) }
        }
        assertTrue(provider.requests.isEmpty()); assertEquals(0, provider.directMutations)
    }

    @Test fun projectSourceInTrashCannotBeOpened() {
        provider.rows += Row(1)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { GalleryRepository(app).checkSource(media(1)) } }
        assertEquals(MediaStore.MATCH_INCLUDE, provider.args.single().getInt(MediaStore.QUERY_ARG_MATCH_TRASHED))
        assertEquals(0, provider.directMutations)
    }

    @Test fun retainedMediaStoreImportStaysHiddenInTrashAndReturnsAfterRestore() = runBlocking {
        val row = Row(1); provider.rows += row
        app.getSharedPreferences("luma", 0).edit().putStringSet("imports", setOf(media(1).uri.toString())).commit()
        val gallery = GalleryRepository(app)
        assertTrue(gallery.loadLibrary().isEmpty())
        row.trashed = false
        assertEquals(listOf(media(1).uri), gallery.loadLibrary().map { it.uri })
        assertTrue(app.getSharedPreferences("luma", 0).getStringSet("imports", emptySet())!!.isNotEmpty())
    }

    @Test @Config(sdk = [29], qualifiers = "de") fun android10DoesNotQueryOrExecuteSystemTrashActions() {
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.load() } }
        assertThrows(IllegalStateException::class.java) { repository.request(MediaAction.DELETE, listOf(media(1))) }
        assertTrue(provider.requests.isEmpty()); assertTrue(provider.args.isEmpty()); assertEquals(0, provider.directMutations)
    }

    data class Row(val id: Long, val video: Boolean = false, var trashed: Boolean = true,
        val pending: Boolean = false, val expires: Long? = 200)

    class TestMediaProvider : ContentProvider() {
        val rows = mutableListOf<Row>()
        val args = mutableListOf<Bundle>()
        val cursors = mutableListOf<MatrixCursor>()
        val requests = mutableListOf<Pair<String, Bundle>>()
        var denyImages = false; var denyVideos = false; var failQueries = false; var nullCursor = false
        var directMutations = 0
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/jpeg"
        override fun query(uri: Uri, projection: Array<out String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?): Cursor? {
            args += Bundle(queryArgs ?: Bundle())
            return result(uri, projection!!, queryArgs?.getInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_EXCLUDE) ?: MediaStore.MATCH_EXCLUDE)
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
            result(uri, projection!!, if (selection != null) MediaStore.MATCH_EXCLUDE else MediaStore.MATCH_INCLUDE)
        private fun result(uri: Uri, projection: Array<out String>, mode: Int): Cursor? {
            val video = uri.pathSegments.contains("video")
            if ((video && denyVideos) || (!video && denyImages)) throw SecurityException("Denied")
            if (failQueries) error("Provider unavailable")
            if (nullCursor) return null
            val id = uri.lastPathSegment?.toLongOrNull()
            return MatrixCursor(projection).also { cursor ->
                cursors += cursor
                rows.filter { it.video == video && (id == null || it.id == id) && !it.pending &&
                    (mode == MediaStore.MATCH_INCLUDE || it.trashed == (mode == MediaStore.MATCH_ONLY)) }.forEach { row ->
                    cursor.addRow(projection.map<String, Any?> { column -> when (column) {
                        "_id" -> row.id; "_display_name" -> "${row.id}.jpg"; "_size" -> 20L
                        "date_added" -> 10L; "datetaken" -> 0L; "bucket_display_name" -> "Urlaub"
                        "date_expires" -> row.expires; "duration" -> 4_000L; "is_trashed" -> if (row.trashed) 1 else 0
                        else -> null
                    } }.toTypedArray())
                }
            }
        }
        override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
            requests += method to Bundle(extras!!)
            return Bundle().apply { putParcelable("result", PendingIntent.getActivity(context, 0,
                Intent("de.luma.TEST_CONFIRMATION"), PendingIntent.FLAG_IMMUTABLE)) }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? { directMutations++; error("Unexpected mutation") }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int { directMutations++; error("Unexpected mutation") }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int { directMutations++; error("Unexpected mutation") }
    }
}
