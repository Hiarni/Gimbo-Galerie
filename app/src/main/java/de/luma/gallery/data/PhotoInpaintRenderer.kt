package de.luma.gallery.data

import android.graphics.*
import de.luma.gallery.model.*
import kotlin.math.*


object PhotoInpaintRenderer {
    fun draw(bitmap: Bitmap, mark: PhotoMarkup, check: () -> Unit, frameWidth: Int = bitmap.width, frameHeight: Int = bitmap.height, originX: Int = 0, originY: Int = 0) {
        val radius = maxOf(1f, minOf(frameWidth, frameHeight) * mark.width / 2)
        val left = floor(mark.left * frameWidth - originX - radius - 4).toInt().coerceAtLeast(0)
        val top = floor(mark.top * frameHeight - originY - radius - 4).toInt().coerceAtLeast(0)
        val right = ceil(mark.right * frameWidth - originX + radius + 4).toInt().coerceAtMost(bitmap.width)
        val bottom = ceil(mark.bottom * frameHeight - originY + radius + 4).toInt().coerceAtMost(bitmap.height)
        val width = right - left; val height = bottom - top
        if (width <= 0 || height <= 0) return
        val selection = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = radius * 2; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; style = Paint.Style.STROKE }
            val canvas = Canvas(selection)
            val path = Path().apply { mark.points.forEachIndexed { i, p -> check(); val x = p.x * frameWidth - originX - left; val y = p.y * frameHeight - originY - top; if (i == 0) moveTo(x,y) else lineTo(x,y) } }
            if (mark.points.size == 1) canvas.drawPoint(mark.points[0].x * frameWidth-originX-left, mark.points[0].y * frameHeight-originY-top, paint) else canvas.drawPath(path, paint)
            val pixels = IntArray(width * height); bitmap.getPixels(pixels, 0, width, left, top, width, height)
            val original = pixels.copyOf(); val mask = BooleanArray(pixels.size); val weights = FloatArray(pixels.size)
            for (y in 0 until height) { check(); for (x in 0 until width) {
                val index = y * width + x; val alpha = Color.alpha(selection.getPixel(x,y)); mask[index] = alpha > 0; weights[index] = alpha / 255f * mark.opacity
            } }
            val pending = mask.copyOf(); val queue = java.util.ArrayDeque<Int>(); val queued = BooleanArray(mask.size)
            fun enqueue(index: Int) { if (index in pending.indices && pending[index] && !queued[index]) { queued[index] = true; queue.add(index) } }
            for (y in 0 until height) for (x in 0 until width) { val i=y*width+x; if (!pending[i]) { if (x>0) enqueue(i-1); if(x+1<width) enqueue(i+1); if(y>0) enqueue(i-width); if(y+1<height) enqueue(i+width) } }
            while (queue.isNotEmpty()) {
                check(); val i=queue.removeFirst(); val x=i%width; val y=i/width
                var sum=0.0; val color=DoubleArray(3)
                for (dy in -3..3) for(dx in -3..3) {
                    val nx=x+dx; val ny=y+dy
                    if(nx !in 0 until width || ny !in 0 until height || dx==0 && dy==0) continue
                    val n=ny*width+nx; if(pending[n] || Color.alpha(pixels[n])==0) continue
                    val weight=1.0/(dx*dx+dy*dy); sum+=weight
                    color[0]+=Color.red(pixels[n])*weight; color[1]+=Color.green(pixels[n])*weight; color[2]+=Color.blue(pixels[n])*weight
                }
                if(sum==0.0) continue
                pixels[i]=Color.argb(Color.alpha(original[i]), (color[0]/sum).roundToInt(), (color[1]/sum).roundToInt(), (color[2]/sum).roundToInt()); pending[i]=false
                if(x>0) enqueue(i-1); if(x+1<width) enqueue(i+1); if(y>0) enqueue(i-width); if(y+1<height) enqueue(i+width)
            }

            for(i in pixels.indices) if(mask[i] && !pending[i]) pixels[i]=androidx.core.graphics.ColorUtils.blendARGB(original[i], pixels[i], weights[i])
            bitmap.setPixels(pixels, 0, width, left, top, width, height)
        } finally { selection.recycle() }
    }
}
