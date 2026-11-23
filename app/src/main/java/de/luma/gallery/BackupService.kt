package de.luma.gallery

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import de.luma.gallery.data.*
import de.luma.gallery.localization.localizedResources
import de.luma.gallery.localization.userMessage
import kotlinx.coroutines.*

class BackupService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job:Job?=null
    private var paused=false
    private val wake by lazy {TaskWakeLock(this,"backup")}
    private val strings get()=localizedResources(this)
    private fun notice():android.app.Notification {
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("gimbo-backup",strings.getString(R.string.backup_title),NotificationManager.IMPORTANCE_LOW))
        val progress=BackupController.state.value
        return NotificationCompat.Builder(this,"gimbo-backup").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(R.string.backup_title)).setContentText(strings.getString(R.string.review_progress,progress.done,progress.total))
            .setProgress(progress.total,progress.done,progress.total==0).setOnlyAlertOnce(true).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this,50,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(0,strings.getString(R.string.background_pause),PendingIntent.getService(this,51,Intent(this,BackupService::class.java).setAction(PAUSE),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==PAUSE) {TaskRecovery.automatic(this,"backup",false);paused=true;job?.cancel();if(job==null) stopSelf();return START_NOT_STICKY}
        if(job!=null) return START_STICKY
        if(intent==null && !TaskRecovery.eligible(this,"backup")) {stopSelf();return START_NOT_STICKY}
        try {ServiceCompat.startForeground(this,2015,notice(),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)}
        catch(e:Exception) {BackupController.update(BackupController.state.value.copy(running=false,interrupted=true,message=e.userMessage(strings,R.string.backup_failed)));stopSelf();return START_NOT_STICKY}
        paused=false;wake.acquire()
        BackupController.update(BackupController.state.value.copy(running=true,interrupted=false))
        job=scope.launch {
            var task:org.json.JSONObject?=null
            var message:String?=null
            try {
                withContext(Dispatchers.IO) {
                    val request=requireNotNull(BackupController.read(this@BackupService));task=request
                    if(request.getString("status")!="RUNNING") return@withContext
                    val operation=BackupOperation.valueOf(request.getString("operation"))
                    val id=request.getString("id")
                    val backup=ProjectBackup(ProjectDatabase.get(this@BackupService))
                    val archive=MediaArchive(this@BackupService,backup)
                    val uri=request.optString("uri").takeIf {it.isNotBlank()}?.let(Uri::parse)
                    val progress:(Int,Int)->Unit={done,total->BackupController.update(BackupController.state.value.copy(done=done,total=total));getSystemService(NotificationManager::class.java).notify(2015,notice())}
                    when(operation) {
                        BackupOperation.ARCHIVE_WRITE->{val sources=request.getJSONArray("media");archive.write(requireNotNull(uri),(0 until sources.length()).map {RecipeJson.readMedia(sources.getJSONObject(it))},request.getString("backup"),progress)}
                        BackupOperation.ARCHIVE_READ->{archive.read(requireNotNull(uri),BackupController.archiveFile(this@BackupService,id));request.put("status","REVIEW")}
                        BackupOperation.ARCHIVE_RESTORE->{
                            val filename=request.getString("source");require(filename.matches(Regex("gimbo-backup-[a-f0-9-]+\\.zip")))
                            val file=java.io.File(filesDir,filename)
                            ArchiveRestoreJournal(this@BackupService,id).use {journal->archive.restoreFile(file,requireNotNull(uri),journal,progress)}

                        }
                        BackupOperation.PROJECT_WRITE->{val bytes=request.getString("backup").toByteArray(Charsets.UTF_8);require(bytes.size<=ProjectBackup.MAX_BYTES)
                            contentResolver.openOutputStream(requireNotNull(uri),"wt")?.use {it.write(bytes)} ?: error("Destination unavailable")}
                        BackupOperation.PROJECT_READ->{request.put("backup",ProjectBackup.encode(backup.read(this@BackupService,requireNotNull(uri))));request.put("status","REVIEW")}
                        BackupOperation.PROJECT_RESTORE->backup.restore(ProjectBackup.decode(request.getString("backup")),id)
                    }
                    if(request.getString("status")!="REVIEW") request.put("status","COMPLETE")
                    BackupController.write(this@BackupService,request)
                    TaskRecovery.automatic(this@BackupService,"backup",false)
                    if(operation==BackupOperation.ARCHIVE_RESTORE) {
                        java.io.File(filesDir,request.getString("source")).delete();ArchiveRestoreJournal(this@BackupService,id).use {it.delete()}
                    }
                    if(request.getString("status")=="REVIEW") BackupController.loadReview(this@BackupService,request)
                    else {
                        BackupController.update(BackupController.state.value.copy(archive=null,backup=null))
                        message=strings.getString(if(operation in listOf(BackupOperation.PROJECT_RESTORE,BackupOperation.ARCHIVE_RESTORE)) R.string.backup_restored else R.string.backup_saved)
                    }
                }
            } catch(e:CancellationException) {
                withContext(NonCancellable+Dispatchers.IO) {task?.let {if(paused) it.put("status","PAUSED");BackupController.write(this@BackupService,it)}}
                throw e
            } catch(e:Exception) {
                TaskRecovery.automatic(this@BackupService,"backup",false)
                withContext(Dispatchers.IO) {task?.let {it.put("status","FAILED");BackupController.write(this@BackupService,it)}}
                message=e.userMessage(strings,R.string.backup_failed)
            } catch(e:OutOfMemoryError) {
                TaskRecovery.automatic(this@BackupService,"backup",false)
                withContext(Dispatchers.IO) {task?.let {it.put("status","FAILED");BackupController.write(this@BackupService,it)}}
                message=strings.getString(R.string.image_memory_limit)
            } finally {
                val status=task?.optString("status")
                BackupController.update(BackupController.state.value.copy(running=false,interrupted=status in listOf("RUNNING","PAUSED","FAILED"),event=BackupController.state.value.event+1,message=message))
                stopForeground(STOP_FOREGROUND_REMOVE);job=null;wake.release();stopSelf()
            }
        }
        return START_STICKY
    }
    override fun onTimeout(startId:Int,fgsType:Int) {job?.cancel();stopSelf()}
    override fun onDestroy() {scope.cancel();wake.release();super.onDestroy()}
    companion object {const val PAUSE="de.luma.gallery.PAUSE_BACKUP"}
}
