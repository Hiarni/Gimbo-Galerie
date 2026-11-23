package de.luma.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import de.luma.gallery.model.*
import kotlinx.coroutines.*

object MoodAnalysis {
    suspend fun scan(context:Context,media:List<GalleryMedia>,progress:(Int,Int)->Unit):List<MoodPhoto> = withContext(Dispatchers.IO) {
        val photos=sources(media)
        photos.mapIndexedNotNull {index,media->
            ensureActive()
            val result=analyze(context,media)
            progress(index+1,photos.size);result
        }
    }
    internal fun sources(media:List<GalleryMedia>)=media.filter {!it.isVideo && !it.isScreenshot}.sortedByDescending {it.timestamp}.take(200)
    internal suspend fun analyze(context:Context,media:GalleryMedia):MoodPhoto? = withContext(Dispatchers.IO) {
        try {
            val image=PhotoEngine.decode(context,media.uri,64)
            try {val (mood,score)=classify(image);MoodPhoto(media,mood,score)} finally {image.recycle()}
        } catch(error:Exception) {if(error is CancellationException) throw error;null}
    }
    internal fun classify(image:Bitmap):Pair<PhotoMood,Float> {
        var saturated=0;var quiet=0;var warm=0;var cool=0;var valid=0;var saturation=0f
        val hsv=FloatArray(3)
        for(y in 0 until image.height step 2) for(x in 0 until image.width step 2) {
            val pixel=image.getPixel(x,y);if(Color.alpha(pixel)<128) continue
            Color.colorToHSV(pixel,hsv);valid++;saturation+=hsv[1]
            if(hsv[1]>.55f && hsv[2]>.4f) saturated++
            if(hsv[1]<.3f) quiet++
            if(hsv[1]>.2f && hsv[2]>.15f) {if(hsv[0]<70 || hsv[0]>320) warm++ else if(hsv[0] in 150f..270f) cool++}
        }
        if(valid==0) return PhotoMood.QUIET to 0f
        val mood=when {saturation/valid<.08f->PhotoMood.MONOCHROME;warm>valid*.4->PhotoMood.WARM;cool>valid*.4->PhotoMood.COOL;saturated>valid*.4->PhotoMood.VIBRANT;else->PhotoMood.QUIET}
        val score=when(mood) {PhotoMood.MONOCHROME->1-saturation/valid;PhotoMood.WARM->warm.toFloat()/valid;PhotoMood.COOL->cool.toFloat()/valid;PhotoMood.VIBRANT->saturated.toFloat()/valid;PhotoMood.QUIET->quiet.toFloat()/valid}
        return mood to score
    }
}
