package de.luma.gallery.ui

import de.luma.gallery.R
import de.luma.gallery.localization.appLocale
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.luma.gallery.model.PhotoRecipe

enum class PhotoToolPage(val labelRes: Int) {
    LIGHT(R.string.light), COLOR(R.string.color), CURVES(R.string.curves), HSL(R.string.hsl), MASKS(R.string.masks), DETAILS(R.string.details), CROP(R.string.crop), TEXT(R.string.text), MARKUP(R.string.markup), LAYERS(R.string.layers), FILTERS(R.string.filters)
}

@Composable
fun PhotoAdjustmentControls(page: PhotoToolPage, recipe: PhotoRecipe, onChange: (PhotoRecipe) -> Unit,
    onFinish: () -> Unit, onReset: (PhotoRecipe) -> Unit) {
    val strings = appStrings()
    when (page) {
        PhotoToolPage.LIGHT -> {
            EditorSlider(strings.getString(R.string.exposure), recipe.exposure, -2f..2f, { onChange(recipe.copy(exposure = it)) }, onFinish)
            EditorSlider(strings.getString(R.string.brightness), recipe.brightness, -.5f.. .5f, { onChange(recipe.copy(brightness = it)) }, onFinish)
            EditorSlider(strings.getString(R.string.contrast), recipe.contrast, .5f..1.8f, { onChange(recipe.copy(contrast = it)) }, onFinish)
            EditorSlider(strings.getString(R.string.highlights), recipe.highlights, -1f..1f, { onChange(recipe.copy(highlights = it)) }, onFinish)
            EditorSlider(strings.getString(R.string.shadows), recipe.shadows, -1f..1f, { onChange(recipe.copy(shadows = it)) }, onFinish)
            TextButton(onClick = { onReset(recipe.copy(exposure = 0f, brightness = 0f, contrast = 1f, highlights = 0f, shadows = 0f)) }) {
                Text(strings.getString(R.string.reset_light))
            }
        }
        PhotoToolPage.COLOR -> {
            EditorSlider(strings.getString(R.string.saturation), recipe.saturation, 0f..2f, { onChange(recipe.copy(saturation = it)) }, onFinish)
            EditorSlider(strings.getString(R.string.temperature), recipe.temperature, -1f..1f, { onChange(recipe.copy(temperature = it)) }, onFinish)
            EditorSlider(strings.getString(R.string.tint), recipe.tint, -1f..1f, { onChange(recipe.copy(tint = it)) }, onFinish)
            TextButton(onClick = { onReset(recipe.copy(saturation = 1f, temperature = 0f, tint = 0f)) }) { Text(strings.getString(R.string.reset_color)) }
        }
        PhotoToolPage.DETAILS -> {
            EditorSlider(strings.getString(R.string.sharpness), recipe.sharpness, 0f..1f, { onChange(recipe.copy(sharpness = it)) }, onFinish)
            Text(strings.getString(R.string.sharpness_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onReset(recipe.copy(sharpness = 0f)) }) { Text(strings.getString(R.string.reset_details)) }
        }
        else -> Unit
    }
}

@Composable
internal fun EditorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onFinish: () -> Unit) {
    val strings = appStrings()
    var editing by remember { mutableStateOf(false) }
    var number by remember(value) { mutableStateOf("%.3f".format(java.util.Locale.ROOT,value)) }
    val parsed=number.replace(',', '.').toFloatOrNull()?.takeIf {it.isFinite() && it in range}
    Surface(shape=RoundedCornerShape(16.dp),color=androidx.compose.ui.graphics.Color.Transparent,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)) {
        Column(Modifier.padding(horizontal=12.dp,vertical=4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                Surface(onClick={editing=true},shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier=Modifier.heightIn(min=48.dp).semantics {contentDescription=strings.getString(R.string.slider_precision,label)}) {
                    Box(Modifier.padding(horizontal=12.dp,vertical=8.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {Text("%.2f".format(strings.appLocale,value),style=MaterialTheme.typography.labelLarge)}
                }
            }
            Slider(value,onChange,valueRange=range,onValueChangeFinished=onFinish,
                modifier=Modifier.heightIn(min=48.dp).semantics {contentDescription=label})
        }
    }
    if(editing) CompactDialog(label,{editing=false},actions={
        TextButton(onClick={editing=false}) {Text(strings.getString(R.string.cancel))}
        TextButton(onClick={parsed?.let {onChange(it);onFinish()};editing=false},enabled=parsed!=null) {Text(strings.getString(R.string.apply))}
    }) {
        OutlinedTextField(number,{number=it},singleLine=true,isError=parsed==null,
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),
            supportingText={Text("${range.start} … ${range.endInclusive}")},
            trailingIcon={if(range.start<0) TextButton(onClick={number=if(number.startsWith("-")) number.drop(1) else "-$number"}) {Text("±")}})
    }
}
