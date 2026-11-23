package de.luma.gallery.data

import de.luma.gallery.localization.*
import de.luma.gallery.R
import de.luma.gallery.model.*
import de.luma.gallery.model.CropBounds
import de.luma.gallery.model.PhotoRecipe
import de.luma.gallery.model.VideoRecipe
import de.luma.gallery.model.PhotoCurves
import de.luma.gallery.model.ToneCurve
import de.luma.gallery.model.PhotoText
import de.luma.gallery.model.PHOTO_TEXT_LIMIT
import de.luma.gallery.model.PhotoMarkup
import de.luma.gallery.model.MarkupKind
import de.luma.gallery.model.MarkupPoint
import de.luma.gallery.model.PHOTO_MARKUP_LIMIT
import de.luma.gallery.model.PHOTO_MARKUP_POINTS
import org.json.JSONArray
import org.json.JSONObject


object RecipeJson {
    fun photo(recipe: PhotoRecipe): String {
        val json = JSONObject().put("version", 7).put("brightness", recipe.brightness).put("contrast", recipe.contrast)
            .put("saturation", recipe.saturation).put("turns", recipe.quarterTurns).put("flip", recipe.flipHorizontal)
            .put("exposure", recipe.exposure).put("temperature", recipe.temperature).put("tint", recipe.tint)
            .put("highlights", recipe.highlights).put("shadows", recipe.shadows).put("sharpness", recipe.sharpness)
            .put("curves", JSONObject().put("rgb", JSONArray(recipe.curves.rgb.values())).put("red", JSONArray(recipe.curves.red.values()))
                .put("green", JSONArray(recipe.curves.green.values())).put("blue", JSONArray(recipe.curves.blue.values())))
            .put("texts", JSONArray(recipe.texts.map { text -> JSONObject().put("id", text.id).put("text", text.text)
                .put("x", text.x).put("y", text.y).put("size", text.size).put("color", text.color)
                .put("bold", text.bold).put("background", text.background).put("visible", text.visible).put("opacity", text.opacity) }))
        json.put("markups", JSONArray(recipe.markups.map { mark -> JSONObject().put("id", mark.id).put("kind", mark.kind.name)
            .put("points", JSONArray(mark.points.map { JSONObject().put("x", it.x).put("y", it.y) }))
            .put("color", mark.color).put("width", mark.width).put("filled", mark.filled).put("blockSize", mark.blockSize).put("visible", mark.visible).put("opacity", mark.opacity).put("sample", JSONObject().put("x", mark.sample.x).put("y", mark.sample.y)) }))
        json.put("perspectiveX", recipe.perspectiveX).put("perspectiveY", recipe.perspectiveY)
        json.put("layerOrder", JSONArray(recipe.layerOrder))
        json.put("hsl", JSONArray(recipe.hsl.bands.map { JSONObject().put("hue", it.hue).put("saturation", it.saturation).put("lightness", it.lightness) }))
        json.put("masks", JSONArray(recipe.masks.map { JSONObject().put("id", it.id).put("shape", it.shape.name).put("x", it.x).put("y", it.y)
            .put("width", it.width).put("height", it.height).put("feather", it.feather).put("exposure", it.exposure).put("saturation", it.saturation)
            .put("points", JSONArray(it.points.map { p -> JSONObject().put("x", p.x).put("y", p.y) })).put("inverted", it.inverted)
            .put("rangeStart", it.rangeStart).put("rangeEnd", it.rangeEnd).put("brushSize", it.brushSize) }))
        recipe.cropRatio?.let { json.put("crop", it) }
        recipe.cropBounds?.let { json.put("bounds", JSONObject().put("left", it.left).put("top", it.top).put("right", it.right).put("bottom", it.bottom)) }
        return json.toString()
    }

