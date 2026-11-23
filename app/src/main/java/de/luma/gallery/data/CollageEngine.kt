package de.luma.gallery.data

import android.content.Context
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import android.graphics.*
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

object CollageEngine {
    suspend fun render(context:Context,items:List<GalleryMedia>,spec:CollageSpec,edge:Int):Bitmap=withContext(Dispatchers.IO) {
        require(items.size==spec.tiles.size && items.all {!it.isVideo} && edge in 128..4096)
        val width=if(spec.ratio>=1) edge else (edge*spec.ratio).toInt().coerceAtLeast(1)
        val height=if(spec.ratio>=1) (edge/spec.ratio).toInt().coerceAtLeast(1) else edge
        requireMessage(width.toLong()*height*6<Runtime.getRuntime().maxMemory()*.65,R.string.image_memory_limit)
        val result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(result);canvas.drawColor(spec.background.toInt())
            val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            spec.cells().forEachIndexed {index,cell->
                ensureActive()
                val image=PhotoEngine.decode(context,items[index].uri,minOf(edge,2048))
                try {drawTile(canvas,image,spec.tiles[index],RectF(cell.left*width,cell.top*height,cell.right*width,cell.bottom*height),spec.corner*minOf(width,height),paint)} finally {image.recycle()}
            }
            ensureActive();result
        } catch(error:Throwable) {result.recycle();throw error}
    }
    internal fun drawTile(canvas:Canvas,image:Bitmap,tile:CollageTile,bounds:RectF,corner:Float,paint:Paint) {
        val transform=Matrix().apply {postRotate(tile.quarterTurns*90f)}
        val rotated=RectF(0f,0f,image.width.toFloat(),image.height.toFloat());transform.mapRect(rotated)
        transform.postTranslate(-rotated.left,-rotated.top)
        val scale=max(bounds.width()/rotated.width(),bounds.height()/rotated.height())*tile.zoom
        transform.postScale(scale,scale)
        val width=rotated.width()*scale;val height=rotated.height()*scale
        transform.postTranslate(bounds.left+(bounds.width()-width)/2-tile.panX*(width-bounds.width())/2,bounds.top+(bounds.height()-height)/2-tile.panY*(height-bounds.height())/2)
        canvas.save()
        canvas.clipPath(Path().apply {addRoundRect(bounds,minOf(corner,bounds.width()/2,bounds.height()/2),minOf(corner,bounds.width()/2,bounds.height()/2),Path.Direction.CW)})
        canvas.drawBitmap(image,transform,paint);canvas.restore()
    }
    fun json(spec:CollageSpec):String=JSONObject().put("layout",spec.layout.name).put("ratio",spec.ratio).put("gap",spec.gap).put("corner",spec.corner).put("background",spec.background)
        .put("tiles",JSONArray(spec.tiles.map {JSONObject().put("zoom",it.zoom).put("x",it.panX).put("y",it.panY).put("turns",it.quarterTurns)})).toString()
    fun read(value:String):CollageSpec {
        require(value.length<=20_000)
        val json=JSONObject(value);val tiles=json.getJSONArray("tiles");require(tiles.length() in 2..9)
        return CollageSpec(CollageLayout.valueOf(json.getString("layout")),json.getDouble("ratio").toFloat(),json.getDouble("gap").toFloat(),json.getDouble("corner").toFloat(),json.getLong("background"),(0 until tiles.length()).map {i->tiles.getJSONObject(i).let {CollageTile(it.getDouble("zoom").toFloat(),it.getDouble("x").toFloat(),it.getDouble("y").toFloat(),it.getInt("turns"))}})
    }
}
