package de.luma.gallery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import de.luma.gallery.data.*
import de.luma.gallery.localization.localizedResources
import de.luma.gallery.localization.userMessage
import kotlinx.coroutines.*


class ExportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val wake by lazy { TaskWakeLock(this,"export") }
    private var cancelRequested = false
    private val strings get() = localizedResources(this)
    private val manager get() = getSystemService(NotificationManager::class.java)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); manager.createNotificationChannel(NotificationChannel(CHANNEL, strings.getString(R.string.export_channel), NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(progress: Int? = null, completed: Int = 0, total: Int = 1) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle(strings.getString(R.string.export_notification_title))
        .setContentText(strings.getString(R.string.export_count, completed, total))
        .setContentIntent(PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setOngoing(true).setOnlyAlertOnce(true).setProgress(100, progress ?: 0, progress == null)
        .addAction(0, strings.getString(R.string.cancel), PendingIntent.getService(this, 2, Intent(this, ExportService::class.java).setAction(CANCEL), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) { TaskRecovery.automatic(this,"export",false); cancelRequested = true; job?.cancel(); if (job == null) stopSelf(); return START_NOT_STICKY }
        if (job != null) return START_STICKY
        if(intent==null && !TaskRecovery.eligible(this,"export")) {stopSelf();return START_NOT_STICKY}
        val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        try { ServiceCompat.startForeground(this, NOTICE, notification(), type) } catch (error: Exception) {
            val old = ExportController.state.value
            ExportController.update(old.copy(running = false, interrupted = true, event = old.event + 1,
                message = error.userMessage(strings, R.string.export_failed)))
            stopSelf(); return START_NOT_STICKY
        }
        wake.acquire()
        ExportController.update(ExportController.state.value.copy(running=true,interrupted=false))
        cancelRequested = false
        job = scope.launch {
            var request: ExportRequest? = null
            var success = false
            var canceled = false
            var fileReplaced=false
            var message: String? = null
            try {
                val task = requireNotNull(ExportController.read(this@ExportService))
                request = task
                val repository = GalleryRepository(this@ExportService)
                for (index in task.completed until task.outputCount) {
                    ensureActive()
                    val current = task.items[index]
                    ExportController.update(ExportController.state.value.copy(running = true, completed = index, total = task.outputCount, percent = null, interrupted = false))
                    manager.notify(NOTICE, notification(null, index, task.outputCount))
                    val outputName = task.outputName(index)
                    if(task.overwrite) SafeReplacement(this@ExportService).replace(task,progress={percent->ExportController.update(ExportController.state.value.copy(percent=percent))},onCommitted={fileReplaced=true})
                    else if(task.collage!=null) repository.exportCollage(task.items,task.collage,task.options,outputName)
                    else if (task.photo != null) repository.exportPhoto(current, task.photo, task.options, outputName)
                    else {
                        val recipe = requireNotNull(task.video)
                        if (repository.exportExists(true, "$outputName.mp4") == null) {
                            val file = VideoExporter(this@ExportService).export(current, recipe) { percent ->
                                val old = ExportController.state.value
                                if (old.percent != percent) {
                                    ExportController.update(old.copy(percent = percent)); manager.notify(NOTICE, notification(percent, index, task.outputCount))
                                }
                            }
                            try { repository.saveVideoFile(file, outputName) } finally { file.delete() }
                        }
                    }
                    val finished = task.copy(completed = index + 1)
                    request = finished
                    ExportController.write(this@ExportService, finished)
                    if(task.overwrite) SafeReplacement(this@ExportService).cleanup(task.id)
                }
                success = true
                message = strings.getString(R.string.saved_files, task.outputCount)
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    val task=request
                    if(task?.overwrite==true && SafeReplacement(this@ExportService).hasCommitted(task.id)) {
                        try {
                            val finished=task.copy(completed=task.outputCount)
                            ExportController.write(this@ExportService,finished);request=finished;success=true
                            message=strings.getString(R.string.saved_files,task.outputCount)
                        } catch(e:Exception) {message=e.userMessage(strings,R.string.export_failed)}
                    } else {canceled=cancelRequested;message=strings.getString(if(canceled) R.string.export_canceled else R.string.export_interrupted)}
                }
                if(!success) throw error
            }
            catch (error: Exception) { TaskRecovery.automatic(this@ExportService,"export",false); message = error.userMessage(strings, R.string.export_failed) }
            catch (error: OutOfMemoryError) { TaskRecovery.automatic(this@ExportService,"export",false); message = strings.getString(R.string.image_memory_limit) }
            finally {
                if (success || canceled) {TaskRecovery.automatic(this@ExportService,"export",false);ExportController.remove(this@ExportService);request?.takeIf {it.overwrite}?.let {SafeReplacement(this@ExportService).cleanup(it.id)}}
                val old = ExportController.state.value
                ExportController.update(old.copy(running = false, percent = null, interrupted = !success && !canceled,
                    completed = request?.completed ?: old.completed, event = old.event + 1, message = message,fileReplaced=fileReplaced))
                stopForeground(STOP_FOREGROUND_REMOVE); job = null; stopSelf()
            }
        }
        return START_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) { job?.cancel(); stopSelf() }
    override fun onDestroy() { scope.cancel(); wake.release(); super.onDestroy() }
    companion object { const val START = "de.luma.gallery.EXPORT"; const val CANCEL = "de.luma.gallery.CANCEL_EXPORT"; private const val CHANNEL = "luma-exports"; private const val NOTICE = 2001 }
}
