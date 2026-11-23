package de.luma.gallery.data

import android.net.Uri
import androidx.room.Room
import de.luma.gallery.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class ProjectSourceProtectionTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private lateinit var database:ProjectDatabase
    private lateinit var original:File
    private lateinit var repository:ProjectRepository
    @Before fun setup() {
        org.robolectric.util.ReflectionHelpers.getStaticField<MutableMap<String,Any>>(androidx.core.content.FileProvider::class.java,"sCache").clear()
        database=Room.inMemoryDatabaseBuilder(app,ProjectDatabase::class.java).build();repository=ProjectRepository(database)
        original=File.createTempFile("protected-source-test-",".jpg",app.cacheDir).apply {writeBytes(byteArrayOf(1,2,3,4))}
        app.getSharedPreferences("luma",0).edit().clear().commit()
    }
    @After fun cleanup() {database.close();original.delete();File(app.filesDir,"luma-project-sources").deleteRecursively()}
    private fun photo()=GalleryMedia("original",Uri.parse("content://media/external/images/media/1"),"Photo.jpg","Camera",0,size=4)
    @Test fun projectsKeepRecipesAndShareOneVerifiedOriginalCopyUntilNoLongerReferenced()=runBlocking {
        val source=photo();val recipe=PhotoRecipe(quarterTurns=1)
        val first=repository.save(source,"One",photo=recipe);val second=repository.save(source,"Two",photo=PhotoRecipe(saturation=0f))
        val protection=ProjectSourceProtection(app,database)
        protection.protect(source,original,UUID.randomUUID().toString())
        val a=repository.find(first)!!;val b=repository.find(second)!!
        assertEquals("content",a.source.uri.scheme);assertEquals(a.source.uri,b.source.uri);assertEquals(recipe,a.photo)
        assertArrayEquals(original.readBytes(),File(app.filesDir,"luma-project-sources/${a.source.uri.lastPathSegment}").readBytes())
        protection.cleanupUnused();assertTrue(File(app.filesDir,"luma-project-sources/${a.source.uri.lastPathSegment}").exists())
        repository.delete(first);protection.cleanupUnused();assertTrue(File(app.filesDir,"luma-project-sources/${a.source.uri.lastPathSegment}").exists())
        repository.delete(second);protection.cleanupUnused();assertFalse(File(app.filesDir,"luma-project-sources/${a.source.uri.lastPathSegment}").exists())
    }
    @Test fun embeddedOverlaySourcesAndCachedVideoDraftsAreAlsoProtected()=runBlocking {
        val source=photo();val video=GalleryMedia("video",Uri.parse("content://media/external/video/media/2"),"Video.mp4","Camera",0,true,5000)
        val recipe=VideoRecipe(0,5000,overlays=listOf(VideoOverlay("overlay",imageUri=source.uri.toString(),startMs=0,endMs=1000)))
        val id=repository.save(video,"Composition",video=recipe)
        app.getSharedPreferences("luma",0).edit().putString("videoRecipe:other",RecipeJson.video(recipe)).commit()
        ProjectSourceProtection(app,database).protect(source,original,UUID.randomUUID().toString())
        val saved=repository.find(id)!!;assertEquals(video,saved.source)
        val uri=saved.video!!.overlays.single().imageUri!!
        assertEquals("${app.packageName}.project-sources",Uri.parse(uri).authority);assertArrayEquals(original.readBytes(),app.contentResolver.openInputStream(Uri.parse(uri))!!.use {it.readBytes()})
        val draft=RecipeJson.readVideo(app.getSharedPreferences("luma",0).getString("videoRecipe:other",null)!!)
        assertEquals(uri,draft.overlays.single().imageUri)
    }
}
