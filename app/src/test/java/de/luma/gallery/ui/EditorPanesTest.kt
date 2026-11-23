package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w1000dp-h600dp")
class EditorPanesTest {
    @get:Rule val compose = createComposeRule()
    @Test fun wideWindowPlacesControlsBesideThePreview() {
        compose.setContent { EditorPanes(Modifier.requiredSize(900.dp, 400.dp), { Text("Preview", it) }, { Text("Controls", it) }) }
        val preview = compose.onNodeWithText("Preview").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithText("Controls").fetchSemanticsNode().boundsInRoot
        assertEquals(preview.top, controls.top, 1f)
        assertTrue(controls.left >= preview.right - 1)
    }

    @Test fun portraitPanelKeepsHandleAndControlsInsideOneInsetSurface() {
        compose.setContent {EditorPanes(Modifier.requiredSize(360.dp,540.dp).testTag("panes"),{Text("Preview",it)},{Text("Controls",it)})}
        val root=compose.onNodeWithTag("panes").fetchSemanticsNode().boundsInRoot
        val initial=compose.onNodeWithTag("editor_controls").fetchSemanticsNode().boundsInRoot
        val controls=compose.onNodeWithText("Controls").fetchSemanticsNode().boundsInRoot
        assertTrue(initial.left>root.left && initial.right<root.right && initial.bottom<root.bottom)
        assertTrue(controls.left>=initial.left && controls.right<=initial.right && controls.bottom<=initial.bottom)
        compose.onNodeWithContentDescription("Tool panel — drag to resize").performClick()
        val expanded=compose.onNodeWithTag("editor_controls").fetchSemanticsNode().boundsInRoot
        assertTrue(expanded.height>initial.height)
        assertTrue(expanded.top>root.top)
    }
}
