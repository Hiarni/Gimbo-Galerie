package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.model.*
import java.util.UUID

@Composable
fun PhotoHslControls(recipe: PhotoRecipe, onChange: (PhotoRecipe) -> Unit, onFinish: () -> Unit) {
    val strings = appStrings()
    var band by remember { mutableIntStateOf(0) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(HSL_LABELS) { index, label -> FilterChip(band == index, { band = index }, { Text(strings.getString(label)) }) }
    }
    val current = recipe.hsl.bands[band]
    fun update(value: HslBand) { onChange(recipe.copy(hsl = PhotoHsl(recipe.hsl.bands.mapIndexed { index, old -> if (index == band) value else old }))) }
    EditorSlider(strings.getString(R.string.hue), current.hue, -45f..45f, { update(current.copy(hue = it)) }, onFinish)
    EditorSlider(strings.getString(R.string.saturation), current.saturation, -1f..1f, { update(current.copy(saturation = it)) }, onFinish)
    EditorSlider(strings.getString(R.string.lightness), current.lightness, -1f..1f, { update(current.copy(lightness = it)) }, onFinish)
}

@Composable
fun PhotoMaskControls(recipe: PhotoRecipe, onChange: (PhotoRecipe) -> Unit, onFinish: () -> Unit, onCommit: (PhotoRecipe) -> Unit, selectedId: String? = null, onSelect: (String) -> Unit = {}) {
    val strings = appStrings()
    var selected by remember { mutableStateOf<String?>(null) }
    val mask = recipe.masks.find { it.id == (selectedId ?: selected) } ?: recipe.masks.firstOrNull()
    Text(strings.getString(R.string.mask_hint), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { val value = PhotoMask(UUID.randomUUID().toString()); selected = value.id; onSelect(value.id); onCommit(recipe.copy(masks = recipe.masks + value)) }, enabled = recipe.masks.size < 8) { Text(strings.getString(R.string.add_mask)) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(recipe.masks, key = { _, value -> value.id }) { index, value -> FilterChip(mask?.id == value.id, { selected = value.id; onSelect(value.id) }, { Text("${index + 1}") }) }
    }
    if (mask != null) {
        fun updated(value: PhotoMask) = recipe.copy(masks = recipe.masks.map { if (it.id == mask.id) value else it })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(MaskShape.entries) { shape -> FilterChip(mask.shape == shape, { onCommit(updated(mask.copy(shape = shape, points = if (mask.points.isNotEmpty()) mask.points else when(shape) {
                MaskShape.FREEHAND -> listOf(MarkupPoint(0f,0f),MarkupPoint(1f,0f),MarkupPoint(1f,1f),MarkupPoint(0f,1f))
                MaskShape.BRUSH -> listOf(MarkupPoint(.25f,.5f),MarkupPoint(.75f,.5f))
                else -> emptyList()
            }))) },
                { Text(strings.getString(when (shape) { MaskShape.ELLIPSE -> R.string.markup_ellipse; MaskShape.RECTANGLE -> R.string.markup_rectangle; MaskShape.FREEHAND -> R.string.mask_freehand; MaskShape.BRUSH -> R.string.mask_brush; MaskShape.LUMINANCE -> R.string.mask_luminance; MaskShape.COLOR -> R.string.mask_color })) }) }
        }
        Row { RoundCheckbox(mask.inverted, { onCommit(updated(mask.copy(inverted = it))) }); Text(strings.getString(R.string.mask_invert)) }
        if (mask.shape in listOf(MaskShape.FREEHAND, MaskShape.BRUSH)) Text(strings.getString(R.string.mask_draw_hint), style = MaterialTheme.typography.bodySmall)
        if (mask.shape in listOf(MaskShape.FREEHAND, MaskShape.BRUSH) && mask.points.isNotEmpty()) {
            var pointIndex by remember(mask.id) { mutableIntStateOf(0) }
            pointIndex = pointIndex.coerceIn(0,mask.points.lastIndex)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { itemsIndexed(mask.points) { index, _ ->
                FilterChip(pointIndex == index, { pointIndex=index }, { Text(strings.getString(R.string.mask_point, index+1)) })
            } }
            val point=mask.points[pointIndex]
            fun updatePoint(value: MarkupPoint) = updated(mask.copy(points=mask.points.mapIndexed { index,old -> if(index==pointIndex)value else old }))
            EditorSlider(strings.getString(R.string.sample_horizontal),point.x,0f..1f,{onChange(updatePoint(point.copy(x=it)))},onFinish)
            EditorSlider(strings.getString(R.string.sample_vertical),point.y,0f..1f,{onChange(updatePoint(point.copy(y=it)))},onFinish)
            Row {
                TextButton(onClick={onCommit(updated(mask.copy(points=mask.points+MarkupPoint(.5f,.5f))))},enabled=mask.points.size<256) { Text(strings.getString(R.string.mask_point_add)) }
                TextButton(onClick={onCommit(updated(mask.copy(points=mask.points.filterIndexed{index,_->index!=pointIndex})))},enabled=mask.points.size>if(mask.shape==MaskShape.FREEHAND)3 else 1) { Text(strings.getString(R.string.mask_point_remove)) }
            }
        }
        if (mask.shape == MaskShape.BRUSH) EditorSlider(strings.getString(R.string.markup_width), mask.brushSize, .005f.. .25f, { onChange(updated(mask.copy(brushSize = it))) }, onFinish)
        if (mask.shape in listOf(MaskShape.LUMINANCE, MaskShape.COLOR)) {
            EditorSlider(strings.getString(R.string.mask_range_start), mask.rangeStart, 0f..1f, { onChange(updated(mask.copy(rangeStart = it, rangeEnd = maxOf(it, mask.rangeEnd)))) }, onFinish)
            EditorSlider(strings.getString(R.string.mask_range_end), mask.rangeEnd, 0f..1f, { onChange(updated(mask.copy(rangeEnd = it, rangeStart = minOf(it, mask.rangeStart)))) }, onFinish)
        }
        EditorSlider(strings.getString(R.string.text_horizontal_position), mask.x, 0f..1f, { onChange(updated(mask.copy(x = it))) }, onFinish)
        EditorSlider(strings.getString(R.string.text_vertical_position), mask.y, 0f..1f, { onChange(updated(mask.copy(y = it))) }, onFinish)
        EditorSlider(strings.getString(R.string.mask_width), mask.width, .01f..1f, { onChange(updated(mask.copy(width = it))) }, onFinish)
        EditorSlider(strings.getString(R.string.mask_height), mask.height, .01f..1f, { onChange(updated(mask.copy(height = it))) }, onFinish)
        EditorSlider(strings.getString(R.string.mask_feather), mask.feather, 0f..1f, { onChange(updated(mask.copy(feather = it))) }, onFinish)
        EditorSlider(strings.getString(R.string.exposure), mask.exposure, -2f..2f, { onChange(updated(mask.copy(exposure = it))) }, onFinish)
        EditorSlider(strings.getString(R.string.saturation), mask.saturation, 0f..2f, { onChange(updated(mask.copy(saturation = it))) }, onFinish)
        TextButton(onClick = { onCommit(recipe.copy(masks = recipe.masks.filter { it.id != mask.id })) }) { Text(strings.getString(R.string.remove_mask)) }
    }
}
