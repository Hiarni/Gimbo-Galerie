package de.luma.gallery.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import de.luma.gallery.model.GalleryMedia
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class VaultItem(val id:String,val media:GalleryMedia,val bytes:Long,val mime:String="image/jpeg",val sha256:String="")

class VaultRepository(context:Context) {
    private val context=context.applicationContext
    private val directory=File(context.filesDir,"gimbo-vault").apply {mkdirs()}
    private fun wrappingKey():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey(ALIAS,null) as? SecretKey)?.let {return it}
        val builder=KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(false)
        if(Build.VERSION.SDK_INT>=30) builder.setUserAuthenticationParameters(30,KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        else builder.setUserAuthenticationValidityDurationSeconds(30)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {init(builder.build())}.generateKey()
    }
    internal fun unlock():ByteArray {
        val envelope=AtomicFile(File(directory,"key"))
        val key=wrappingKey()
        if(envelope.baseFile.exists() || File(envelope.baseFile.path+".bak").exists()) {
            val bytes=envelope.openRead().use {it.readBytes()};require(bytes.size in 29..128)
            return Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(0,12)));updateAAD(AAD)}
                .doFinal(bytes.copyOfRange(12,bytes.size)).also {require(it.size==32)}
        }
        require(directory.listFiles().orEmpty().none {it.extension in listOf("media","meta")})
        val master=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key);updateAAD(AAD)}
        var stream:java.io.FileOutputStream?=null
        try {stream=envelope.startWrite();stream.write(cipher.iv+cipher.doFinal(master));envelope.finishWrite(stream)}
        catch(e:Throwable) {master.fill(0);stream?.let(envelope::failWrite);throw e}
        return master
    }
    internal fun cipher(key:ByteArray,mode:Int,id:String,iv:ByteArray?=null)=Cipher.getInstance("AES/GCM/NoPadding").apply {
        if(iv==null) init(mode,SecretKeySpec(key,"AES")) else init(mode,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv))
        updateAAD("Gimbo-vault:$id".toByteArray(Charsets.UTF_8))
    }
    internal fun seal(key:ByteArray,name:String,json:JSONObject) {
        val file=AtomicFile(File(directory,name));val encryption=cipher(key,Cipher.ENCRYPT_MODE,name)
        var stream:java.io.FileOutputStream?=null
        try {stream=file.startWrite();stream.write(encryption.iv+encryption.doFinal(json.toString().toByteArray(Charsets.UTF_8)));file.finishWrite(stream)}
        catch(e:Throwable) {stream?.let(file::failWrite);throw e}
    }
    internal fun unseal(key:ByteArray,name:String):JSONObject {
        val file=AtomicFile(File(directory,name));val bytes=file.openRead().use {it.readBytes()};require(bytes.size>=28)
        return JSONObject(cipher(key,Cipher.DECRYPT_MODE,name,bytes.copyOfRange(0,12)).doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8))
    }
    internal fun pending(key:ByteArray):JSONObject? = if(File(directory,"pending").exists() || File(directory,"pending.bak").exists()) unseal(key,"pending") else null
    internal fun clearPending() {AtomicFile(File(directory,"pending")).delete()}
    internal fun items(key:ByteArray):List<VaultItem> = directory.listFiles().orEmpty().filter {it.extension=="meta"}.map {file->
        val json=unseal(key,file.name)
        VaultItem(file.nameWithoutExtension,RecipeJson.readMedia(json.getJSONObject("media")),json.getLong("bytes"),json.optString("mime",if(RecipeJson.readMedia(json.getJSONObject("media")).isVideo) "video/mp4" else "image/jpeg"),json.optString("sha256"))
    }.sortedByDescending {it.media.timestamp}
    internal suspend fun import(key:ByteArray,id:String,media:GalleryMedia):VaultItem = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        if(File(directory,"$id.meta").isFile) {
            val json=unseal(key,"$id.meta");return@withContext VaultItem(id,RecipeJson.readMedia(json.getJSONObject("media")),json.getLong("bytes"),json.optString("mime",if(RecipeJson.readMedia(json.getJSONObject("media")).isVideo) "video/mp4" else "image/jpeg"),json.optString("sha256"))
        }
        val target=File(directory,"$id.media");val temporary=File(directory,"$id.part")
        val mediaKey=mediaKey(key,id)
        val prefix=ByteArray(8).also {SecureRandom().nextBytes(it)}
        var size=0L
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        try {
            context.contentResolver.openInputStream(media.uri)?.use {input->temporary.outputStream().use {raw->
                val output=java.io.DataOutputStream(raw)
                output.writeInt(MAGIC);output.write(prefix);val buffer=ByteArray(65536);var index=0
                while(true) {
                    ensureActive();val count=input.read(buffer);if(count<0) break;if(count==0) continue
                    size+=count;digest.update(buffer,0,count)
                    val encrypted=cipher(mediaKey,Cipher.ENCRYPT_MODE,"$id:$index",nonce(prefix,index)).doFinal(buffer,0,count)
                    output.writeInt(encrypted.size);output.write(encrypted);index++
                }
                val end=cipher(mediaKey,Cipher.ENCRYPT_MODE,"$id:$index:end",nonce(prefix,index)).doFinal()
                output.writeInt(end.size);output.write(end);output.flush();raw.fd.sync()
            }} ?: error("Source unavailable")
            require(size>0 && temporary.renameTo(target))
            val mime=context.contentResolver.getType(media.uri) ?: if(media.isVideo) "video/mp4" else "image/jpeg"
            val hash=digest.digest().joinToString("") {"%02x".format(it.toInt() and 255)}
            seal(key,"$id.meta",JSONObject().put("media",RecipeJson.media(media)).put("bytes",size).put("mime",mime).put("sha256",hash))
            VaultItem(id,media,size,mime,hash)
        } finally {mediaKey.fill(0);temporary.delete()}
    }
    internal suspend fun decrypt(key:ByteArray,item:VaultItem,target:File,check:()->Unit={})=withContext(Dispatchers.IO) {
        require(item.id.matches(Regex("[a-f0-9-]{36}")))
        try {
            dataSource(key,item,check).use {source->target.outputStream().use {output->
                val buffer=ByteArray(65536);var position=0L
                while(position<source.size) {ensureActive();check();val count=source.readAt(position,buffer,0,buffer.size);require(count>0);output.write(buffer,0,count);position+=count}
            }}
            require(target.length()==item.bytes);check()
        } catch(e:Throwable) {target.delete();throw e}
    }
    internal suspend fun restore(key:ByteArray,item:VaultItem):Uri=withContext(Dispatchers.IO) {
        val extension=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(item.mime) ?: if(item.media.isVideo) "mp4" else "jpg"
        val stem=item.media.name.substringBeforeLast('.').take(120)
        val name="${stem}_Gimbo-Galerie_${item.id}.$extension"
        val gallery=GalleryRepository(context)
        for(candidate in listOf(name,"${stem}_Gimbo_${item.id}.$extension")) gallery.exportExists(item.media.isVideo,candidate)?.let {return@withContext it}
        val collection=if(item.media.isVideo) android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI else android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val target=requireNotNull(context.contentResolver.insert(collection,android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,name);put(android.provider.MediaStore.MediaColumns.MIME_TYPE,item.mime)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,if(item.media.isVideo) "Movies/Gimbo Galerie" else "Pictures/Gimbo Galerie")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
        }))
        var success=false
        try {
            dataSource(key,item).use {source->context.contentResolver.openOutputStream(target)?.use {output->
                var position=0L;val buffer=ByteArray(65536)
                while(position<source.size) {ensureActive();val count=source.readAt(position,buffer,0,buffer.size);require(count>0);output.write(buffer,0,count);position+=count}
            } ?: error("Destination unavailable")}
            ensureActive()
            require(context.contentResolver.update(target,android.content.ContentValues().apply {put(android.provider.MediaStore.MediaColumns.IS_PENDING,0)},null,null)==1)
            success=true;target
        } finally {if(!success) withContext(NonCancellable) {runCatching {context.contentResolver.delete(target,null,null)}}}
    }
    private fun mediaKey(key:ByteArray,id:String)=javax.crypto.Mac.getInstance("HmacSHA256").apply {init(SecretKeySpec(key,"HmacSHA256"))}.doFinal("Gimbo-vault-media:$id".toByteArray(Charsets.UTF_8))
    internal fun dataSource(key:ByteArray,item:VaultItem,check:()->Unit={})=VaultDataSource(this,mediaKey(key,item.id),item,File(directory,"${item.id}.media"),check)
    internal suspend fun verifyOriginals(key:ByteArray,items:List<VaultItem>,check:()->Unit={}):List<GalleryMedia> = withContext(Dispatchers.IO) {
        val known=this@VaultRepository.items(key).associateBy {it.id}
        items.forEach {item->ensureActive();check();val saved=known.getValue(item.id)
            de.luma.gallery.localization.requireMessage(saved.sha256.matches(Regex("[a-f0-9]{64}")) && DuplicateScanner.hash(context,saved.media).first==saved.sha256,de.luma.gallery.R.string.vault_original_changed)
        }
        check();items.map {known.getValue(it.id).media}
    }
    internal fun removeId(id:String) {require(id.matches(Regex("[a-f0-9-]{36}")));val media=File(directory,"$id.media");require(!media.exists() || media.delete());AtomicFile(File(directory,"$id.meta")).delete()}
    internal fun clearPreviews() {File(context.cacheDir,"gimbo-vault-preview").deleteRecursively()}
    companion object {
        internal const val MAGIC=0x474D4231
        internal fun nonce(prefix:ByteArray,index:Int)=java.nio.ByteBuffer.allocate(12).put(prefix).putInt(index).array()
        private const val ALIAS="gimbo-vault-wrap-v1";private val AAD="Gimbo-vault-master-v1".toByteArray(Charsets.UTF_8)}
}
