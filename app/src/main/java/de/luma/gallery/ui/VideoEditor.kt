package de.luma.gallery.ui

import android.content.Intent
import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import de.luma.gallery.R
import de.luma.gallery.GalleryViewModel
import de.luma.gallery.data.VideoEffects
import de.luma.gallery.localization.userMessage
import de.luma.gallery.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(item: GalleryMedia, mute: Boolean = false, startMs: Long = 0, endMs: Long = item.durationMs,
    playRequest: Int = 0, onError: (String) -> Unit = {}, recipe: VideoRecipe? = null, speed: Float = 1f, volume: Float = 1f,autoPlay:Boolean=false,showControls:Boolean=true,onSurfaceTap:(()->Unit)?=null,controlInsetPx:Int=0) {
    VideoPreview(item.uri, onError) { onFailure ->
        ClipPlaybackSession(item, mute, startMs, endMs, playRequest, recipe, speed, volume,
            autoPlay, showControls, onSurfaceTap, controlInsetPx, onFailure)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ClipPlaybackSession(item: GalleryMedia, mute: Boolean, startMs: Long, endMs: Long,
    playRequest: Int, recipe: VideoRecipe?, speed: Float, volume: Float, autoPlay: Boolean,
    showControls: Boolean, onSurfaceTap: (() -> Unit)?, controlInsetPx: Int, onFailure: (Exception) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentFailure by rememberUpdatedState(onFailure)
    val created = remember(item.uri) {
        videoPreviewResult {
            ExoPlayer.Builder(context).setLooper(android.os.Looper.getMainLooper())
                .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true).build()
        }
    }
    val player = created.getOrNull()
    if (player == null) {
        LaunchedEffect(created) { currentFailure(created.exceptionOrNull() as Exception) }
        return
    }
    DisposableEffect(owner, player) {
        val lifecycle = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) videoPreviewOperation(currentFailure) { player.pause() }
        }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { currentFailure(error) }
        }
        owner.lifecycle.addObserver(lifecycle)
        player.addListener(listener)
        onDispose {
            owner.lifecycle.removeObserver(lifecycle)
            videoPreviewOperation { player.removeListener(listener); player.release() }
        }
    }
    LaunchedEffect(player, startMs, endMs) {
        videoPreviewOperation(currentFailure) {
            val builder = MediaItem.Builder().setUri(item.uri)
            if (endMs > startMs) builder.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs).setEndPositionMs(endMs).build())
            player.setMediaItem(builder.build())
            player.prepare()
        }
    }
    LaunchedEffect(player, autoPlay) { videoPreviewOperation(currentFailure) { player.playWhenReady = autoPlay } }
    LaunchedEffect(player, mute, speed, volume) {
        videoPreviewOperation(currentFailure) {
            player.volume = if (mute) 0f else volume.coerceIn(0f, 1f)
            player.playbackParameters = PlaybackParameters(speed)
        }
    }
    LaunchedEffect(player, listOf(recipe?.brightness, recipe?.contrast, recipe?.saturation, recipe?.caption,
        recipe?.quarterTurns, recipe?.flipHorizontal, recipe?.flipVertical, recipe?.cropBounds,
        recipe?.aspectRatio, recipe?.fillFrame, recipe?.temperature, recipe?.tint)) {
        videoPreviewOperation(currentFailure) {
            player.setVideoEffects(recipe?.let { VideoEffects.visual(it) } ?: emptyList())
            if (!player.isPlaying) player.seekTo(player.currentPosition)
        }
    }
    LaunchedEffect(player, playRequest) {
        if (playRequest > 0) videoPreviewOperation(currentFailure) { player.seekTo(0); player.play() }
    }
    VideoPreviewSurface(player, showControls = showControls, onSurfaceTap = onSurfaceTap,
        controlInsetPx = controlInsetPx, onFailure = currentFailure)
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
fun VideoEditor(item: GalleryMedia, vm: GalleryViewModel, session: EditorSession, onBack: () -> Unit) {
    val strings = appStrings(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    val state by vm.state.collectAsStateWithLifecycle()
    var history by remember(session.token) { mutableStateOf(History(present = session.video ?: VideoRecipe(0, item.durationMs))) }
    var draft by remember(history.present) { mutableStateOf(history.present) }
    var selected by rememberSaveable(session.token) { mutableStateOf("primary") }
    var timelinePreview by rememberSaveable(session.token) { mutableStateOf(true) }
    var previewPositionMs by remember { mutableLongStateOf(0) }
    var play by remember { mutableIntStateOf(0) }; var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }; var projectDialog by remember { mutableStateOf(false) }
    var preciseTrim by remember {mutableStateOf(false)}
    var replacingClip by remember { mutableStateOf<VideoClip?>(null) }
    var clipReplacement by remember { mutableStateOf<VideoClip?>(null) }
    var captionDialog by remember { mutableStateOf(false) }; var importing by remember { mutableStateOf(false) }
    val enteredProjectId = remember(session.token) { session.projectId }
    LaunchedEffect(session.projectId) { if (session.projectId != null && session.projectId != enteredProjectId) vm.videoDraft(session, draft) }
    val clips = draft.orderedClips(item); val active = clips.find { it.id == selected } ?: clips.firstOrNull()
    val project = state.projects.find { it.id == session.projectId }
    val sourceMime by produceState<String?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            try { context.contentResolver.getType(item.uri) }
            catch (error: Exception) { if (error is CancellationException) throw error; null }
        }
    }
    fun commit(value: VideoRecipe) { history = history.commit(value); if (value.isValid(item.durationMs)) vm.videoDraft(session, value) }
    fun accept(value: History<VideoRecipe>) { history = value; if (value.present.isValid(item.durationMs)) vm.videoDraft(session, value.present) }
    fun updateClip(clip: VideoClip) {
        draft = if (clip.id == "primary") draft.copy(startMs = clip.startMs, endMs = clip.endMs, mute = clip.mute, speed = clip.speed, volume = clip.volume)
            else draft.copy(extraClips = draft.extraClips.map { if (it.id == clip.id) clip else it })
    }
    val addClips = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            importing = true
            scope.launch {
                try {
                    require(uris.size <= draft.additionalClipCapacity(item))
                    val additions = uris.map { uri ->
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val source = vm.repository.inspectSource(uri); require(source.isVideo && source.durationMs in 500..604_800_000)
                        VideoClip(UUID.randomUUID().toString(), source)
                    }
                    commit(draft.copy(extraClips = draft.extraClips + additions))
                } catch (e: Exception) { if (e is CancellationException) throw e; error = e.userMessage(strings, R.string.video_import_failed) }
                finally { importing = false }
            }
        }
    }
    val replaceClipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val previous = replacingClip
        if (uri != null && previous != null) scope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val source = vm.repository.inspectSource(uri)
                require(source.isVideo && source.durationMs in 500..604_800_000)
                de.luma.gallery.data.SourceProbe.inspect(context, source)
                val fits = previous.endMs <= source.durationMs
                clipReplacement = previous.copy(source = source,
                    startMs = if (fits) previous.startMs else 0,
                    endMs = if (fits) previous.endMs else source.durationMs)
            } catch (e: Exception) { if (e is CancellationException) throw e; error = e.userMessage(strings, R.string.video_import_failed) }
        } else replacingClip = null
    }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val music = withContext(Dispatchers.IO) {
                    require(context.contentResolver.getType(uri).orEmpty().startsWith("audio/"))
                    var name = strings.getString(R.string.music)
                    context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
                    val duration = MediaMetadataRetriever().use { it.setDataSource(context, uri); it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0 }
                    VideoMusic(uri.toString(), name, duration)
                }
                commit(draft.copy(music = music))
            } catch (e: Exception) { if (e is CancellationException) throw e; error = e.userMessage(strings, R.string.video_import_failed) }
        }
    }
    val previewContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.padding(12.dp)) {
            if (timelinePreview) VideoCompositionPreview(item, draft, play, previewPositionMs) { error = it }
            else active?.let { clip -> key(clip.id) { VideoPlayer(clip.source, clip.mute, clip.startMs, clip.endMs, play, { error = it }, draft, clip.speed, clip.volume) } }
        }
    }
    val controlsContent: @Composable (Modifier) -> Unit = { modifier ->
        Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainer) { Column {
        error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
        if (state.exporting) ExportStatus(vm::cancelExport, state.exportSnapshot)
        else Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            EditorSection(strings.getString(R.string.timeline),LumaIcons.Timeline,true) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!timelinePreview, { timelinePreview = false }, { Text(strings.getString(R.string.preview_clip)) })
                Spacer(Modifier.width(8.dp))
                FilterChip(timelinePreview, { timelinePreview = true }, { Text(strings.getString(R.string.preview_timeline)) })
            }
            Text(strings.getString(R.string.timeline), style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(clips, key = { _, clip -> clip.id }) { index, clip -> VideoClipCard(clip,index,active?.id==clip.id) {selected=clip.id;timelinePreview=false} }
            }
            OutlinedButton(onClick = { addClips.launch(arrayOf("video/*")) }, enabled = draft.additionalClipCapacity(item) > 0 && !importing) { Text(strings.getString(R.string.add_video_clips)) }
            Text(strings.getString(R.string.timeline_hint), style = MaterialTheme.typography.bodySmall)
            active?.let { clip ->
                Text("${videoTimecode(clip.startMs)} – ${videoTimecode(clip.endMs)}")
                TextButton(onClick={preciseTrim=true}) {Icon(LumaIcons.Crop,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(strings.getString(R.string.precise_trim))}
                RangeSlider(clip.startMs.toFloat()..clip.endMs.toFloat(), { range -> if (range.endInclusive - range.start >= 500)
                    updateClip(clip.copy(startMs = range.start.toLong(), endMs = range.endInclusive.toLong().coerceAtMost(clip.source.durationMs))) }, onValueChangeFinished = { commit(draft) }, valueRange = 0f..maxOf(clip.source.durationMs.toFloat(), 1f))
                EditorSlider(strings.getString(R.string.video_speed), clip.speed, .25f..4f, { updateClip(clip.copy(speed = it)) }, { commit(draft) })
                EditorSlider(strings.getString(R.string.audio_volume), clip.volume, 0f..2f, { updateClip(clip.copy(volume = it)) }, { commit(draft) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.getString(R.string.mute_audio), Modifier.weight(1f))
                    Switch(clip.mute, { updateClip(clip.copy(mute = it)); commit(draft) })
                }
                val index = clips.indexOf(clip)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { val order = clips.map { it.id }.toMutableList(); java.util.Collections.swap(order, index, index - 1); commit(draft.copy(clipOrder = order)) }, enabled = index > 0) { Text(strings.getString(R.string.move_up)) }
                    TextButton(onClick = { val order = clips.map { it.id }.toMutableList(); java.util.Collections.swap(order, index, index + 1); commit(draft.copy(clipOrder = order)) }, enabled = index < clips.lastIndex) { Text(strings.getString(R.string.move_down)) }
                    TextButton(onClick = { commit(if (clip.id == "primary") draft.copy(includeSource = false) else draft.copy(extraClips = draft.extraClips.filter { it.id != clip.id })) }, enabled = clips.size > 1) { Text(strings.getString(R.string.remove_clip)) }
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={if(draft.additionalClipCapacity(item)>0) commit(draft.duplicateClip(item,clip.id))},enabled=draft.additionalClipCapacity(item)>0) {Text(strings.getString(R.string.duplicate_clip))}
                    TextButton(onClick={if(draft.additionalClipCapacity(item)>0) commit(draft.splitClip(item,clip.id,(clip.startMs+clip.endMs)/2))},enabled=draft.additionalClipCapacity(item)>0 && clip.endMs-clip.startMs>=1000) {Text(strings.getString(R.string.split_clip_middle))}
                }
                if (clip.id != "primary") TextButton(onClick = { replacingClip = clip; replaceClipPicker.launch(arrayOf("video/*")) }) {
                    Text(strings.getString(R.string.replace_clip))
                }
                OutlinedButton(onClick = { timelinePreview = false; play++ }) { Text(strings.getString(R.string.play_clip)) }
            }
            Text(strings.getString(R.string.total_video_length, durationLabel(draft.outputDuration(item))))
            OutlinedButton(onClick = { timelinePreview = true; play++ }) { Text(strings.getString(R.string.preview_timeline)) }
            EditorSlider(strings.getString(R.string.video_transition), draft.transitionMs / 1000f, 0f..2f, { draft = draft.copy(transitionMs = (it * 1000).toLong()) }, { commit(draft) })
            }
            EditorSection(strings.getString(R.string.video_geometry),LumaIcons.Crop) {VideoGeometryControls(draft,{draft=it},{commit(draft)},::commit)}
            EditorSection(strings.getString(R.string.filters),LumaIcons.AutoAwesome) {VideoFilterControls(draft,{draft=it},{commit(draft)},::commit)}
            EditorSection(strings.getString(R.string.color_and_text),LumaIcons.Palette) {
            EditorSlider(strings.getString(R.string.brightness), draft.brightness, -1f..1f, { draft = draft.copy(brightness = it) }, { commit(draft) })
            EditorSlider(strings.getString(R.string.contrast), draft.contrast, -1f..1f, { draft = draft.copy(contrast = it) }, { commit(draft) })
            EditorSlider(strings.getString(R.string.saturation), draft.saturation, -1f..1f, { draft = draft.copy(saturation = it) }, { commit(draft) })
            EditorSlider(strings.getString(R.string.temperature),draft.temperature,-1f..1f,{draft=draft.copy(temperature=it)},{commit(draft)})
            EditorSlider(strings.getString(R.string.tint),draft.tint,-1f..1f,{draft=draft.copy(tint=it)},{commit(draft)})
            TextButton(onClick = { captionDialog = true }) { Text(strings.getString(if (draft.caption == null) R.string.add_text else R.string.edit_text)) }
            draft.caption?.let { text ->
                EditorSlider(strings.getString(R.string.text_horizontal_position), text.x, 0f..1f, { draft = draft.copy(caption = text.copy(x = it)) }, { commit(draft) })
                EditorSlider(strings.getString(R.string.text_vertical_position), text.y, 0f..1f, { draft = draft.copy(caption = text.copy(y = it)) }, { commit(draft) })
                EditorSlider(strings.getString(R.string.text_size), text.size, .025f.. .2f, { draft = draft.copy(caption = text.copy(size = it)) }, { commit(draft) })
                TextButton(onClick = { commit(draft.copy(caption = null)) }) { Text(strings.getString(R.string.remove_text)) }
            }
            }
            EditorSection(strings.getString(R.string.audio),LumaIcons.Audio) {
            OutlinedButton(onClick = { musicPicker.launch(arrayOf("audio/*")) }) { Text(strings.getString(R.string.add_music)) }
            draft.music?.let { music ->
                Text(music.name)
                EditorSlider(strings.getString(R.string.music_volume), music.volume, 0f..2f, { draft = draft.copy(music = music.copy(volume = it)) }, { commit(draft) })
                TextButton(onClick = { commit(draft.copy(music = null)) }) { Text(strings.getString(R.string.remove_music)) }
            }
            }
            VideoAdvancedControls(item, draft, { draft = it }, { commit(draft) }, ::commit, { error = it }, { previewPositionMs = it; timelinePreview = true; play = 0 })
            Text(strings.getString(R.string.music_preview_hint), style = MaterialTheme.typography.bodySmall)
            EditorSlider(strings.getString(R.string.audio_fade_in), draft.fadeInMs / 1000f, 0f..10f, { draft = draft.copy(fadeInMs = (it * 1000).toLong()) }, { commit(draft) })
            EditorSlider(strings.getString(R.string.audio_fade_out), draft.fadeOutMs / 1000f, 0f..10f, { draft = draft.copy(fadeOutMs = (it * 1000).toLong()) }, { commit(draft) })
            EditorSection(strings.getString(R.string.export),LumaIcons.Share) {
            Text(strings.getString(R.string.video_resolution))
            LazyRow { itemsIndexed(listOf(720, 1080, 1440, 2160)) { _, height -> FilterChip(draft.outputHeight == height, { commit(draft.copy(outputHeight = height)) }, { Text("${height}p") }) } }
            Text(strings.getString(R.string.video_bitrate))
            LazyRow { itemsIndexed(listOf(3, 6, 12, 24)) { _, mbps -> FilterChip(draft.bitrate == mbps * 1_000_000, { commit(draft.copy(bitrate = mbps * 1_000_000)) }, { Text("$mbps Mbps") }) } }            }

        }        } }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(LumaIcons.ArrowBack, strings.getString(R.string.back)) }
            Text(project?.name ?: strings.getString(R.string.edit_video), Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Medium)
            IconButton(onClick={accept(history.undo())},enabled=history.past.isNotEmpty()&&!state.exporting) {Icon(LumaIcons.Undo,strings.getString(R.string.undo))}
            IconButton(onClick={accept(history.redo())},enabled=history.future.isNotEmpty()&&!state.exporting) {Icon(LumaIcons.Redo,strings.getString(R.string.redo))}
        }
        LazyRow(contentPadding=PaddingValues(horizontal=16.dp)) {
            item {TextButton(onClick = { projectDialog = true }, enabled = draft.isValid(item.durationMs) && !state.exporting && !state.savingProject) { Text(strings.getString(R.string.save_project)) }}
            item {OverwriteButton(item,enabled=draft.isValid(item.durationMs)&&!state.exporting&&!importing&&sourceMime=="video/mp4") {vm.overwriteVideo(item,draft)}}
            item {TextButton(onClick = { confirm = true }, enabled = draft.isValid(item.durationMs) && !state.exporting && !importing) { Text(strings.getString(R.string.export)) }}
        }
        EditorPanes(Modifier.weight(1f), previewContent, controlsContent)
    }
    if(preciseTrim) active?.let {clip->VideoTrimDialog(clip,{preciseTrim=false}) {start,end->updateClip(clip.copy(startMs=start,endMs=end));commit(draft);preciseTrim=false}}
    clipReplacement?.let { replacement ->
        AlertDialog(onDismissRequest = { clipReplacement = null; replacingClip = null },
            title = { Text(strings.getString(R.string.replace_clip)) },
            text = { Text(strings.getString(R.string.replace_clip_review, replacement.source.name,
                durationLabel(replacement.startMs), durationLabel(replacement.endMs))) },
            confirmButton = { TextButton(onClick = {
                if (draft.extraClips.find { it.id == replacement.id } == replacingClip)
                    commit(draft.copy(extraClips = draft.extraClips.map { if (it.id == replacement.id) replacement else it }))
                else error = strings.getString(R.string.source_review_changed)
                clipReplacement = null; replacingClip = null
            }) { Text(strings.getString(R.string.apply)) } },
            dismissButton = { TextButton(onClick = { clipReplacement = null; replacingClip = null }) { Text(strings.getString(R.string.cancel)) } })
    }
    if(confirm) CompactDialog(strings.getString(R.string.save_video_copy),{confirm=false},actions={
        TextButton(onClick={confirm=false}) {Text(strings.getString(R.string.cancel))}
        TextButton(onClick={confirm=false;vm.exportVideo(item,draft)}) {Text(strings.getString(R.string.export_action))}
    }) {Text(strings.getString(R.string.export_video_hint))}
    if (projectDialog) SaveProjectDialog(project?.name ?: item.name.substringBeforeLast('.'), session.projectId != null,
        onDismiss = { projectDialog = false }, onSave = { name, copy -> vm.saveProject(name, copy, video = draft); projectDialog = false })
    if (captionDialog) PhotoTextDialog(draft.caption?.text.orEmpty(), draft.caption != null, { captionDialog = false }) {
        commit(draft.copy(caption = draft.caption?.copy(text = it) ?: PhotoText("video-caption", it))); captionDialog = false
    }
}
