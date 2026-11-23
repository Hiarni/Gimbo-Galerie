package de.luma.gallery.model

import de.luma.gallery.R
import java.util.Locale

data class VideoPreset(val id:String,val label:Int,val brightness:Float=0f,val contrast:Float=0f,val saturation:Float=0f,val temperature:Float=0f,val tint:Float=0f)

val VIDEO_PRESETS=listOf(
    VideoPreset("natural",R.string.filter_natural),
    VideoPreset("clear",R.string.filter_clear,contrast=.15f,saturation=.1f),
    VideoPreset("warm",R.string.filter_warm,saturation=.08f,temperature=.4f),
    VideoPreset("cool",R.string.filter_cool,contrast=.08f,temperature=-.4f),
    VideoPreset("gold",R.string.filter_gold,brightness=.05f,temperature=.65f),
    VideoPreset("cinema",R.string.filter_cinema,contrast=.2f,saturation=-.3f,temperature=-.1f),
    VideoPreset("vintage",R.string.filter_vintage,brightness=.04f,contrast=-.15f,saturation=-.35f,temperature=.2f),
    VideoPreset("vivid",R.string.filter_vivid,contrast=.12f,saturation=.45f),
    VideoPreset("soft",R.string.filter_soft,brightness=.06f,contrast=-.15f,saturation=-.1f),
    VideoPreset("bw",R.string.filter_bw,contrast=.12f,saturation=-1f),
    VideoPreset("noir",R.string.filter_noir,contrast=.4f,saturation=-1f),
    VideoPreset("sepia",R.string.filter_sepia,saturation=-.8f,temperature=.7f,tint=.15f)
)

fun VideoRecipe.withPreset(preset:VideoPreset,strength:Float):VideoRecipe {
    require(strength.isFinite() && strength in 0f..1f)
    return copy(brightness=preset.brightness*strength,contrast=preset.contrast*strength,saturation=preset.saturation*strength,temperature=preset.temperature*strength,tint=preset.tint*strength)
}

fun videoTimecode(ms:Long):String {
    require(ms>=0)
    return String.format(Locale.ROOT,"%02d:%02d:%02d.%03d",ms/3_600_000,ms/60_000%60,ms/1000%60,ms%1000)
}

fun parseVideoTimecode(value:String):Long? {
    val match=Regex("^(\\d{1,3}):([0-5]\\d):([0-5]\\d)[.,](\\d{1,3})$").matchEntire(value.trim()) ?: return null
    val (hours,minutes,seconds,millis)=match.destructured
    return hours.toLong()*3_600_000+minutes.toLong()*60_000+seconds.toLong()*1000+millis.padEnd(3,'0').toLong()
}

fun VideoRecipe.additionalClipCapacity(source: GalleryMedia): Int =
    minOf(20 - orderedClips(source).size, 19 - extraClips.size).coerceAtLeast(0)

fun VideoRecipe.duplicateClip(source:GalleryMedia,id:String):VideoRecipe {
    val clips=orderedClips(source)
    require(additionalClipCapacity(source)>0)
    val original=clips.first {it.id==id}
    val addition=original.copy(id=java.util.UUID.randomUUID().toString())
    val order=clips.flatMap {if(it.id==id) listOf(it.id,addition.id) else listOf(it.id)}
    return copy(extraClips=extraClips+addition,clipOrder=order)
}

fun VideoRecipe.splitClip(source:GalleryMedia,id:String,atMs:Long):VideoRecipe {
    val clips=orderedClips(source)
    require(additionalClipCapacity(source)>0)
    val original=clips.first {it.id==id}
    require(atMs-original.startMs>=500 && original.endMs-atMs>=500)
    val addition=original.copy(id=java.util.UUID.randomUUID().toString(),startMs=atMs)
    val order=clips.flatMap {if(it.id==id) listOf(it.id,addition.id) else listOf(it.id)}
    return if(id=="primary") copy(endMs=atMs,extraClips=extraClips+addition,clipOrder=order)
        else copy(extraClips=extraClips.map {if(it.id==id) it.copy(endMs=atMs) else it}+addition,clipOrder=order)
}
