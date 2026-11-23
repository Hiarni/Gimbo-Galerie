package de.luma.gallery.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp

@Composable
fun SelectionIndicator(state:ToggleableState,modifier:Modifier=Modifier,overMedia:Boolean=false,enabled:Boolean=true,number:Int?=null,animate:Boolean=true) {
    val marked=state!=ToggleableState.Off
    val scheme=MaterialTheme.colorScheme
    val fill by animateColorAsState(if(marked) scheme.primary else if(overMedia) Color.Black.copy(alpha=.3f) else Color.Transparent,
        if(animate) tween(160) else snap(),label="selectionFill")
    val ink=if(marked) scheme.onPrimary else scheme.onSurfaceVariant
    val border=if(overMedia) Color.White.copy(alpha=if(marked) .95f else .8f) else if(marked) scheme.primary else scheme.outline.copy(alpha=.7f)
    val reveal by animateFloatAsState(if(marked) 1f else 0f,if(animate) tween(160) else snap(),label="selectionCheck")
    Surface(modifier.size(26.dp).clearAndSetSemantics {},shape=CircleShape,color=if(enabled) fill else fill.copy(alpha=.38f),
        contentColor=ink,border=BorderStroke(if(overMedia) 1.5.dp else 1.25.dp,if(enabled) border else border.copy(alpha=.38f))) {
        Box(contentAlignment=Alignment.Center) {
            if(number!=null && marked) Text("$number",style=MaterialTheme.typography.labelMedium)
            else Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                if(reveal>0f) {
                    val color=ink.copy(alpha=reveal*if(enabled) 1f else .38f)
                    if(state==ToggleableState.Indeterminate) drawLine(color,androidx.compose.ui.geometry.Offset(0f,size.height*.5f),androidx.compose.ui.geometry.Offset(size.width,size.height*.5f),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)
                    else drawPath(Path().apply {moveTo(0f,size.height*.48f);lineTo(size.width*.38f,size.height*.86f);lineTo(size.width,size.height*.12f)},color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                }
            }
        }
    }
}

@Composable
fun RoundCheckbox(checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val state=if(checked) ToggleableState.On else ToggleableState.Off
    Box(modifier.size(48.dp).then(if(onCheckedChange!=null) Modifier.triStateToggleable(state,enabled=enabled,role=Role.Checkbox,onClick={onCheckedChange(!checked)}) else Modifier),contentAlignment=Alignment.Center) {
        SelectionIndicator(state,enabled=enabled)
    }
}
