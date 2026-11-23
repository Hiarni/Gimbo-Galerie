package de.luma.gallery.data

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.*
import de.luma.gallery.MainActivity
import de.luma.gallery.R
import de.luma.gallery.localization.localizedResources
import de.luma.gallery.localization.userMessage
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

object CloudScheduler {
    private const val PERIODIC="luma-cloud-periodic"
    private const val MANUAL="luma-cloud-manual"
    fun configure(context:Context,config:CloudStatus) {
        val manager=WorkManager.getInstance(context)
        if(!config.automatic || config.tree==null) {manager.cancelUniqueWork(PERIODIC);return}
        val constraints=Constraints.Builder().setRequiredNetworkType(if(config.wifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(config.charging).setRequiresBatteryNotLow(true).build()
        manager.enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CloudBackupWorker>(config.hours.toLong(),TimeUnit.HOURS).setConstraints(constraints).build())
    }
    fun now(context:Context) {WorkManager.getInstance(context).enqueueUniqueWork(MANUAL,ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<CloudBackupWorker>().build())}
    fun cancel(context:Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MANUAL)
        val repository=CloudBackup(context);repository.save(repository.status().copy(automatic=false))
    }
    fun observe(context:Context)=kotlinx.coroutines.flow.combine(WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(MANUAL),
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PERIODIC)) { a,b -> a+b }
    fun trash(context:Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork("luma-trash-reminder",ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<TrashReminderWorker>(24,TimeUnit.HOURS).build()) }
}
class CloudBackupWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    private val strings get()=localizedResources(applicationContext)
    private fun notice(done:Int=0,total:Int=0,name:String=""):ForegroundInfo {
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("luma-cloud",strings.getString(R.string.cloud_title),NotificationManager.IMPORTANCE_LOW))
        val pending=PendingIntent.getActivity(applicationContext,22,Intent(applicationContext,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=NotificationCompat.Builder(applicationContext,"luma-cloud").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(R.string.cloud_title)).setContentText(strings.getString(R.string.cloud_progress,done,total,name)).setContentIntent(pending)
            .setOngoing(true).setProgress(total,done,total==0).build()
        return ForegroundInfo(2003,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
    override suspend fun getForegroundInfo()=notice()
    override suspend fun doWork():Result {
        val repository=CloudBackup(applicationContext)
        if(repository.status().tree==null) return Result.failure()
        return try {
            setForeground(notice())
            repository.synchronize { done,total,name -> setForeground(notice(done,total,name));setProgress(workDataOf("done" to done,"total" to total)) }
            Result.success()
        } catch(e:CancellationException) {throw e}
        catch(e:Exception) {repository.message(e.userMessage(strings,R.string.cloud_failed));if(runAttemptCount<2) Result.retry() else Result.failure()}
    }
}
class TrashReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        if(android.os.Build.VERSION.SDK_INT<30) return Result.success()
        val context=applicationContext
        return try {
            val due=TrashRetention(context).due(TrashRepository(context).load().items)
            if(due.isNotEmpty()) {
                val strings=localizedResources(context);val manager=context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("luma-trash",strings.getString(R.string.trash),NotificationManager.IMPORTANCE_DEFAULT))
                val pending=PendingIntent.getActivity(context,23,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                try {manager.notify(2004,NotificationCompat.Builder(context,"luma-trash").setSmallIcon(R.drawable.ic_notification).setContentTitle(strings.getString(R.string.trash_due_title))
                    .setContentText(strings.getString(R.string.trash_due_hint,due.size)).setContentIntent(pending).setAutoCancel(true).build())}catch(_:SecurityException) { }
            };Result.success()
        } catch(e:CancellationException) {throw e} catch(_:Exception) {Result.retry()}
    }
}
