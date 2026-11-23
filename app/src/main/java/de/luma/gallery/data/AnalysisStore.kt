package de.luma.gallery.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.luma.gallery.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AnalysisKind { DUPLICATES, MOODS }
enum class AnalysisStatus { QUEUED, RUNNING, PAUSED, FAILED, COMPLETE }
data class AnalysisTask(val id:String,val kind:AnalysisKind,val similar:Boolean,val archived:Boolean,val status:AnalysisStatus,
    val comparing:Boolean=false,val done:Int=0,val total:Int=0,val error:String?=null) {
    val active get()=status==AnalysisStatus.QUEUED || status==AnalysisStatus.RUNNING
}
data class AnalysisSnapshot(val task:AnalysisTask?=null,val report:DuplicateReport?=null,val moods:List<MoodPhoto>?=null)

internal class AnalysisStore(context:Context):SQLiteOpenHelper(context.applicationContext,"gimbo-analysis.db",null,1) {
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE jobs (kind TEXT PRIMARY KEY, id TEXT NOT NULL, similar INTEGER NOT NULL, archived INTEGER NOT NULL, status TEXT NOT NULL, comparing INTEGER NOT NULL, done INTEGER NOT NULL, total INTEGER NOT NULL, error TEXT, result TEXT)")
        db.execSQL("CREATE TABLE sources (job TEXT NOT NULL, position INTEGER NOT NULL, media TEXT NOT NULL, PRIMARY KEY(job, position))")
        db.execSQL("CREATE TABLE checkpoints (job TEXT NOT NULL, phase TEXT NOT NULL, key TEXT NOT NULL, value TEXT, PRIMARY KEY(job, phase, key))")
        db.execSQL("CREATE TABLE groups_found (job TEXT NOT NULL, position INTEGER NOT NULL, id TEXT NOT NULL, kind TEXT NOT NULL, PRIMARY KEY(job, position))")
        db.execSQL("CREATE TABLE members_found (job TEXT NOT NULL, group_position INTEGER NOT NULL, position INTEGER NOT NULL, value TEXT NOT NULL, PRIMARY KEY(job, group_position, position))")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unsupported analysis database version")
    fun task(kind:AnalysisKind):AnalysisTask? = readableDatabase.query("jobs",null,"kind=?",arrayOf(kind.name),null,null,null).use { c ->
        if(!c.moveToFirst()) null else AnalysisTask(c.getString(c.getColumnIndexOrThrow("id")),kind,c.getInt(c.getColumnIndexOrThrow("similar"))!=0,
            c.getInt(c.getColumnIndexOrThrow("archived"))!=0,AnalysisStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))),
            c.getInt(c.getColumnIndexOrThrow("comparing"))!=0,c.getInt(c.getColumnIndexOrThrow("done")),c.getInt(c.getColumnIndexOrThrow("total")),
            c.getString(c.getColumnIndexOrThrow("error")))
    }
    private fun transaction(block:()->Unit) {val db=writableDatabase;db.beginTransaction();try {block();db.setTransactionSuccessful()} finally {db.endTransaction()}}
    fun create(kind:AnalysisKind,media:List<GalleryMedia>,similar:Boolean=false,archived:Boolean=false):AnalysisTask {
        val task=AnalysisTask(UUID.randomUUID().toString(),kind,similar,archived,AnalysisStatus.QUEUED,total=media.size)
        transaction {
            clearRows(kind)
            writableDatabase.insertOrThrow("jobs",null,ContentValues().apply {
                put("kind",kind.name);put("id",task.id);put("similar",if(similar) 1 else 0);put("archived",if(archived) 1 else 0)
                put("status",task.status.name);put("comparing",0);put("done",0);put("total",media.size)
            })
            media.forEachIndexed {index,item->writableDatabase.insertOrThrow("sources",null,ContentValues().apply {
                put("job",task.id);put("position",index);put("media",RecipeJson.media(item).toString())
            })}
        }
        return task
    }
    fun sources(task:AnalysisTask):List<GalleryMedia> = readableDatabase.query("sources",arrayOf("media"),"job=?",arrayOf(task.id),null,null,"position").use { c ->
        buildList {while(c.moveToNext()) add(RecipeJson.readMedia(JSONObject(c.getString(0))))}
    }
    fun clear(kind:AnalysisKind)=transaction {clearRows(kind)}
    private fun clearRows(kind:AnalysisKind) {
        task(kind)?.let {task->listOf("sources","checkpoints","groups_found","members_found").forEach {writableDatabase.delete(it,"job=?",arrayOf(task.id))}}
        writableDatabase.delete("jobs","kind=?",arrayOf(kind.name))
    }
    fun transition(task:AnalysisTask,status:AnalysisStatus,error:String?=null):Boolean = writableDatabase.update("jobs",ContentValues().apply {
        put("status",status.name);put("error",error)
    },"id=? AND status=?",arrayOf(task.id,task.status.name))==1
    fun progress(task:AnalysisTask,comparing:Boolean,done:Int,total:Int) {
        writableDatabase.update("jobs",ContentValues().apply {put("comparing",if(comparing) 1 else 0);put("done",done);put("total",total)},
            "id=? AND status IN (?,?)",arrayOf(task.id,AnalysisStatus.QUEUED.name,AnalysisStatus.RUNNING.name))
    }
    fun checkpoint(task:AnalysisTask,phase:String,key:String):Pair<Boolean,JSONObject?> = readableDatabase.query("checkpoints",arrayOf("value"),
        "job=? AND phase=? AND key=?",arrayOf(task.id,phase,key),null,null,null).use {c->
        if(!c.moveToFirst()) false to null else true to c.getString(0)?.let(::JSONObject)
    }
    fun saveCheckpoint(task:AnalysisTask,phase:String,key:String,value:JSONObject?) {
        writableDatabase.insertWithOnConflict("checkpoints",null,ContentValues().apply {put("job",task.id);put("phase",phase);put("key",key);put("value",value?.toString())},SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun checkpoints(task:AnalysisTask)=object:DuplicateScanner.Checkpoints {
        override fun file(media:GalleryMedia):Pair<Boolean,DuplicateFile?> {
            val cached=checkpoint(task,"hash",media.uri.toString())
            return cached.first to cached.second?.let {DuplicateFile(media,it.getString("hash"),it.getLong("bytes"))}
        }
        override fun saveFile(media:GalleryMedia,file:DuplicateFile?)=saveCheckpoint(task,"hash",media.uri.toString(),file?.let {
            JSONObject().put("hash",it.sha256).put("bytes",it.bytes)
        })
        override fun visual(digest:String):Pair<Boolean,DuplicateScanner.Visual?> {
            val cached=checkpoint(task,"visual",digest)
            return cached.first to cached.second?.let {v->val pixels=v.getJSONArray("pixels");require(pixels.length()==72)
                DuplicateScanner.Visual(v.getLong("hash"),v.getDouble("ratio").toFloat(),IntArray(72) {pixels.getInt(it)})}
        }
        override fun saveVisual(digest:String,visual:DuplicateScanner.Visual?)=saveCheckpoint(task,"visual",digest,visual?.let {
            JSONObject().put("hash",it.hash).put("ratio",it.ratio).put("pixels",JSONArray(it.pixels.toList()))
        })
    }
    fun complete(task:AnalysisTask,report:DuplicateReport?=null) = transaction {
        val current=task(task.kind)
        if(current?.id!=task.id || !current.active) return@transaction
        if(report!=null) {
            writableDatabase.delete("groups_found","job=?",arrayOf(task.id));writableDatabase.delete("members_found","job=?",arrayOf(task.id))
            report.groups.forEachIndexed {groupIndex,group->
                writableDatabase.insertOrThrow("groups_found",null,ContentValues().apply {put("job",task.id);put("position",groupIndex);put("id",group.id);put("kind",group.kind.name)})
                group.files.forEachIndexed {index,file->writableDatabase.insertOrThrow("members_found",null,ContentValues().apply {
                    put("job",task.id);put("group_position",groupIndex);put("position",index)
                    put("value",JSONObject().put("media",RecipeJson.media(file.media)).put("hash",file.sha256).put("bytes",file.bytes).toString())
                })}
            }
        }
        writableDatabase.update("jobs",ContentValues().apply {
            put("status",AnalysisStatus.COMPLETE.name);putNull("error")
            put("result",report?.let {JSONObject().put("checked",it.checked).put("unavailable",it.unavailable).toString()})
        },"id=?",arrayOf(task.id))
    }
    fun snapshot(kind:AnalysisKind):AnalysisSnapshot {
        val task=task(kind) ?: return AnalysisSnapshot()
        if(task.status!=AnalysisStatus.COMPLETE) return AnalysisSnapshot(task)
        if(kind==AnalysisKind.MOODS) return AnalysisSnapshot(task,moods=sources(task).mapNotNull {media->
            checkpoint(task,"mood",media.uri.toString()).second?.let {MoodPhoto(media,PhotoMood.valueOf(it.getString("mood")),it.getDouble("score").toFloat())}
        })
        val groups=readableDatabase.query("groups_found",arrayOf("position","id","kind"),"job=?",arrayOf(task.id),null,null,"position").use {c->buildList {
            while(c.moveToNext()) {
                val files=readableDatabase.query("members_found",arrayOf("value"),"job=? AND group_position=?",arrayOf(task.id,c.getInt(0).toString()),null,null,"position").use {members->buildList {
                    while(members.moveToNext()) {val f=JSONObject(members.getString(0));add(DuplicateFile(RecipeJson.readMedia(f.getJSONObject("media")),f.getString("hash"),f.getLong("bytes")))}
                }}
                add(DuplicateGroup(c.getString(1),DuplicateKind.valueOf(c.getString(2)),files))
            }
        }}
        val result=readableDatabase.query("jobs",arrayOf("result"),"id=?",arrayOf(task.id),null,null,null).use {c->check(c.moveToFirst());JSONObject(c.getString(0))}
        return AnalysisSnapshot(task,DuplicateReport(groups,result.getInt("checked"),result.getInt("unavailable")))
    }
}
