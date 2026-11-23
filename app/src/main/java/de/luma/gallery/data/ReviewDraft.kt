package de.luma.gallery.data

import android.content.Context
import de.luma.gallery.model.ReviewAction
import de.luma.gallery.model.ReviewSession
import org.json.JSONArray
import org.json.JSONObject

class ReviewDraft(context:Context) {
    private val preferences=context.applicationContext.getSharedPreferences("gimbo-review-draft",Context.MODE_PRIVATE)
    fun read():ReviewSession? = preferences.getString("session",null)?.let {text->runCatching {
        val json=JSONObject(text);val ids=json.getJSONArray("ids");val actions=json.getJSONArray("actions")
        ReviewSession((0 until ids.length()).map {ids.getString(it)},(0 until actions.length()).map {ReviewAction.valueOf(actions.getString(it))})
    }.getOrNull()}
    fun save(session:ReviewSession) {check(preferences.edit().putString("session",JSONObject().put("ids",JSONArray(session.ids)).put("actions",JSONArray(session.decisions.map {it.name})).toString()).commit())}
    fun clear() {check(preferences.edit().remove("session").commit())}
}
