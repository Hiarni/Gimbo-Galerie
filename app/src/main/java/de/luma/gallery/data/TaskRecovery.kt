package de.luma.gallery.data

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object TaskRecovery {
    private val mutex=Mutex()
    private fun preferences(context:Context)=context.getSharedPreferences("gimbo-task-recovery",Context.MODE_PRIVATE)
    internal fun automatic(context:Context,task:String,value:Boolean) {check(preferences(context).edit().putBoolean(task,value).commit())}
    internal fun eligible(context:Context,task:String)=preferences(context).getBoolean(task,false)
    suspend fun recover(context:Context)=mutex.withLock {
        runCatching {
            ExportController.inspect(context)
            if(eligible(context,"export") && !ExportController.state.value.running && ExportController.read(context)!=null) ExportController.resume(context)
        }.onFailure {if(it is kotlinx.coroutines.CancellationException) throw it}
        runCatching {
            TransferController.inspect(context)
            if(eligible(context,"transfer") && !TransferController.state.value.running && TransferController.state.value.interrupted) TransferController.resume(context)
        }.onFailure {if(it is kotlinx.coroutines.CancellationException) throw it}
        runCatching {BackupController.recover(context)}.onFailure {if(it is kotlinx.coroutines.CancellationException) throw it}
    }
}

internal class TaskWakeLock(context:Context,name:String) {
    private val lock=context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GimboGalerie:$name").apply {setReferenceCounted(false)}
    fun acquire() {lock.acquire(6*60*60*1000L)}
    fun release() {if(lock.isHeld) lock.release()}
}
