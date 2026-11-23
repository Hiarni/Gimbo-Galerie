package de.luma.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import de.luma.gallery.R

@Composable
fun EditorPanes(modifier: Modifier, preview: @Composable (Modifier) -> Unit, controls: @Composable (Modifier) -> Unit) {
    val strings = appStrings()
    var fraction by rememberSaveable { mutableFloatStateOf(.52f) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        val wide = maxWidth >= 840.dp || maxWidth > maxHeight * 1.5f
        if (wide) Row(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            preview(Modifier.weight(1f).fillMaxHeight())
            Surface(Modifier.width((windowWidth*.43f).coerceAtMost(400.dp)).fillMaxHeight().testTag("editor_controls"),
                shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
                controls(Modifier.fillMaxSize())
            }
        } else Column(Modifier.fillMaxSize()) {
            val heightPx = with(density) { windowHeight.toPx() }.coerceAtLeast(1f)
            val minimum = minOf(164.dp, (windowHeight-80.dp).coerceAtLeast(0.dp))
            val panelHeight = (windowHeight*fraction).coerceIn(minimum,windowHeight)
            preview(Modifier.weight(1f).fillMaxWidth())
            Surface(Modifier.fillMaxWidth().padding(horizontal=12.dp).padding(bottom=12.dp).height(panelHeight).testTag("editor_controls"),
                shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    Box(Modifier.fillMaxWidth().height(28.dp)
                        .semantics {
                            contentDescription=strings.getString(R.string.editor_panel)
                            customActions=listOf(
                                CustomAccessibilityAction(strings.getString(R.string.expand_tools)) {fraction=.78f;true},
                                CustomAccessibilityAction(strings.getString(R.string.collapse_tools)) {fraction=.34f;true})
                        }
                        .draggable(rememberDraggableState {delta->fraction=(fraction-delta/heightPx).coerceIn(.28f,.8f)},Orientation.Vertical)
                        .clickable {fraction=if(fraction<.65f) .78f else .34f},contentAlignment=Alignment.Center) {
                        Box(Modifier.width(40.dp).height(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.45f),RoundedCornerShape(50)))
                    }
                    controls(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}
