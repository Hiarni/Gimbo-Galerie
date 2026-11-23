package de.luma.gallery.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import de.luma.gallery.VaultService
import de.luma.gallery.model.GalleryMedia
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class VaultSnapshot(val unlocked:Boolean=false,val items:List<VaultItem> = emptyList(),val running:Boolean=false,
    val pending:Boolean=false,val operation:String="IMPORT",val done:Int=0,val total:Int=0,val error:String?=null)
object VaultController {
    private val monitor=Any()
    private val taskMutex=Mutex()
    private var key:ByteArray?=null
    private var generation=0L
    private var processingKey:ByteArray?=null
    internal fun takeProcessingKey():ByteArray?=synchronized(monitor) {processingKey.also {processingKey=null}}
    private val mutable=MutableStateFlow(VaultSnapshot())
    val state=mutable.asStateFlow()
    internal fun update(value:VaultSnapshot) {mutable.value=value}
    internal fun keyCopy():ByteArray=synchronized(monitor) {requireNotNull(key).copyOf()}
    suspend fun unlock(context:Context)=withContext(Dispatchers.IO) {
        val version=synchronized(monitor) {generation}
        val repository=VaultRepository(context)
        val master=repository.unlock()
        var resumeKey:ByteArray?=null
        try {
            val items=repository.items(master);val pending=repository.pending(master)
            ensureActive()
            synchronized(monitor) {
                if(generation!=version) throw CancellationException("Vault locked")
                if(pending!=null && !pending.optBoolean("paused",false) && TaskRecovery.eligible(context,"vault") && !state.value.running) resumeKey=master.copyOf()
                key?.fill(0);key=master;generation++
                update(state.value.copy(unlocked=true,items=items,pending=pending!=null,operation=pending?.optString("operation","IMPORT")?:state.value.operation,
                    done=pending?.optInt("done",0)?:state.value.done,total=pending?.optJSONArray("media")?.length()?:state.value.total,error=null))
            }
        } catch(e:Throwable) {master.fill(0);resumeKey?.fill(0);throw e}
        resumeKey?.let {taskKey->
            try {
                taskMutex.withLock {
                    if(!state.value.running) runCatching {launch(context,taskKey)}.onFailure {
                        update(state.value.copy(error=de.luma.gallery.localization.localizedResources(context).getString(de.luma.gallery.R.string.vault_failed)))
                    }
                }
            } finally {taskKey.fill(0)}
        }
    }
    fun lock(context:Context) {
        synchronized(monitor) {generation++;key?.fill(0);key=null;update(state.value.copy(unlocked=false,items=emptyList(),error=null))}
        VaultRepository(context).clearPreviews()
    }
    suspend fun refresh(context:Context)=withContext(Dispatchers.IO) {
        val master=runCatching {keyCopy()}.getOrNull() ?: return@withContext
        val version=synchronized(monitor) {generation}
        try {
            val repository=VaultRepository(context);val items=repository.items(master);val pending=repository.pending(master)!=null
            synchronized(monitor) {if(version==generation && key!=null) update(state.value.copy(items=items,pending=pending,error=null))}
        } finally {master.fill(0)}
    }
    suspend fun start(context:Context,media:List<GalleryMedia>)=taskMutex.withLock {withContext(Dispatchers.IO) {
        require(media.isNotEmpty() && media.size<=5000 && !state.value.running && !state.value.pending)
        val master=keyCopy()
        try {
            val sources=media.distinctBy {it.uri};val task=JSONObject().put("operation","IMPORT").put("done",0).put("paused",false).put("media",JSONArray(sources.map {JSONObject().put("id",UUID.randomUUID().toString()).put("source",RecipeJson.media(it))}))
            VaultRepository(context).seal(master,"pending",task)
            update(state.value.copy(pending=true,operation="IMPORT",done=0,total=sources.size))
            launch(context,master)
        } finally {master.fill(0)}
    }}
    suspend fun export(context:Context,items:List<VaultItem>)=taskMutex.withLock {withContext(Dispatchers.IO) {
        require(items.isNotEmpty() && !state.value.running && !state.value.pending)
        val master=keyCopy()
        try {
            val task=JSONObject().put("operation","EXPORT").put("done",0).put("paused",false).put("media",JSONArray(items.map {JSONObject().put("id",it.id)}))
            VaultRepository(context).seal(master,"pending",task);update(state.value.copy(pending=true,operation="EXPORT",done=0,total=items.size));launch(context,master)
        } finally {master.fill(0)}
    }}
    internal fun sessionCheck():()->Unit {
        val version=synchronized(monitor) {require(key!=null);generation}
        return {synchronized(monitor) {check(key!=null && generation==version)}}
    }
    suspend fun bitmap(context:Context,item:VaultItem,maxSide:Int=384):android.graphics.Bitmap?=withContext(Dispatchers.IO) {
        val master=keyCopy()
        try {
            VaultRepository(context).dataSource(master,item,sessionCheck()).use {source->
                if(item.media.isVideo) {
                    android.media.MediaMetadataRetriever().use {retriever->retriever.setDataSource(source);retriever.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,maxSide,maxSide)}
                } else {
                    class Stream:java.io.InputStream() {
                        var position=0L
                        override fun read():Int {val one=ByteArray(1);return if(read(one)==-1) -1 else one[0].toInt() and 255}
                        override fun read(b:ByteArray,off:Int,len:Int):Int=source.readAt(position,b,off,len).also {if(it>0) position+=it}
                    }
                    val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
                    android.graphics.BitmapFactory.decodeStream(Stream(),null,bounds)
                    var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxSide*2) sample*=2
                    val image=android.graphics.BitmapFactory.decodeStream(Stream(),null,android.graphics.BitmapFactory.Options().apply {inSampleSize=sample})
                    if(image!=null) {
                        val exif=runCatching {androidx.exifinterface.media.ExifInterface(Stream())}.getOrNull()
                        if(exif!=null && (exif.rotationDegrees!=0 || exif.isFlipped)) {
                            val matrix=android.graphics.Matrix().apply {if(exif.isFlipped) postScale(-1f,1f);postRotate(exif.rotationDegrees.toFloat())}
                            android.graphics.Bitmap.createBitmap(image,0,0,image.width,image.height,matrix,true).also {if(it!==image) image.recycle()}
                        } else image
                    } else null
                }
            }
        } finally {master.fill(0)}
    }
    suspend fun resume(context:Context)=taskMutex.withLock {withContext(Dispatchers.IO) {
        require(!state.value.running)
        val master=keyCopy()
        try {val repo=VaultRepository(context);val task=requireNotNull(repo.pending(master));task.put("paused",false);repo.seal(master,"pending",task);launch(context,master)} finally {master.fill(0)}
    }}
    private fun launch(context:Context,master:ByteArray) {
        TaskRecovery.automatic(context,"vault",true)
        synchronized(monitor) {processingKey?.fill(0);processingKey=master.copyOf()}
        update(state.value.copy(running=true,error=null))
        try {ContextCompat.startForegroundService(context,Intent(context,VaultService::class.java))}
        catch(e:Exception) {TaskRecovery.automatic(context,"vault",false);synchronized(monitor) {processingKey?.fill(0);processingKey=null};update(state.value.copy(running=false));throw e}
    }
    fun pause(context:Context) {TaskRecovery.automatic(context,"vault",false);if(state.value.running) context.startService(Intent(context,VaultService::class.java).setAction(VaultService.PAUSE))}
    suspend fun preview(context:Context,item:VaultItem):File=withContext(Dispatchers.IO) {
        val master=keyCopy();val version=synchronized(monitor) {generation}
        val folder=File(context.cacheDir,"gimbo-vault-preview").apply {mkdirs()}
        val extension=if(item.media.isVideo) "mp4" else "image"
        val target=File(folder,"${item.id}.$extension")
        fun checkSession() {synchronized(monitor) {check(key!=null && generation==version)}}
        try {
            if(!target.isFile) VaultRepository(context).decrypt(master,item,target,::checkSession)
            checkSession();target
        } catch(e:Throwable) {target.delete();throw e} finally {master.fill(0)}
    }
    suspend fun verifyOriginals(context:Context,items:List<VaultItem>):List<GalleryMedia> = withContext(Dispatchers.IO) {
        val check=sessionCheck();val master=keyCopy()
        try {
            VaultRepository(context).verifyOriginals(master,items,check)
        } finally {master.fill(0)}
    }
    suspend fun remove(context:Context,items:List<VaultItem>)=taskMutex.withLock {withContext(Dispatchers.IO) {
        require(items.isNotEmpty() && !state.value.running && !state.value.pending)
        val master=keyCopy()
        try {
            val task=JSONObject().put("operation","DELETE").put("done",0).put("paused",false).put("media",JSONArray(items.map {JSONObject().put("id",it.id)}))
            VaultRepository(context).seal(master,"pending",task)
            update(state.value.copy(pending=true,operation="DELETE",done=0,total=items.size));launch(context,master)
        } finally {master.fill(0)}
    }}
}
