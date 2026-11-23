package de.luma.gallery.data

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import de.luma.gallery.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SafeReplacementTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private lateinit var provider:WritableProvider
    private lateinit var task:ExportRequest
    @Before fun setup() {
        org.robolectric.util.ReflectionHelpers.getStaticField<MutableMap<String,Any>>(androidx.core.content.FileProvider::class.java,"sCache").clear()
        provider=WritableProvider();provider.attachInfo(app,ProviderInfo().apply {authority="media";exported=true;grantUriPermissions=true})
        ShadowContentResolver.registerProviderInternal("media",provider)
        provider.file=File.createTempFile("replacement-test-",".jpg",app.cacheDir)
        val image=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)}
        provider.file.outputStream().use {image.compress(Bitmap.CompressFormat.JPEG,95,it)};image.recycle()
        task=ExportRequest(items=listOf(GalleryMedia("original",Uri.parse("content://media/external/images/media/1"),"original.jpg","Camera",1000,size=provider.file.length())),
            photo=PhotoRecipe(quarterTurns=1),overwrite=true)
    }
    @After fun cleanup() {provider.file.delete();SafeReplacement(app).cleanup(task.id)}
    @Test fun overwriteRotatesOriginalAndResumingCompletedCommitDoesNotRotateAgain()=runBlocking {
        val before=provider.file.readBytes()
        GalleryRepository(app).saveRecipe("original",task.photo!!)
        val replacement=SafeReplacement(app);replacement.replace(task)
        assertEquals(PhotoRecipe(),GalleryRepository(app).loadRecipe("original"))
        val after=provider.file.readBytes();assertFalse(before.contentEquals(after))
        val image=android.graphics.BitmapFactory.decodeFile(provider.file.path)
        assertEquals(20,image.width);assertEquals(40,image.height);image.recycle()
        val nextDraft=PhotoRecipe(saturation=.7f);GalleryRepository(app).saveRecipe("original",nextDraft)
        replacement.replace(ExportRequest.read(task.json()));assertArrayEquals(after,provider.file.readBytes())
        assertEquals(nextDraft,GalleryRepository(app).loadRecipe("original"));assertFalse(replacement.needsRecovery(task.id))
    }
    @Test fun failureAfterTruncationRestoresExactOriginalBytes()=runBlocking {
        val before=provider.file.readBytes();provider.failNextWrite=true
        assertTrue(runCatching {SafeReplacement(app).replace(task)}.isFailure)
        assertArrayEquals(before,provider.file.readBytes())
        SafeReplacement(app).replace(task)
        assertFalse(before.contentEquals(provider.file.readBytes()))
    }
    @Test fun cacheFailureBeforeCommitMarkerRestoresOriginalAndDiscardNeedsNoFileAccess()=runBlocking {
        val before=provider.file.readBytes()
        val replacement=SafeReplacement(app,clearDraft={throw java.io.IOException("Preference write failed")})
        assertTrue(runCatching {replacement.replace(task)}.isFailure)
        assertArrayEquals(before,provider.file.readBytes())
        assertFalse(replacement.hasCommitted(task.id));assertFalse(replacement.needsRecovery(task.id))
    }
    @Test fun unsupportedFormatNeverOpensOriginalForWriting()=runBlocking {
        val before=provider.file.readBytes()
        assertTrue(runCatching {SafeReplacement(app).replace(task.copy(options=PhotoExportOptions(format=PhotoFormat.PNG)))}.isFailure)
        assertArrayEquals(before,provider.file.readBytes());assertEquals(0,provider.writes)
    }
    class WritableProvider:ContentProvider() {
        lateinit var file:File;var writes=0;var failNextWrite=false
        override fun onCreate()=true
        override fun getType(uri:Uri)="image/jpeg"
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
            if(mode.contains('w')) {
                writes++
                if(failNextWrite) {failNextWrite=false;file.writeBytes(byteArrayOf(0));throw java.io.IOException("Write interrupted")}
            }
            return ParcelFileDescriptor.open(file,if(mode.contains('w')) ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE else ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,order:String?):Cursor?=null
        override fun insert(uri:Uri,values:ContentValues?):Uri?=null
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
    }
}
