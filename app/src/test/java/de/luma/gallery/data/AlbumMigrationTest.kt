package de.luma.gallery.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import de.luma.gallery.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AlbumMigrationTest {
    private lateinit var context: Context
    private lateinit var name: String
    @Before fun setup() { context = RuntimeEnvironment.getApplication(); name = "migration-${UUID.randomUUID()}.db" }
    @After fun cleanup() { context.deleteDatabase(name) }


    private fun oldDatabase(): SQLiteDatabase {
        val schema = javaClass.getResourceAsStream("/de.luma.gallery.data.ProjectDatabase/1.json")!!.use {
            JSONObject(it.reader().readText()).getJSONObject("database")
        }
        val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            fun expand(sql: String) = sql.replace("\${TABLE_NAME}", entity.getString("tableName"))
            database.execSQL(expand(entity.getString("createSql")))
            val indices = entity.getJSONArray("indices")
            for (j in 0 until indices.length()) database.execSQL(expand(indices.getJSONObject(j).getString("createSql")))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) database.execSQL(setup.getString(i))
        database.version = 1
        for (video in listOf(false, true)) {
            database.insertOrThrow("editor_projects", null, ContentValues().apply {
                put("id", if (video) "old-video" else "old-photo"); put("name", if (video) "Clip" else "Foto")
                put("sourceId", "source-$video"); put("sourceUri", "content://media/external/images/media/10")
                put("sourceName", "Original"); put("sourceAlbum", "Kamera"); put("sourceTimestamp", 42L)
                put("isVideo", video); put("durationMs", if (video) 5000L else 0L); put("size", 123L); put("isDemo", false)
                put("recipeJson", if (video) RecipeJson.video(VideoRecipe(500, 3000, true))
                    else JSONObject(RecipeJson.photo(PhotoRecipe(highlights = -.3f, sharpness = .4f))).apply {
                        put("version", 2); remove("curves"); remove("texts"); remove("markups")
                    }.toString())
                put("createdAt", 100L); put("updatedAt", 200L)
            })
        }
        return database
    }

    @Test fun migrationFromCommittedSchemaPreservesPhotoVideoAndMetadataAndEnablesAlbums() = runBlocking {
        oldDatabase().close()
        val database = ProjectDatabase.open(context, name)
        try {
            val projects = ProjectRepository(database)
            val photo = projects.find("old-photo")!!; val video = projects.find("old-video")!!
            assertEquals(PhotoRecipe(highlights = -.3f, sharpness = .4f), photo.photo)
            assertEquals(VideoRecipe(500, 3000, true), video.video)
            assertEquals(100L, photo.createdAt); assertEquals(200L, photo.updatedAt)
            assertEquals("Kamera", photo.source.album); assertEquals(123L, video.source.size)
            val albums = AlbumRepository(database)
            val id = albums.create("Alt und neu", setOf(photo.source.id, video.source.id))
            assertEquals(setOf("source-false", "source-true"), albums.find(id)!!.mediaIds)
            assertEquals(2, database.openHelper.readableDatabase.version)
            assertNotNull(projects.find("old-photo")); assertNotNull(projects.find("old-video"))
        } finally { database.close() }
    }

    @Test fun failedSchemaValidationDoesNotReplaceExistingProjectsWithAnEmptyDatabase() {
        oldDatabase().use { it.execSQL("DROP INDEX index_editor_projects_updatedAt") }
        val database = ProjectDatabase.open(context, name)
        try {
            assertThrows(IllegalStateException::class.java) { runBlocking { ProjectRepository(database).find("old-photo") } }
        } finally { database.close() }
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { old ->
            assertEquals(1, old.version)
            old.rawQuery("SELECT COUNT(*) FROM editor_projects", null).use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        }
    }
}
