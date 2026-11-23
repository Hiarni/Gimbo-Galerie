package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.model.*

private data class BatchPreset(val id: String, val name: String, val recipe: PhotoRecipe)

@Composable
fun BatchPhotoDialog(items: List<GalleryMedia>, projects: List<EditorProject>, initial: PhotoExportOptions,
    onDismiss: () -> Unit, onExport: (PhotoRecipe, PhotoExportOptions) -> Unit) {
    val strings = appStrings()
    val count = items.size
    val presets = listOf(BatchPreset("copy", strings.getString(R.string.batch_no_edits), PhotoRecipe()),
        BatchPreset("bw", strings.getString(R.string.filter_bw), PhotoRecipe(saturation = 0f)),
        BatchPreset("warm", strings.getString(R.string.filter_warm), PhotoRecipe(temperature = .35f))) +
        projects.mapNotNull { p -> p.photo?.let { BatchPreset("project:${p.id}", strings.getString(R.string.batch_project_preset, p.name), it) } }
    var selectedId by remember { mutableStateOf("copy") }
    val selected = presets.find { it.id == selectedId }
    var index by remember(items.map { it.id }) { mutableIntStateOf(0) }
    val item = items.getOrNull(index)
    var original by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    val valid = count in 1..100 && items.none { it.isVideo }
    if (options && selected != null && valid) PhotoExportDialog(initial, onDismiss, { onExport(selected.recipe, it) },
        needsMediaLocation = items.any { it.uri.authority == "media" }, extraHint = strings.getString(R.string.batch_review, count, selected.name))
    else AlertDialog(onDismissRequest = onDismiss, title = { Text(strings.getString(R.string.batch_photos)) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text(strings.getString(R.string.batch_hint, count), style = MaterialTheme.typography.bodySmall)
            if (!valid) Text(strings.getString(R.string.batch_selection_invalid), color = MaterialTheme.colorScheme.error)
            if (item != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { index = (index - 1).coerceAtLeast(0); options = false }, enabled = index > 0) {
                        Icon(LumaIcons.ArrowBack, strings.getString(R.string.previous_media))
                    }
                    Text(strings.getString(R.string.batch_preview_position, index + 1, count), Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton(onClick = { index = (index + 1).coerceAtMost(count - 1); options = false }, enabled = index < count - 1) {
                        Icon(LumaIcons.ArrowForward, strings.getString(R.string.next_media))
                    }
                }
                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(original, { original = true }, label = { Text(strings.getString(R.string.original)) })
                    FilterChip(!original, { original = false }, label = { Text(strings.getString(R.string.edit_preview)) })
                }
                if (selected != null) BatchPhotoPreview(item, selected.recipe, original)
            }
            if (selected == null) Text(strings.getString(R.string.batch_preset_missing), color = MaterialTheme.colorScheme.error)
            Text(strings.getString(R.string.batch_preview_hint), style = MaterialTheme.typography.bodySmall)
            presets.forEach { preset ->
                val select = { selectedId = preset.id; original = false; options = false }
                Row { RadioButton(selectedId == preset.id, select); TextButton(onClick = select) { Text(preset.name) } }
            }
        } }, confirmButton = { TextButton(onClick = { options = true }, enabled = valid && selected != null) { Text(strings.getString(R.string.export)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.getString(R.string.cancel)) } })
}
