package de.luma.gallery.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.AtomicFile
import org.json.JSONObject

internal fun AtomicFile.writeJson(value:JSONObject) {
    var stream:java.io.FileOutputStream?=null
    try {stream=startWrite();stream.write(value.toString().toByteArray(Charsets.UTF_8));finishWrite(stream)}
    catch(e:Throwable) {stream?.let(::failWrite);throw e}
}

class ArchiveRestoreJournal(context:Context,val id:String):java.io.Closeable {
    private class Database(context:Context):SQLiteOpenHelper(context.applicationContext,"gimbo-restore.db",null,1) {
        override fun onCreate(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE jobs (id TEXT PRIMARY KEY, folder TEXT)")
            db.execSQL("CREATE TABLE files (job TEXT NOT NULL, position INTEGER NOT NULL, name TEXT NOT NULL, uri TEXT, PRIMARY KEY(job,position))")
        }
        override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unsupported restore database version")
    }
    private val helper=Database(context)
    val folderName="Gimbo-Galerie-Restore-$id"
    var folder:String?
        get()=helper.readableDatabase.query("jobs",arrayOf("folder"),"id=?",arrayOf(id),null,null,null).use {if(it.moveToFirst()) it.getString(0) else null}
        set(value) {helper.writableDatabase.insertWithOnConflict("jobs",null,ContentValues().apply {put("id",id);put("folder",value)},SQLiteDatabase.CONFLICT_REPLACE)}
    fun name(index:Int)=field(index,"name")
    private fun field(index:Int,column:String)=helper.readableDatabase.query("files",arrayOf(column),"job=? AND position=?",arrayOf(id,index.toString()),null,null,null).use {if(it.moveToFirst()) it.getString(0) else null}
    fun plan(index:Int,name:String) {helper.writableDatabase.insertWithOnConflict("files",null,ContentValues().apply {put("job",id);put("position",index);put("name",name)},SQLiteDatabase.CONFLICT_IGNORE)}
    fun target(index:Int)=field(index,"uri")
    fun target(index:Int,uri:String) {check(helper.writableDatabase.update("files",ContentValues().apply {put("uri",uri)},"job=? AND position=?",arrayOf(id,index.toString()))==1)}
    fun delete() {
        val db=helper.writableDatabase;db.beginTransaction()
        try {db.delete("jobs","id=?",arrayOf(id));db.delete("files","job=?",arrayOf(id));db.setTransactionSuccessful()} finally {db.endTransaction()}
    }
    override fun close()=helper.close()
}
