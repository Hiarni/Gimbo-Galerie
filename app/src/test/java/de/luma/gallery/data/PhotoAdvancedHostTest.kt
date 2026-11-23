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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoAdvancedHostTest {
    @Test fun lassoBrushInversionAndRangesRoundTripAndSelectActualPixels(){
        val points=listOf(MarkupPoint(.2f,.2f),MarkupPoint(.8f,.2f),MarkupPoint(.8f,.8f),MarkupPoint(.2f,.8f))
        val lasso=PhotoMask("lasso",MaskShape.FREEHAND,width=1f,height=1f,feather=0f,points=points)
        assertEquals(1f,lasso.weight(.5f,.5f),0f);assertEquals(0f,lasso.weight(.1f,.5f),0f)
        assertEquals(0f,lasso.copy(inverted=true).weight(.5f,.5f),0f)
        val brush=lasso.copy(shape=MaskShape.BRUSH,points=listOf(MarkupPoint(.2f,.5f),MarkupPoint(.8f,.5f)),brushSize=.1f)
        assertEquals(1f,brush.weight(.5f,.5f),0f);assertEquals(0f,brush.weight(.5f,.6f),0f)
        val luma=PhotoMask("luma",MaskShape.LUMINANCE,rangeStart=.6f,rangeEnd=1f,feather=0f)
        assertEquals(1f,luma.weight(.5f,.5f,Color.WHITE),0f);assertEquals(0f,luma.weight(.5f,.5f,Color.BLACK),0f)
        val hue=PhotoMask("hue",MaskShape.COLOR,rangeStart=.25f,rangeEnd=.4f,feather=0f)
        assertEquals(1f,hue.weight(.5f,.5f,Color.GREEN),0f);assertEquals(0f,hue.weight(.5f,.5f,Color.RED),0f)
        val recipe=PhotoRecipe(masks=listOf(lasso,brush.copy(id="brush"),luma,hue))
        assertEquals(recipe,RecipeJson.readPhoto(RecipeJson.photo(recipe)))
        assertEquals(PhotoRecipe(),RecipeJson.readPhoto("{\"version\":6}"))
        assertTrue(runCatching{PhotoMask("bad",points=List(257){MarkupPoint(.5f,.5f)})}.isFailure)
    }
    @Test fun repairUsesTheSurroundingsWithoutSamplePointAndKeepsOtherPixels(){
        val source=Bitmap.createBitmap(160,120,Bitmap.Config.ARGB_8888);source.eraseColor(Color.rgb(30,160,70))
        val canvas=Canvas(source);canvas.drawCircle(80f,60f,3f,Paint().apply{color=Color.RED})
        val mark=PhotoMarkup("repair",MarkupKind.INPAINT,listOf(MarkupPoint(.5f,.5f)),width=.1f)
        val result=PhotoEngine.render(source,PhotoRecipe(markups=listOf(mark)))
        try {assertEquals(Color.rgb(30,160,70),result.getPixel(80,60));assertEquals(Color.RED,source.getPixel(80,60));assertEquals(source.getPixel(10,10),result.getPixel(10,10))} finally {source.recycle();result.recycle()}
    }
    @Test fun streamingPngPreservesAlphaAndStreamingJpegIsDecodableWithCorrectDimensions(){
        val context=RuntimeEnvironment.getApplication();val file=File.createTempFile("raster-test",".argb",context.cacheDir)
        val bitmap=Bitmap.createBitmap(137,131,Bitmap.Config.ARGB_8888)
        for(y in 0 until bitmap.height)for(x in 0 until bitmap.width)bitmap.setPixel(x,y,Color.argb(if(x<20)128 else 255,x*255/136,y*255/130,40))
        try {DiskRaster(file,137,131).use{raster->raster.write(bitmap,0,0)
            val png=ByteArrayOutputStream();RasterEncoders.png(raster,png){};val decoded=BitmapFactory.decodeByteArray(png.toByteArray(),0,png.size())
            assertNotNull(decoded);assertEquals(137,decoded.width);assertEquals(bitmap.getPixel(5,5),decoded.getPixel(5,5));assertEquals(bitmap.getPixel(136,130),decoded.getPixel(136,130));decoded.recycle()
            for(quality in listOf(60,95,100)){val jpg=ByteArrayOutputStream();RasterEncoders.jpeg(raster,quality,jpg){};val image=BitmapFactory.decodeByteArray(jpg.toByteArray(),0,jpg.size())
                assertNotNull(image);assertEquals(137,image.width);assertEquals(131,image.height);assertEquals(255,Color.alpha(image.getPixel(5,5)));assertTrue(abs(Color.red(image.getPixel(100,100))-Color.red(bitmap.getPixel(100,100)))<12);image.recycle()}
        }}finally{bitmap.recycle();file.delete()}
    }
    @Test fun tiledGeometryMasksSharpnessAndGlobalTextMatchWholeBitmapAcrossTileBoundaries()=runBlocking{
        val context=RuntimeEnvironment.getApplication();val file=File.createTempFile("tile-source",".png",context.cacheDir)
        val bitmap=Bitmap.createBitmap(321,273,Bitmap.Config.ARGB_8888)
        for(y in 0 until bitmap.height)for(x in 0 until bitmap.width)bitmap.setPixel(x,y,Color.rgb(x*255/320,y*255/272,70))
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        val source=GalleryMedia("source",Uri.fromFile(file),"Test.png","",0)
        val recipe=PhotoRecipe(quarterTurns=1,flipHorizontal=true,exposure=.3f,sharpness=.4f,
            masks=listOf(PhotoMask("mask",exposure=.5f)),texts=listOf(PhotoText("caption","Across tiles",size=.12f)))
        val expected=PhotoEngine.render(bitmap,recipe)
        val output=ByteArrayOutputStream()
        try {TiledPhotoExporter(context, ::nativeRegions).render(source,recipe,PhotoExportOptions(PhotoFormat.PNG,8192),output)
            val actual=BitmapFactory.decodeByteArray(output.toByteArray(),0,output.size())
            try {assertEquals(expected.width,actual.width);assertEquals(expected.height,actual.height)
                for(y in listOf(0,20,126,127,128,129,250,320))for(x in listOf(0,20,126,127,128,129,250)){if(x>=actual.width || y>=actual.height)continue;val a=actual.getPixel(x,y);val e=expected.getPixel(x,y)
                    assertTrue("$x,$y: $a / $e",abs(Color.red(a)-Color.red(e))<=3 && abs(Color.green(a)-Color.green(e))<=3 && abs(Color.blue(a)-Color.blue(e))<=3)}
            }finally{actual.recycle()}
            assertTrue(context.cacheDir.listFiles()!!.none{it.name.startsWith("luma_tile_")})
        }finally{file.delete();expected.recycle();bitmap.recycle()}
    }
    @Test fun tiledPixelationAndInpaintingReadAcrossTileEdges()=runBlocking{
        val context=RuntimeEnvironment.getApplication();val file=File.createTempFile("tile-source",".png",context.cacheDir)
        val bitmap=Bitmap.createBitmap(300,200,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.GREEN)
        Canvas(bitmap).drawCircle(128f,100f,2f,Paint().apply{color=Color.RED})
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        val source=GalleryMedia("source",Uri.fromFile(file),"Test.png","",0)
        val recipe=PhotoRecipe(markups=listOf(PhotoMarkup("repair",MarkupKind.INPAINT,listOf(MarkupPoint(128/300f,.5f)),width=.08f)))
        val output=ByteArrayOutputStream()
        try{TiledPhotoExporter(context, ::nativeRegions).render(source,recipe,PhotoExportOptions(PhotoFormat.PNG,8192),output)
            val actual=BitmapFactory.decodeByteArray(output.toByteArray(),0,output.size());try{assertEquals(Color.GREEN,actual.getPixel(128,100));assertEquals(Color.GREEN,actual.getPixel(127,100));assertEquals(Color.GREEN,actual.getPixel(129,100))}finally{actual.recycle()}
        }finally{file.delete();bitmap.recycle()}
    }
    @Test fun cancellationLeavesNoTemporaryImages()=runBlocking{
        val context=RuntimeEnvironment.getApplication();val file=File.createTempFile("tile-source",".png",context.cacheDir)
        val bitmap=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888);file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val output=object: java.io.OutputStream(){override fun write(value: Int){throw kotlinx.coroutines.CancellationException()}}
        try{assertTrue(runCatching{TiledPhotoExporter(context, ::nativeRegions).render(GalleryMedia("id",Uri.fromFile(file),"A.png","",0),PhotoRecipe(),PhotoExportOptions(PhotoFormat.PNG,8192),output)}.exceptionOrNull() is kotlinx.coroutines.CancellationException)
            assertTrue(context.cacheDir.listFiles()!!.none{it.name.startsWith("luma_tile_")})}finally{file.delete()}
    }


    private fun nativeRegions(file: File): PhotoRegions {
        val source=BitmapFactory.decodeFile(file.path)
        return object: PhotoRegions {
            override val width=source.width;override val height=source.height
            override fun decode(bounds: Rect,sample: Int): Bitmap {
                val crop=Bitmap.createBitmap(source,bounds.left,bounds.top,bounds.width(),bounds.height())
                if(sample==1)return crop
                return Bitmap.createScaledBitmap(crop,(bounds.width()+sample-1)/sample,(bounds.height()+sample-1)/sample,true).also{if(it!==crop)crop.recycle()}
            }
            override fun close(){source.recycle()}
        }
    }
    private fun abs(value: Int)=kotlin.math.abs(value)
}
