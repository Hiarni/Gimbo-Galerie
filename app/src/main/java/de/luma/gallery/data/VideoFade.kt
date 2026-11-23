package de.luma.gallery.data

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix


@androidx.annotation.OptIn(UnstableApi::class)
class VideoFade(private val durationMs: Long, transitionMs: Long) : RgbMatrix {
    private val fadeMs = minOf(transitionMs, durationMs / 2).coerceAtLeast(0)
    fun gain(timeUs: Long): Float {
        if (fadeMs == 0L) return 1f
        val timeMs = timeUs / 1000.0
        return minOf(1.0, timeMs / fadeMs, (durationMs - timeMs) / fadeMs).coerceIn(0.0, 1.0).toFloat()
    }
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        val gain = gain(presentationTimeUs)
        return floatArrayOf(gain, 0f, 0f, 0f, 0f, gain, 0f, 0f, 0f, 0f, gain, 0f, 0f, 0f, 0f, 1f)
    }
    override fun isNoOp(inputWidth: Int, inputHeight: Int) = fadeMs == 0L
}
