package de.luma.gallery.ui

import android.content.Intent
import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.data.PhotoEngine
import de.luma.gallery.localization.userMessage
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import java.util.UUID


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoAdvancedControls(source: GalleryMedia, recipe: VideoRecipe, onChange: (VideoRecipe) -> Unit,
    onFinish: () -> Unit, onCommit: (VideoRecipe) -> Unit, onError: (String) -> Unit, onSeek: (Long) -> Unit = {}) {
    val strings=appStrings(); val context=LocalContext.current; val scope=rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var keyTime by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableStateOf(VideoKeyframe(0)) }
    var trackId by remember { mutableStateOf<String?>(null) }
    var overlayId by remember { mutableStateOf<String?>(null) }
    var textDialog by remember { mutableStateOf(false) }
    var editingText by remember { mutableStateOf<VideoOverlay?>(null) }
    var importing by remember { mutableStateOf(false) }
    val duration=recipe.outputDuration(source).coerceAtLeast(1)
    val current by rememberUpdatedState(recipe)
    val commit by rememberUpdatedState(onCommit)
    val audioPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) {
        importing=true
        scope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val music=withContext(Dispatchers.IO) {
                    require(context.contentResolver.getType(uri).orEmpty().startsWith("audio/"))
                    var name=strings.getString(R.string.music)
                    context.contentResolver.query(uri,arrayOf("_display_name"),null,null,null)?.use { if(it.moveToFirst()) name=it.getString(0) ?: name }
                    val length=MediaMetadataRetriever().use { it.setDataSource(context,uri); it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0 }
                    VideoMusic(uri.toString(),name,length)
                }
                val track=VideoAudioTrack(UUID.randomUUID().toString(),music,lengthMs=minOf(music.durationMs,current.outputDuration(source)).coerceAtLeast(1))
                if(current.audioTracks.size<8) { trackId=track.id; commit(current.copy(audioTracks=current.audioTracks+track)) }
            } catch(e: Exception) { if(e is CancellationException) throw e; onError(e.userMessage(strings,R.string.video_import_failed)) }
            finally { importing=false }
        }
    } }
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) {
        importing=true
        scope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                require(context.contentResolver.getType(uri).orEmpty().startsWith("image/"))
                withContext(Dispatchers.IO) { PhotoEngine.decode(context,uri,64).recycle() }
                val overlay=VideoOverlay(UUID.randomUUID().toString(),imageUri=uri.toString(),endMs=current.outputDuration(source).coerceAtLeast(1))
                if(current.overlays.size<16) { overlayId=overlay.id; commit(current.copy(overlays=current.overlays+overlay)) }
            } catch(e: Exception) { if(e is CancellationException) throw e; onError(e.userMessage(strings,R.string.video_import_failed)) }
            finally { importing=false }
        }
    } }
    TextButton(onClick={expanded=!expanded}) { Text(strings.getString(if(expanded) R.string.video_advanced_hide else R.string.video_advanced)) }
    if(!expanded) return
    Text(strings.getString(R.string.video_advanced_hint),style=MaterialTheme.typography.bodySmall)
    EditorSlider(strings.getString(R.string.video_crossfade),recipe.crossfadeMs/1000f,0f..5f,{onChange(recipe.copy(crossfadeMs=(it*1000).toLong()))},onFinish)
    EditorSection(strings.getString(R.string.video_keyframes),LumaIcons.Curve) {
    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { items(recipe.keyframes.sortedBy { it.timeMs },key={it.timeMs}) { k ->
        FilterChip(frame.timeMs==k.timeMs,{frame=k;keyTime=k.timeMs/1000f;onSeek(k.timeMs)},{Text(durationLabel(k.timeMs))})
    } }
    EditorSlider(strings.getString(R.string.timeline_position),keyTime.coerceIn(0f,duration/1000f),0f..duration/1000f,{keyTime=it;frame=recipe.frameAt((it*1000).toLong());onSeek(frame.timeMs)},{})
    EditorSlider(strings.getString(R.string.video_zoom),frame.zoom,1f..4f,{frame=frame.copy(zoom=it)},{})
    EditorSlider(strings.getString(R.string.horizontal_position),frame.x,0f..1f,{frame=frame.copy(x=it)},{})
    EditorSlider(strings.getString(R.string.vertical_position),frame.y,0f..1f,{frame=frame.copy(y=it)},{})
    EditorSlider(strings.getString(R.string.brightness),frame.brightness,-1f..1f,{frame=frame.copy(brightness=it)},{})
    EditorSlider(strings.getString(R.string.video_master_volume),frame.volume,0f..2f,{frame=frame.copy(volume=it)},{})
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick={val k=frame.copy(timeMs=(keyTime*1000).toLong().coerceIn(0,duration));onCommit(recipe.copy(keyframes=(recipe.keyframes.filter{it.timeMs!=k.timeMs}+k).sortedBy{it.timeMs}))},enabled=recipe.keyframes.size<100 || recipe.keyframes.any{it.timeMs==frame.timeMs}) {Text(strings.getString(R.string.keyframe_save))}
        TextButton(onClick={onCommit(recipe.copy(keyframes=recipe.keyframes.filter{it.timeMs!=frame.timeMs}))},enabled=recipe.keyframes.any{it.timeMs==frame.timeMs}) {Text(strings.getString(R.string.keyframe_remove))}
    }
    }
    EditorSection(strings.getString(R.string.video_extra_audio),LumaIcons.Audio) {
    OutlinedButton(onClick={audioPicker.launch(arrayOf("audio/*"))},enabled=recipe.audioTracks.size<8 && !importing) {Text(strings.getString(R.string.audio_track_add))}
    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { items(recipe.audioTracks,key={it.id}) { t -> FilterChip(t.id==trackId,{trackId=t.id},{Text(t.music.name)}) } }
    recipe.audioTracks.find{it.id==trackId}?.let { t ->
        fun update(v: VideoAudioTrack)=recipe.copy(audioTracks=recipe.audioTracks.map{if(it.id==t.id) v else it})
        EditorSlider(strings.getString(R.string.timeline_position),t.timelineStartMs/1000f,0f..duration/1000f,{onChange(update(t.copy(timelineStartMs=(it*1000).toLong())))},onFinish)
        val sourceDuration=t.music.durationMs
        EditorSlider(strings.getString(R.string.audio_source_start),t.sourceStartMs/1000f,0f..(sourceDuration-1)/1000f,{
            val start=(it*1000).toLong().coerceAtMost(sourceDuration-1);val end=maxOf(start+1,t.sourceEndMs)
            onChange(update(t.copy(sourceStartMs=start,sourceEndMs=end,lengthMs=minOf(t.lengthMs,(end-start)*if(t.loop)2048 else 1))))
        },onFinish)
        EditorSlider(strings.getString(R.string.audio_source_end),t.sourceEndMs/1000f,0f..sourceDuration/1000f,{
            val end=(it*1000).toLong().coerceIn(t.sourceStartMs+1,sourceDuration)
            onChange(update(t.copy(sourceEndMs=end,lengthMs=minOf(t.lengthMs,(end-t.sourceStartMs)*if(t.loop)2048 else 1))))
        },onFinish)
        EditorSlider(strings.getString(R.string.audio_track_length),t.lengthMs/1000f,0f..duration/1000f,{
            val length=(it*1000).toLong().coerceAtLeast(1)
            onChange(update(t.copy(lengthMs=minOf(length,(t.sourceEndMs-t.sourceStartMs)*if(t.loop)2048 else 1))))
        },onFinish)
        Row(verticalAlignment=Alignment.CenterVertically) { RoundCheckbox(t.loop,{onCommit(update(t.copy(loop=it,lengthMs=if(it)t.lengthMs else minOf(t.lengthMs,t.sourceEndMs-t.sourceStartMs))))});Text(strings.getString(R.string.audio_loop)) }
        EditorSlider(strings.getString(R.string.music_volume),t.music.volume,0f..2f,{onChange(update(t.copy(music=t.music.copy(volume=it))))},onFinish)
        EditorSlider(strings.getString(R.string.audio_fade_in),t.fadeInMs/1000f,0f..10f,{onChange(update(t.copy(fadeInMs=(it*1000).toLong())))},onFinish)
        EditorSlider(strings.getString(R.string.audio_fade_out),t.fadeOutMs/1000f,0f..10f,{onChange(update(t.copy(fadeOutMs=(it*1000).toLong())))},onFinish)
        TextButton(onClick={onCommit(recipe.copy(audioTracks=recipe.audioTracks.filter{it.id!=t.id}))}) {Text(strings.getString(R.string.remove_music))}
    }
    }
    EditorSection(strings.getString(R.string.video_overlays),LumaIcons.Layers) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick={editingText=null;textDialog=true},enabled=recipe.overlays.size<16) {Text(strings.getString(R.string.add_text))}
        TextButton(onClick={imagePicker.launch(arrayOf("image/*"))},enabled=recipe.overlays.size<16 && !importing) {Text(strings.getString(R.string.video_overlay_image))}
    }
    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { items(recipe.overlays,key={it.id}) { o -> FilterChip(o.id==overlayId,{overlayId=o.id;onSeek(o.startMs)},{Text(o.text?.text?.take(20) ?: strings.getString(R.string.photo))}) } }
    recipe.overlays.find{it.id==overlayId}?.let { o ->
        fun update(v: VideoOverlay)=recipe.copy(overlays=recipe.overlays.map{if(it.id==o.id)v else it})
        EditorSlider(strings.getString(R.string.overlay_start),o.startMs/1000f,0f..duration/1000f,{
            val start=(it*1000).toLong().coerceAtMost(86_399_999);onChange(update(o.copy(startMs=start,endMs=maxOf(start+1,o.endMs))))
        },onFinish)
        EditorSlider(strings.getString(R.string.overlay_end),o.endMs.coerceAtMost(duration)/1000f,0f..duration/1000f,{onChange(update(o.copy(endMs=(it*1000).toLong().coerceIn(o.startMs+1,86_400_000))))},onFinish)
        EditorSlider(strings.getString(R.string.horizontal_position),o.x,0f..1f,{onChange(update(o.copy(x=it)))},onFinish)
        EditorSlider(strings.getString(R.string.vertical_position),o.y,0f..1f,{onChange(update(o.copy(y=it)))},onFinish)
        EditorSlider(strings.getString(R.string.text_size),o.scale,.025f..2f,{onChange(update(o.copy(scale=it)))},onFinish)
        EditorSlider(strings.getString(R.string.layer_opacity),o.opacity,0f..1f,{onChange(update(o.copy(opacity=it)))},onFinish)
        EditorSlider(strings.getString(R.string.overlay_rotation),o.rotation,-180f..180f,{onChange(update(o.copy(rotation=it)))},onFinish)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(o.text!=null) TextButton(onClick={editingText=o;textDialog=true}) {Text(strings.getString(R.string.edit_text))}
            TextButton(onClick={onCommit(recipe.copy(overlays=recipe.overlays.filter{it.id!=o.id}))}) {Text(strings.getString(R.string.markup_remove))}
        }
    }
    }
    if(importing) LinearProgressIndicator()
    if(textDialog) PhotoTextDialog(editingText?.text?.text.orEmpty(),editingText!=null,{textDialog=false}) { value ->
        val old=editingText
        val overlay=old?.copy(text=old.text!!.copy(text=value)) ?: VideoOverlay(UUID.randomUUID().toString(),text=PhotoText(UUID.randomUUID().toString(),value),endMs=duration)
        overlayId=overlay.id
        onCommit(if(old==null) recipe.copy(overlays=recipe.overlays+overlay) else recipe.copy(overlays=recipe.overlays.map{if(it.id==old.id)overlay else it}))
        textDialog=false
    }
}
