package de.luma.gallery.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.room.withTransaction
import de.luma.gallery.model.GalleryMedia
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ProjectSourceProtection(private val context:Context,private val database:ProjectDatabase=ProjectDatabase.get(context)) {
    private val folder get()=File(context.filesDir,"luma-project-sources")
    private fun uri(file:File)=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.project-sources",file).toString()
    private val prefs get()=context.getSharedPreferences("luma",0)
    private fun redirect(value:Any?,from:String,to:String,id:String):Boolean {
        var changed=false
        when(value) {
            is JSONObject -> value.keys().asSequence().toList().forEach {key->
                val child=value.opt(key)
                if(key in listOf("uri","image") && child==from) {
                    value.put(key,to)
                    if(key=="uri" && value.has("video") && value.has("id")) {value.put("id",id);value.put("demo",false)}
                    changed=true
                } else if(redirect(child,from,to,id)) changed=true
            }
            is JSONArray -> (0 until value.length()).forEach {if(redirect(value.opt(it),from,to,id)) changed=true}
        }
        return changed
    }
    private fun hash(file:File):String {
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input->val bytes=ByteArray(65536);while(true) {val n=input.read(bytes);if(n<0) break;digest.update(bytes,0,n)}}
        return digest.digest().joinToString("") {"%02x".format(it)}
    }
    suspend fun protect(item:GalleryMedia,original:File,taskId:String)=withContext(Dispatchers.IO) {
        database.withTransaction {
            val backup=File(folder,"$taskId.${original.extension}")
            val target=uri(backup);val id="protected:$taskId"
            val rows=database.projects().all()
            val changed=rows.mapNotNull {row->
                val recipe=JSONObject(row.recipeJson)
                val embedded=redirect(recipe,item.uri.toString(),target,id)
                if(row.sourceUri!=item.uri.toString() && !embedded) null else row.copy(
                    sourceId=if(row.sourceUri==item.uri.toString()) id else row.sourceId,
                    sourceUri=if(row.sourceUri==item.uri.toString()) target else row.sourceUri,
                    isDemo=if(row.sourceUri==item.uri.toString()) false else row.isDemo,
                    recipeJson=recipe.toString())
            }
            val drafts=prefs.all.entries.filter {it.key.startsWith("videoRecipe:") && it.key!="videoRecipe:${item.id}"}.mapNotNull {(key,value)->
                val json=runCatching {JSONObject(value as String)}.getOrNull() ?: return@mapNotNull null
                if(redirect(json,item.uri.toString(),target,id)) key to json.toString() else null
            }
            if(changed.isEmpty() && drafts.isEmpty()) return@withTransaction
            folder.mkdirs()
            if(!backup.exists()) {
                val atomic=AtomicFile(backup);val stream=atomic.startWrite()
                try {original.inputStream().use {it.copyTo(stream)};atomic.finishWrite(stream)} catch(e:Throwable) {atomic.failWrite(stream);throw e}
            }
            require(hash(backup)==hash(original))
            changed.forEach {check(database.projects().replace(it)==1)}
            if(drafts.isNotEmpty()) check(prefs.edit().also {editor->drafts.forEach {(key,value)->editor.putString(key,value)}}.commit())
        }
    }
    suspend fun cleanupUnused()=withContext(Dispatchers.IO) {
        database.withTransaction {
            val rows=database.projects().all()
            val references=rows.map {it.sourceUri}+rows.map {it.recipeJson}+prefs.all.filterKeys {it.startsWith("videoRecipe:")}.values.map {it.toString()}
            folder.listFiles()?.filter {it.isFile && it.name.matches(Regex("[a-f0-9-]{36}\\.(jpg|png|mp4)"))}?.forEach {file->
                val uri=uri(file)
                if(references.none {it==uri || it.contains(uri) || it.contains(uri.replace("/","\\/"))}) file.delete()
            }
        }
    }
}
