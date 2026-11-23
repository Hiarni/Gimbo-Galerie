package de.luma.gallery.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withSave
import de.luma.gallery.model.PhotoText
import kotlin.math.ceil


object PhotoTextRenderer {
    private data class Caption(val layout: StaticLayout, val left: Float, val top: Float, val frame: RectF)

    private fun caption(width: Int, height: Int, text: PhotoText): Caption {
        require(width > 0 && height > 0)
        val shortEdge = minOf(width, height).toFloat()
        val padding = shortEdge * .012f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = text.color.toInt()
            typeface = Typeface.create("sans-serif", if (text.bold) Typeface.BOLD else Typeface.NORMAL)
            textSize = shortEdge * text.size
        }
        val maxWidth = maxOf(1f, width * .9f - 2 * padding)

        val measured = text.text.split('\n').maxOf(paint::measureText)
        if (measured > maxWidth) paint.textSize *= maxWidth / measured * .98f
        fun layout() = StaticLayout.Builder.obtain(text.text, 0, text.text.length, paint, maxOf(1, ceil(maxWidth).toInt()))
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        var layout = layout()
        if (layout.height + 2 * padding > height * .9f) {
            paint.textSize *= (height * .9f - 2 * padding).coerceAtLeast(.01f) / layout.height.coerceAtLeast(1)
            layout = layout()
        }
        val drawnWidth = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
        val frameWidth = minOf(width.toFloat(), drawnWidth + 2 * padding)
        val frameHeight = minOf(height.toFloat(), layout.height + 2 * padding)
        val centerX = (text.x * width).coerceIn(frameWidth / 2, width - frameWidth / 2)
        val centerY = (text.y * height).coerceIn(frameHeight / 2, height - frameHeight / 2)
        val left = centerX - layout.width / 2f
        val top = centerY - layout.height / 2f
        return Caption(layout, left, top, RectF(centerX - frameWidth / 2, centerY - frameHeight / 2,
            centerX + frameWidth / 2, centerY + frameHeight / 2))
    }

    fun bounds(width: Int, height: Int, text: PhotoText): RectF = caption(width, height, text).frame

    fun draw(bitmap: Bitmap, texts: List<PhotoText>, checkActive: () -> Unit, frameWidth: Int = bitmap.width, frameHeight: Int = bitmap.height, originX: Int = 0, originY: Int = 0) {
        val canvas = Canvas(bitmap).apply { translate(-originX.toFloat(), -originY.toFloat()) }
        for (text in texts.filter { it.visible && it.opacity > 0f }) {
            checkActive()
            val caption = caption(frameWidth, frameHeight, text)
            canvas.withSave {
            saveLayerAlpha(caption.frame, (text.opacity * 255).toInt())
            if (text.background) {
                val background = if (ColorUtils.calculateLuminance(text.color.toInt()) > .45) 0xB0000000.toInt() else 0xD0FFFFFF.toInt()
                val radius = minOf(frameWidth, frameHeight) * .012f
                canvas.drawRoundRect(caption.frame, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
            }
            canvas.withSave {
                translate(caption.left, caption.top)
                caption.layout.draw(this)
            }
            }
        }
    }
}
