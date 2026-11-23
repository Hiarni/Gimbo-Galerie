package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.luma.gallery.R

@Composable
fun SelectionSummary(count:Int,onClear:()->Unit,onSelectAll:()->Unit) {
    val strings=appStrings()
    Surface(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
        IconButton(onClick=onClear) {Icon(LumaIcons.Close,strings.getString(R.string.clear_selection))}
        Text(strings.getString(R.string.selected_count,count),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        TextButton(onClick=onSelectAll) {Text(strings.getString(R.string.select_all))}
    }
    }
}
