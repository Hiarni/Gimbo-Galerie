package de.luma.gallery.data

import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import de.luma.gallery.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID


data class ExportRequest(val id: String = UUID.randomUUID().toString(), val items: List<GalleryMedia>,
    val photo: PhotoRecipe? = null, val video: VideoRecipe? = null, val options: PhotoExportOptions = PhotoExportOptions(), val completed: Int = 0, val overwrite:Boolean=false, val collage:CollageSpec?=null,val outputPrefix:String="Gimbo-Galerie") {
    val outputCount:Int get()=if(collage!=null) 1 else items.size
    fun outputName(index:Int)="${outputPrefix}_${id}_$index"
    init {
        require(outputPrefix in listOf("Gimbo-Galerie","Gimbo","Luma"))
        requireMessage(!overwrite || items.size==1 && canManageMedia(items.single()),R.string.local_media_only)
        requireMessage(runCatching { UUID.fromString(id) }.isSuccess && items.size in 1..100 && completed in 0..outputCount &&
            ((collage!=null && photo==null && video==null && !overwrite && items.size==collage.tiles.size && items.all {!it.isVideo} && options.maxEdge<=4096) || (photo != null && collage==null && video == null && items.all { !it.isVideo }) || (video != null && collage==null && photo == null && items.size == 1 && items[0].isVideo && video.isValid(items[0].durationMs))), R.string.invalid_export_request)
    }
    fun json(): String = JSONObject().put("version", 1).put("id", id).put("items", JSONArray(items.map(RecipeJson::media)))
        .put("photo", photo?.let(RecipeJson::photo)).put("video", video?.let(RecipeJson::video)).put("completed", completed)
        .put("outputPrefix",outputPrefix).put("collage",collage?.let(CollageEngine::json)).put("overwrite",overwrite).put("format", options.format.name).put("edge", options.maxEdge).put("quality", options.quality).put("metadata", options.metadata.name).toString()
    companion object {
        fun read(value: String): ExportRequest {
            requireMessage(value.length <= 4_000_000, R.string.invalid_export_request)
            val json = JSONObject(value); requireMessage(json.getInt("version") == 1, R.string.invalid_export_request)
            val media = json.getJSONArray("items"); requireMessage(media.length() in 1..100, R.string.invalid_export_request)
            return ExportRequest(json.getString("id"), (0 until media.length()).map { RecipeJson.readMedia(media.getJSONObject(it)) },
                if (json.has("photo")) RecipeJson.readPhoto(json.getString("photo")) else null,
                if (json.has("video")) RecipeJson.readVideo(json.getString("video")) else null,
                PhotoExportOptions(PhotoFormat.valueOf(json.getString("format")), json.getInt("edge"), json.getInt("quality"), MetadataMode.valueOf(json.optString("metadata", "NONE"))), json.getInt("completed"),json.optBoolean("overwrite",false),if(json.has("collage")) CollageEngine.read(json.getString("collage")) else null,json.optString("outputPrefix","Luma"))
        }
    }
}
