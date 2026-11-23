package de.luma.gallery.data

import android.net.Uri
import androidx.room.Room
import de.luma.gallery.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ProjectBackupTest {
    @Test fun referenceBackupRestoresIndependentProjectsAndResolvesAlbumNames() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ProjectDatabase::class.java).build()
        try {
            val projects = ProjectRepository(database); val albums = AlbumRepository(database)
            val source = GalleryMedia("ref", Uri.parse("content://gone/image/1"), "A.png", "Device", 0)
            val recipe = PhotoRecipe(exposure = .3f, texts = listOf(PhotoText("t", "Two\nlines", opacity = .5f)))
            val id = projects.save(source, "Retained", photo = recipe)
            albums.create("Trip", setOf(source.id))
            val backup = ProjectBackup(database)
            val json = backup.snapshot()
            val checked = ProjectBackup.decode(json)
            backup.restore(checked)
            assertEquals(2, projects.projects.first().size)
            assertEquals(recipe, projects.find(id)!!.photo)
            assertTrue(projects.projects.first().all { it.source == source && it.photo == recipe })
            assertEquals(setOf("Trip", "Trip (2)"), albums.albums.first().map { it.name }.toSet())
        } finally { database.close() }
    }
    @Test fun invalidBackupCannotPartiallyImportProjects() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ProjectDatabase::class.java).build()
        try {
            val valid = EditorProject("id", "Good", GalleryMedia("ref", Uri.parse("content://gone/1"), "A", "", 0), PhotoRecipe(), null, 0, 0)
            val review = BackupReview(listOf(valid, valid.copy(name = "\n")), emptyList())
            assertTrue(runCatching { ProjectBackup(database).restore(review) }.isFailure)
            assertTrue(ProjectRepository(database).projects.first().isEmpty())
            assertTrue(runCatching { ProjectBackup.decode("""{"format":"luma-projects","version":2,"projects":[],"albums":[]}""") }.isFailure)
        } finally { database.close() }
    }
    @Test fun retryAfterCommittedRestoreDoesNotDuplicateProjectsOrAlbums() = runBlocking {
        val database=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),ProjectDatabase::class.java).build()
        try {
            val source=GalleryMedia("ref",Uri.parse("content://gone/image/1"),"Photo.png","Camera",0)
            val review=BackupReview(listOf(EditorProject("old","Project",source,PhotoRecipe(),null,0,0)),listOf(VirtualAlbum("old-album","Trip",setOf(source.id),0,0)))
            val backup=ProjectBackup(database)
            backup.restore(review,"restore-job");backup.restore(review,"restore-job")
            assertEquals(1,ProjectRepository(database).projects.first().size)
            assertEquals(1,AlbumRepository(database).albums.first().size)
            assertEquals(setOf(source.id),AlbumRepository(database).albums.first().single().mediaIds)
        } finally {database.close()}
    }

}
