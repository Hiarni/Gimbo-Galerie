package de.luma.gallery.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import de.luma.gallery.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
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
class ProjectRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: ProjectDatabase
    private lateinit var repository: ProjectRepository
    private lateinit var sourceFile: File
    private lateinit var source: GalleryMedia
    private lateinit var databaseName: String
    private var time = 1000L

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        databaseName = "projects-${UUID.randomUUID()}.db"
        database = open()
        repository = ProjectRepository(database) { ++time }
        sourceFile = File.createTempFile("project_source_", ".jpg", context.cacheDir).apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        source = GalleryMedia("source", Uri.fromFile(sourceFile), "Original.jpg", "Test", 0)
    }

    private fun open() = Room.databaseBuilder(context, ProjectDatabase::class.java, databaseName).build()
    @After fun cleanup() { database.close(); context.deleteDatabase(databaseName); sourceFile.delete() }

    @Test fun photoProjectSurvivesClosingAndReopeningDatabase() = runBlocking {
        val recipe = PhotoRecipe(.1f, 1.2f, .8f, 1, true, 1f, CropBounds(.1f, .2f, .7f, .8f), .5f, .3f, -.1f,
            highlights = -.4f, shadows = .6f, sharpness = .5f,
            curves = PhotoCurves(rgb = ToneCurve(midtones = .6f), red = ToneCurve(highlights = .8f)),
            texts = listOf(PhotoText("text-one", "Grüße", x = .2f, bold = true), PhotoText("text-two", "Sommer", y = .8f)),
            markups = MarkupKind.entries.map { PhotoMarkup(it.name, it, filled = true, width = .025f, blockSize = .1f) })
        val id = repository.save(source, "  Urlaub  ", photo = recipe)
        val original = sourceFile.readBytes()
        database.close()
        database = open()
        repository = ProjectRepository(database)
        val restored = repository.find(id)!!
        assertEquals("Urlaub", restored.name)
        assertEquals(source, restored.source)
        assertEquals(recipe, restored.photo)
        assertArrayEquals(original, sourceFile.readBytes())
    }

    @Test fun projectsUsingSameSourceKeepIndependentRecipesAndOrderByLastEdit() = runBlocking {
        val first = repository.save(source, "Warm", photo = PhotoRecipe(temperature = .5f))
        val second = repository.save(source, "Schwarzweiß", photo = PhotoRecipe(saturation = 0f))
        val created = repository.find(first)!!.createdAt
        assertEquals(listOf(second, first), repository.projects.first().map { it.id })
        repository.update(first, source, photo = PhotoRecipe(temperature = .8f, exposure = .3f))
        assertEquals(created, repository.find(first)!!.createdAt)
        assertEquals(0f, repository.find(second)!!.photo!!.saturation, 0f)
        assertEquals(.8f, repository.find(first)!!.photo!!.temperature, 0f)
        assertEquals(listOf(first, second), repository.projects.first().map { it.id })
    }

    @Test fun renameAndDeleteDoNotTouchSourceOrOtherProjects() = runBlocking {
        val original = sourceFile.readBytes()
        val first = repository.save(source, "A", photo = PhotoRecipe())
        val second = repository.save(source, "B", photo = PhotoRecipe(brightness = .2f))
        repository.rename(first, "  Neuer Name  ")
        assertEquals("Neuer Name", repository.find(first)!!.name)
        repository.delete(first)
        assertNull(repository.find(first))
        assertNotNull(repository.find(second))
        assertArrayEquals(original, sourceFile.readBytes())
    }

    @Test fun videoTrimAndMuteArePersistedAndInvalidUpdateKeepsPreviousState() = runBlocking {
        val video = source.copy(isVideo = true, durationMs = 5000, name = "Original.mp4")
        val recipe = VideoRecipe(500, 3500, true)
        val id = repository.save(video, "Clip", video = recipe)
        assertEquals(recipe, repository.find(id)!!.video)
        assertNull(repository.find(id)!!.photo)
        val failure = runCatching { repository.save(video, "Ungültig", video = VideoRecipe(0, 6000), id = id) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals("Clip", repository.find(id)!!.name)
        assertEquals(recipe, repository.find(id)!!.video)
        database.close(); database = open(); repository = ProjectRepository(database)
        assertEquals(recipe, repository.find(id)!!.video)
    }

    @Test fun failedSourceValidationKeepsProjectAndItsRecipe() = runBlocking {
        val id = repository.save(source, "Nicht verlieren", photo = PhotoRecipe(contrast = 1.3f))
        sourceFile.delete()
        assertNotNull(runCatching { GalleryRepository(context).checkSource(source) }.exceptionOrNull())
        assertNotNull(repository.find(id))
        assertEquals(1.3f, repository.find(id)!!.photo!!.contrast, 0f)
    }

    @Test fun updatingWithDifferentSourceCannotOverwriteExistingProject() = runBlocking {
        val id = repository.save(source, "Original", photo = PhotoRecipe())
        val failure = runCatching { repository.save(source.copy(id = "other"), "Falsch", photo = PhotoRecipe(exposure = 1f), id = id) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals("Original", repository.find(id)!!.name)
        assertEquals(PhotoRecipe(), repository.find(id)!!.photo)
    }

    @Test fun unsupportedRecipeVersionIsReportedWithoutDeletingStoredProject() = runBlocking {
        val id = repository.save(source, "Zukünftig", photo = PhotoRecipe())
        database.projects().updateRecipe(id, """{"version":99}""", ++time)
        assertTrue(runCatching { repository.find(id) }.exceptionOrNull() is IllegalArgumentException)
        assertNotNull(database.projects().find(id))
    }

    @Test fun reviewedReplacementPreservesRecipeAndOtherVariants() = runBlocking {
        val recipe = PhotoRecipe(exposure = .7f, texts = listOf(PhotoText("caption", "Hallo")))
        val first = repository.save(source, "A", photo = recipe)
        val second = repository.save(source, "B", photo = PhotoRecipe())
        val reviewed = repository.find(first)!!
        val replacement = source.copy(id = "new", uri = Uri.parse("content://test/new"), name = "New.png")
        repository.replaceSource(reviewed, replacement)
        assertEquals(replacement, repository.find(first)!!.source)
        assertEquals(recipe, repository.find(first)!!.photo)
        assertEquals(reviewed.createdAt, repository.find(first)!!.createdAt)
        assertEquals(source, repository.find(second)!!.source)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), sourceFile.readBytes())
    }

    @Test fun staleReviewAndTooShortVideoCannotReplaceSource() = runBlocking {
        val video = source.copy(isVideo = true, durationMs = 5000)
        val id = repository.save(video, "Clip", video = VideoRecipe(1000, 4000))
        val reviewed = repository.find(id)!!
        assertTrue(runCatching { repository.replaceSource(reviewed, video.copy(durationMs = 2000)) }.isFailure)
        assertEquals(reviewed, repository.find(id))
        repository.rename(id, "New name")
        assertTrue(runCatching { repository.replaceSource(reviewed, video.copy(id = "other")) }.isFailure)
        assertEquals(video, repository.find(id)!!.source)
        assertEquals("New name", repository.find(id)!!.name)
    }
}
