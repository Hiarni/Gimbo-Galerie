package de.luma.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.text.SpannableString
import android.text.Spanned
import android.text.style.*
import androidx.media3.common.*
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import de.luma.gallery.model.*


@androidx.annotation.OptIn(UnstableApi::class)
internal class CrossfadeCompositor(private val timeline: List<TimedVideoClip>, private val width: Int, private val height: Int) : VideoCompositorSettings {
    override fun getOutputSize(inputSizes: List<Size>) = Size(width,height)
    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        val time = presentationTimeUs/1000.0
        val active = timeline.withIndex().filter { time >= it.value.startMs && time < it.value.endMs }
        val clip = active.find { it.index % 2 == inputId }?.value
        val alpha = if (clip == null) 0f else if (inputId != 0 || active.size < 2) 1f else {
            if (clip.fadeInMs > 0 && time < clip.startMs+clip.fadeInMs) ((time-clip.startMs)/clip.fadeInMs).toFloat()
            else if (clip.fadeOutMs > 0 && time > clip.endMs-clip.fadeOutMs) ((clip.endMs-time)/clip.fadeOutMs).toFloat() else 1f
        }
        return StaticOverlaySettings.Builder().setAlphaScale(alpha.coerceIn(0f,1f)).build()
    }
}
@androidx.annotation.OptIn(UnstableApi::class)
object VideoTimelineEffects {
    fun create(recipe: VideoRecipe, width: Int, height: Int, context: Context?): List<Effect> = buildList {
        if (recipe.keyframes.isNotEmpty()) {
            add(object : GlMatrixTransformation {
                override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray {
                    val k=recipe.frameAt(presentationTimeUs/1000)
                    return floatArrayOf(k.zoom,0f,0f,0f, 0f,k.zoom,0f,0f, 0f,0f,1f,0f, (1-2*k.x)*(k.zoom-1),(2*k.y-1)*(k.zoom-1),0f,1f)
                }
            })
            add(object : RgbMatrix {
                override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
                    val value=recipe.frameAt(presentationTimeUs/1000).brightness
                    return floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, value,value,value,1f)
                }
            })
        }
        if (recipe.overlays.isNotEmpty()) add(OverlayEffect(recipe.overlays.map { o ->
            fun settings(timeUs: Long, imageWidth: Int? = null): OverlaySettings {
                val active = timeUs/1000 >= o.startMs && timeUs/1000 < o.endMs
                val scale = if(imageWidth != null) width * o.scale / imageWidth else o.scale/.25f
                return StaticOverlaySettings.Builder().setAlphaScale(if(active) o.opacity else 0f)
                    .setBackgroundFrameAnchor(o.x*2-1, 1-o.y*2).setScale(scale, scale).setRotationDegrees(o.rotation).build()
            }
            if (o.text != null) {
                val text=o.text
                val value=SpannableString(text.text)
                fun span(s: Any) { value.setSpan(s,0,value.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                span(ForegroundColorSpan(text.color.toInt())); span(AbsoluteSizeSpan((text.size*minOf(width,height)).toInt().coerceAtLeast(8)))
                if(text.bold) span(StyleSpan(android.graphics.Typeface.BOLD))
                if(text.background) span(BackgroundColorSpan(0xB0000000.toInt()))
                object : TextOverlay() {
                    override fun getText(presentationTimeUs: Long)=value
                    override fun getOverlaySettings(presentationTimeUs: Long)=settings(presentationTimeUs)
                }
            } else object : BitmapOverlay() {
                private var owned: Bitmap? = null
                override fun getBitmap(presentationTimeUs: Long): Bitmap = owned ?: PhotoEngine.decode(requireNotNull(context),android.net.Uri.parse(o.imageUri),1024).also { owned=it }
                override fun getOverlaySettings(presentationTimeUs: Long)=settings(presentationTimeUs, getBitmap(presentationTimeUs).width)
                override fun release() { try { super.release() } finally { owned?.recycle(); owned=null } }
            }
        }))
    }
}
