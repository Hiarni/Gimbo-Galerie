package de.luma.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.model.*

@Composable
fun PhotoMarkupControls(markups: List<PhotoMarkup>, selected: PhotoMarkup?, tool: MarkupKind,
    onTool: (MarkupKind) -> Unit, onSelect: (String) -> Unit, onAdd: () -> Unit,
    onChange: (PhotoMarkup) -> Unit, onFinish: () -> Unit, onCommit: (List<PhotoMarkup>) -> Unit) {
    val strings = appStrings()
    Text(strings.getString(if (selected == null) R.string.markup_draw_hint else R.string.markup_move_hint),
        style = MaterialTheme.typography.bodySmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MarkupKind.entries) { kind -> FilterChip(selected == null && tool == kind,
            { onTool(kind) }, { Text(strings.getString(kind.labelRes)) }, enabled = markups.size < PHOTO_MARKUP_LIMIT) }
    }
    if (markups.size >= PHOTO_MARKUP_LIMIT) Text(strings.getString(R.string.markup_limit), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = onAdd, enabled = markups.size < PHOTO_MARKUP_LIMIT) { Text(strings.getString(R.string.markup_add)) }
    if (markups.isNotEmpty()) LazyRow(Modifier.semantics { contentDescription = strings.getString(R.string.markup_objects) },
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(markups, key = { _, mark -> mark.id }) { index, mark -> FilterChip(mark.id == selected?.id,
            { onSelect(mark.id) }, { Text(strings.getString(R.string.markup_number, index + 1, strings.getString(mark.kind.labelRes))) }) }
    }
    selected?.let { mark ->
        val change: (PhotoMarkup) -> Unit = { updated -> onCommit(markups.map { if (it.id == mark.id) updated else it }) }
        if (mark.kind == MarkupKind.PIXELATE) {
            Text(strings.getString(R.string.markup_pixelate_hint), style = MaterialTheme.typography.bodySmall)
            EditorSlider(strings.getString(R.string.markup_block_size), mark.blockSize * 100, 1f..20f,
                { onChange(mark.copy(blockSize = it / 100)) }, onFinish)
        } else {
            if (mark.kind != MarkupKind.COVER) EditorSlider(strings.getString(R.string.markup_line_width), mark.width * 100, .2f..10f,
                { onChange(mark.copy(width = it / 100)) }, onFinish)
            val palette = listOf(R.string.white to 0xFFFFFFFFL, R.string.black to 0xFF000000L, R.string.red to 0xFFFF5252L,
                R.string.yellow to 0xFFFFD740L, R.string.green to 0xFF69F0AEL, R.string.blue to 0xFF448AFFL)
            if (mark.kind !in listOf(MarkupKind.CLONE, MarkupKind.HEAL, MarkupKind.INPAINT)) LazyRow(Modifier.semantics { contentDescription = strings.getString(R.string.markup_colors) }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(palette) { (name, color) -> FilterChip(mark.color == color, { change(mark.copy(color = color)) },
                    { Text(strings.getString(name)) }, leadingIcon = { Box(Modifier.size(12.dp).background(Color(color))) }) }
            }
            if (mark.kind == MarkupKind.RECTANGLE || mark.kind == MarkupKind.ELLIPSE) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.getString(R.string.markup_filled), Modifier.weight(1f))
                Switch(mark.filled, { change(mark.copy(filled = it)) }, Modifier.semantics { contentDescription = strings.getString(R.string.markup_filled) })
            }
            if (mark.kind == MarkupKind.COVER) Text(strings.getString(R.string.markup_cover_hint), style = MaterialTheme.typography.bodySmall)
        }
        if (mark.kind == MarkupKind.CLONE || mark.kind == MarkupKind.HEAL) {
            Text(strings.getString(R.string.retouch_hint), style = MaterialTheme.typography.bodySmall)
            EditorSlider(strings.getString(R.string.sample_horizontal), mark.sample.x, 0f..1f, { onChange(mark.copy(sample = mark.sample.copy(x = it))) }, onFinish)
            EditorSlider(strings.getString(R.string.sample_vertical), mark.sample.y, 0f..1f, { onChange(mark.copy(sample = mark.sample.copy(y = it))) }, onFinish)
        }
        if (!mark.stroke) {
            EditorSlider(strings.getString(R.string.markup_width), (mark.right - mark.left) * 100, 1f..100f,
                { onChange(mark.resized(it / 100, mark.bottom - mark.top)) }, onFinish)
            EditorSlider(strings.getString(R.string.markup_height), (mark.bottom - mark.top) * 100, 1f..100f,
                { onChange(mark.resized(mark.right - mark.left, it / 100)) }, onFinish)
        }
        EditorSlider(strings.getString(R.string.horizontal_position), mark.centerX * 100, 0f..100f,
            { onChange(mark.moved(it / 100 - mark.centerX, 0f)) }, onFinish)
        EditorSlider(strings.getString(R.string.vertical_position), mark.centerY * 100, 0f..100f,
            { onChange(mark.moved(0f, it / 100 - mark.centerY)) }, onFinish)
        TextButton(onClick = { onCommit(markups.filter { it.id != mark.id }) }) { Text(strings.getString(R.string.markup_remove)) }
    }
}
