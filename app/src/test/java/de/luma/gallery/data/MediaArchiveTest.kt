package de.luma.gallery.data

import android.Manifest
import android.content.pm.ProviderInfo
import android.graphics.*
import android.provider.DocumentsContract
import androidx.room.Room
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.*
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaArchiveTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var provider: DocumentToolsTest.TestProvider
    private lateinit var database: ProjectDatabase
    private val tree get()=DocumentsContract.buildTreeDocumentUri("luma.test.docs","root")
    private fun uri(id: String)=DocumentsContract.buildDocumentUriUsingTree(tree,id)
    private val source get()=GalleryMedia("old-id",uri("source"),"Photo.jpg","Album",0,size=provider.records.getValue("source").file!!.length())
    private val archive get()=MediaArchive(context,ProjectBackup(database))
    @Before fun setup(){
        val app=context
        provider=DocumentToolsTest.TestProvider().apply{attachInfo(app,ProviderInfo().apply{authority="luma.test.docs";exported=true;grantUriPermissions=true;readPermission=Manifest.permission.MANAGE_DOCUMENTS;writePermission=Manifest.permission.MANAGE_DOCUMENTS})}
        ShadowContentResolver.registerProviderInternal("luma.test.docs",provider)
        database=Room.inMemoryDatabaseBuilder(context,ProjectDatabase::class.java).build()
        val bitmap=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);provider.records.getValue("source").file!!.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @After fun cleanup(){database.close();provider.folder.deleteRecursively();context.cacheDir.listFiles()?.filter{it.name.startsWith("luma_backup_") || it.name.startsWith("archive-test")}?.forEach{it.delete()}}
    private suspend fun save(): File {
        val target=DocumentsContract.createDocument(context.contentResolver,uri("target"),"application/zip","Backup.zip")!!
        archive.write(target,listOf(source));return archive.read(target).file
    }
    private fun mutate(file: File, transform: (String,ByteArray)->Pair<String,ByteArray>): File {
        val changed=File.createTempFile("archive-test",".zip",context.cacheDir)
        ZipFile(file).use{zip->ZipOutputStream(changed.outputStream()).use{out->for(entry in zip.entries().toList()){val (name,bytes)=transform(entry.name,zip.getInputStream(entry).use{it.readBytes()});out.putNextEntry(ZipEntry(name));out.write(bytes);out.closeEntry()}}}
        return changed
    }
    @Test fun originalsAndRecipesArePortableAndAlbumsUseNewUris()=runBlocking {
        val original=provider.records.getValue("source").file!!.readBytes()
        ProjectRepository(database).save(source,"Project",photo=PhotoRecipe(exposure=.5f));AlbumRepository(database).create("Album",setOf(source.id))
        val file=save();val review=archive.validate(file)
        assertEquals(1,review.media.size);assertEquals(original.size.toLong(),review.totalBytes)
        val restored=archive.restore(review,tree)
        assertEquals(1,restored.size);assertNotEquals(source.uri,restored.single())
        assertArrayEquals(original,context.contentResolver.openInputStream(restored.single())!!.use{it.readBytes()});assertArrayEquals(original,provider.records.getValue("source").file!!.readBytes())
        val projects=ProjectRepository(database).projects.first();assertEquals(2,projects.size);assertTrue(projects.any{it.source.uri==restored.single() && it.photo!!.exposure==.5f})
        assertTrue(AlbumRepository(database).albums.first().any{it.name=="Album (2)" && it.mediaIds==setOf(restored.single().toString())})
    }
    @Test fun corruptedMediaOrBadManifestAreRejectedBeforeCreatingCopies()=runBlocking {
        val file=save();val corrupt=mutate(file){name,bytes->name to if(name.startsWith("media/"))byteArrayOf(9)else bytes}
        val before=provider.records.keys.toSet();assertTrue(runCatching{archive.validate(corrupt)}.isFailure);assertEquals(before,provider.records.keys.toSet())
        val bad=mutate(file){name,bytes->name to if(name=="manifest.json")bytes.toString(Charsets.UTF_8).replace("\"version\":1","\"version\":99").toByteArray()else bytes}
        assertTrue(runCatching{archive.validate(bad)}.isFailure)
    }
    @Test fun pathTraversalUnlistedEntriesAndOversizedManifestAreRejected()=runBlocking {
        val file=save();val traversal=mutate(file){name,bytes->if(name.startsWith("media/"))"../outside" to bytes else name to bytes}
        assertTrue(runCatching{archive.validate(traversal)}.isFailure)
        val oversized=mutate(file){name,bytes->name to if(name=="manifest.json")ByteArray(MediaArchive.MAX_MANIFEST+1)else bytes}
        assertTrue(runCatching{archive.validate(oversized)}.isFailure)
    }
    @Test fun externallyChangedArchiveIsRevalidatedAtRestoreAndCannotPartiallyImportProjects()=runBlocking {
        ProjectRepository(database).save(source,"Project",photo=PhotoRecipe())
        val file=save();val review=archive.validate(file);val bad=mutate(file){name,bytes->name to if(name.startsWith("media/"))byteArrayOf(0)else bytes};bad.copyTo(file,true)
        assertTrue(runCatching{archive.restore(review,tree)}.isFailure);assertEquals(1,ProjectRepository(database).projects.first().size)
    }
    @Test fun duplicateReferencesAreStoredOnlyOnce()=runBlocking {
        val projects=ProjectRepository(database);projects.save(source,"One",photo=PhotoRecipe());projects.save(source,"Two",photo=PhotoRecipe(exposure=.3f))
        val file=save();assertEquals(1,archive.validate(file).media.size);assertEquals(2,archive.validate(file).backup.projects.size)
    }
    @Test fun albumUriAliasesForALibrarySourceAreRestoredToTheSameNewFile()=runBlocking {
        ProjectRepository(database).save(source.copy(id=source.uri.toString()),"Relinked",photo=PhotoRecipe())
        AlbumRepository(database).create("Aliases",setOf(source.uri.toString(),source.id))
        val file=save();val review=archive.validate(file);val restored=archive.restore(review,tree)
        assertEquals(1,restored.size)
        val album=AlbumRepository(database).albums.first().first{it.name=="Aliases (2)"}
        assertEquals(setOf(restored.single().toString()),album.mediaIds)
    }

    @Test fun cancelledArchiveReadRemovesItsOwnPartialCacheFileAndPreservesOriginals()=runBlocking {
        val target=DocumentsContract.createDocument(context.contentResolver,uri("target"),"application/zip","Backup.zip")!!
        archive.write(target,listOf(source))
        val before=context.cacheDir.listFiles()!!.filter{it.name.startsWith("luma_backup_")}.map{it.name}.toSet()
        val original=provider.records.getValue("source").file!!.readBytes()
        val request=async(start=CoroutineStart.LAZY){archive.read(target)}
        provider.onOpenDocument={request.cancel()};request.start()
        assertTrue(runCatching{request.await()}.exceptionOrNull() is CancellationException)
        assertEquals(before,context.cacheDir.listFiles()!!.filter{it.name.startsWith("luma_backup_")}.map{it.name}.toSet())
        assertArrayEquals(original,provider.records.getValue("source").file!!.readBytes())
    }

    @Test fun largerArchiveRestoresIntoBrowsableGroupsWithAtMostOneThousandFiles()=runBlocking {
        val bytes=provider.records.getValue("source").file!!.readBytes()
        val library=(0..1000).map { i ->
            provider.add("input-$i","root","Photo-$i.jpg",bytes)
            GalleryMedia("input-$i",uri("input-$i"),"Photo-$i.jpg","",0)
        }
        val target=DocumentsContract.createDocument(context.contentResolver,uri("target"),"application/zip","Large.zip")!!
        archive.write(target,library)
        val review=archive.read(target)
        val restored=archive.restore(review,tree)
        assertEquals(1001,restored.size)
        val root=DocumentTools(context).children(uri("root")).single{it.name=="Gimbo-Galerie-Restore"}
        val groups=DocumentTools(context).children(root.uri)
        assertEquals(listOf("0001","0002"),groups.map{it.name})
        assertEquals(listOf(1000,1),groups.map{DocumentTools(context).children(it.uri).size})
        assertArrayEquals(bytes,context.contentResolver.openInputStream(restored.last())!!.use{it.readBytes()})
    }

    @Test fun interruptedArchiveRestoreReusesVerifiedCopiesAndRestoresProjectsOnce():Unit=runBlocking {
        ProjectRepository(database).save(source,"Draft",photo=PhotoRecipe())
        val file=save();val checked=archive.validate(file)
        val legacyFolder=DocumentTools(context).createFolder(DocumentTools(context).root(tree),"Gimbo-Restore-test-recovery")
        ArchiveRestoreJournal(context,"test-recovery").use {journal->
            try {archive.restore(checked,tree,journal) {_,_->throw CancellationException()};fail()} catch(_:CancellationException) {}
            assertEquals(legacyFolder.toString(),journal.folder);assertNotNull(journal.target(0))
        }
        val result=ArchiveRestoreJournal(context,"test-recovery").use {journal->archive.restore(checked,tree,journal)}
        val repeated=ArchiveRestoreJournal(context,"test-recovery").use {journal->archive.restore(checked,tree,journal)}
        assertEquals(result,repeated);assertEquals(2,ProjectRepository(database).projects.first().size)
        ArchiveRestoreJournal(context,"test-recovery").use {it.delete()}
        context.deleteDatabase("gimbo-restore.db")
    }

}
