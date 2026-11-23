package de.luma.gallery.data

import android.content.Context
import androidx.core.content.edit
import de.luma.gallery.model.*

class TrashRetention(context: Context) {
    private val prefs=context.getSharedPreferences("luma-trash-policy",Context.MODE_PRIVATE)
    fun record(items:List<GalleryMedia>,days:Int,now:Long=System.currentTimeMillis()) {
        require(days in 1..30)
        prefs.edit { items.forEach { putLong(it.uri.toString(),now+days*86_400_000L) } }
    }
    fun deadline(media:GalleryMedia):Long? = prefs.getLong(media.uri.toString(),Long.MAX_VALUE).takeIf {it!=Long.MAX_VALUE}
    fun forget(items:List<GalleryMedia>) { prefs.edit { items.forEach { remove(it.uri.toString()) } } }
    fun due(items:List<TrashMedia>,now:Long=System.currentTimeMillis()):List<GalleryMedia> = items.filter {
        val due=prefs.getLong(it.media.uri.toString(),Long.MAX_VALUE)
        due<=now
    }.map { it.media }.take(MEDIA_ACTION_LIMIT)
}
