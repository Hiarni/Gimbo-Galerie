package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import de.luma.gallery.R
import de.luma.gallery.model.*

@Composable
fun PhotoLayersControls(recipe: PhotoRecipe, onChange: (PhotoRecipe) -> Unit, onFinish: () -> Unit, onCommit: (PhotoRecipe) -> Unit) {
    val strings = appStrings()
    val keys = recipe.layerKeys()
    Text(strings.getString(R.string.layer_hint), style = MaterialTheme.typography.bodySmall)
    keys.forEachIndexed { index, key ->
        val title = if (key.startsWith("text:")) recipe.texts.first { "text:${it.id}" == key }.text
            else strings.getString(recipe.markups.first { "mark:${it.id}" == key }.kind.labelRes)
        Row {
            Text(title, Modifier.weight(1f), maxLines = 2)
            Switch(recipe.layerVisible(key), { onCommit(recipe.withLayer(key, visible = it)) })
        }
        EditorSlider(strings.getString(R.string.layer_opacity), recipe.layerOpacity(key), 0f..1f,
            { onChange(recipe.withLayer(key, opacity = it)) }, onFinish)
        Row {
            TextButton(onClick = { val order = keys.toMutableList(); java.util.Collections.swap(order, index, index - 1); onCommit(recipe.copy(layerOrder = order)) }, enabled = index > 0) { Text(strings.getString(R.string.layer_back)) }
            TextButton(onClick = { val order = keys.toMutableList(); java.util.Collections.swap(order, index, index + 1); onCommit(recipe.copy(layerOrder = order)) }, enabled = index < keys.lastIndex) { Text(strings.getString(R.string.layer_front)) }
        }
        HorizontalDivider()
    }
}
