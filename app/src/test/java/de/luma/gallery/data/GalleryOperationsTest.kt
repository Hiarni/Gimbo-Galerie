package de.luma.gallery.data

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
open class GalleryOperationsTest {
    protected val app get()=RuntimeEnvironment.getApplication()
    private lateinit var docs:DocumentToolsTest.TestProvider
    protected lateinit var originals:OriginalProvider
    private val tree get()=DocumentsContract.buildTreeDocumentUri("luma.test.docs","target")
    @Before fun setup() {
        app.getSharedPreferences("luma",0).edit().clear().commit();app.getSharedPreferences("luma-cloud",0).edit().clear().commit()
        val application=app
        docs=DocumentToolsTest.TestProvider();docs.attachInfo(application,ProviderInfo().apply {authority="luma.test.docs";exported=true;grantUriPermissions=true;readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;writePermission=android.Manifest.permission.MANAGE_DOCUMENTS})
        ShadowContentResolver.registerProviderInternal("luma.test.docs",docs)
        originals=OriginalProvider();originals.attachInfo(application,ProviderInfo().apply {authority="media";exported=true;grantUriPermissions=true})
        ShadowContentResolver.registerProviderInternal("media",originals)
        originals.add(1,byteArrayOf(1,2,3));originals.add(2,byteArrayOf(4,5,6))
        SelectionJournal(app).delete()
    }
    @After fun cleanup() {docs.folder.deleteRecursively();originals.files.values.forEach {it.delete()};SelectionJournal(app).delete()}
    private fun media(id:Long)=GalleryMedia(OriginalProvider.uri(id).toString(),OriginalProvider.uri(id),"Photo-$id.jpg","Camera",1000,size=3)
    private fun task(move:Boolean=false)=SelectionTask(tree.toString(),move,listOf(SelectionCopy(media(1)),SelectionCopy(media(2))))
    protected fun cloud():CloudBackup {
        app.getSharedPreferences("luma-cloud",0).edit().putString("tree",tree.toString()).putBoolean("mirror",true).commit()
        return CloudBackup(app)
    }
    @Test fun copyingSelectionKeepsOriginalsAndResumingDoesNotDuplicateCompletedFiles()=runBlocking {
        val initial=task();SelectionJournal(app).write(initial)
        try {SelectionTransfer(app).run(initial) {if(it.completed==1) throw CancellationException()};fail()} catch(_:CancellationException) { }
        val partial=SelectionJournal(app).read()!!;assertNotNull(partial.copies[0].target);assertNull(partial.copies[1].target)
        SelectionTransfer(app).run(partial) {}
        val children=DocumentTools(app).children(DocumentTools(app).root(tree));assertEquals(2,children.size)
        assertEquals(setOf("Photo-1.jpg","Photo-2.jpg"),children.map {it.name}.toSet())
        assertArrayEquals(byteArrayOf(1,2,3),originals.files.getValue(1).readBytes());assertNull(SelectionJournal(app).read())
    }
    @Test fun moveWaitsForSystemDeletionAndRechecksChangedOriginalBeforeRequest()=runBlocking {
        val initial=task(true);SelectionJournal(app).write(initial)
        assertEquals(2,SelectionTransfer(app).run(initial) {}.size)
        assertTrue(SelectionJournal(app).read()!!.waiting);assertEquals(2,originals.files.size)
        assertEquals(2,SelectionTransfer(app).verifyMove().size)
        originals.files.getValue(1).writeBytes(byteArrayOf(9,9,9))
        assertTrue(runCatching {SelectionTransfer(app).verifyMove()}.isFailure)
        assertEquals(2,originals.files.size)
    }
    @Test fun sourceChangesAndDestinationCorruptionStopResumptionWithoutDeletion()=runBlocking {
        val initial=task(true);SelectionJournal(app).write(initial);SelectionTransfer(app).run(initial) {}
        val copy=SelectionJournal(app).read()!!.copies.first()
        docs.records.getValue(DocumentsContract.getDocumentId(Uri.parse(copy.target))).file!!.writeBytes(byteArrayOf(8))
        assertTrue(runCatching {SelectionTransfer(app).verifyMove()}.isFailure);assertEquals(2,originals.files.size)
    }
    @Test fun cloudSkipsUnchangedFilesAndKeepsChangedVersionsUntilDeletionReview()=runBlocking {
        val cloud=cloud();cloud.synchronize();assertEquals(2,cloud.records().size)
        cloud.synchronize();assertEquals(2,cloud.records().size)
        originals.files.getValue(1).writeBytes(byteArrayOf(7,8,9));cloud.synchronize()
        assertEquals(3,cloud.records().size);assertEquals(1,cloud.deletionReview().size)
        cloud.deleteConfirmed(cloud.deletionReview().map {it.first}.toSet())
        assertEquals(2,cloud.records().size);assertEquals(2,originals.files.size)
    }
    @Test fun cloudDeletionNeedsFullPermissionsAndNeverDeletesUntrackedFiles()=runBlocking {
        val cloud=cloud();cloud.synchronize()
        val root=Uri.parse(app.getSharedPreferences("luma-cloud",0).getString("root",null))
        docs.add("foreign",DocumentsContract.getDocumentId(root),"Personal.jpg",byteArrayOf(42))
        originals.files.remove(1)?.delete()
        shadowOf(app).denyPermissions(*(if(android.os.Build.VERSION.SDK_INT>=33) arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES,android.Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)))
        assertTrue(cloud.deletionReview().isEmpty())
        shadowOf(app).grantPermissions(*(if(android.os.Build.VERSION.SDK_INT>=33) arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES,android.Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)))
        val review=cloud.deletionReview();assertEquals(1,review.size)
        cloud.deleteConfirmed(review.map {it.first}.toSet())
        assertTrue(docs.records.containsKey("foreign"));assertEquals(1,cloud.records().size)
    }
    @Test fun externallyChangedOrRenamedCloudCopiesAreProtected()=runBlocking {
        val cloud=cloud();cloud.synchronize();originals.files.getValue(1).writeBytes(byteArrayOf(8,8,8));cloud.synchronize()
        val old=cloud.records().first {it.superseded}
        val record=docs.records.getValue(DocumentsContract.getDocumentId(Uri.parse(old.target)))
        record.name="Kept by user.jpg"
        assertTrue(runCatching {cloud.deleteConfirmed(setOf(old.id))}.isFailure)
        record.name=old.cloudName;record.file!!.writeBytes(byteArrayOf(44))
        assertTrue(runCatching {cloud.deleteConfirmed(setOf(old.id))}.isFailure)
        assertTrue(cloud.records().any {it.id==old.id})
    }
    @Test fun retentionOnlyAppliesToRecordedMediaAndCanBeCancelled() {
        val policy=TrashRetention(app);val a=media(1);val b=media(2)
        policy.record(listOf(a),2,1000)
        assertTrue(policy.due(listOf(TrashMedia(a),TrashMedia(b)),1000+86_400_000).isEmpty())
        assertEquals(listOf(a),policy.due(listOf(TrashMedia(a),TrashMedia(b)),1000+2*86_400_000))
        policy.forget(listOf(a));assertTrue(policy.due(listOf(TrashMedia(a)),Long.MAX_VALUE-1).isEmpty())
    }
    class OriginalProvider:ContentProvider() {
        val files=linkedMapOf<Long,File>()
        fun add(id:Long,bytes:ByteArray) {files[id]=File.createTempFile("gallery-source-", ".jpg",RuntimeEnvironment.getApplication().cacheDir).apply {writeBytes(bytes)}}
        override fun onCreate()=true
        override fun getType(uri:Uri)="image/jpeg"
        override fun openFile(uri:Uri,mode:String)=ParcelFileDescriptor.open(files.getValue(uri.lastPathSegment!!.toLong()),ParcelFileDescriptor.MODE_READ_ONLY)
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,order:String?):Cursor {
            val columns=projection?:arrayOf("_id")
            val cursor=MatrixCursor(columns)
            if(uri.pathSegments.contains("video")) return cursor
            val id=uri.lastPathSegment?.toLongOrNull()
            files.filterKeys {id==null || it==id}.forEach { (key,file)->cursor.addRow(columns.map {column->when(column) {
                "_id"->key;"_display_name"->"Photo-$key.jpg";"_size"->file.length();"date_added"->1000L;"datetaken"->1000000L;
                "relative_path"->"Pictures/Camera/";"bucket_display_name"->"Camera";else->0L
            }}) }
            return cursor
        }
        override fun insert(uri:Uri,values:ContentValues?):Uri?=null
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
        companion object {fun uri(id:Long)=Uri.parse("content://media/external/images/media/$id")}
    }
}
