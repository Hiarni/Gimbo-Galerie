package de.luma.gallery.data

import android.graphics.*
import android.net.Uri
import de.luma.gallery.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollageEngineTest {
    @Test fun collageJournalCountsOneOutputAndPreservesEverySourceAndAdjustment() {
        val items=(1..3).map {GalleryMedia("$it",Uri.parse("content://media/external/images/media/$it"),"$it.jpg","Camera",0)}
        val spec=CollageSpec(layout=CollageLayout.HERO,ratio=.8f,background=0xFF112233,tiles=listOf(CollageTile(2f,.3f,-.2f,1),CollageTile(),CollageTile()))
        val request=ExportRequest(items=items,collage=spec)
        assertEquals(1,request.outputCount);assertEquals(request,ExportRequest.read(request.json()))
        assertEquals(spec,CollageEngine.read(CollageEngine.json(spec)))
        assertTrue(runCatching {request.copy(completed=2)}.isFailure)
        assertTrue(runCatching {request.copy(photo=PhotoRecipe())}.isFailure)
        assertTrue(runCatching {request.copy(items=items.dropLast(1))}.isFailure)
        val photo=ExportRequest(items=items.take(2),photo=PhotoRecipe())
        assertEquals(2,photo.outputCount)
        assertTrue(photo.outputName(0).startsWith("Gimbo-Galerie_"))
        val previous=photo.copy(outputPrefix="Gimbo",completed=1)
        val resumed=ExportRequest.read(previous.json())
        assertEquals(previous,resumed);assertEquals(1,resumed.completed)
        assertEquals("Gimbo_${previous.id}_1",resumed.outputName(1))
        val legacy=org.json.JSONObject(photo.json()).apply {remove("outputPrefix")}
        assertTrue(ExportRequest.read(legacy.toString()).outputName(0).startsWith("Luma_"))
    }
    @Test fun renderingPreservesSourcesAndDrawsDifferentPhotosAndBackground():Unit=runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val files=listOf(Color.RED,Color.BLUE).mapIndexed {i,color->File(context.cacheDir,"collage-$i.png").apply {
            val bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply {eraseColor(color)}
            outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }}
        try {
            val before=files.map {it.readBytes()}
            val items=files.map {GalleryMedia(it.name,Uri.fromFile(it),it.name,"Camera",0)}
            val spec=CollageSpec(layout=CollageLayout.COLUMNS,gap=.04f,corner=0f,tiles=listOf(CollageTile(),CollageTile(quarterTurns=1)))
            val bitmap=CollageEngine.render(context,items,spec,256)
            assertEquals(Color.RED,bitmap.getPixel(64,128));assertEquals(Color.BLUE,bitmap.getPixel(192,128));assertEquals(spec.background.toInt(),bitmap.getPixel(128,128))
            bitmap.recycle()
            files.forEachIndexed {i,file->assertArrayEquals(before[i],file.readBytes())}
        } finally {files.forEach {it.delete()}}
    }
    @Test fun panAndRotationKeepTheSelectedPhotoInsideItsBounds() {
        val source=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888)
        Canvas(source).apply {drawColor(Color.BLUE);drawRect(0f,0f,20f,20f,Paint().apply {color=Color.RED})}
        val target=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(target);canvas.drawColor(Color.GREEN)
        CollageEngine.drawTile(canvas,source,CollageTile(2f,1f,-1f,1),RectF(8f,8f,56f,56f),0f,Paint(Paint.FILTER_BITMAP_FLAG))
        assertEquals(Color.GREEN,target.getPixel(0,0));assertNotEquals(Color.GREEN,target.getPixel(32,32))
        target.recycle();source.recycle()
    }
}
