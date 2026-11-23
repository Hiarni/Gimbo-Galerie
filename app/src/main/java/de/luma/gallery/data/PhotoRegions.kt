package de.luma.gallery.data

import android.graphics.*
import java.io.File

internal interface PhotoRegions: AutoCloseable {
    val width: Int
    val height: Int
    fun decode(bounds: Rect,sample: Int): Bitmap?
}
internal class PlatformPhotoRegions private constructor(private val decoder: BitmapRegionDecoder): PhotoRegions {
    override val width get()=decoder.width
    override val height get()=decoder.height
    override fun decode(bounds: Rect,sample: Int)=decoder.decodeRegion(bounds,BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888})
    override fun close()=decoder.recycle()
    companion object {
        @Suppress("DEPRECATION")
        fun open(file: File)=BitmapRegionDecoder.newInstance(file.path,false)?.let{PlatformPhotoRegions(it)}
    }
}
