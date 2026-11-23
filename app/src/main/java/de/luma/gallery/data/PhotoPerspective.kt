package de.luma.gallery.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.createBitmap

object PhotoPerspective {
    fun matrix(width: Int, height: Int, horizontal: Float, vertical: Float): Matrix {
        val w = width.toFloat(); val h = height.toFloat()
        val top = maxOf(vertical, 0f) * w; val bottom = maxOf(-vertical, 0f) * w
        val left = maxOf(horizontal, 0f) * h; val right = maxOf(-horizontal, 0f) * h
        return Matrix().apply {
            check(setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0,
                floatArrayOf(top, left, w - top, right, w - bottom, h - right, bottom, h - left), 0, 4))
        }
    }
}
