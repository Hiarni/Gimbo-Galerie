package de.luma.gallery.data

import android.graphics.Bitmap
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer


internal class DiskRaster(val file: File, val width: Int, val height: Int) : AutoCloseable {
    private val data=RandomAccessFile(file,"rw")
    private val cache=object : LinkedHashMap<Long,IntArray>(16,.75f,true) { override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long,IntArray>)=size>16 }
    init { require(width>0 && height>0); data.setLength(width.toLong()*height*4) }
    fun read(x: Int,y: Int,w: Int,h: Int): Bitmap {
        val pixels=IntArray(w*h); val bytes=ByteArray(w*4)
        for(row in 0 until h) { data.seek(((y+row).toLong()*width+x)*4);data.readFully(bytes);val values=ByteBuffer.wrap(bytes).asIntBuffer();values.get(pixels,row*w,w) }
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply { setPixels(pixels,0,w,0,0,w,h) }
    }
    fun write(bitmap: Bitmap,x: Int,y: Int,sx: Int=0,sy: Int=0,w: Int=bitmap.width,h: Int=bitmap.height) {
        val row=IntArray(w);val bytes=ByteArray(w*4)
        for(i in 0 until h) { bitmap.getPixels(row,0,w,sx,sy+i,w,1);ByteBuffer.wrap(bytes).asIntBuffer().put(row);data.seek(((y+i).toLong()*width+x)*4);data.write(bytes) }
        cache.clear()
    }
    fun row(y: Int, x: Int = 0, count: Int = width): IntArray {
        val bytes=ByteArray(count*4); data.seek((y.toLong()*width+x)*4); data.readFully(bytes)
        return IntArray(count).apply { ByteBuffer.wrap(bytes).asIntBuffer().get(this) }
    }
    fun pixel(x: Int,y: Int): Int {
        val px=x.coerceIn(0,width-1);val py=y.coerceIn(0,height-1);val tx=px/TILE;val ty=py/TILE
        val key=(ty.toLong() shl 32) or tx.toLong()
        val values=cache.getOrPut(key) {
            val w=minOf(TILE,width-tx*TILE);val h=minOf(TILE,height-ty*TILE)
            val tile=read(tx*TILE,ty*TILE,w,h)
            try { IntArray(TILE*TILE).apply { tile.getPixels(this,0,TILE,0,0,w,h) } } finally { tile.recycle() }
        }
        return values[(py%TILE)*TILE+px%TILE]
    }
    override fun close() { cache.clear();data.close() }
    companion object { const val TILE=128 }
}
