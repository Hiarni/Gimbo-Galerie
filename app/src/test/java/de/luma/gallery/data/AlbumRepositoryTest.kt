package de.luma.gallery.data

import android.content.Context
import android.net.Uri
import de.luma.gallery.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AlbumRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: ProjectDatabase
    private lateinit var repository: AlbumRepository
    private lateinit var name: String
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        name = "albums-${UUID.randomUUID()}.db"
        database = ProjectDatabase.open(context, name)
        repository = AlbumRepository(database)
    }
    @After fun cleanup() { database.close(); context.deleteDatabase(name) }

    @Test fun namesMembershipsAndEmptyAlbumsSurviveDatabaseReopening() = runBlocking {
        val id = repository.create("  Urlaub  ", setOf("photo", "video", "demo:0"))
        val empty = repository.create("Leer")
        val initial = repository.find(id)!!
        database.close(); database = ProjectDatabase.open(context, name); repository = AlbumRepository(database)
        assertEquals(initial, repository.find(id)); assertEquals("Urlaub", initial.name)
        assertTrue(repository.find(empty)!!.mediaIds.isEmpty())
        assertEquals(listOf("Leer", "Urlaub"), repository.albums.first().map { it.name })
    }

    @Test fun addingIsIdempotentAndRemovingFromOneAlbumKeepsOtherAlbumsProjectsAndFiles() = runBlocking {
        val file = File.createTempFile("album-original-", ".jpg", context.cacheDir).apply { writeBytes(byteArrayOf(1, 3, 5, 7)) }
        try {
            val source = GalleryMedia(file.toURI().toString(), Uri.fromFile(file), "Original.jpg", "Test", 0)
            val projects = ProjectRepository(database)
            val project = projects.save(source, "Bearbeitung", photo = PhotoRecipe(shadows = .3f))
            val first = repository.create("A", setOf(source.id))
            val second = repository.create("B", setOf(source.id))
            assertEquals(0, repository.add(first, setOf(source.id)))
            assertEquals(1, repository.add(first, setOf(source.id, "video")))
            assertEquals(1, repository.remove(first, setOf(source.id)))
            assertEquals(setOf("video"), repository.find(first)!!.mediaIds)
            assertEquals(setOf(source.id), repository.find(second)!!.mediaIds)
            repository.delete(first)
            assertNotNull(projects.find(project)); assertNotNull(repository.find(second))
            assertArrayEquals(byteArrayOf(1, 3, 5, 7), file.readBytes())
        } finally { file.delete() }
    }

    @Test fun normalizedDuplicateNamesCannotCreateOrOverwriteAlbums() = runBlocking {
        val first = repository.create("Fächer", setOf("one"))
        val second = repository.create("Andere", setOf("two"))
        assertTrue(runCatching { repository.create("  FA\u0308CHER ") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.rename(second, "FÄCHER") }.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Andere", repository.find(second)!!.name)
        assertEquals(setOf("two"), repository.find(second)!!.mediaIds)
        repository.rename(first, "fächer")
        assertEquals("fächer", repository.find(first)!!.name)
        assertEquals(setOf("one"), repository.find(first)!!.mediaIds)
    }

    @Test fun invalidBatchesAndStaleAlbumIdsDoNotPartiallyWrite() = runBlocking {
        assertTrue(runCatching { repository.create("Ungültig", setOf("photo", "")) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(repository.albums.first().isEmpty())
        val id = repository.create("A", setOf("existing"))
        assertTrue(runCatching { repository.add(id, setOf("new", " ")) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(setOf("existing"), repository.find(id)!!.mediaIds)
        repository.delete(id)
        assertTrue(runCatching { repository.add(id, setOf("new")) }.exceptionOrNull() is IllegalStateException)
        assertTrue(repository.albums.first().isEmpty())
    }

    @Test fun missingMediaAndTemporaryAccessChangesKeepStoredMemberships() = runBlocking {
        val photo = GalleryMedia("photo", Uri.EMPTY, "Photo.jpg", "Ordner", 0)
        val video = GalleryMedia("video", Uri.EMPTY, "Video.mp4", "Ordner", 1, true)
        val id = repository.create("Sammlung", setOf(photo.id, video.id))
        val album = repository.find(id)!!
        assertEquals(listOf(photo), albumMedia(album, listOf(photo)))
        assertTrue(albumMedia(album, emptyList()).isEmpty())
        assertEquals(setOf("photo", "video"), repository.find(id)!!.mediaIds)
        assertEquals(listOf(video, photo), albumMedia(album, listOf(video, photo)))
    }

    @Test fun deletingAlbumCascadesOnlyItsOwnMembershipsAndRejectsOrphans() = runBlocking {
        val first = repository.create("A", setOf("one", "two"))
        val second = repository.create("B", setOf("two"))
        repository.delete(first)
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM album_members").use {
            assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
        }
        assertEquals(setOf("two"), repository.find(second)!!.mediaIds)
        assertNotNull(runCatching { database.albums().add(listOf(AlbumMemberEntity(first, "orphan", 0))) }.exceptionOrNull())
        assertNull(repository.find(first))
    }
}
