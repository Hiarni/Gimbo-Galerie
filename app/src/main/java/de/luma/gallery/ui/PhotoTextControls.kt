package de.luma.gallery.ui

import de.luma.gallery.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.luma.gallery.model.*
import java.util.UUID

@Composable
fun PhotoTextControls(texts: List<PhotoText>, selected: PhotoText?, onSelect: (String) -> Unit,
    onChange: (PhotoText) -> Unit, onFinish: () -> Unit, onCommit: (List<PhotoText>) -> Unit) {
    val strings = appStrings()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PhotoText?>(null) }
    Text(strings.getString(R.string.text_intro),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = { creating = true }, enabled = texts.size < PHOTO_TEXT_LIMIT) { Text(strings.getString(R.string.add_text)) }
    if (texts.isNotEmpty()) LazyRow(Modifier.semantics { contentDescription = strings.getString(R.string.text_objects) }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(texts, key = { _, text -> text.id }) { index, text ->
            FilterChip(text.id == selected?.id, { onSelect(text.id) }, { Text(strings.getString(R.string.text_number, index + 1)) })
        }
    }
    selected?.let { text ->
        Text(text.text, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { editing = text }) { Text(strings.getString(R.string.edit_text_content)) }
        EditorSlider(strings.getString(R.string.text_size), text.size * 100, 2.5f..20f, { onChange(text.copy(size = it / 100)) }, onFinish)
        EditorSlider(strings.getString(R.string.text_horizontal_position), text.x * 100, 0f..100f, { onChange(text.copy(x = it / 100)) }, onFinish)
        EditorSlider(strings.getString(R.string.text_vertical_position), text.y * 100, 0f..100f, { onChange(text.copy(y = it / 100)) }, onFinish)
        val palette = listOf(strings.getString(R.string.white) to 0xFFFFFFFFL, strings.getString(R.string.black) to 0xFF000000L, strings.getString(R.string.red) to 0xFFFF5252L,
            strings.getString(R.string.yellow) to 0xFFFFD740L, strings.getString(R.string.green) to 0xFF69F0AEL, strings.getString(R.string.blue) to 0xFF448AFFL)
        LazyRow(Modifier.semantics { contentDescription = strings.getString(R.string.text_colors) }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(palette) { _, (name, color) -> FilterChip(text.color == color,
                { onCommit(texts.map { if (it.id == text.id) text.copy(color = color) else it }) },
                { Text(name) }, leadingIcon = { Box(Modifier.size(12.dp).background(Color(color))) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.getString(R.string.bold), Modifier.weight(1f))
            Switch(text.bold, { value -> onCommit(texts.map { if (it.id == text.id) text.copy(bold = value) else it }) },
                Modifier.semantics { contentDescription = strings.getString(R.string.bold) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.getString(R.string.contrast_background), Modifier.weight(1f))
            Switch(text.background, { value -> onCommit(texts.map { if (it.id == text.id) text.copy(background = value) else it }) },
                Modifier.semantics { contentDescription = strings.getString(R.string.contrast_background) })
        }
        TextButton(onClick = { onCommit(texts.filter { it.id != text.id }) }) { Text(strings.getString(R.string.remove_text)) }
    }
    if (creating || editing != null) {
        val existing = editing
        PhotoTextDialog(existing?.text.orEmpty(), existing != null, onDismiss = { creating = false; editing = null }, onSave = { value ->
            if (existing == null) {
                val text = PhotoText(UUID.randomUUID().toString(), value)
                onCommit(texts + text); onSelect(text.id)
            } else onCommit(texts.map { if (it.id == existing.id) it.copy(text = value) else it })
            creating = false; editing = null
        })
    }
}

@Composable
fun PhotoTextDialog(initial: String, existing: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val strings = appStrings()
    var value by remember { mutableStateOf(initial) }
    val valid = value.isNotBlank() && value.length <= PHOTO_TEXT_LENGTH && value.none { it.isISOControl() && it != '\n' } && value.count { it == '\n' } <= 3
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing) strings.getString(R.string.edit_text) else strings.getString(R.string.add_text)) }, text = {
        Column {
            OutlinedTextField(value, { value = it.take(PHOTO_TEXT_LENGTH) }, label = { Text(strings.getString(R.string.text_content)) }, minLines = 2, maxLines = 4)
            Text(strings.getString(R.string.text_character_limit, value.length, PHOTO_TEXT_LENGTH), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onSave(value.trim()) }, enabled = valid) { Text(strings.getString(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.getString(R.string.cancel)) } })
}