    fun readPhoto(value: String): PhotoRecipe {
        val json = JSONObject(value)
        val version = json.optInt("version", 1)
        requireMessage(version in 1..7, R.string.unsupported_recipe)
        return PhotoRecipe(json.optDouble("brightness", 0.0).toFloat(), json.optDouble("contrast", 1.0).toFloat(),
            json.optDouble("saturation", 1.0).toFloat(), json.optInt("turns"), json.optBoolean("flip"),
            if (json.has("crop")) json.getDouble("crop").toFloat() else null,
            json.optJSONObject("bounds")?.let { CropBounds(it.getDouble("left").toFloat(), it.getDouble("top").toFloat(),
                it.getDouble("right").toFloat(), it.getDouble("bottom").toFloat()) },
            json.optDouble("exposure", 0.0).toFloat(), json.optDouble("temperature", 0.0).toFloat(), json.optDouble("tint", 0.0).toFloat(),
            if (version >= 2) json.optDouble("highlights", 0.0).toFloat() else 0f,
            if (version >= 2) json.optDouble("shadows", 0.0).toFloat() else 0f,
            if (version >= 2) json.optDouble("sharpness", 0.0).toFloat() else 0f,
            if (version >= 3 && json.has("curves")) readCurves(json.getJSONObject("curves")) else PhotoCurves(),
            if (version >= 4 && json.has("texts")) readTexts(json.getJSONArray("texts")) else emptyList(),
            if (version >= 5 && json.has("markups")) readMarkups(json.getJSONArray("markups")) else emptyList(),
            if (version >= 6 && json.has("layerOrder")) json.getJSONArray("layerOrder").let { a ->
                requireMessage(a.length() <= PHOTO_TEXT_LIMIT + PHOTO_MARKUP_LIMIT, R.string.invalid_layers)
                (0 until a.length()).map(a::getString) } else emptyList(),
            if (version >= 6 && json.has("hsl")) json.getJSONArray("hsl").let { a ->
                requireMessage(a.length() == 6, R.string.invalid_photo_details)
                PhotoHsl((0 until a.length()).map { a.getJSONObject(it).let { band -> HslBand(band.getDouble("hue").toFloat(), band.getDouble("saturation").toFloat(), band.getDouble("lightness").toFloat()) } }) } else PhotoHsl(),
            if (version >= 6 && json.has("masks")) json.getJSONArray("masks").let { a ->
                requireMessage(a.length() <= 8, R.string.invalid_mask)
                (0 until a.length()).map { a.getJSONObject(it).let { m -> PhotoMask(m.getString("id"), MaskShape.valueOf(m.getString("shape")),
                    m.getDouble("x").toFloat(), m.getDouble("y").toFloat(), m.getDouble("width").toFloat(), m.getDouble("height").toFloat(),
                    m.getDouble("feather").toFloat(), m.getDouble("exposure").toFloat(), m.getDouble("saturation").toFloat(),
                    m.optJSONArray("points")?.let { pts -> requireMessage(pts.length() <= 256, R.string.invalid_mask); (0 until pts.length()).map { i -> pts.getJSONObject(i).let { MarkupPoint(it.getDouble("x").toFloat(), it.getDouble("y").toFloat()) } } }.orEmpty(),
                    m.optBoolean("inverted"), m.optDouble("rangeStart", 0.0).toFloat(), m.optDouble("rangeEnd", 1.0).toFloat(), m.optDouble("brushSize", .05).toFloat()) } } } else emptyList(),
            if (version >= 6) json.optDouble("perspectiveX", 0.0).toFloat() else 0f,
            if (version >= 6) json.optDouble("perspectiveY", 0.0).toFloat() else 0f)
    }

    private fun readMarkups(json: JSONArray): List<PhotoMarkup> {
        requireMessage(json.length() <= PHOTO_MARKUP_LIMIT, R.string.invalid_markup)
        return (0 until json.length()).map { index -> json.getJSONObject(index).let { obj ->
            val points = obj.getJSONArray("points")
            requireMessage(points.length() in 1..PHOTO_MARKUP_POINTS, R.string.invalid_markup)
            val kind = MarkupKind.entries.find { it.name == obj.getString("kind") }
            requireMessage(kind != null, R.string.invalid_markup)
            PhotoMarkup(obj.getString("id"), kind, (0 until points.length()).map { i -> points.getJSONObject(i).let {
                MarkupPoint(it.getDouble("x").toFloat(), it.getDouble("y").toFloat())
            } }, obj.getLong("color"), obj.getDouble("width").toFloat(), obj.getBoolean("filled"), obj.getDouble("blockSize").toFloat(), obj.optBoolean("visible", true), obj.optDouble("opacity", 1.0).toFloat(), obj.optJSONObject("sample")?.let { MarkupPoint(it.getDouble("x").toFloat(), it.getDouble("y").toFloat()) } ?: MarkupPoint(.5f, .5f))
        } }
    }

    private fun readTexts(json: JSONArray): List<PhotoText> {
        requireMessage(json.length() <= PHOTO_TEXT_LIMIT, R.string.too_many_texts)
        return (0 until json.length()).map { index -> json.getJSONObject(index).let {
            PhotoText(it.getString("id"), it.getString("text"), it.getDouble("x").toFloat(), it.getDouble("y").toFloat(),
                it.getDouble("size").toFloat(), it.getLong("color"), it.getBoolean("bold"), it.getBoolean("background"), it.optBoolean("visible", true), it.optDouble("opacity", 1.0).toFloat())
        } }
    }

