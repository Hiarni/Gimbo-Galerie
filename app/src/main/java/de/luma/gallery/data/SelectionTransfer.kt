package de.luma.gallery.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class SelectionCopy(val media: GalleryMedia, val target: String? = null, val size: Long = -1, val hash: String? = null)
internal data class SelectionTask(val tree: String, val move: Boolean, val copies: List<SelectionCopy>, val waiting: Boolean = false)
internal class SelectionJournal(context: Context) {
    private val file=AtomicFile(File(context.filesDir,"luma-selection-transfer.json"))
    fun read(): SelectionTask? {
        if(!file.baseFile.exists()) return null
        requireMessage(file.baseFile.length()<=8*1024*1024,R.string.file_action_failed)
        val bytes=file.openRead().use { it.readBytes() }
        requireMessage(bytes.size<=8*1024*1024,R.string.file_action_failed)
        val o=JSONObject(bytes.toString(Charsets.UTF_8));require(o.getInt("v")==1)
        val a=o.getJSONArray("items");require(a.length() in 1..5000)
        val copies=(0 until a.length()).map { i -> val n=a.getJSONObject(i)
            val uri=Uri.parse(n.getString("uri"));require(uri.scheme=="content")
            val media=GalleryMedia(n.getString("id"),uri,n.getString("name"),n.getString("album"),n.getLong("time"),n.getBoolean("video"),n.getLong("duration"),n.getLong("bytes"))
            SelectionCopy(media,n.optString("target").takeIf { it.isNotBlank() },n.optLong("size",-1),n.optString("hash").takeIf { it.isNotBlank() })
        }
        val tree=o.getString("tree");require(Uri.parse(tree).scheme=="content")
        require(copies.map { it.media.uri }.distinct().size==copies.size)
        require(copies.all { it.target==null || Uri.parse(it.target).scheme=="content" && it.size>=0 && it.hash?.matches(Regex("[a-f0-9]{64}"))==true })
        return SelectionTask(tree,o.getBoolean("move"),copies,o.optBoolean("waiting"))
    }
    fun write(task: SelectionTask) {
        val a=JSONArray();task.copies.forEach { n -> val m=n.media
            a.put(JSONObject().put("id",m.id).put("uri",m.uri).put("name",m.name).put("album",m.album).put("time",m.timestamp)
                .put("video",m.isVideo).put("duration",m.durationMs).put("bytes",m.size).put("target",n.target).put("size",n.size).put("hash",n.hash))
        }
        val o=JSONObject().put("v",1).put("tree",task.tree).put("move",task.move).put("waiting",task.waiting).put("items",a)
        val stream=file.startWrite()
        try { stream.write(o.toString().toByteArray());file.finishWrite(stream) } catch(e:Throwable) { file.failWrite(stream);throw e }
    }
    fun delete()=file.delete()
}
internal class SelectionTransfer(private val context:Context) {
    suspend fun run(initial:SelectionTask,progress:suspend(DocumentTransferProgress)->Unit):List<GalleryMedia> = withContext(Dispatchers.IO) {
        val journal=SelectionJournal(context);val tools=DocumentTools(context);val root=tools.root(Uri.parse(initial.tree))
        var task=initial
        requireMessage(!task.move || task.copies.all { canManageMedia(it.media) },R.string.local_media_only)
        val children=tools.children(root).map { it.uri.toString() }.toSet()
        task.copies.filter { it.target!=null }.forEach { n ->
            requireMessage(n.target in children,R.string.copy_verification_failed)
            requireMessage(tools.fingerprint(Uri.parse(n.target))==DocumentTools.Fingerprint(n.size,n.hash!!),R.string.copy_verification_failed)
            requireMessage(tools.fingerprint(n.media.uri)==DocumentTools.Fingerprint(n.size,n.hash),R.string.copy_source_changed)
        }
        task.copies.indices.forEach { i ->
            currentCoroutineContext().ensureActive()
            val n=task.copies[i]
            progress(DocumentTransferProgress(DocumentTransferPhase.COPYING,i,task.copies.size,n.media.name))
            if(n.target==null) {
                val mime=context.contentResolver.getType(n.media.uri).orEmpty()
                requireMessage(mime.startsWith("image/")||mime.startsWith("video/"),R.string.not_media_file)
                val copy=tools.copyVerified(DocumentEntry(n.media.uri,n.media.name,mime,0),root)
                requireMessage(tools.fingerprint(n.media.uri)==copy.fingerprint,R.string.copy_source_changed)
                task=task.copy(copies=task.copies.toMutableList().also { it[i]=n.copy(target=copy.uri.toString(),size=copy.fingerprint.size,hash=copy.fingerprint.sha256) })
                withContext(NonCancellable) { journal.write(task) }
            }
        }
        task.copies.forEach { n ->
            requireMessage(tools.fingerprint(Uri.parse(n.target))==DocumentTools.Fingerprint(n.size,n.hash!!),R.string.copy_verification_failed)
            if(task.move) requireMessage(tools.fingerprint(n.media.uri)==DocumentTools.Fingerprint(n.size,n.hash),R.string.copy_source_changed)
        }
        if(task.move) { journal.write(task.copy(waiting=true)); task.copies.map { it.media } }
        else { journal.delete();emptyList() }
    }
    suspend fun verifyMove():List<GalleryMedia> = withContext(Dispatchers.IO) {
        val task=requireNotNull(SelectionJournal(context).read());require(task.move&&task.waiting)
        val tools=DocumentTools(context);val children=tools.children(tools.root(Uri.parse(task.tree))).map { it.uri.toString() }.toSet()
        task.copies.forEach { n ->
            requireMessage(n.target in children,R.string.copy_verification_failed)
            val expected=DocumentTools.Fingerprint(n.size,n.hash!!)
            requireMessage(tools.fingerprint(Uri.parse(n.target))==expected,R.string.copy_verification_failed)
            requireMessage(tools.fingerprint(n.media.uri)==expected,R.string.copy_source_changed)
        };task.copies.map { it.media }
    }
}
