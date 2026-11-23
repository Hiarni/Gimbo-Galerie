package de.luma.gallery

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import de.luma.gallery.data.*
import de.luma.gallery.localization.localizedResources
import de.luma.gallery.localization.userMessage
import kotlinx.coroutines.*


class TransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val wake by lazy { TaskWakeLock(this,"transfer") }
    private val strings get() = localizedResources(this)
    private val manager get() = getSystemService(NotificationManager::class.java)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); manager.createNotificationChannel(NotificationChannel(CHANNEL, strings.getString(R.string.transfer_channel), NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(progress: DocumentTransferProgress? = null): Notification {
        val text = progress?.let { strings.getString(R.string.transfer_notice, it.completed, it.total, it.name) } ?: strings.getString(R.string.folder_scanning)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(R.string.transfer_channel)).setContentText(text).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setProgress(progress?.total ?: 0, progress?.completed ?: 0, progress == null || progress.total == 0)
            .addAction(0, strings.getString(R.string.transfer_pause), PendingIntent.getService(this, 11, Intent(this, TransferService::class.java).setAction(CANCEL), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) { TaskRecovery.automatic(this,"transfer",false); job?.cancel(); if (job == null) stopSelf(); return START_NOT_STICKY }
        if (job != null) return START_STICKY
        if(intent==null && !TaskRecovery.eligible(this,"transfer")) {stopSelf();return START_NOT_STICKY}
        try { ServiceCompat.startForeground(this, NOTICE, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }
        catch (e: Exception) { finish(false, e.userMessage(strings, R.string.file_action_failed)); stopSelf(); return START_NOT_STICKY }
        wake.acquire()
        TransferController.update(TransferController.state.value.copy(running=true,interrupted=false))
        job = scope.launch {
            var success = false
            var message = strings.getString(R.string.transfer_interrupted)
            try {
                val progress: suspend (DocumentTransferProgress) -> Unit = { p ->
                    TransferController.update(TransferController.state.value.copy(running = true, progress = p))
                    withContext(Dispatchers.Main) { manager.notify(NOTICE, notification(p)) }
                }
                val selected = withContext(Dispatchers.IO) { SelectionJournal(this@TransferService).read() }
                val document=withContext(Dispatchers.IO) {DocumentActionJournal(this@TransferService).read()}
                if(document!=null) DocumentActionJournal(this@TransferService).run(this@TransferService,document)
                else if (selected != null) {
                    val sources = SelectionTransfer(this@TransferService).run(selected, progress)
                    TransferController.update(TransferController.state.value.copy(moveSources=sources))
                } else {
                    val journal = TransferJournal(this@TransferService)
                    val task = withContext(Dispatchers.IO) { requireNotNull(journal.read()) }
                    ResumableTransfer(this@TransferService) { checkpoint -> withContext(Dispatchers.IO) { journal.write(checkpoint) } }.run(task, progress)
                    withContext(Dispatchers.IO) { journal.delete() }
                }
                success = true; message = strings.getString(R.string.file_action_done)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { TaskRecovery.automatic(this@TransferService,"transfer",false); message = e.userMessage(strings, R.string.file_action_failed) }
            catch (e:OutOfMemoryError) {TaskRecovery.automatic(this@TransferService,"transfer",false);message=strings.getString(R.string.image_memory_limit)}
            finally { finish(success, message); stopForeground(STOP_FOREGROUND_REMOVE); job = null; stopSelf() }
        }
        return START_STICKY
    }
    private fun finish(success: Boolean, message: String) {
        if(success) TaskRecovery.automatic(this,"transfer",false)
        val old = TransferController.state.value
        TransferController.update(old.copy(running = false, interrupted = !success, message = message, event = old.event + 1))
    }
    override fun onTimeout(startId: Int, fgsType: Int) { job?.cancel(); stopSelf() }
    override fun onDestroy() { scope.cancel(); wake.release(); super.onDestroy() }
    companion object { const val START = "de.luma.gallery.TRANSFER"; const val CANCEL = "de.luma.gallery.PAUSE_TRANSFER"; private const val CHANNEL = "luma-transfers"; private const val NOTICE = 2002 }
}
