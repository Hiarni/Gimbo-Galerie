package de.luma.gallery.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class ImportReferencesTest {
    @Test fun importedReferencesHaveExplicitOriginAndRemovalPreservesFilesDraftsAndCloudCopies():Unit=runBlocking {
        val app=RuntimeEnvironment.getApplication()
        val file=File(app.cacheDir,"import-reference.png").apply {writeBytes(byteArrayOf(1,2,3,4))}
        val provider=SourceProvider(file)
        ShadowContentResolver.registerProviderInternal("imports.test",provider)
        val uri=Uri.parse("content://imports.test/source")
        val prefs=app.getSharedPreferences("luma",0)
        prefs.edit().clear().putString("recipe:$uri","saved-draft").commit()
        val repository=GalleryRepository(app)
        repository.registerGranted(listOf(uri))
        val imported=repository.loadLibrary().single {it.uri==uri}
        assertTrue(imported.isImported)
        assertEquals(imported,RecipeJson.readMedia(RecipeJson.media(imported)))
        val legacy=RecipeJson.media(imported).apply {remove("imported")}
        assertFalse(RecipeJson.readMedia(legacy).isImported)
        val ledger="""[{"id":"copy","source":"$uri","target":"content://cloud/copy","name":"Original.png","hash":"hash","size":4}]"""
        val cloudPrefs=app.getSharedPreferences("luma-cloud",0)
        cloudPrefs.edit().clear().putBoolean("mirror",true).putString("ledger",ledger).commit()
        repository.forgetReferences(setOf(uri.toString(),"unknown"))
        assertTrue(repository.importedReferences().isEmpty())
        assertFalse(repository.loadLibrary().any {it.uri==uri})
        assertArrayEquals(byteArrayOf(1,2,3,4),file.readBytes())
        assertEquals(0,provider.deletes)
        assertEquals("saved-draft",prefs.getString("recipe:$uri",null))
        assertEquals(ledger,cloudPrefs.getString("ledger",null))
        assertTrue(CloudBackup(app).deletionReview().isEmpty())
        repository.registerGranted(listOf(uri))
        assertEquals(setOf(uri.toString()),repository.importedReferences())
        file.delete()
    }

    private class SourceProvider(private val file:File):ContentProvider() {
        var deletes=0
        override fun onCreate()=true
        override fun getType(uri:Uri)="image/png"
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor =
            MatrixCursor(arrayOf("_display_name","_size")).apply {addRow(arrayOf<Any>("Original.png",file.length()))}
        override fun openFile(uri:Uri,mode:String)=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
        override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int {deletes++;return 1}
        override fun insert(uri:Uri,values:ContentValues?):Uri?=null
        override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
    }
}
