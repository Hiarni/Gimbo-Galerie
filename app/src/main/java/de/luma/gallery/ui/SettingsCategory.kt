package de.luma.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import de.luma.gallery.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun SettingsCategory(title:String,initiallyOpen:Boolean=false,content:@Composable ()->Unit) {
    var open by rememberSaveable(title) {mutableStateOf(initiallyOpen)}
    val strings=appStrings()
    val rotation by animateFloatAsState(if(open) 180f else 0f,label="settingsSection")
    Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).semantics(mergeDescendants=true) {stateDescription=strings.getString(if(open) R.string.section_expanded else R.string.section_collapsed)}.clickable(role=Role.Button) {open=!open},verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                Icon(LumaIcons.ChevronDown,null,Modifier.size(22.dp).graphicsLayer {rotationZ=rotation},tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(open) {Spacer(Modifier.height(8.dp));content()}
        }
    }
}
