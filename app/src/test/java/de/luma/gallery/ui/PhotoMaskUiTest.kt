package de.luma.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoMaskUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun dragMovesTheNeutralMaskAndCommitsOnceWithoutChangingItsAdjustment() {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val selected = mutableStateOf(PhotoMask("mask", exposure = 0f)); var commits = 0
        try {
            compose.setContent { MaterialTheme { Box(Modifier.size(300.dp)) {
                PhotoMaskPreview(bitmap, selected.value, { selected.value = it }, { commits++ })
            } } }
            compose.onNodeWithContentDescription("Selected mask. Drag to move; position and size sliders are available below.").performTouchInput {
                swipe(center, center + Offset(width * .25f, height * .15f))
            }
            compose.runOnIdle {

                assertTrue(selected.value.x in .69f.. .76f); assertTrue(selected.value.y in .60f.. .66f)
                assertEquals(0f, selected.value.exposure, 0f); assertEquals(.5f, selected.value.width, 0f)
                assertEquals(1, commits)
            }
        } finally { bitmap.recycle() }
    }
}
