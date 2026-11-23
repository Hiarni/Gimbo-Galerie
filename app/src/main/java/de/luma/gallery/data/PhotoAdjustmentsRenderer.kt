package de.luma.gallery.data

import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import de.luma.gallery.model.*
import kotlin.math.pow
import kotlin.math.roundToInt

object PhotoAdjustmentsRenderer {
    fun apply(bitmap: Bitmap, hsl: PhotoHsl, masks: List<PhotoMask>, check: () -> Unit, originX: Int = 0, originY: Int = 0, frameWidth: Int = bitmap.width, frameHeight: Int = bitmap.height) {
        val row = IntArray(bitmap.width); val channels = FloatArray(3)
        val exposure = masks.map { mask -> IntArray(256) { byte ->
            val value = byte / 255.0
            val linear = if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
            val out = (linear * 2.0.pow(mask.exposure.toDouble())).coerceIn(0.0, 1.0)
            ((if (out <= .0031308) out * 12.92 else 1.055 * out.pow(1 / 2.4) - .055) * 255).roundToInt().coerceIn(0, 255)
        } }
        for (y in 0 until bitmap.height) {
            check(); bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                var pixel = row[x]
                val alpha = pixel ushr 24
                if (!hsl.neutral) {
                    ColorUtils.colorToHSL(pixel, channels)
                    val hue = channels[0]; var hueDelta = 0f; var satDelta = 0f; var lightDelta = 0f
                    hsl.bands.forEachIndexed { band, adjustment ->
                        val delta = kotlin.math.abs(hue - band * 60f)
                        val weight = (1f - minOf(delta, 360f - delta) / 60f).coerceAtLeast(0f)
                        hueDelta += adjustment.hue * weight; satDelta += adjustment.saturation * weight; lightDelta += adjustment.lightness * weight
                    }
                    channels[0] = (hue + hueDelta + 360) % 360
                    channels[1] = (channels[1] + satDelta).coerceIn(0f, 1f); channels[2] = (channels[2] + lightDelta).coerceIn(0f, 1f)
                    pixel = (ColorUtils.HSLToColor(channels) and 0xFFFFFF) or (alpha shl 24)
                }
                masks.forEachIndexed { index, mask ->
                    val weight = mask.weight((x + originX + .5f) / frameWidth, (y + originY + .5f) / frameHeight, pixel)
                    if (weight > 0f) {
                        ColorUtils.colorToHSL(pixel, channels); channels[1] = (channels[1] * mask.saturation).coerceIn(0f, 1f)
                        val saturated = ColorUtils.HSLToColor(channels)
                        val lut = exposure[index]
                        val adjusted = (alpha shl 24) or (lut[(saturated ushr 16) and 255] shl 16) or (lut[(saturated ushr 8) and 255] shl 8) or lut[saturated and 255]
                        pixel = ColorUtils.blendARGB(pixel, adjusted, weight)
                    }
                }
                row[x] = pixel
            }
            bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
    }
}
