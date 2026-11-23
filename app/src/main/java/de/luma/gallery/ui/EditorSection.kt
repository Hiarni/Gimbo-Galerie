package de.luma.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.*
import de.luma.gallery.R

@Composable
fun EditorSection(title:String,icon:ImageVector,initiallyOpen:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    val strings=appStrings()
    var expanded by rememberSaveable(title) {mutableStateOf(initiallyOpen)}
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth().padding(vertical=6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().semantics {stateDescription=strings.getString(if(expanded) R.string.collapse_tools else R.string.expand_tools)}.clickable {expanded=!expanded}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                Text(title,Modifier.weight(1f).padding(horizontal=10.dp),style=MaterialTheme.typography.titleSmall)
                Icon(LumaIcons.ChevronDown,null,Modifier.size(24.dp).rotate(if(expanded) 180f else 0f))
            }
            if(expanded) content()
        }
    }
}
