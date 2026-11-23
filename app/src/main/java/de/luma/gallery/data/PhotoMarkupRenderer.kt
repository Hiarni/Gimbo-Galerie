package de.luma.gallery.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import de.luma.gallery.model.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

object PhotoMarkupRenderer {

    fun draw(bitmap: Bitmap, markups: List<PhotoMarkup>, checkActive: () -> Unit = {}, frameWidth: Int = bitmap.width, frameHeight: Int = bitmap.height, originX: Int = 0, originY: Int = 0) {
        markups.filter { it.visible && it.opacity > 0f && it.kind == MarkupKind.PIXELATE }.forEach { pixelate(bitmap, it, checkActive) }
        val canvas = Canvas(bitmap).apply { translate(-originX.toFloat(), -originY.toFloat()) }
        markups.filter { it.visible && it.opacity > 0f && it.kind != MarkupKind.PIXELATE }.forEach { mark ->
            checkActive()
            if (mark.kind == MarkupKind.INPAINT) { PhotoInpaintRenderer.draw(bitmap, mark, checkActive); return@forEach }
            if (mark.kind == MarkupKind.CLONE || mark.kind == MarkupKind.HEAL) { PhotoRetouchRenderer.draw(bitmap, mark, checkActive); return@forEach }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mark.color.toInt(); alpha = (mark.opacity * 255).roundToInt()
                strokeWidth = maxOf(1f, minOf(frameWidth, frameHeight) * mark.width)
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                style = if (mark.filled || mark.kind == MarkupKind.COVER) Paint.Style.FILL else Paint.Style.STROKE
            }
            val frame = bounds(frameWidth, frameHeight, mark)
            when (mark.kind) {
                MarkupKind.BRUSH -> {
                    paint.style = Paint.Style.STROKE
                    val path = Path()
                    mark.points.forEachIndexed { index, point ->
                        if (index == 0) path.moveTo(point.x * frameWidth, point.y * frameHeight)
                        else path.lineTo(point.x * frameWidth, point.y * frameHeight)
                    }
                    if (mark.points.size == 1) canvas.drawPoint(mark.points[0].x * frameWidth, mark.points[0].y * frameHeight, paint)
                    else canvas.drawPath(path, paint)
                }
                MarkupKind.ARROW -> {
                    paint.style = Paint.Style.STROKE
                    val start = mark.points.first(); val end = mark.points.last()
                    val x = end.x * frameWidth; val y = end.y * frameHeight
                    val angle = kotlin.math.atan2((end.y - start.y) * frameHeight, (end.x - start.x) * frameWidth)
                    val length = maxOf(paint.strokeWidth * 4, minOf(frameWidth, frameHeight) * .05f)
                    canvas.drawLine(start.x * frameWidth, start.y * frameHeight, x, y, paint)
                    for (offset in listOf(-.5f, .5f)) canvas.drawLine(x, y,
                        x - kotlin.math.cos(angle + offset) * length, y - kotlin.math.sin(angle + offset) * length, paint)
                }
                MarkupKind.RECTANGLE -> canvas.drawRect(frame, paint)
                MarkupKind.ELLIPSE -> canvas.drawOval(frame, paint)
                MarkupKind.COVER -> {

                    paint.isAntiAlias = false
                    canvas.drawRect(floor(frame.left), floor(frame.top), ceil(frame.right), ceil(frame.bottom), paint)
                }
                MarkupKind.PIXELATE, MarkupKind.CLONE, MarkupKind.HEAL, MarkupKind.INPAINT -> Unit
            }
        }
    }

    fun bounds(width: Int, height: Int, mark: PhotoMarkup) = RectF(mark.left * width, mark.top * height, mark.right * width, mark.bottom * height)

    private fun pixelate(bitmap: Bitmap, mark: PhotoMarkup, checkActive: () -> Unit) {
        val frame = bounds(bitmap.width, bitmap.height, mark)
        val left = floor(frame.left).toInt().coerceIn(0, bitmap.width)
        val top = floor(frame.top).toInt().coerceIn(0, bitmap.height)
        val right = ceil(frame.right).toInt().coerceIn(left, bitmap.width)
        val bottom = ceil(frame.bottom).toInt().coerceIn(top, bitmap.height)
        if (left == right || top == bottom) return
        val block = maxOf(1, (minOf(bitmap.width, bitmap.height) * mark.blockSize).roundToInt())
        val row = IntArray(minOf(block, right - left))
        val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        val canvas = Canvas(bitmap)
        for (y in top until bottom step block) for (x in left until right step block) {
            val w = minOf(block, right - x); val h = minOf(block, bottom - y)
            var alpha = 0L; var red = 0L; var green = 0L; var blue = 0L
            for (dy in 0 until h) {
                checkActive()
                bitmap.getPixels(row, 0, w, x, y + dy, w, 1)
                for (i in 0 until w) {
                    val pixel = row[i]; val a = (pixel ushr 24).toLong()
                    alpha += a; red += ((pixel ushr 16) and 255) * a
                    green += ((pixel ushr 8) and 255) * a; blue += (pixel and 255) * a
                }
            }
            val a = (alpha / (w * h)).toInt()
            fun channel(sum: Long) = if (alpha == 0L) 0 else (sum / alpha).toInt()
            paint.color = (a shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
            if (mark.opacity == 1f) canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
            else for (dy in 0 until h) {
                checkActive()
                bitmap.getPixels(row, 0, w, x, y + dy, w, 1)
                for (i in 0 until w) row[i] = androidx.core.graphics.ColorUtils.blendARGB(row[i], paint.color, mark.opacity)
                bitmap.setPixels(row, 0, w, x, y + dy, w, 1)
            }
        }
    }
}
