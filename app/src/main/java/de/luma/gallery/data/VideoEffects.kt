package de.luma.gallery.data

import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.AbsoluteSizeSpan
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.audio.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import androidx.media3.transformer.Effects
import de.luma.gallery.model.*
import java.nio.ByteBuffer

@androidx.annotation.OptIn(UnstableApi::class)
object VideoEffects {
    fun visual(recipe: VideoRecipe, width: Int? = null, height: Int? = null): List<Effect> = buildList {
        recipe.cropBounds?.let {add(Crop(it.left*2-1,it.right*2-1,1-it.bottom*2,1-it.top*2))}
        if(recipe.quarterTurns!=0) add(ScaleAndRotateTransformation.Builder().setRotationDegrees(-90f*recipe.quarterTurns).build())
        if(recipe.flipHorizontal || recipe.flipVertical) add(ScaleAndRotateTransformation.Builder().setScale(if(recipe.flipHorizontal) -1f else 1f,if(recipe.flipVertical) -1f else 1f).build())
        if (width != null && height != null) add(Presentation.createForWidthAndHeight(width, height, if(recipe.fillFrame) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT))
        else recipe.aspectRatio?.let {add(Presentation.createForAspectRatio(it,if(recipe.fillFrame) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT))}
        if(recipe.temperature!=0f || recipe.tint!=0f) add(RgbAdjustment.Builder()
            .setRedScale(1f+recipe.temperature*.2f+recipe.tint*.1f).setGreenScale(1f-recipe.tint*.2f).setBlueScale(1f-recipe.temperature*.2f+recipe.tint*.1f).build())
        if (recipe.brightness != 0f) add(Brightness(recipe.brightness))
        if (recipe.contrast != 0f) add(Contrast(recipe.contrast))
        if (recipe.saturation != 0f) add(HslAdjustment.Builder().adjustSaturation(recipe.saturation * 100).build())
        recipe.caption?.takeIf { it.visible && it.opacity > 0f }?.let { text ->
            val caption = SpannableString(text.text)
            fun span(value: Any) { caption.setSpan(value, 0, caption.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            span(ForegroundColorSpan(text.color.toInt()))
            if (text.background) span(BackgroundColorSpan(0xB0000000.toInt()))
            if (text.bold) span(StyleSpan(android.graphics.Typeface.BOLD))
            span(AbsoluteSizeSpan((text.size * (height ?: recipe.outputHeight)).toInt().coerceAtLeast(8)))
            val settings = StaticOverlaySettings.Builder().setAlphaScale(text.opacity)
                .setBackgroundFrameAnchor(text.x * 2 - 1, 1 - text.y * 2).build()
            add(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(caption, settings))))
        }
    }
    fun effects(clip: VideoClip, recipe: VideoRecipe, width: Int, height: Int, startMs: Long = 0, overlapIn: Long = 0, overlapOut: Long = 0): Effects {
        val audio = mutableListOf<AudioProcessor>()
        val video = visual(recipe, width, height).toMutableList()
        if (clip.volume != 1f || recipe.fadeInMs > 0 || recipe.fadeOutMs > 0 || overlapIn > 0 || overlapOut > 0)
            audio += VolumeAudioProcessor(clip.volume, clip.outputDurationMs, maxOf(recipe.fadeInMs, overlapIn), maxOf(recipe.fadeOutMs, overlapOut))
        if (recipe.transitionMs > 0) video += VideoFade(clip.outputDurationMs, recipe.transitionMs)
        return Effects(audio, video)
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
class VolumeAudioProcessor(private val volume: Float, private val durationMs: Long, private val fadeInMs: Long = 0, private val fadeOutMs: Long = 0, private val envelope: (Long) -> Float = { 1f }) : BaseAudioProcessor() {
    private var samples = 0L
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT)
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }
    override fun onFlush() { samples = 0 }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val channels = inputAudioFormat.channelCount
        while (inputBuffer.hasRemaining()) {
            val time = samples / channels * 1000.0 / inputAudioFormat.sampleRate
            val gain = volume * envelope(time.toLong()) * minOf(1.0, if (fadeInMs > 0) time / fadeInMs else 1.0,
                if (fadeOutMs > 0) (durationMs - time).coerceAtLeast(0.0) / fadeOutMs else 1.0).toFloat()
            if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) output.putShort((inputBuffer.short * gain).toInt().coerceIn(-32768, 32767).toShort())
            else output.putFloat((inputBuffer.float * gain).coerceIn(-1f, 1f))
            samples++
        }
        output.flip()
    }
}
