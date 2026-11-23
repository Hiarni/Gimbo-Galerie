package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.model.AppSettings

@Composable
fun AppearanceControls(settings: AppSettings, enabled: Boolean, onChange: (AppSettings) -> Unit) {
    val strings = appStrings()
    Row {
        Text(strings.getString(R.string.thumbnail_animation), Modifier.weight(1f))
        Switch(settings.thumbnailAnimations, { onChange(settings.copy(thumbnailAnimations = it)) }, enabled = enabled)
    }
    Text(strings.getString(R.string.tool_order), style = MaterialTheme.typography.titleSmall)
    settings.toolOrder.forEachIndexed { index, name ->
        val page = PhotoToolPage.entries.firstOrNull { it.name == name } ?: return@forEachIndexed
        Row {
            Text(strings.getString(page.labelRes), Modifier.weight(1f))
            TextButton(onClick = { val list = settings.toolOrder.toMutableList(); java.util.Collections.swap(list, index, index - 1); onChange(settings.copy(toolOrder = list)) }, enabled = enabled && index > 0) { Text(strings.getString(R.string.move_up)) }
            TextButton(onClick = { val list = settings.toolOrder.toMutableList(); java.util.Collections.swap(list, index, index + 1); onChange(settings.copy(toolOrder = list)) }, enabled = enabled && index < settings.toolOrder.lastIndex) { Text(strings.getString(R.string.move_down)) }
        }
    }
}
