package de.luma.gallery.data

import android.content.Context
import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import java.io.File
import java.io.OutputStream
import kotlin.math.*


internal class TiledPhotoExporter(private val context: Context, private val regions: (File) -> PhotoRegions? = { PlatformPhotoRegions.open(it) }) {
    suspend fun render(item: GalleryMedia, recipe: PhotoRecipe, options: PhotoExportOptions, output: OutputStream): Pair<Int,Int> = withContext(Dispatchers.IO) {
        val source=File.createTempFile("luma_tile_source_",".bin",context.cacheDir)
        val files=mutableListOf(source)
        var raster: DiskRaster?=null
        val active=currentCoroutineContext();val check={active.ensureActive()}
        try {
            val input=if(item.isDemo) context.assets.open(item.uri.path!!.removePrefix("/android_asset/")) else requireNotNull(context.contentResolver.openInputStream(item.uri))
            input.use { stream -> source.outputStream().use { target -> val buffer=ByteArray(65536);while(true){check();val n=stream.read(buffer);if(n<0)break;target.write(buffer,0,n)} } }
            val decoder=runCatching { regions(source) }.getOrNull()
            requireMessage(decoder!=null,R.string.large_format_unsupported)
            val region=requireNotNull(decoder)
            try {
                val exif=runCatching{ExifInterface(source)}.getOrNull()
                val scale=minOf(1f,options.maxEdge.toFloat()/maxOf(region.width,region.height))
                val matrix=Matrix().apply {
                    setScale(scale,scale)
                    if(exif?.isFlipped==true) postScale(-1f,1f)
                    postRotate((exif?.rotationDegrees ?: 0).toFloat())
                }
                fun normalize(): RectF { val frame=RectF(0f,0f,region.width.toFloat(),region.height.toFloat());matrix.mapRect(frame);matrix.postTranslate(-frame.left,-frame.top);return frame }
                normalize()
                matrix.postRotate((recipe.quarterTurns.mod(4)*90).toFloat());if(recipe.flipHorizontal)matrix.postScale(-1f,1f)
                val frame=normalize();val fw=frame.width().roundToInt().coerceAtLeast(1);val fh=frame.height().roundToInt().coerceAtLeast(1)
                if(recipe.perspectiveX!=0f || recipe.perspectiveY!=0f)matrix.postConcat(PhotoPerspective.matrix(fw,fh,recipe.perspectiveX,recipe.perspectiveY))
                val crop=recipe.boundsFor(fw.toFloat()/fh).pixels(fw,fh);matrix.postTranslate(-crop.x.toFloat(),-crop.y.toFloat())
                val inverse=Matrix();check(matrix.invert(inverse))
                val file=File.createTempFile("luma_tile_",".argb",context.cacheDir);files+=file
                val initial=DiskRaster(file,crop.width,crop.height);raster=initial
                var sample=1;while(sample*2 <= 1/scale)sample*=2
                for(y in 0 until crop.height step DiskRaster.TILE)for(x in 0 until crop.width step DiskRaster.TILE) {
                    check();val w=minOf(DiskRaster.TILE,crop.width-x);val h=minOf(DiskRaster.TILE,crop.height-y)
                    val left=maxOf(0,x-2);val top=maxOf(0,y-2);val right=minOf(crop.width,x+w+2);val bottom=minOf(crop.height,y+h+2)
                    val bitmap=Bitmap.createBitmap(right-left,bottom-top,Bitmap.Config.ARGB_8888)
                    try {
                        val raw=RectF(left.toFloat(),top.toFloat(),right.toFloat(),bottom.toFloat());inverse.mapRect(raw)
                        val bounds=Rect(floor(raw.left).toInt().coerceIn(0,region.width),floor(raw.top).toInt().coerceIn(0,region.height),ceil(raw.right).toInt().coerceIn(0,region.width),ceil(raw.bottom).toInt().coerceIn(0,region.height))
                        if(bounds.width()>0 && bounds.height()>0) {
                            requireMessage(((bounds.width()+sample-1)/sample).toLong()*((bounds.height()+sample-1)/sample)*4 < Runtime.getRuntime().maxMemory()*.25, R.string.image_memory_limit)
                            val decoded=requireNotNull(region.decode(bounds,sample))
                            try {
                                val local=Matrix(matrix);local.preTranslate(bounds.left.toFloat(),bounds.top.toFloat());local.preScale(bounds.width().toFloat()/decoded.width,bounds.height().toFloat()/decoded.height);local.postTranslate(-left.toFloat(),-top.toFloat())
                                Canvas(bitmap).drawBitmap(decoded,local,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply{colorFilter=PhotoEngine.colorFilter(recipe)})
                            } finally { decoded.recycle() }
                        }
                        PhotoEngine.adjust(bitmap,recipe,check,left,top,crop.width,crop.height)
                        initial.write(bitmap,x,y,x-left,y-top,w,h)
                    } finally {bitmap.recycle()}
                }
            } finally {region.close()}
            for(key in recipe.layerKeys()) {
                check();val current=requireNotNull(raster)
                val text=recipe.texts.find{"text:${it.id}"==key};val mark=recipe.markups.find{"mark:${it.id}"==key}
                if(text?.let{!it.visible || it.opacity==0f}==true || mark?.let{!it.visible || it.opacity==0f}==true)continue
                val file=File.createTempFile("luma_tile_",".argb",context.cacheDir);files+=file
                val next=DiskRaster(file,current.width,current.height)
                try {
                    val blockAverages = mutableMapOf<Long, Int>()
                    if(mark?.kind==MarkupKind.INPAINT) {
                        current.file.inputStream().use{input->file.outputStream().use{out->val buffer=ByteArray(65536);while(true){check();val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n)}}}
                        DiskPhotoLayers.inpaint(current,next,mark,check)
                    } else for(y in 0 until current.height step DiskRaster.TILE)for(x in 0 until current.width step DiskRaster.TILE) {
                        check();val tile=current.read(x,y,minOf(DiskRaster.TILE,current.width-x),minOf(DiskRaster.TILE,current.height-y))
                        try {
                            if(text!=null && PhotoTextRenderer.bounds(current.width,current.height,text).intersects(x.toFloat(),y.toFloat(),(x+tile.width).toFloat(),(y+tile.height).toFloat()))PhotoTextRenderer.draw(tile,listOf(text),check,current.width,current.height,x,y)
                            else if(mark!=null)DiskPhotoLayers.draw(current,tile,mark,x,y,check,blockAverages)
                            next.write(tile,x,y)
                        } finally {tile.recycle()}
                    }
                } catch(e: Throwable){next.close();throw e}
                current.close();current.file.delete();raster=next
            }
            val result=requireNotNull(raster)
            if(options.format==PhotoFormat.PNG)RasterEncoders.png(result,output,check) else RasterEncoders.jpeg(result,options.quality,output,check)
            result.width to result.height
        } finally {raster?.close();files.forEach{it.delete()} }
    }
}
