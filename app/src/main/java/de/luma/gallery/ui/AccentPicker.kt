package de.luma.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.model.AppSettings
import java.util.Locale

@Composable
fun AccentPicker(settings: AppSettings, enabled: Boolean, onChange: (AppSettings) -> Unit) {
    val strings=appStrings()
    var custom by remember { mutableStateOf(false) }
    val colors=listOf(0xFFB9A7FFL,0xFF8D78F0L,0xFFFF7AB6L,0xFFFF7070L,0xFFF0BD91L,0xFFFFA447L,
        0xFFFFD66AL,0xFFC8DF70L,0xFFA5D6BEL,0xFF51CAA4L,0xFF54D2D5L,0xFF9DCBEAL,0xFF69A1FFL,0xFFAEABFEL,0xFFD5C6B8L,0xFFBAC1CAL)
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        colors.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            row.forEach { color -> Surface(Modifier.size(48.dp).semantics {contentDescription = strings.getString(R.string.accent_color) + " #" + "%06X".format(Locale.ROOT,color and 0xFFFFFF)}.clickable(enabled=enabled) {onChange(settings.copy(accent=color))},color=Color(color),shape=CircleShape) {
                if(color==settings.accent) Box(contentAlignment=Alignment.Center) { Icon(LumaIcons.Check,strings.getString(R.string.accent_color),tint=Color(textOnAccent(color.toInt())),modifier=Modifier.size(20.dp)) }
            } }
        } }
        OutlinedButton(onClick={custom=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text(strings.getString(R.string.create_color)) }
    }
    if(custom) {
        val hsv=remember(settings.accent) { FloatArray(3).also { android.graphics.Color.colorToHSV(settings.accent.toInt(),it) } }
        var hue by remember { mutableFloatStateOf(hsv[0]) };var saturation by remember { mutableFloatStateOf(hsv[1]) };var value by remember { mutableFloatStateOf(hsv[2]) }
        val color=android.graphics.Color.HSVToColor(floatArrayOf(hue,saturation,value)).toLong() and 0xFFFFFFFFL
        var advanced by remember { mutableStateOf(false) };var hex by remember(color) { mutableStateOf("%06X".format(Locale.ROOT,color and 0xFFFFFF)) }
        AlertDialog(onDismissRequest={custom=false},title={Text(strings.getString(R.string.create_color))},text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(Modifier.fillMaxWidth().height(64.dp),color=Color(color),shape=RoundedCornerShape(18.dp)) {
                Box(contentAlignment=Alignment.Center) { Text(strings.getString(R.string.color_preview),color=Color(textOnAccent(color.toInt()))) }
            }
            Text(strings.getString(R.string.hue));Box(Modifier.fillMaxWidth().height(8.dp).background(Brush.horizontalGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)),CircleShape))
            Slider(hue,{hue=it},valueRange=0f..360f)
            Text(strings.getString(R.string.saturation));Slider(saturation,{saturation=it})
            Text(strings.getString(R.string.color_brightness));Slider(value,{value=it})
            TextButton(onClick={advanced=!advanced}) { Text(strings.getString(R.string.hex_optional)) }
            if(advanced) OutlinedTextField(hex,{input -> hex=input.filter { it in "0123456789abcdefABCDEF" }.take(6)
                hex.takeIf { it.length==6 }?.toLongOrNull(16)?.let { n -> val h=FloatArray(3);android.graphics.Color.colorToHSV((0xFF000000L or n).toInt(),h);hue=h[0];saturation=h[1];value=h[2] }
            },prefix={Text("#")},singleLine=true,isError=hex.length!=6,label={Text(strings.getString(R.string.custom_accent))})
        } },confirmButton={TextButton(onClick={onChange(settings.copy(accent=color));custom=false},enabled=!advanced||hex.length==6) {Text(strings.getString(R.string.apply))}},dismissButton={TextButton(onClick={custom=false}) {Text(strings.getString(R.string.cancel))}})
    }
}
