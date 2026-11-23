package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.luma.gallery.R

@Composable
internal fun DaySelectionHeader(title:String,ids:Set<String>,selection:Set<String>,onSelection:(Set<String>)->Unit,modifier:Modifier=Modifier) {
    val strings=appStrings()
    val count=ids.count {it in selection}
    val state=when(count) {0->ToggleableState.Off;ids.size->ToggleableState.On;else->ToggleableState.Indeterminate}
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
        Row(modifier.fillMaxWidth().heightIn(min=56.dp).triStateToggleable(state,role=Role.Checkbox,onClick={
            onSelection(if(state==ToggleableState.On) selection-ids else selection+ids)
        }).semantics {
            contentDescription=strings.getString(if(state==ToggleableState.On) R.string.clear_day_selection else R.string.select_day,title)
            stateDescription=strings.getString(R.string.day_selection_state,count,ids.size)
        }.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(if(count>0) "$count / ${ids.size}" else "${ids.size}",Modifier.padding(start=8.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {SelectionIndicator(state)}
        }
    }
}
