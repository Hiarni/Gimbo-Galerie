package de.luma.gallery.data

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.*
import de.luma.gallery.MainActivity
import de.luma.gallery.R
import de.luma.gallery.localization.localizedResources
import de.luma.gallery.localization.userMessage
import de.luma.gallery.model.GalleryMedia
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AnalysisController {
    private val mutex=Mutex()
    private val changes=MutableSharedFlow<Unit>(replay=1,extraBufferCapacity=1)
    internal fun changed() {changes.tryEmit(Unit)}
    private fun name(kind:AnalysisKind)="gimbo-analysis-${kind.name}"
    private fun enqueue(context:Context,task:AnalysisTask,policy:ExistingWorkPolicy) {
        val request=OneTimeWorkRequestBuilder<AnalysisWorker>().setInputData(workDataOf("kind" to task.kind.name,"task" to task.id))
            .setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(task.kind),policy,request)
    }
    suspend fun recover(context:Context)=mutex.withLock {withContext(Dispatchers.IO) {
        AnalysisStore(context).use {store->AnalysisKind.entries.forEach {kind->store.task(kind)?.takeIf {it.active}?.let {enqueue(context,it,ExistingWorkPolicy.KEEP)}}}
        changed()
    }}
    suspend fun start(context:Context,kind:AnalysisKind,media:List<GalleryMedia>,similar:Boolean=false,archived:Boolean=false)=mutex.withLock {withContext(Dispatchers.IO) {
        AnalysisStore(context).use {store->
            if(store.task(kind)?.active==true) return@use
            val sources=if(kind==AnalysisKind.MOODS) MoodAnalysis.sources(media) else media.filter {!it.isVideo}.distinctBy {it.uri}
            val task=store.create(kind,sources,similar,archived)
            enqueue(context,task,ExistingWorkPolicy.REPLACE)
        }
        changed()
    }}
    suspend fun resume(context:Context,kind:AnalysisKind)=mutex.withLock {withContext(Dispatchers.IO) {
        AnalysisStore(context).use {store->store.task(kind)?.takeIf {!it.active && it.status!=AnalysisStatus.COMPLETE}?.let {task->
            if(store.transition(task,AnalysisStatus.QUEUED)) enqueue(context,task,ExistingWorkPolicy.REPLACE)
        }}
        changed()
    }}
    suspend fun pause(context:Context,kind:AnalysisKind)=mutex.withLock {withContext(Dispatchers.IO) {
        AnalysisStore(context).use {store->store.task(kind)?.takeIf {it.active}?.let {store.transition(it,AnalysisStatus.PAUSED)}}
        WorkManager.getInstance(context).cancelUniqueWork(name(kind)).result.get()
        changed()
    }}
    suspend fun reset(context:Context,kind:AnalysisKind)=mutex.withLock {withContext(Dispatchers.IO) {
        AnalysisStore(context).use {store->if(store.task(kind)?.active!=true) store.clear(kind)}
        changed()
    }}
    fun observe(context:Context,kind:AnalysisKind):Flow<AnalysisSnapshot> = flow {
        var previous:AnalysisTask?=null
        var cached=AnalysisSnapshot()
        combine(changes.onStart {emit(Unit)},WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(name(kind))) {_,_->Unit}
            .collect {
                val snapshot=withContext(Dispatchers.IO) {AnalysisStore(context).use {store->
                    val task=store.task(kind)
                    if(task==previous) cached else store.snapshot(kind)
                }}
                previous=snapshot.task;cached=snapshot;emit(snapshot)
            }
    }.catch {error->
        if(error is CancellationException) throw error
        val strings=localizedResources(context)
        val message=if(error is OutOfMemoryError) strings.getString(R.string.image_memory_limit) else error.userMessage(strings,R.string.background_task_failed)
        emit(AnalysisSnapshot(AnalysisTask("unavailable",kind,false,false,AnalysisStatus.FAILED,error=message)))
    }.distinctUntilChanged()
}

class AnalysisWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    private val kind get()=AnalysisKind.valueOf(requireNotNull(inputData.getString("kind")))
    private val strings get()=localizedResources(applicationContext)
    private fun notice(task:AnalysisTask?=null):ForegroundInfo {
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("gimbo-analysis",strings.getString(R.string.background_tasks),NotificationManager.IMPORTANCE_LOW))
        val intent=Intent(applicationContext,MainActivity::class.java).putExtra("gimbo.analysis",kind.name)
        val pending=PendingIntent.getActivity(applicationContext,30+kind.ordinal,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=NotificationCompat.Builder(applicationContext,"gimbo-analysis").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(if(kind==AnalysisKind.DUPLICATES) R.string.duplicates_title else R.string.moods_title))
            .setContentText(if(task==null) strings.getString(R.string.background_continues) else strings.getString(R.string.review_progress,task.done,task.total))
            .setContentIntent(pending).setOnlyAlertOnce(true).setOngoing(true).setProgress(task?.total?:0,task?.done?:0,task==null || task.total==0)
            .addAction(0,strings.getString(R.string.background_pause),PendingIntent.getBroadcast(applicationContext,40+kind.ordinal,Intent(applicationContext,de.luma.gallery.AnalysisActionReceiver::class.java).putExtra("kind",kind.name),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
        return ForegroundInfo(2010+kind.ordinal,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
    override suspend fun getForegroundInfo()=notice()
    override suspend fun doWork():Result = withContext(Dispatchers.IO) {
        AnalysisStore(applicationContext).use {store->
            val initial=store.task(kind) ?: return@withContext Result.success()
            if(initial.id!=inputData.getString("task") || !initial.active) return@withContext Result.success()
            store.transition(initial,AnalysisStatus.RUNNING)
            var task=store.task(kind) ?: return@withContext Result.success()
            AnalysisController.changed()
            val started=SystemClock.elapsedRealtime()
            var lastNotice=0L
            fun progress(comparing:Boolean,done:Int,total:Int) {
                if(SystemClock.elapsedRealtime()-started>SLICE) throw AnalysisYield()
                if(task.comparing && !comparing) return
                val completed=if(task.comparing==comparing) maxOf(task.done,done) else done
                store.progress(task,comparing,completed,total)
                task=task.copy(comparing=comparing,done=completed,total=total)
                if(SystemClock.elapsedRealtime()-lastNotice>=250 || done==total) {
                    AnalysisController.changed();lastNotice=SystemClock.elapsedRealtime()
                }
            }
            try {
                setForeground(notice(task))
                val noticeJob=launch {while(isActive) {delay(1500);setForeground(notice(store.task(kind)))}}
                try {
                    val sources=store.sources(task)
                    if(kind==AnalysisKind.DUPLICATES) {
                        val report=DuplicateScanner.scan(applicationContext,sources,task.similar,store.checkpoints(task),::progress)
                        ensureActive();store.complete(task,report)
                    } else {
                        sources.forEachIndexed {index,media->
                            ensureActive()
                            if(!store.checkpoint(task,"mood",media.uri.toString()).first) {
                                val photo=MoodAnalysis.analyze(applicationContext,media)
                                store.saveCheckpoint(task,"mood",media.uri.toString(),photo?.let {JSONObject().put("mood",it.mood.name).put("score",it.score)})
                            }
                            progress(false,index+1,sources.size)
                        }
                        ensureActive();store.complete(task)
                    }
                } finally {noticeJob.cancelAndJoin()}
                AnalysisController.changed();Result.success()
            } catch(e:AnalysisYield) {
                store.task(kind)?.takeIf {it.id==task.id && it.active}?.let {store.transition(it,AnalysisStatus.QUEUED)}
                AnalysisController.changed();Result.retry()
            } catch(e:CancellationException) {
                withContext(NonCancellable) {
                    store.task(kind)?.takeIf {it.id==task.id && it.active}?.let {store.transition(it,AnalysisStatus.QUEUED)}
                    AnalysisController.changed()
                }
                throw e
            } catch(e:OutOfMemoryError) {
                store.task(kind)?.takeIf {it.id==task.id && it.active}?.let {store.transition(it,AnalysisStatus.FAILED,strings.getString(R.string.image_memory_limit))}
                AnalysisController.changed();Result.failure()
            } catch(e:Exception) {
                if(android.os.Build.VERSION.SDK_INT>=31 && e is android.app.ForegroundServiceStartNotAllowedException) {
                    store.task(kind)?.takeIf {it.id==task.id && it.active}?.let {store.transition(it,AnalysisStatus.QUEUED)}
                    AnalysisController.changed();Result.retry()
                } else {
                    store.task(kind)?.takeIf {it.id==task.id && it.active}?.let {store.transition(it,AnalysisStatus.FAILED,e.userMessage(strings,R.string.background_task_failed))}
                    AnalysisController.changed();Result.failure()
                }
            }
        }
    }
    private class AnalysisYield:Exception()
    companion object {private const val SLICE=4*60*1000L}
}
