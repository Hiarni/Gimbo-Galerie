package de.luma.gallery.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

enum class DocumentAction { CREATE, RENAME, DELETE }
data class DocumentActionTask(val action:DocumentAction,val parent:Uri,val source:DocumentEntry?,val name:String)
class DocumentActionJournal(context:Context) {
    private val file=AtomicFile(File(context.filesDir,"gimbo-document-action.json"))
    fun read():DocumentActionTask? {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return null
        val json=file.openRead().use {JSONObject(it.readBytes().toString(Charsets.UTF_8))}
        val source=json.optJSONObject("source")?.let {DocumentEntry(Uri.parse(it.getString("uri")),it.getString("name"),it.getString("mime"),it.getInt("flags"))}
        return DocumentActionTask(DocumentAction.valueOf(json.getString("action")),Uri.parse(json.getString("parent")),source,json.getString("name"))
    }
    fun write(task:DocumentActionTask) {file.writeJson(JSONObject().put("action",task.action.name).put("parent",task.parent.toString()).put("name",task.name)
        .put("source",task.source?.let {JSONObject().put("uri",it.uri.toString()).put("name",it.name).put("mime",it.mime).put("flags",it.flags)}))}
    fun delete()=file.delete()
    suspend fun run(context:Context,task:DocumentActionTask) {
        val tools=DocumentTools(context)
        when(task.action) {
            DocumentAction.CREATE->{if(tools.children(task.parent).none {it.directory && it.name==task.name}) tools.createFolder(task.parent,task.name)}
            DocumentAction.RENAME->{val source=requireNotNull(task.source);val current=tools.find(source.uri)
                if(current!=null && current.name!=task.name) {
                    require(current.name==source.name && current.mime==source.mime);tools.rename(task.parent,current,task.name)
                } else if(current==null) require(tools.children(task.parent).any {it.name==task.name && it.mime==source.mime})
            }
            DocumentAction.DELETE->{val source=requireNotNull(task.source);val current=tools.find(source.uri)
                if(current!=null) {require(current.name==source.name && current.mime==source.mime);tools.delete(task.parent,current)}
            }
        }
        delete()
    }
}