    private fun readCurves(json: JSONObject): PhotoCurves {
        fun curve(key: String): ToneCurve {
            if (!json.has(key)) return ToneCurve()
            val points = json.getJSONArray(key)
            requireMessage(points.length() == 5, R.string.invalid_curve)
            return ToneCurve(points.getDouble(0).toFloat(), points.getDouble(1).toFloat(), points.getDouble(2).toFloat(),
                points.getDouble(3).toFloat(), points.getDouble(4).toFloat())
        }
        return PhotoCurves(curve("rgb"), curve("red"), curve("green"), curve("blue"))
    }

    fun media(source: GalleryMedia) = JSONObject().put("id", source.id).put("uri", source.uri.toString()).put("name", source.name)
        .put("album", source.album).put("timestamp", source.timestamp).put("video", source.isVideo).put("duration", source.durationMs).put("size", source.size).put("demo", source.isDemo).put("imported",source.isImported)
    fun readMedia(json: JSONObject): GalleryMedia {
        val uri = android.net.Uri.parse(json.getString("uri"))
        requireMessage(uri.scheme in listOf("content", "file") && json.getString("id").length in 1..4096 && json.getString("name").length <= 4096 &&
            json.getString("album").length <= 4096 && json.getLong("duration") in 0..604_800_000 && json.getLong("size") >= 0, R.string.invalid_media_reference)
        return GalleryMedia(json.getString("id"), uri, json.getString("name"), json.getString("album"), json.getLong("timestamp"), json.getBoolean("video"), json.getLong("duration"), json.getLong("size"), json.getBoolean("demo"),json.optBoolean("imported",false))
    }
    fun video(recipe: VideoRecipe): String = JSONObject().put("version", 4).put("start", recipe.startMs).put("end", recipe.endMs).put("mute", recipe.mute)
        .put("speed", recipe.speed).put("volume", recipe.volume).put("brightness", recipe.brightness).put("contrast", recipe.contrast).put("saturation", recipe.saturation)
        .put("rotation",recipe.quarterTurns).put("flipX",recipe.flipHorizontal).put("flipY",recipe.flipVertical)
        .put("crop",recipe.cropBounds?.let {JSONObject().put("left",it.left).put("top",it.top).put("right",it.right).put("bottom",it.bottom)})
        .put("aspect",recipe.aspectRatio).put("fill",recipe.fillFrame).put("temperature",recipe.temperature).put("tint",recipe.tint)
        .put("caption", recipe.caption?.let { JSONObject(photo(PhotoRecipe(texts = listOf(it)))).getJSONArray("texts").getJSONObject(0) })
        .put("clips", JSONArray(recipe.extraClips.map { c -> JSONObject().put("id", c.id).put("source", media(c.source)).put("start", c.startMs).put("end", c.endMs)
            .put("mute", c.mute).put("speed", c.speed).put("volume", c.volume) })).put("order", JSONArray(recipe.clipOrder)).put("includeSource", recipe.includeSource)
        .put("music", recipe.music?.let { JSONObject().put("uri", it.uri).put("name", it.name).put("duration", it.durationMs).put("volume", it.volume) })
        .put("fadeIn", recipe.fadeInMs).put("fadeOut", recipe.fadeOutMs).put("height", recipe.outputHeight).put("bitrate", recipe.bitrate).put("transition", recipe.transitionMs).put("crossfade", recipe.crossfadeMs)
        .put("keyframes", JSONArray(recipe.keyframes.map { JSONObject().put("time",it.timeMs).put("zoom",it.zoom).put("x",it.x).put("y",it.y).put("brightness",it.brightness).put("volume",it.volume) }))
        .put("audioTracks", JSONArray(recipe.audioTracks.map { t -> JSONObject().put("id",t.id).put("music", JSONObject().put("uri",t.music.uri).put("name",t.music.name).put("duration",t.music.durationMs).put("volume",t.music.volume))
            .put("start",t.timelineStartMs).put("sourceStart",t.sourceStartMs).put("sourceEnd",t.sourceEndMs).put("length",t.lengthMs).put("loop",t.loop).put("fadeIn",t.fadeInMs).put("fadeOut",t.fadeOutMs) }))
        .put("overlays", JSONArray(recipe.overlays.map { o -> JSONObject().put("id",o.id).put("image",o.imageUri)
            .put("text",o.text?.let { JSONObject(photo(PhotoRecipe(texts=listOf(it)))).getJSONArray("texts").getJSONObject(0) })
            .put("start",o.startMs).put("end",o.endMs).put("x",o.x).put("y",o.y).put("scale",o.scale).put("opacity",o.opacity).put("rotation",o.rotation) })).toString()

