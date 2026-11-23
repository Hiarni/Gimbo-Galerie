package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.model.*

@Composable
fun VideoGeometryControls(recipe:VideoRecipe,onChange:(VideoRecipe)->Unit,onFinish:()->Unit,onCommit:(VideoRecipe)->Unit) {
    val strings=appStrings()
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        item {OutlinedButton(onClick={onCommit(recipe.copy(quarterTurns=(recipe.quarterTurns+1)%4))}) {Icon(LumaIcons.Rotate90DegreesCw,null);Text(strings.getString(R.string.rotate_90))}}
        item {FilterChip(recipe.flipHorizontal,{onCommit(recipe.copy(flipHorizontal=!recipe.flipHorizontal))},{Text(strings.getString(R.string.video_flip_horizontal))},leadingIcon={Icon(LumaIcons.Flip,null)})}
        item {FilterChip(recipe.flipVertical,{onCommit(recipe.copy(flipVertical=!recipe.flipVertical))},{Text(strings.getString(R.string.video_flip_vertical))})}
    }
    CompactChoice(strings.getString(R.string.video_aspect),recipe.aspectRatio,listOf(null,1f,4f/3,3f/4,16f/9,9f/16,4f/5),{
        when(it) {null->strings.getString(R.string.original);1f->"1:1";4f/3->"4:3";3f/4->"3:4";16f/9->"16:9";9f/16->"9:16";else->"4:5"}
    },{onCommit(recipe.copy(aspectRatio=it))})
    if(recipe.aspectRatio!=null) CompactChoice(strings.getString(R.string.video_frame_mode),recipe.fillFrame,listOf(false,true),{
        strings.getString(if(it) R.string.video_fill_frame else R.string.video_fit_frame)
    },{onCommit(recipe.copy(fillFrame=it))})
    val crop=recipe.cropBounds ?: CropBounds()
    EditorSlider(strings.getString(R.string.width),crop.width,.2f..1f,{onChange(recipe.copy(cropBounds=crop.withSize(it,crop.height)))},onFinish)
    EditorSlider(strings.getString(R.string.height),crop.height,.2f..1f,{onChange(recipe.copy(cropBounds=crop.withSize(crop.width,it)))},onFinish)
    if(crop.width<1f) EditorSlider(strings.getString(R.string.horizontal_position),crop.left/(1-crop.width),0f..1f,{onChange(recipe.copy(cropBounds=crop.move(it*(1-crop.width)-crop.left,0f)))},onFinish)
    if(crop.height<1f) EditorSlider(strings.getString(R.string.vertical_position),crop.top/(1-crop.height),0f..1f,{onChange(recipe.copy(cropBounds=crop.move(0f,it*(1-crop.height)-crop.top)))},onFinish)
    Text(strings.getString(R.string.video_geometry_hint),style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={onCommit(recipe.copy(quarterTurns=0,flipHorizontal=false,flipVertical=false,cropBounds=null,aspectRatio=null,fillFrame=false))}) {Text(strings.getString(R.string.reset_crop))}
}

@Composable
fun VideoFilterControls(recipe:VideoRecipe,onChange:(VideoRecipe)->Unit,onFinish:()->Unit,onCommit:(VideoRecipe)->Unit) {
    val strings=appStrings()
    var selected by remember {mutableStateOf<VideoPreset?>(null)}
    var strength by remember {mutableFloatStateOf(1f)}
    val matching=selected?.takeIf {recipe.withPreset(it,strength)==recipe}
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items(VIDEO_PRESETS,key={it.id}) {preset->FilterChip(matching==preset,{
            selected=preset;strength=1f;onCommit(recipe.withPreset(preset,1f))
        },{Text(strings.getString(preset.label))},leadingIcon={Icon(LumaIcons.AutoAwesome,null,Modifier.size(18.dp))})}
    }
    matching?.let {preset->EditorSlider(strings.getString(R.string.filter_strength),strength,0f..1f,{
        strength=it;onChange(recipe.withPreset(preset,it))
    },onFinish)}
}

@Composable
fun VideoTrimDialog(clip:VideoClip,onDismiss:()->Unit,onApply:(Long,Long)->Unit) {
    val strings=appStrings()
    var start by remember(clip.id) {mutableStateOf(videoTimecode(clip.startMs))}
    var end by remember(clip.id) {mutableStateOf(videoTimecode(clip.endMs))}
    val from=parseVideoTimecode(start);val to=parseVideoTimecode(end)
    val valid=from!=null && to!=null && from>=0 && to<=clip.source.durationMs && to-from>=500
    CompactDialog(strings.getString(R.string.precise_trim),onDismiss,actions={
        TextButton(onClick=onDismiss) {Text(strings.getString(R.string.cancel))}
        TextButton(onClick={onApply(requireNotNull(from),requireNotNull(to))},enabled=valid) {Text(strings.getString(R.string.apply))}
    }) {
        Text(clip.source.name,style=MaterialTheme.typography.titleSmall)
        OutlinedTextField(start,{start=it.take(16)},label={Text(strings.getString(R.string.trim_start))},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(end,{end=it.take(16)},label={Text(strings.getString(R.string.trim_end))},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text(strings.getString(R.string.trim_time_hint,videoTimecode(clip.source.durationMs)),style=MaterialTheme.typography.bodySmall)
        if(!valid) Text(strings.getString(R.string.trim_invalid),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }
}
