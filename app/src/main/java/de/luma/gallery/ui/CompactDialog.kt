package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.luma.gallery.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactDialog(title:String,onDismiss:()->Unit,actions:@Composable ()->Unit,content:@Composable ColumnScope.()->Unit) {
    val strings=appStrings()
    var panelBounds by remember {mutableStateOf(Rect.Zero)}
    val dismiss by rememberUpdatedState(onDismiss)
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp).pointerInput(Unit) {detectTapGestures {point->if(!panelBounds.contains(point)) dismiss()}},contentAlignment=Alignment.Center) {
            Surface(Modifier.widthIn(max=560.dp).fillMaxWidth().heightIn(max=maxHeight*.9f).testTag("compact_dialog").onGloballyPositioned {panelBounds=it.boundsInParent()},shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                        LabeledIconButton(LumaIcons.Close,strings.getString(R.string.close),onDismiss)
                    }
                    Column(Modifier.weight(1f,fill=false).fillMaxWidth().testTag("compact_dialog_content").verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
                    HorizontalDivider()
                    FlowRow(Modifier.fillMaxWidth().testTag("compact_dialog_actions").padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {actions()}
                }
            }
        }
    }
}

@Composable
fun <T> CompactChoice(label:String,value:T,choices:List<T>,text:(T)->String,onChange:(T)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth()) {
        Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {
                Text(text(value),Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                Icon(LumaIcons.ChevronDown,null,Modifier.size(20.dp))
            }
            DropdownMenu(expanded,{expanded=false}) {
                choices.forEach {item->DropdownMenuItem(text={Text(text(item))},onClick={expanded=false;onChange(item)},leadingIcon={if(item==value) Icon(LumaIcons.Check,null)})}
            }
        }
    }
}
