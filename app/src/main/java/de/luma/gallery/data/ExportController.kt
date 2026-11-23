package de.luma.gallery.data

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import de.luma.gallery.ExportService
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File


data class ExportSnapshot(val running: Boolean = false, val completed: Int = 0, val total: Int = 0,
    val percent: Int? = null, val interrupted: Boolean = false, val event: Long = 0, val message: String? = null, val overwriting:Boolean=false,val overwritingSourceId:String?=null,val fileReplaced:Boolean=false)
object ExportController {
    private val mutable = MutableStateFlow(ExportSnapshot())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    internal fun update(value: ExportSnapshot) { mutable.value = value }
    private fun journal(context: Context) = AtomicFile(File(context.filesDir, "luma-export.json"))
    internal suspend fun write(context: Context, request: ExportRequest) = withContext(Dispatchers.IO) {
        val file = journal(context); var stream: java.io.FileOutputStream? = null
        try { stream = file.startWrite(); stream.write(request.json().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Throwable) { stream?.let(file::failWrite); throw error }
    }
    internal suspend fun read(context: Context): ExportRequest? = withContext(Dispatchers.IO) {
        val file = journal(context)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) null else file.openRead().use { stream ->
            requireMessage(file.baseFile.length() <= 4_000_000, R.string.invalid_export_request)
            ExportRequest.read(stream.readBytes().toString(Charsets.UTF_8))
        }
    }
    internal fun remove(context: Context) { journal(context).delete() }
    suspend fun inspect(context: Context) = mutex.withLock {
        if (state.value.running) return@withLock
        try {
            read(context)?.let { request -> update(state.value.copy(interrupted = true, completed = request.completed, total = request.outputCount)) }
            withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.filter { (it.name.startsWith("luma_export_") && it.extension == "mp4") || it.name.startsWith("luma_tile_") }?.forEach { it.delete() } }
        } catch (error: Exception) { update(state.value.copy(interrupted = true)); throw error }
    }
    suspend fun start(context: Context, request: ExportRequest) = mutex.withLock {
        requireMessage(!state.value.running && !state.value.interrupted, R.string.export_already_pending)
        write(context, request)
        launch(context, request)
    }
    suspend fun resume(context: Context) = mutex.withLock {
        requireMessage(!state.value.running, R.string.export_already_pending)
        val request = requireNotNull(read(context))
        launch(context, request)
    }
    private fun launch(context: Context, request: ExportRequest) {
        TaskRecovery.automatic(context,"export",true)
        update(state.value.copy(running = true, interrupted = false, percent = null, completed = request.completed, total = request.outputCount,overwriting=request.overwrite,overwritingSourceId=if(request.overwrite) request.items.single().id else null,fileReplaced=false))
        try { ContextCompat.startForegroundService(context, Intent(context, ExportService::class.java).setAction(ExportService.START)) }
        catch (error: Throwable) { update(state.value.copy(running = false, interrupted = true)); throw error }
    }
    suspend fun discard(context: Context) = mutex.withLock {
        requireMessage(!state.value.running, R.string.export_already_pending)
        read(context)?.let {task->if(task.overwrite) {SafeReplacement(context).recover(task.id);SafeReplacement(context).cleanup(task.id)}}
        TaskRecovery.automatic(context,"export",false)
        remove(context); update(state.value.copy(interrupted = false, completed = 0, total = 0))
    }
    fun cancel(context: Context) {
        TaskRecovery.automatic(context,"export",false)
        if (state.value.running) context.startService(Intent(context, ExportService::class.java).setAction(ExportService.CANCEL))
    }
}
