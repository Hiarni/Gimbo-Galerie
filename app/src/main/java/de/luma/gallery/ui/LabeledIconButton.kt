package de.luma.gallery.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabeledIconButton(icon:ImageVector,label:String,onClick:()->Unit,enabled:Boolean=true,active:Boolean=false) {
    TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),tooltip={PlainTooltip {Text(label)}},state=rememberTooltipState()) {
        FilledTonalIconButton(onClick=onClick,enabled=enabled,colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=if(active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,contentColor=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) {Icon(icon,label,Modifier.size(22.dp))}
    }
}
