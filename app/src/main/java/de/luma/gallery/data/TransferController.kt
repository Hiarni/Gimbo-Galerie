package de.luma.gallery.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import de.luma.gallery.TransferService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TransferSnapshot(val running: Boolean = false, val interrupted: Boolean = false,
    val progress: DocumentTransferProgress? = null, val moveSources: List<de.luma.gallery.model.GalleryMedia> = emptyList(), val message: String? = null, val event: Long = 0)
object TransferController {
    private val mutable = MutableStateFlow(TransferSnapshot())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    internal fun update(value: TransferSnapshot) { mutable.value = value }
    suspend fun inspect(context: Context) = mutex.withLock {
        if (!state.value.running) withContext(Dispatchers.IO) {
            try { update(state.value.copy(interrupted = TransferJournal(context).read() != null || SelectionJournal(context).read() != null || DocumentActionJournal(context).read()!=null)) }
            catch (e: Exception) { update(state.value.copy(interrupted = true)); throw e }
        }
    }
    suspend fun startDocumentAction(context:Context,action:DocumentAction,parent:Uri,source:DocumentEntry?=null,name:String="")=mutex.withLock {
        check(!state.value.running && !state.value.interrupted && state.value.moveSources.isEmpty())
        withContext(Dispatchers.IO) {DocumentActionJournal(context).write(DocumentActionTask(action,parent,source,if(action==DocumentAction.DELETE) "" else documentName(name)))}
        launch(context)
    }
    suspend fun start(context: Context, source: Uri, target: Uri, move: Boolean, sourceParent: Uri? = null) = mutex.withLock {
        check(!state.value.running && !state.value.interrupted)
        withContext(Dispatchers.IO) { TransferJournal(context).write(TransferTask(source.toString(), target.toString(), move, sourceParent = sourceParent?.toString())) }
        launch(context)
    }
    suspend fun startSelection(context: Context, media: List<de.luma.gallery.model.GalleryMedia>, target: Uri, move: Boolean) = mutex.withLock {
        check(!state.value.running && !state.value.interrupted)
        require(media.size in 1..if(move) de.luma.gallery.model.MEDIA_ACTION_LIMIT else 5000)
        context.contentResolver.takePersistableUriPermission(target, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        withContext(Dispatchers.IO) { SelectionJournal(context).write(SelectionTask(target.toString(),move,media.map { SelectionCopy(it) })) }
        launch(context)
    }
    suspend fun verifyMove(context: Context) = SelectionTransfer(context).verifyMove()
    suspend fun finishMove(context: Context) = mutex.withLock {
        withContext(Dispatchers.IO) { SelectionJournal(context).delete() }
        update(state.value.copy(moveSources=emptyList(),interrupted=false))
    }
    suspend fun resume(context: Context) = mutex.withLock { check(!state.value.running); launch(context) }
    private fun launch(context: Context) {
        TaskRecovery.automatic(context,"transfer",true)
        update(state.value.copy(running = true, interrupted = false, message = null))
        try { ContextCompat.startForegroundService(context, Intent(context, TransferService::class.java).setAction(TransferService.START)) }
        catch (e: Throwable) { update(state.value.copy(running = false, interrupted = true)); throw e }
    }
    suspend fun discard(context: Context) = mutex.withLock {
        check(!state.value.running)
        TaskRecovery.automatic(context,"transfer",false)
        withContext(Dispatchers.IO) { TransferJournal(context).delete(); SelectionJournal(context).delete();DocumentActionJournal(context).delete() }
        update(state.value.copy(interrupted = false, progress = null, moveSources = emptyList()))
    }
    fun cancel(context: Context) { TaskRecovery.automatic(context,"transfer",false); if (state.value.running) context.startService(Intent(context, TransferService::class.java).setAction(TransferService.CANCEL)) }
}
