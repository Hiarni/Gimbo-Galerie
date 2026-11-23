package de.luma.gallery.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import de.luma.gallery.BackupService
import de.luma.gallery.model.GalleryMedia
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class BackupOperation { ARCHIVE_WRITE, ARCHIVE_READ, ARCHIVE_RESTORE, PROJECT_WRITE, PROJECT_READ, PROJECT_RESTORE }
data class BackupSnapshot(val running:Boolean=false,val interrupted:Boolean=false,val done:Int=0,val total:Int=0,
    val archive:ArchiveReview?=null,val backup:BackupReview?=null,val event:Long=0,val message:String?=null)
object BackupController {
    private val mutex=Mutex()
    private val mutable=MutableStateFlow(BackupSnapshot())
    val state=mutable.asStateFlow()
    private fun journal(context:Context)=AtomicFile(File(context.filesDir,"gimbo-backup-task.json"))
    internal fun read(context:Context):JSONObject? {
        val file=journal(context)
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return null
        return file.openRead().use {JSONObject(it.readBytes().toString(Charsets.UTF_8))}
    }
    internal fun write(context:Context,task:JSONObject) {journal(context).writeJson(task)}
    internal fun update(value:BackupSnapshot) {mutable.value=value}
    internal fun archiveFile(context:Context,id:String)=File(context.filesDir,"gimbo-backup-$id.zip")
    suspend fun start(context:Context,operation:BackupOperation,uri:Uri?=null,media:List<GalleryMedia> = emptyList(),review:BackupReview?=null,archive:ArchiveReview?=null)=mutex.withLock {
        check(!state.value.running)
        uri?.takeIf {it.scheme=="content"}?.let {source->
            val flags=if(operation in listOf(BackupOperation.ARCHIVE_WRITE,BackupOperation.PROJECT_WRITE,BackupOperation.ARCHIVE_RESTORE)) Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION else Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(source,flags)
        }
        withContext(Dispatchers.IO) {
            val snapshot=if(operation in listOf(BackupOperation.ARCHIVE_WRITE,BackupOperation.PROJECT_WRITE)) ProjectBackup(ProjectDatabase.get(context)).snapshot() else review?.let(ProjectBackup::encode)
            val task=JSONObject().put("id",UUID.randomUUID().toString()).put("operation",operation.name).put("uri",uri?.toString())
                .put("media",JSONArray(media.map(RecipeJson::media))).put("backup",snapshot).put("source",archive?.file?.name).put("status","RUNNING")
            write(context,task)
        }
        launch(context)
    }
    private fun launch(context:Context) {
        TaskRecovery.automatic(context,"backup",true)
        update(state.value.copy(running=true,interrupted=false,message=null))
        try {ContextCompat.startForegroundService(context,Intent(context,BackupService::class.java))}
        catch(e:Exception) {update(state.value.copy(running=false,interrupted=true));throw e}
    }
    suspend fun recover(context:Context)=mutex.withLock {
        if(state.value.running) return@withLock
        val task=withContext(Dispatchers.IO) {read(context)} ?: return@withLock
        when(task.getString("status")) {
            "RUNNING"->if(TaskRecovery.eligible(context,"backup")) launch(context) else update(state.value.copy(interrupted=true))
            "REVIEW"->loadReview(context,task)
            "PAUSED","FAILED"->update(state.value.copy(interrupted=true))
        }
    }
    internal suspend fun loadReview(context:Context,task:JSONObject) {
        val operation=BackupOperation.valueOf(task.getString("operation"))
        if(operation==BackupOperation.ARCHIVE_READ) {
            val review=MediaArchive(context,ProjectBackup(ProjectDatabase.get(context))).validate(archiveFile(context,task.getString("id")))
            update(state.value.copy(running=false,interrupted=false,archive=review,backup=null))
        } else update(state.value.copy(running=false,interrupted=false,backup=ProjectBackup.decode(task.getString("backup")),archive=null))
    }
    suspend fun resume(context:Context)=mutex.withLock {
        check(!state.value.running)
        withContext(Dispatchers.IO) {val task=requireNotNull(read(context));task.put("status","RUNNING");write(context,task)}
        launch(context)
    }
    fun pause(context:Context) {TaskRecovery.automatic(context,"backup",false);if(state.value.running) context.startService(Intent(context,BackupService::class.java).setAction(BackupService.PAUSE))}
    suspend fun dismiss(context:Context)=mutex.withLock {
        if(state.value.running) return@withLock
        TaskRecovery.automatic(context,"backup",false)
        withContext(Dispatchers.IO) {
            read(context)?.let {task->archiveFile(context,task.getString("id")).delete();ArchiveRestoreJournal(context,task.getString("id")).use {it.delete()}}
            journal(context).delete()
        }
        state.value.archive?.file?.takeIf {it.name.startsWith("gimbo-backup-") && it.parentFile==context.filesDir}?.delete()
        update(BackupSnapshot(event=state.value.event))
    }
}
