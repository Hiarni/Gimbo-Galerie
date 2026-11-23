package de.luma.gallery.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.os.Build
import android.os.Bundle
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import de.luma.gallery.R
import de.luma.gallery.model.canManageMedia
import de.luma.gallery.localization.requireMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class CloudRecord(val id:String,val source:String,val target:String,val name:String,val hash:String,val size:Long,val superseded:Boolean=false,val cloudName:String=name)
data class CloudStatus(val tree:String?=null,val automatic:Boolean=false,val wifi:Boolean=true,val charging:Boolean=false,
    val mirror:Boolean=true,val hours:Int=24,val photos:Boolean=true,val videos:Boolean=true,val lastSync:Long=0,
    val count:Int=0,val pending:Int=0,val message:String?=null,val destinationName:String?=null)
class CloudBackup(private val context:Context) {
    private val prefs=context.getSharedPreferences("luma-cloud",Context.MODE_PRIVATE)
    val states=callbackFlow {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->trySend(status()) }
        prefs.registerOnSharedPreferenceChangeListener(listener);trySend(status())
        awaitClose {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    fun status()=CloudStatus(prefs.getString("tree",null),prefs.getBoolean("automatic",false),prefs.getBoolean("wifi",true),prefs.getBoolean("charging",false),
        prefs.getBoolean("mirror",true),prefs.getInt("hours",24).coerceIn(1,24),prefs.getBoolean("photos",true),prefs.getBoolean("videos",true),prefs.getLong("last",0),
        prefs.getInt("count",0),prefs.getInt("pending",0),prefs.getString("message",null),prefs.getString("destination",null))
    fun save(value:CloudStatus) {
        require(value.hours in 1..24)
        prefs.edit {putBoolean("automatic",value.automatic);putBoolean("wifi",value.wifi);putBoolean("charging",value.charging);putBoolean("mirror",value.mirror)
            putInt("hours",value.hours);putBoolean("photos",value.photos);putBoolean("videos",value.videos)}
        CloudScheduler.configure(context,value)
    }
    suspend fun choose(tree:Uri)=mutex.withLock {
        requireMessage(!status().automatic,R.string.cloud_disable_first)
        context.contentResolver.takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val tools=DocumentTools(context);val root=tools.describe(tools.root(tree))
        requireMessage(root.canCreate,R.string.provider_action_unsupported)
        val old=prefs.getString("tree",null)
        val provider=context.packageManager.resolveContentProvider(tree.authority.orEmpty(),0)?.loadLabel(context.packageManager)?.toString().orEmpty()
        prefs.edit(commit=true) {putString("tree",tree.toString());putString("destination",listOf(provider,root.name).filter {it.isNotBlank()}.joinToString(" · "));remove("message")
            if(old!=tree.toString()) {remove("root");remove("ledger");putInt("count",0);putInt("pending",0);putLong("last",0)} }
    }
    internal fun records():List<CloudRecord> {
        val text=prefs.getString("ledger","[]")!!;require(text.length<=8*1024*1024)
        val a=JSONArray(text);require(a.length()<=5000)
        return (0 until a.length()).map { i->val o=a.getJSONObject(i)
            CloudRecord(o.getString("id"),o.getString("source"),o.getString("target"),o.getString("name"),o.getString("hash"),o.getLong("size"),o.optBoolean("old"),o.optString("cloudName",o.getString("name")))
        }
    }
    private fun write(records:List<CloudRecord>,pending:Int=0) {
        val a=JSONArray();records.forEach { n->a.put(JSONObject().put("id",n.id).put("source",n.source).put("target",n.target).put("name",n.name).put("hash",n.hash).put("size",n.size).put("old",n.superseded).put("cloudName",n.cloudName)) }
        requireMessage(prefs.edit().putString("ledger",a.toString()).putInt("count",records.count {!it.superseded}).putInt("pending",pending).commit(),R.string.storage_unavailable)
    }
    fun message(value:String) {prefs.edit {putString("message",value)}}
    private suspend fun root():Uri {
        val tree=Uri.parse(requireNotNull(status().tree));val tools=DocumentTools(context);val parent=tools.root(tree)
        val known=prefs.getString("root",null)?.let(Uri::parse)
        if(known!=null) {
            requireMessage(tools.children(parent).any {it.uri==known && it.directory},R.string.cloud_folder_changed)
            return known
        }
        val created=tools.createFolder(parent,"Gimbo-Galerie-Backup-${UUID.randomUUID().toString().take(8)}")
        requireMessage(prefs.edit().putString("root",created.toString()).commit(),R.string.storage_unavailable)
        return created
    }
    suspend fun synchronize(progress:suspend(Int,Int,String)->Unit={_,_,_->})=mutex.withLock {
        val config=status();requireMessage(config.tree!=null,R.string.cloud_choose_folder)
        val tools=DocumentTools(context);val folder=root();var records=records()
        val media=GalleryRepository(context).loadLibrary().filter {if(it.isVideo) config.videos else config.photos}
        requireMessage(media.size<=5000,R.string.cloud_limit)
        val currentTargets=tools.children(folder).associateBy {it.uri.toString()}
        media.forEachIndexed { i,m ->
            currentCoroutineContext().ensureActive();progress(i,media.size,m.name)
            val hash=tools.fingerprint(m.uri)
            val previous=records.lastOrNull {it.source==m.uri.toString()&&!it.superseded}
            if(previous!=null && previous.hash==hash.sha256 && previous.size==hash.size && previous.name==m.name && previous.target in currentTargets) {
                requireMessage(currentTargets[previous.target]?.name==previous.cloudName,R.string.cloud_folder_changed)
                requireMessage(tools.fingerprint(Uri.parse(previous.target))==hash,R.string.cloud_conflict)
            } else {
                requireMessage(records.size<5000,R.string.cloud_limit)
                if(previous!=null && previous.target in currentTargets)
                    requireMessage(tools.fingerprint(Uri.parse(previous.target))==DocumentTools.Fingerprint(previous.size,previous.hash),R.string.cloud_conflict)
                val mime=context.contentResolver.getType(m.uri).orEmpty();requireMessage(mime.startsWith("image/")||mime.startsWith("video/"),R.string.not_media_file)
                val copy=tools.copyVerified(DocumentEntry(m.uri,m.name.take(120),mime,0),folder)
                requireMessage(copy.fingerprint==hash && tools.fingerprint(m.uri)==hash,R.string.copy_source_changed)
                records=records.map {if(it.id==previous?.id) it.copy(superseded=true) else it}+CloudRecord(UUID.randomUUID().toString(),m.uri.toString(),copy.uri.toString(),m.name,hash.sha256,hash.size,cloudName=tools.describe(copy.uri).name)
                withContext(NonCancellable) {write(records)}
            }
        }
        val pending=if(config.mirror) candidates(records).size else 0
        write(records,pending);prefs.edit {putLong("last",System.currentTimeMillis());remove("message")};progress(media.size,media.size,"")
    }
    private fun fullAccess(video:Boolean):Boolean {
        val permission=if(Build.VERSION.SDK_INT>=33) {if(video) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES} else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED
    }
    private suspend fun candidates(records:List<CloudRecord>):List<CloudRecord> = withContext(Dispatchers.IO) {
        if(!status().mirror) return@withContext emptyList()
        records.filter { n ->
            if(n.superseded) true
            else if(Uri.parse(n.source).let {uri->
                val video=uri.pathSegments.getOrNull(1)=="video"
                canManageMedia(de.luma.gallery.model.GalleryMedia(n.source,uri,n.name,"",0,isVideo=video)) && fullAccess(video)
            }) {
                try {context.contentResolver.query(Uri.parse(n.source),arrayOf("_id"),Bundle(),null)?.use {!it.moveToFirst()}==true}
                catch(_:Exception) {false}
            } else false
        }
    }
    suspend fun deletionReview():List<Pair<String,String>> = mutex.withLock {candidates(records()).map {it.id to it.name}}
    suspend fun deleteConfirmed(ids:Set<String>)=mutex.withLock {
        val tools=DocumentTools(context);val folder=root();var records=records();val allowed=candidates(records).associateBy {it.id}
        val children=tools.children(folder).associateBy {it.uri.toString()}
        for(id in ids) {
            currentCoroutineContext().ensureActive();val n=allowed[id]?:continue
            requireMessage(children[n.target]?.name==n.cloudName,R.string.cloud_folder_changed)
            val target=Uri.parse(n.target)
            requireMessage(tools.fingerprint(target)==DocumentTools.Fingerprint(n.size,n.hash),R.string.cloud_conflict)
            withContext(NonCancellable+Dispatchers.IO) {
                requireMessage(DocumentsContract.deleteDocument(context.contentResolver,target),R.string.provider_action_unsupported)
                records=records.filter {it.id!=id};write(records)
            }
        }
        write(records, candidates(records).size)
    }
    companion object {private val mutex=Mutex()}
}
