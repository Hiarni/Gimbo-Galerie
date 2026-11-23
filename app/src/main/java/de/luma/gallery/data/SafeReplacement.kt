package de.luma.gallery.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import de.luma.gallery.R
import de.luma.gallery.model.*
import de.luma.gallery.localization.requireMessage
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal class SafeReplacement(private val context:Context,private val clearDraft:suspend (String)->Unit={id->GalleryRepository(context).clearDraft(id)}) {
    private val resolver=context.contentResolver
    private fun directory(id:String)=File(context.filesDir,"luma-replacement-$id").also {require(java.util.UUID.fromString(id).toString()==id)}
    private fun hash(file:File)=file.inputStream().use {digest(it)}
    private fun hash(uri:Uri)=requireNotNull(resolver.openInputStream(android.provider.MediaStore.setRequireOriginal(uri))).use {digest(it)}
    private fun digest(input:java.io.InputStream):String {
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536)
        while(true) {val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n)}
        return digest.digest().joinToString("") {"%02x".format(it)}
    }
    private fun write(file:AtomicFile,json:JSONObject) {
        val stream=file.startWrite()
        try {stream.write(json.toString().toByteArray());file.finishWrite(stream)} catch(e:Throwable) {file.failWrite(stream);throw e}
    }
    suspend fun replace(task:ExportRequest,progress:(Int?)->Unit={},onCommitted:()->Unit={})=withContext(Dispatchers.IO) {
        require(task.overwrite && task.items.size==1)
        val item=task.items.single();requireMessage(canManageMedia(item),R.string.local_media_only)
        val mime=resolver.getType(item.uri)
        requireMessage(if(item.isVideo) mime=="video/mp4" else mime==task.options.format.mime,R.string.overwrite_format_hint)
        val dir=directory(task.id);dir.mkdirs()
        val journal=AtomicFile(File(dir,"journal.json"))
        val old=File(dir,"original.${if(item.isVideo) "mp4" else task.options.format.extension}")
        val edited=File(dir,"edited.${if(item.isVideo) "mp4" else task.options.format.extension}")
        if(journal.baseFile.exists() || File(journal.baseFile.path+".bak").exists()) {
            val saved=journal.openRead().use {JSONObject(it.readBytes().toString(Charsets.UTF_8))}
            require(saved.getString("uri")==item.uri.toString())
            if(saved.getString("phase")=="DONE") {
                requireMessage(hash(item.uri)==saved.getString("editedHash"),R.string.copy_source_changed)
                return@withContext
            }
            recover(task.id)
            requireMessage(hash(item.uri)==saved.getString("originalHash"),R.string.copy_source_changed)
        }
        requireMessage(context.filesDir.usableSpace > (item.size.coerceAtLeast(16_000_000)*3),R.string.storage_unavailable)
        resolver.openInputStream(android.provider.MediaStore.setRequireOriginal(item.uri))?.use {input->old.outputStream().use {input.copyTo(it)}} ?: throw java.io.IOException()
        val originalHash=hash(old)
        write(journal,JSONObject().put("uri",item.uri.toString()).put("original",old.name).put("originalHash",originalHash).put("phase","PREPARING"))
        val source=item.copy(uri=Uri.fromFile(old),isDemo=false)
        if(task.photo!=null) {
            val options=task.options
            if(options.maxEdge>=8192) {
                val dimensions=edited.outputStream().use {TiledPhotoExporter(context).render(source,task.photo,options,it)}
                if(options.format==PhotoFormat.JPEG) PhotoMetadata.copy(context,source,Uri.fromFile(edited),options.metadata,dimensions.first,dimensions.second)
            } else {
                val active=currentCoroutineContext()
                val bitmap=PhotoEngine.decode(context,source.uri,options.maxEdge)
                val rendered=try {PhotoEngine.render(bitmap,task.photo) {active.ensureActive()}} finally {bitmap.recycle()}
                try {
                    edited.outputStream().use {PhotoEngine.encode(rendered,options,it)}
                    if(options.format==PhotoFormat.JPEG) PhotoMetadata.copy(context,source,Uri.fromFile(edited),options.metadata,rendered.width,rendered.height)
                } finally {rendered.recycle()}
            }
        } else {
            val file=VideoExporter(context).export(source,requireNotNull(task.video),progress)
            try {file.inputStream().use {input->edited.outputStream().use {input.copyTo(it)}}} finally {file.delete()}
        }
        require(edited.length()>0)
        val editedHash=hash(edited)
        currentCoroutineContext().ensureActive()
        requireMessage(hash(item.uri)==originalHash,R.string.copy_source_changed)
        val saved=JSONObject().put("uri",item.uri.toString()).put("original",old.name).put("originalHash",originalHash)
            .put("editedHash",editedHash).put("phase","WRITING")
        ProjectSourceProtection(context).protect(item,old,task.id)
        requireMessage(hash(item.uri)==originalHash,R.string.copy_source_changed)
        currentCoroutineContext().ensureActive()
        write(journal,saved)
        withContext(NonCancellable) {
            try {
                copy(edited,item.uri)
                requireMessage(hash(item.uri)==editedHash,R.string.copy_verify_failed)
                clearDraft(item.id)
                write(journal,saved.put("phase","DONE"))
                onCommitted()
                resolver.notifyChange(item.uri,null)
            } catch(e:Throwable) {recover(task.id);throw e}
        }
    }
    private suspend fun phase(id:String):String?=withContext(Dispatchers.IO) {
        val journal=AtomicFile(File(directory(id),"journal.json"))
        if(!journal.baseFile.exists() && !File(journal.baseFile.path+".bak").exists()) null
        else journal.openRead().use {JSONObject(it.readBytes().toString(Charsets.UTF_8)).getString("phase")}
    }
    suspend fun needsRecovery(id:String)=phase(id)=="WRITING"
    suspend fun hasCommitted(id:String)=phase(id)=="DONE"
    private fun copy(file:File,uri:Uri) {
        requireNotNull(resolver.openOutputStream(uri,"wt")).use {output->file.inputStream().use {it.copyTo(output)}}
    }
    suspend fun recover(id:String)=withContext(Dispatchers.IO+NonCancellable) {
        val dir=directory(id);val journal=AtomicFile(File(dir,"journal.json"))
        if(!journal.baseFile.exists() && !File(journal.baseFile.path+".bak").exists()) return@withContext
        val saved=journal.openRead().use {JSONObject(it.readBytes().toString(Charsets.UTF_8))}
        if(saved.getString("phase")!="WRITING") return@withContext
        val name=saved.getString("original");require(name=="original.jpg"||name=="original.png"||name=="original.mp4")
        val original=File(dir,name);val uri=Uri.parse(saved.getString("uri"))
        requireMessage(hash(original)==saved.getString("originalHash"),R.string.copy_verify_failed)
        copy(original,uri)
        requireMessage(hash(uri)==saved.getString("originalHash"),R.string.copy_verify_failed)
        write(journal,saved.put("phase","RESTORED"))
    }
    fun cleanup(id:String) {directory(id).deleteRecursively()}
}
