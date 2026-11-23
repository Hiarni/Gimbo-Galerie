package de.luma.gallery.data

import android.graphics.*
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.luma.gallery.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File


@RunWith(AndroidJUnit4::class)
class TiledPhotoPipelineTest {
    @Test fun regionDecodeMatchesWholeImageAtTileEdgesAndAfterRotation()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=Bitmap.createBitmap(1025,777,Bitmap.Config.ARGB_8888)
        for(y in 0 until source.height)for(x in 0 until source.width)source.setPixel(x,y,Color.rgb(x*255/1024,y*255/776,60))
        val file=File.createTempFile("luma_region_test_",".png",context.cacheDir)
        file.outputStream().use{source.compress(Bitmap.CompressFormat.PNG,100,it)}
        val recipe=PhotoRecipe(quarterTurns=1,sharpness=.4f,masks=listOf(PhotoMask("mask",exposure=.5f)))
        val expected=PhotoEngine.render(source,recipe)
        try{
            val output=ByteArrayOutputStream();TiledPhotoExporter(context).render(GalleryMedia("id",Uri.fromFile(file),"A.png","",0),recipe,PhotoExportOptions(PhotoFormat.PNG,8192),output)
            val actual=BitmapFactory.decodeByteArray(output.toByteArray(),0,output.size())
            try{assertEquals(expected.width,actual.width);assertEquals(expected.height,actual.height)
                for(y in listOf(1,126,127,128,129,512,1023))for(x in listOf(1,126,127,128,129,512,775)){val a=actual.getPixel(x,y);val e=expected.getPixel(x,y)
                    assertTrue(kotlin.math.abs(Color.red(a)-Color.red(e))<=3 && kotlin.math.abs(Color.green(a)-Color.green(e))<=3 && kotlin.math.abs(Color.blue(a)-Color.blue(e))<=3)}
            }finally{actual.recycle()}
        }finally{expected.recycle();source.recycle();file.delete()}
    }
}
