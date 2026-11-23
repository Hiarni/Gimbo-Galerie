package de.luma.gallery.data

import android.graphics.Bitmap
import android.graphics.Color
import de.luma.gallery.model.PhotoMood
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoodAnalysisTest {
    @Test fun suggestionsUseActualPixelsAndHandleTransparentSources() {
        listOf(Color.RED to PhotoMood.WARM,Color.BLUE to PhotoMood.COOL,Color.GRAY to PhotoMood.MONOCHROME,Color.TRANSPARENT to PhotoMood.QUIET).forEach {(color,mood)->
            val bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply {eraseColor(color)}
            val result=MoodAnalysis.classify(bitmap);assertEquals(mood,result.first);assertTrue(result.second in 0f..1f);bitmap.recycle()
        }
    }
}