    fun readVideo(value: String): VideoRecipe {
        val json = JSONObject(value)
        val version = json.optInt("version", 1)
        requireMessage(version in 1..4, R.string.unsupported_recipe)
        if (version == 1) return VideoRecipe(json.getLong("start"), json.getLong("end"), json.optBoolean("mute"))
        val clips = json.optJSONArray("clips") ?: JSONArray()
        requireMessage(clips.length() <= 19, R.string.invalid_video_clip)
        val order = json.optJSONArray("order") ?: JSONArray()
        requireMessage(order.length() <= 20, R.string.invalid_video_clip)
        return VideoRecipe(json.getLong("start"), json.getLong("end"), json.optBoolean("mute"),
            json.optDouble("speed", 1.0).toFloat(), json.optDouble("volume", 1.0).toFloat(), json.optDouble("brightness", 0.0).toFloat(),
            json.optDouble("contrast", 0.0).toFloat(), json.optDouble("saturation", 0.0).toFloat(),
            json.optJSONObject("caption")?.let { readTexts(JSONArray().put(it)).first() },
            (0 until clips.length()).map { clips.getJSONObject(it).let { c -> VideoClip(c.getString("id"), readMedia(c.getJSONObject("source")), c.getLong("start"), c.getLong("end"),
                c.getBoolean("mute"), c.getDouble("speed").toFloat(), c.getDouble("volume").toFloat()) } },
            (0 until order.length()).map(order::getString), json.optBoolean("includeSource", true),
            json.optJSONObject("music")?.let { VideoMusic(it.getString("uri"), it.getString("name"), it.getLong("duration"), it.getDouble("volume").toFloat()) },
            json.optLong("fadeIn", 0), json.optLong("fadeOut", 0), json.optInt("height", 1080), json.optInt("bitrate", 6_000_000), json.optLong("transition", 0), json.optLong("crossfade", 0),
            json.optJSONArray("keyframes")?.let { a -> requireMessage(a.length() <= 100, R.string.invalid_video_clip); (0 until a.length()).map { a.getJSONObject(it).let { k -> VideoKeyframe(k.getLong("time"), k.getDouble("zoom").toFloat(), k.getDouble("x").toFloat(), k.getDouble("y").toFloat(), k.getDouble("brightness").toFloat(), k.getDouble("volume").toFloat()) } } }.orEmpty(),
            json.optJSONArray("audioTracks")?.let { a -> requireMessage(a.length() <= 8, R.string.invalid_video_clip); (0 until a.length()).map { a.getJSONObject(it).let { t ->
                val m=t.getJSONObject("music"); VideoAudioTrack(t.getString("id"), VideoMusic(m.getString("uri"),m.getString("name"),m.getLong("duration"),m.getDouble("volume").toFloat()), t.getLong("start"),t.getLong("sourceStart"),t.getLong("sourceEnd"),t.getLong("length"),t.getBoolean("loop"),t.getLong("fadeIn"),t.getLong("fadeOut")) } } }.orEmpty(),
            json.optJSONArray("overlays")?.let { a -> requireMessage(a.length() <= 16, R.string.invalid_video_clip); (0 until a.length()).map { a.getJSONObject(it).let { o ->
                VideoOverlay(o.getString("id"),o.optJSONObject("text")?.let { readTexts(JSONArray().put(it)).first() },if (o.isNull("image")) null else o.getString("image"),o.getLong("start"),o.getLong("end"),o.getDouble("x").toFloat(),o.getDouble("y").toFloat(),o.getDouble("scale").toFloat(),o.getDouble("opacity").toFloat(),o.getDouble("rotation").toFloat()) } } }.orEmpty(),
            json.optInt("rotation",0),json.optBoolean("flipX"),json.optBoolean("flipY"),
            json.optJSONObject("crop")?.let {CropBounds(it.getDouble("left").toFloat(),it.getDouble("top").toFloat(),it.getDouble("right").toFloat(),it.getDouble("bottom").toFloat())},
            if(json.isNull("aspect")) null else json.getDouble("aspect").toFloat(),json.optBoolean("fill"),json.optDouble("temperature",0.0).toFloat(),json.optDouble("tint",0.0).toFloat())
    }
}
