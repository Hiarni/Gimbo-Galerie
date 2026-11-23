package de.luma.gallery.data

import android.app.Application
import android.net.Uri
import de.luma.gallery.model.GalleryMedia
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class VaultRepositoryTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var source:File
    private lateinit var key:ByteArray
    private lateinit var repository:VaultRepository
    private val directory get()=File(context.filesDir,"gimbo-vault")
    @Before fun setup() {
        directory.deleteRecursively();repository=VaultRepository(context)
        key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        source=File(context.cacheDir,"private-name-test.bin").apply {writeBytes(ByteArray(240123) {(it*31).toByte()})}
    }
    @After fun cleanup() {key.fill(0);source.delete();directory.deleteRecursively();File(context.cacheDir,"decrypted-test").delete()}
    private suspend fun secure()=repository.import(key,UUID.randomUUID().toString(),GalleryMedia("original",Uri.fromFile(source),source.name,"Private album",0,size=source.length()))
    @Test fun encryptedMediaAndMetadataDoNotExposeNamesAndRandomAccessIsExact():Unit=runBlocking {
        val item=secure();val original=source.readBytes()
        assertEquals(listOf(item),repository.items(key));assertTrue(source.isFile)
        assertEquals(DuplicateScanner.hash(context,item.media).first,item.sha256)
        directory.listFiles()!!.forEach {assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains(source.name))}
        repository.dataSource(key,item).use {data->
            assertEquals(original.size.toLong(),data.size)
            listOf(0L,65500L,196700L,100L).forEach {offset->
                val buffer=ByteArray(10000);val size=data.readAt(offset,buffer,0,buffer.size)
                assertArrayEquals(original.copyOfRange(offset.toInt(),offset.toInt()+size),buffer.copyOf(size))
            }
        }
        val target=File(context.cacheDir,"decrypted-test");repository.decrypt(key,item,target);assertArrayEquals(original,target.readBytes())
    }
    @Test fun tamperingOrWrongKeysCannotProduceAUsablePlaintextFile():Unit=runBlocking {
        val item=secure();val target=File(context.cacheDir,"decrypted-test")
        assertTrue(runCatching {repository.decrypt(ByteArray(32),item,target)}.isFailure);assertFalse(target.exists())
        RandomAccessFile(File(directory,"${item.id}.media"),"rw").use {file->file.seek(42);val value=file.read();file.seek(42);file.write(value xor 1)}
        assertTrue(runCatching {repository.decrypt(key,item,target)}.isFailure);assertFalse(target.exists());assertTrue(source.exists())
    }
    @Test fun truncatedOrReorderedEncryptedChunksAreRejected():Unit=runBlocking {
        val item=secure();val encrypted=File(directory,"${item.id}.media");val original=encrypted.readBytes()
        encrypted.writeBytes(original.copyOf(original.size-1))
        assertTrue(runCatching {repository.dataSource(key,item).close()}.isFailure)
        encrypted.writeBytes(original)
        RandomAccessFile(encrypted,"rw").use {file->
            file.seek(12);val length=file.readInt();val first=ByteArray(length);file.readFully(first)
            val secondStart=file.filePointer;val secondLength=file.readInt();assertEquals(length,secondLength)
            val second=ByteArray(length);file.readFully(second);file.seek(16);file.write(second);file.seek(secondStart+4);file.write(first)
        }
        repository.dataSource(key,item).use {data->assertTrue(runCatching {data.readAt(0,ByteArray(10),0,10)}.isFailure)}
    }
    @Test fun aLockedSessionStopsFurtherReadsAndEncryptedRequestsRemainPrivate():Unit=runBlocking {
        val item=secure();var unlocked=true
        repository.dataSource(key,item) {check(unlocked)}.use {data->unlocked=false;assertTrue(runCatching {data.readAt(0,ByteArray(10),0,10)}.isFailure)}
        val request=JSONObject().put("name",source.name).put("done",1)
        repository.seal(key,"pending",request)
        assertFalse(File(directory,"pending").readText(Charsets.ISO_8859_1).contains(source.name))
        assertEquals(request.toString(),repository.pending(key)!!.toString())
    }
    @Test fun interruptedImportsReuseTheirPlannedItemWithoutDuplicatingOrRemovingTheSource():Unit=runBlocking {
        val item=secure();val again=repository.import(key,item.id,item.media)
        assertEquals(item,again);assertEquals(1,repository.items(key).size);assertTrue(source.isFile)
    }
    @Test fun sourceRemovalVerificationRejectsEditedOrMissingOriginals():Unit=runBlocking {
        val item=secure()
        assertEquals(listOf(item.media),repository.verifyOriginals(key,listOf(item)))
        source.appendBytes(byteArrayOf(42))
        assertTrue(runCatching {repository.verifyOriginals(key,listOf(item))}.isFailure)
        assertEquals(1,repository.items(key).size)
        source.delete()
        assertTrue(runCatching {repository.verifyOriginals(key,listOf(item))}.isFailure)
    }

    @Test fun retriedVaultDeletionIsIdempotentAndKeepsPublicOriginals():Unit=runBlocking {
        val item=secure();repository.removeId(item.id);repository.removeId(item.id)
        assertTrue(repository.items(key).isEmpty());assertTrue(source.isFile)
    }

}
