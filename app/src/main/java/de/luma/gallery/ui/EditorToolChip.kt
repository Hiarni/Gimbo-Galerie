package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*

@Composable
fun EditorToolChip(page:PhotoToolPage,selected:Boolean,onClick:()->Unit) {
    val strings=appStrings()
    val icon=when(page) {
        PhotoToolPage.LIGHT->LumaIcons.Sun;PhotoToolPage.COLOR,PhotoToolPage.HSL->LumaIcons.Palette
        PhotoToolPage.CURVES->LumaIcons.Curve;PhotoToolPage.CROP->LumaIcons.Crop;PhotoToolPage.TEXT->LumaIcons.Text
        PhotoToolPage.MARKUP->LumaIcons.Brush;PhotoToolPage.FILTERS->LumaIcons.AutoAwesome
        PhotoToolPage.LAYERS->LumaIcons.Layers;PhotoToolPage.MASKS->LumaIcons.Mask;else->LumaIcons.Tune
    }
    Surface(shape=RoundedCornerShape(22.dp),color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.widthIn(min=76.dp).heightIn(min=76.dp).semantics(mergeDescendants=true) {}.selectable(selected=selected,role=Role.Tab,onClick=onClick).padding(horizontal=10.dp,vertical=12.dp)) {
            Icon(icon,null,Modifier.size(25.dp))
            Text(strings.getString(page.labelRes),style=MaterialTheme.typography.labelMedium)
        }
    }
}
