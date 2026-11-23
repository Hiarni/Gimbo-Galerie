package de.luma.gallery.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import de.luma.gallery.model.*
import kotlin.math.*

internal object DiskPhotoLayers {
    fun draw(source: DiskRaster,tile: Bitmap,mark: PhotoMarkup,left: Int,top: Int,check: ()->Unit, averages: MutableMap<Long, Int> = mutableMapOf()) {
        val padding = minOf(source.width, source.height) * maxOf(mark.width * 4, .08f)
        if (left + tile.width < mark.left * source.width - padding || left > mark.right * source.width + padding || top + tile.height < mark.top * source.height - padding || top > mark.bottom * source.height + padding) return
        if(mark.kind !in listOf(MarkupKind.PIXELATE,MarkupKind.CLONE,MarkupKind.HEAL)) {
            PhotoMarkupRenderer.draw(tile,listOf(mark),check,source.width,source.height,left,top);return
        }
        val radius=maxOf(1f,minOf(source.width,source.height)*mark.width/2)
        val block=maxOf(1,(minOf(source.width,source.height)*mark.blockSize).roundToInt())
        val ml=floor(mark.left*source.width).toInt();val mt=floor(mark.top*source.height).toInt()
        val mr=ceil(mark.right*source.width).toInt();val mb=ceil(mark.bottom*source.height).toInt()
        val fromX=mark.sample.x*source.width;val fromY=mark.sample.y*source.height
        val toX=mark.points.first().x*source.width;val toY=mark.points.first().y*source.height
        fun average(cx: Float,cy: Float): IntArray {
            val sum=LongArray(3);var count=0
            val r=radius.roundToInt().coerceAtLeast(2);val step=maxOf(1,r/16)
            for(y in (cy.toInt()-r).coerceAtLeast(0)..(cy.toInt()+r).coerceAtMost(source.height-1) step step)
                for(x in (cx.toInt()-r).coerceAtLeast(0)..(cx.toInt()+r).coerceAtMost(source.width-1) step step) {
                    val p=source.pixel(x,y);if(Color.alpha(p)>0){sum[0]+=Color.red(p);sum[1]+=Color.green(p);sum[2]+=Color.blue(p);count++}
                }
            return IntArray(3){(sum[it]/count.coerceAtLeast(1)).toInt()}
        }
        val delta=if(mark.kind==MarkupKind.HEAL){
            if (Long.MIN_VALUE !in averages) { val from=average(fromX,fromY);val to=average(toX,toY);for(i in 0..2)averages[Long.MIN_VALUE+i]=to[i]-from[i] }
            IntArray(3){averages.getValue(Long.MIN_VALUE+it)}
        }else IntArray(3)
        val row=IntArray(tile.width)
        for(y in 0 until tile.height) {
            check();tile.getPixels(row,0,row.size,0,y,row.size,1)
            for(x in row.indices) {
                val gx=left+x;val gy=top+y
                var weight=mark.opacity
                val color=if(mark.kind==MarkupKind.PIXELATE) {
                    if(gx !in ml until mr || gy !in mt until mb)continue
                    val bx=ml+(gx-ml)/block*block;val by=mt+(gy-mt)/block*block
                    val key=(by.toLong() shl 32) or bx.toLong()
                    averages.getOrPut(key) {
                        val endX=minOf(mr,bx+block,source.width);val endY=minOf(mb,by+block,source.height)
                        var a=0L;var r=0L;var g=0L;var b=0L;var n=0
                        for(py in by.coerceAtLeast(0) until endY) {check();for(p in source.row(py,bx.coerceAtLeast(0),(endX-bx.coerceAtLeast(0)).coerceAtLeast(0))){val alpha=Color.alpha(p);a+=alpha;r+=Color.red(p)*alpha;g+=Color.green(p)*alpha;b+=Color.blue(p)*alpha;n++}}
                        Color.argb((a/n.coerceAtLeast(1)).toInt(),(r/a.coerceAtLeast(1)).toInt(),(g/a.coerceAtLeast(1)).toInt(),(b/a.coerceAtLeast(1)).toInt())
                    }
                } else {
                    var distance=Float.MAX_VALUE
                    for(i in mark.points.indices) {
                        val a=mark.points[i];val b=mark.points[minOf(i+1,mark.points.lastIndex)]
                        val ax=a.x*source.width;val ay=a.y*source.height;val dx=(b.x-a.x)*source.width;val dy=(b.y-a.y)*source.height
                        val t=if(dx*dx+dy*dy==0f)0f else (((gx+.5f-ax)*dx+(gy+.5f-ay)*dy)/(dx*dx+dy*dy)).coerceIn(0f,1f)
                        distance=minOf(distance,hypot(gx+.5f-ax-t*dx,gy+.5f-ay-t*dy))
                    }
                    if(distance>=radius)continue
                    if(mark.kind==MarkupKind.HEAL)weight*=((radius-distance)/(radius*.2f)).coerceIn(0f,1f)
                    val p=source.pixel((gx+fromX-toX).roundToInt(),(gy+fromY-toY).roundToInt())
                    Color.argb(Color.alpha(p),(Color.red(p)+delta[0]).coerceIn(0,255),(Color.green(p)+delta[1]).coerceIn(0,255),(Color.blue(p)+delta[2]).coerceIn(0,255))
                }
                row[x]=ColorUtils.blendARGB(row[x],color,weight)
            }
            tile.setPixels(row,0,row.size,0,y,row.size,1)
        }
    }
    fun inpaint(source: DiskRaster,target: DiskRaster,mark: PhotoMarkup,check: ()->Unit) {
        val radius=minOf(source.width,source.height)*mark.width/2+5
        val left=floor(mark.left*source.width-radius).toInt().coerceAtLeast(0);val top=floor(mark.top*source.height-radius).toInt().coerceAtLeast(0)
        val right=ceil(mark.right*source.width+radius).toInt().coerceAtMost(source.width);val bottom=ceil(mark.bottom*source.height+radius).toInt().coerceAtMost(source.height)
        requireMessage((right-left).toLong()*(bottom-top)*40 < Runtime.getRuntime().maxMemory()*.3,R.string.repair_region_too_large)
        if(right<=left || bottom<=top)return
        val region=source.read(left,top,right-left,bottom-top)
        try {PhotoInpaintRenderer.draw(region,mark,check,source.width,source.height,left,top);target.write(region,left,top)} finally {region.recycle()}
    }
}
