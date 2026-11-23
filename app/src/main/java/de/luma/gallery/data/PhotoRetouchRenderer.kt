package de.luma.gallery.data

import android.graphics.*
import de.luma.gallery.model.*
import kotlin.math.roundToInt


object PhotoRetouchRenderer {
    fun draw(bitmap: Bitmap, mark: PhotoMarkup, check: () -> Unit) {
        val source = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        try {
            val shortEdge = minOf(bitmap.width, bitmap.height).toFloat()
            val radius = maxOf(1f, shortEdge * mark.width / 2)
            val first = mark.points.first()
            val sx = mark.sample.x * bitmap.width; val sy = mark.sample.y * bitmap.height
            val dx = first.x * bitmap.width; val dy = first.y * bitmap.height
            var corrected = source
            try {
                if (mark.kind == MarkupKind.HEAL) {
                    fun average(x: Float, y: Float): IntArray {
                        val sums = LongArray(3); var count = 0L
                        val r = maxOf(2, radius.roundToInt())

                        val step = maxOf(1, r / 16)
                        for (py in (y.toInt() - r).coerceAtLeast(0)..(y.toInt() + r).coerceAtMost(bitmap.height - 1) step step)
                            for (px in (x.toInt() - r).coerceAtLeast(0)..(x.toInt() + r).coerceAtMost(bitmap.width - 1) step step) {
                                val pixel = source.getPixel(px, py)
                                if (Color.alpha(pixel) > 0) { sums[0] += Color.red(pixel); sums[1] += Color.green(pixel); sums[2] += Color.blue(pixel); count++ }
                            }
                        return IntArray(3) { (sums[it] / count.coerceAtLeast(1)).toInt() }
                    }
                    val from = average(sx, sy); val to = average(dx, dy)
                    corrected = source.copy(Bitmap.Config.ARGB_8888, true)
                    val row = IntArray(bitmap.width)
                    for (y in 0 until bitmap.height) {
                        check(); source.getPixels(row, 0, row.size, 0, y, row.size, 1)
                        for (x in row.indices) { val p = row[x]; row[x] = Color.argb(Color.alpha(p), (Color.red(p) + to[0] - from[0]).coerceIn(0, 255),
                            (Color.green(p) + to[1] - from[1]).coerceIn(0, 255), (Color.blue(p) + to[2] - from[2]).coerceIn(0, 255)) }
                        corrected.setPixels(row, 0, row.size, 0, y, row.size, 1)
                    }
                }
                check()
                val shader = BitmapShader(corrected, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                shader.setLocalMatrix(Matrix().apply { setTranslate(dx - sx, dy - sy) })
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.shader = shader; alpha = (mark.opacity * 255).roundToInt(); strokeWidth = radius * 2
                    strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; style = Paint.Style.STROKE
                    if (mark.kind == MarkupKind.HEAL) maskFilter = BlurMaskFilter(maxOf(.5f, radius * .2f), BlurMaskFilter.Blur.NORMAL)
                }
                val canvas = Canvas(bitmap)
                if (mark.points.size == 1) canvas.drawPoint(dx, dy, paint)
                else canvas.drawPath(Path().apply { mark.points.forEachIndexed { i, p -> check(); if (i == 0) moveTo(p.x * bitmap.width, p.y * bitmap.height) else lineTo(p.x * bitmap.width, p.y * bitmap.height) } }, paint)
            } finally { if (corrected !== source) corrected.recycle() }
        } finally { source.recycle() }
    }
}
