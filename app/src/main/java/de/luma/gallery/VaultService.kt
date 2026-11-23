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

class VaultService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job:Job?=null
    private var paused=false
    private val wake by lazy {TaskWakeLock(this,"vault")}
    private val strings get()=localizedResources(this)
    private fun notice():Notification {
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("gimbo-vault",strings.getString(R.string.vault_title),NotificationManager.IMPORTANCE_LOW))
        val state=VaultController.state.value
        return NotificationCompat.Builder(this,"gimbo-vault").setSmallIcon(R.drawable.ic_notification).setContentTitle(strings.getString(when(state.operation) {"DELETE"->R.string.vault_deleting;"EXPORT"->R.string.vault_restoring;else->R.string.vault_importing}))
            .setContentText(strings.getString(R.string.review_progress,state.done,state.total)).setProgress(state.total,state.done,state.total==0).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(this,60,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(0,strings.getString(R.string.background_pause),PendingIntent.getService(this,61,Intent(this,VaultService::class.java).setAction(PAUSE),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==PAUSE) {TaskRecovery.automatic(this,"vault",false);paused=true;job?.cancel();if(job==null) stopSelf();return START_NOT_STICKY}
        if(job!=null) return START_NOT_STICKY
        val master=VaultController.takeProcessingKey() ?: run {VaultController.update(VaultController.state.value.copy(running=false));stopSelf();return START_NOT_STICKY}
        try {ServiceCompat.startForeground(this,2016,notice(),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)}
        catch(e:Exception) {master.fill(0);VaultController.update(VaultController.state.value.copy(running=false,error=e.userMessage(strings,R.string.vault_failed)));stopSelf();return START_NOT_STICKY}
        paused=false;wake.acquire()
        job=scope.launch {
            val repository=VaultRepository(this@VaultService)
            var task:org.json.JSONObject?=null
            try {
                withContext(Dispatchers.IO) {
                    val request=requireNotNull(repository.pending(master));task=request
                    val media=request.getJSONArray("media")
                    val exporting=request.optString("operation")=="EXPORT"
                    val deleting=request.optString("operation")=="DELETE"
                    val vaultItems=if(exporting) repository.items(master).associateBy {it.id} else emptyMap()
                    for(index in request.getInt("done") until media.length()) {
                        ensureActive();val source=media.getJSONObject(index)
                        if(deleting) repository.removeId(source.getString("id"))
                        else if(exporting) repository.restore(master,vaultItems.getValue(source.getString("id")))
                        else repository.import(master,source.getString("id"),RecipeJson.readMedia(source.getJSONObject("source")))
                        request.put("done",index+1);repository.seal(master,"pending",request)
                        VaultController.update(VaultController.state.value.copy(done=index+1,total=media.length(),running=true))
                        getSystemService(NotificationManager::class.java).notify(2016,notice())
                    }
                    repository.clearPending();TaskRecovery.automatic(this@VaultService,"vault",false);VaultController.update(VaultController.state.value.copy(pending=false));VaultController.refresh(this@VaultService)
                }
            } catch(e:CancellationException) {
                withContext(NonCancellable+Dispatchers.IO) {task?.let {it.put("paused",paused);repository.seal(master,"pending",it)}}
                throw e
            } catch(e:Exception) {TaskRecovery.automatic(this@VaultService,"vault",false);VaultController.update(VaultController.state.value.copy(error=e.userMessage(strings,R.string.vault_failed)))}
            catch(e:OutOfMemoryError) {TaskRecovery.automatic(this@VaultService,"vault",false);VaultController.update(VaultController.state.value.copy(error=strings.getString(R.string.image_memory_limit)))}
            finally {master.fill(0);VaultController.update(VaultController.state.value.copy(running=false));wake.release();stopForeground(STOP_FOREGROUND_REMOVE);job=null;stopSelf()}
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId:Int,fgsType:Int) {job?.cancel();stopSelf()}
    override fun onDestroy() {scope.cancel();wake.release();super.onDestroy()}
    companion object {const val PAUSE="de.luma.gallery.PAUSE_VAULT"}
}
