package de.luma.gallery.data

import de.luma.gallery.localization.*
import de.luma.gallery.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.graphics.createBitmap
import de.luma.gallery.model.PhotoRecipe
import de.luma.gallery.model.PhotoExportOptions
import de.luma.gallery.model.PhotoFormat
import de.luma.gallery.model.boundsFor
import de.luma.gallery.model.fitWithin
import de.luma.gallery.model.PhotoCurves
import de.luma.gallery.model.ToneCurveSampler
import de.luma.gallery.model.layerKeys
import kotlin.math.roundToInt

object PhotoEngine {

    fun encode(bitmap: Bitmap, options: PhotoExportOptions, stream: java.io.OutputStream) {
        val png = options.format == PhotoFormat.PNG
        val flattened = if (!png && bitmap.hasAlpha()) createBitmap(bitmap.width, bitmap.height).apply {
            Canvas(this).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
        } else bitmap
        try {
            checkMessage(flattened.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, options.quality, stream), R.string.image_save_failed)
        } finally { if (flattened !== bitmap) flattened.recycle() }
    }

    fun decode(context: Context, uri: Uri, maxEdge: Int): Bitmap {
        val source = if (uri.scheme == "file" && uri.path.orEmpty().startsWith("/android_asset/")) {
            ImageDecoder.createSource(context.assets, uri.path!!.removePrefix("/android_asset/"))
        } else ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (width, height) = fitWithin(info.size.width, info.size.height, maxEdge)
            requireMessage(width.toLong() * height * 20 < Runtime.getRuntime().maxMemory() * .65, R.string.image_memory_limit)
            decoder.setTargetSize(width, height)
        }
    }

    fun render(source: Bitmap, recipe: PhotoRecipe, checkActive: () -> Unit = {}): Bitmap {
        checkActive()
        val matrix = Matrix().apply {
            postRotate((recipe.quarterTurns.mod(4) * 90).toFloat())
            if (recipe.flipHorizontal) postScale(-1f, 1f)
        }
        val frame = android.graphics.RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        matrix.mapRect(frame)
        matrix.postTranslate(-frame.left, -frame.top)
        val width = frame.width().roundToInt(); val height = frame.height().roundToInt()
        if (recipe.perspectiveX != 0f || recipe.perspectiveY != 0f) matrix.postConcat(PhotoPerspective.matrix(width, height, recipe.perspectiveX, recipe.perspectiveY))
        val crop = recipe.boundsFor(width.toFloat() / height).pixels(width, height)
        matrix.postTranslate(-crop.x.toFloat(), -crop.y.toFloat())
        val result = createBitmap(crop.width, crop.height)
        try {
                Canvas(result).drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = colorFilter(recipe) })
                adjust(result, recipe, checkActive)
                for (key in recipe.layerKeys()) {
                    if (key.startsWith("text:")) PhotoTextRenderer.draw(result, recipe.texts.filter { "text:${it.id}" == key }, checkActive)
                    else PhotoMarkupRenderer.draw(result, recipe.markups.filter { "mark:${it.id}" == key }, checkActive)
                }
                checkActive()
                return result
        } catch (failure: Throwable) { result.recycle(); throw failure }
    }

    internal fun colorFilter(recipe: PhotoRecipe): ColorMatrixColorFilter {
                val contrast = recipe.contrast
                val offset = 127.5f * (1 - contrast) + recipe.brightness * 255
                val warmth = recipe.temperature * 24
                val tint = recipe.tint * 20
                val color = ColorMatrix().apply {
                    setSaturation(recipe.saturation)
                    postConcat(ColorMatrix(floatArrayOf(
                        contrast, 0f, 0f, 0f, offset,
                        0f, contrast, 0f, 0f, offset,
                        0f, 0f, contrast, 0f, offset,
                        0f, 0f, 0f, 1f, 0f,
                    )))
                    postConcat(ColorMatrix(floatArrayOf(
                        1f, 0f, 0f, 0f, warmth + tint / 2,
                        0f, 1f, 0f, 0f, -tint,
                        0f, 0f, 1f, 0f, -warmth + tint / 2,
                        0f, 0f, 0f, 1f, 0f,
                    )))
                }
        return ColorMatrixColorFilter(color)
    }
    internal fun adjust(bitmap: Bitmap, recipe: PhotoRecipe, checkActive: () -> Unit, originX: Int = 0, originY: Int = 0,
        frameWidth: Int = bitmap.width, frameHeight: Int = bitmap.height) {
                if (recipe.exposure != 0f) applyExposure(bitmap, recipe.exposure, checkActive)
                if (recipe.highlights != 0f || recipe.shadows != 0f) applyTones(bitmap, recipe.highlights, recipe.shadows, checkActive)
                if (!recipe.curves.isNeutral) applyCurves(bitmap, recipe.curves, checkActive)
                if (!recipe.hsl.neutral || recipe.masks.isNotEmpty()) PhotoAdjustmentsRenderer.apply(bitmap, recipe.hsl, recipe.masks, checkActive, originX, originY, frameWidth, frameHeight)
                if (recipe.sharpness != 0f) applySharpness(bitmap, recipe.sharpness, checkActive)
    }


    private fun applyExposure(bitmap: Bitmap, stops: Float, checkActive: () -> Unit) {
        val factor = Math.pow(2.0, stops.toDouble())
        val lookup = IntArray(256) { value ->
            val srgb = value / 255.0
            val linear = if (srgb <= .04045) srgb / 12.92 else Math.pow((srgb + .055) / 1.055, 2.4)
            val adjusted = (linear * factor).coerceIn(0.0, 1.0)
            val encoded = if (adjusted <= .0031308) adjusted * 12.92 else 1.055 * Math.pow(adjusted, 1 / 2.4) - .055
            (encoded * 255).roundToInt().coerceIn(0, 255)
        }

        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            checkActive()
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                val pixel = row[x]
                row[x] = (pixel and -0x1000000) or (lookup[(pixel ushr 16) and 255] shl 16) or
                    (lookup[(pixel ushr 8) and 255] shl 8) or lookup[pixel and 255]
            }
            bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
    }


    private fun applyTones(bitmap: Bitmap, highlights: Float, shadows: Float, checkActive: () -> Unit) {
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            checkActive()
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                val pixel = row[x]
                if (pixel ushr 24 == 0) continue
                val luma = luminance(pixel) / 255f
                val inverse = 1f - luma
                val delta = 1.5f * luma * inverse * (shadows * inverse * inverse + highlights * luma * luma)
                if (delta == 0f) continue
                val mix = (if (delta > 0f) delta / inverse else -delta / luma).coerceIn(0f, 1f)
                val target = if (delta > 0f) 255f else 0f
                fun channel(shift: Int): Int {
                    val value = (pixel ushr shift) and 255
                    return (value + (target - value) * mix).roundToInt().coerceIn(0, 255)
                }
                row[x] = (pixel and -0x1000000) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
            }
            bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
    }


    private fun applyCurves(bitmap: Bitmap, curves: PhotoCurves, checkActive: () -> Unit) {
        val rgb = ToneCurveSampler(curves.rgb).lookup()
        val red = ToneCurveSampler(curves.red).lookup()
        val green = ToneCurveSampler(curves.green).lookup()
        val blue = ToneCurveSampler(curves.blue).lookup()
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            checkActive()
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                val pixel = row[x]
                if (pixel ushr 24 == 0) continue
                row[x] = (pixel and -0x1000000) or (red[rgb[(pixel ushr 16) and 255]] shl 16) or
                    (green[rgb[(pixel ushr 8) and 255]] shl 8) or blue[rgb[pixel and 255]]
            }
            bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
    }



    private fun applySharpness(bitmap: Bitmap, strength: Float, checkActive: () -> Unit) {
        val width = bitmap.width
        var previous = IntArray(width)
        var current = IntArray(width)
        var next = IntArray(width)
        val output = IntArray(width)
        bitmap.getPixels(current, 0, width, 0, 0, width, 1)
        current.copyInto(previous)
        bitmap.getPixels(next, 0, width, 0, minOf(1, bitmap.height - 1), width, 1)
        for (y in 0 until bitmap.height) {
            checkActive()
            for (x in 0 until width) {
                val pixel = current[x]
                if (pixel ushr 24 == 0) { output[x] = pixel; continue }
                var sum = 0f
                var total = 0f
                for (dy in -1..1) {
                    val line = when (dy) { -1 -> previous; 0 -> current; else -> next }
                    for (dx in -1..1) {
                        val neighbor = line[(x + dx).coerceIn(0, width - 1)]
                        val weight = (neighbor ushr 24).toFloat() * (if (dy == 0) 2 else 1) * (if (dx == 0) 2 else 1)
                        sum += luminance(neighbor) * weight
                        total += weight
                    }
                }
                val detail = luminance(pixel) - sum / total

                val delta = if (kotlin.math.abs(detail) < 2f) 0 else (detail * strength * 1.5f).roundToInt()
                fun channel(shift: Int) = (((pixel ushr shift) and 255) + delta).coerceIn(0, 255)
                output[x] = (pixel and -0x1000000) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
            }
            bitmap.setPixels(output, 0, width, 0, y, width, 1)
            val reusable = previous
            previous = current; current = next; next = reusable
            if (y + 2 < bitmap.height) bitmap.getPixels(next, 0, width, 0, y + 2, width, 1)
            else current.copyInto(next)
        }
    }

    private fun luminance(pixel: Int): Float = (((pixel ushr 16) and 255) * 54 +
        ((pixel ushr 8) and 255) * 183 + (pixel and 255) * 19) / 256f
}
