package de.luma.gallery.ui

import de.luma.gallery.R
import android.graphics.Bitmap
import kotlinx.coroutines.flow.conflate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luma.gallery.GalleryViewModel
import de.luma.gallery.data.PhotoEngine
import de.luma.gallery.model.GalleryMedia
import de.luma.gallery.model.History
import de.luma.gallery.model.PhotoRecipe
import de.luma.gallery.model.EditorSession
import de.luma.gallery.model.CropBounds
import de.luma.gallery.model.boundsFor
import de.luma.gallery.model.rotatedClockwise
import de.luma.gallery.model.mirrored
import de.luma.gallery.model.withText
import de.luma.gallery.model.withMarkup
import de.luma.gallery.model.PhotoMarkup
import de.luma.gallery.model.MarkupKind
import de.luma.gallery.model.PHOTO_MARKUP_LIMIT
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Composable
fun PhotoEditor(item: GalleryMedia, vm: GalleryViewModel, session: EditorSession, onBack: () -> Unit) {
    val strings = appStrings()
    val context = LocalContext.current
    val appState by vm.state.collectAsStateWithLifecycle()
    var history by remember(session.token) { mutableStateOf(History(present = session.photo ?: PhotoRecipe())) }
    var draft by remember(history.present) { mutableStateOf(history.present) }
    var tab by rememberSaveable(item.id) { mutableStateOf(PhotoToolPage.LIGHT) }
    var original by rememberSaveable(item.id) { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var projectDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preciseCrop by rememberSaveable(item.id) { mutableStateOf(false) }
    var selectedMaskId by rememberSaveable(session.token) { mutableStateOf<String?>(null) }
    var selectedTextId by rememberSaveable(session.token) { mutableStateOf<String?>(null) }
    var selectedMarkupId by rememberSaveable(session.token) { mutableStateOf<String?>(null) }
    var markupTool by rememberSaveable(session.token) { mutableStateOf(MarkupKind.BRUSH) }
    val selectedMarkup = draft.markups.find { it.id == selectedMarkupId }
    val selectedText = draft.texts.find { it.id == selectedTextId } ?: draft.texts.firstOrNull()
    val source by produceState<Bitmap?>(null, item.id) {
        var generated: Bitmap? = null
        try { value = withContext(Dispatchers.IO) { PhotoEngine.decode(context, item.uri, 1280).also { generated = it } } }
        catch (failure: Exception) {
            generated?.recycle()
            if (failure is CancellationException) throw failure
            error = strings.getString(R.string.image_open_failed)
        }
        catch(failure:OutOfMemoryError) {generated?.recycle();error=strings.getString(R.string.image_memory_limit)}
    }
    val previewRecipe = if (tab == PhotoToolPage.CROP) draft.copy(cropRatio = null, cropBounds = null, texts = emptyList(), markups = emptyList()) else draft
    var edited by remember(source) {mutableStateOf<Bitmap?>(null)}
    val liveRecipe by rememberUpdatedState(previewRecipe)
    LaunchedEffect(source) {
        val loaded=source ?: return@LaunchedEffect
        snapshotFlow {liveRecipe}.conflate().collect {current->
            var generated:Bitmap?=null
            try {
                edited=withContext(Dispatchers.Default) {
                    val renderContext=currentCoroutineContext()
                    PhotoEngine.render(loaded,current) {renderContext.ensureActive()}.also {generated=it}
                }
                error=null
            } catch(failure:Exception) {
                generated?.recycle()
                if(failure is CancellationException) throw failure
                error=strings.getString(R.string.image_edit_failed)
            }
            catch(failure:OutOfMemoryError) {generated?.recycle();error=strings.getString(R.string.image_memory_limit)}
        }
    }
    val enteredProjectId = remember(session.token) { session.projectId }
    LaunchedEffect(session.projectId) { if (session.projectId != null && session.projectId != enteredProjectId) vm.photoDraft(session, history.present) }
    val accept: (History<PhotoRecipe>) -> Unit = {
        val changed = it.present != history.present
        history = it
        if (changed) vm.photoDraft(session, it.present)
    }
    val commit: (PhotoRecipe) -> Unit = { accept(history.commit(it)) }
    val createMarkup: (PhotoMarkup) -> Unit = { mark ->
        if (draft.markups.size < PHOTO_MARKUP_LIMIT) {
            commit(draft.copy(markups = draft.markups + mark))

            selectedMarkupId = if (mark.kind == MarkupKind.BRUSH) null else mark.id
        }
    }
    val project = appState.projects.find { it.id == session.projectId }
    val imageAspect = source?.let { if (draft.quarterTurns.mod(2) == 0) it.width.toFloat() / it.height else it.height.toFloat() / it.width } ?: 1f
    val bounds = draft.boundsFor(imageAspect)
    val previewContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.padding(16.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            val preview = if (original) source else edited
            if (preview != null && tab == PhotoToolPage.CROP && !original && !appState.exporting) CropPreview(preview, bounds, draft.cropRatio != null,
                onChange = { draft = draft.copy(cropBounds = it) }, onFinish = { commit(draft) })
            else if (preview != null && tab == PhotoToolPage.MASKS && !original && !appState.exporting) PhotoMaskPreview(preview,
                draft.masks.find { it.id == selectedMaskId } ?: draft.masks.firstOrNull(),
                onChange = { mask -> draft = draft.copy(masks = draft.masks.map { if (it.id == mask.id) mask else it }) }, onFinish = { commit(draft) })
            else if (preview != null && tab == PhotoToolPage.TEXT && !original && !appState.exporting) PhotoTextPreview(preview, selectedText,
                onChange = { draft = draft.withText(it) }, onFinish = { commit(draft) })
            else if (preview != null && tab == PhotoToolPage.MARKUP && !original && !appState.exporting) PhotoMarkupPreview(preview,
                selectedMarkup, markupTool, draft.markups.lastOrNull { it.kind == markupTool }, draft.markups.size < PHOTO_MARKUP_LIMIT,
                onChange = { draft = draft.withMarkup(it) }, onFinish = { commit(draft) }, onCreate = createMarkup)
            else if (preview != null) Image(preview.asImageBitmap(), if (original) strings.getString(R.string.original) else strings.getString(R.string.edit_preview), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else if (error == null) CircularProgressIndicator()
            error?.let { Text(it, Modifier.padding(24.dp)) }
        }
    }
    val controlsContent: @Composable (Modifier) -> Unit = { modifier ->
        Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainer) { Column {
        LazyRow(contentPadding=PaddingValues(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            item {Row {
            IconButton(onClick = { accept(history.undo()) }, enabled = history.past.isNotEmpty() && !appState.exporting) { Icon(LumaIcons.Undo, strings.getString(R.string.undo)) }
            IconButton(onClick = { accept(history.redo()) }, enabled = history.future.isNotEmpty() && !appState.exporting) { Icon(LumaIcons.Redo, strings.getString(R.string.redo)) }
            LabeledIconButton(LumaIcons.Rotate90DegreesCw,strings.getString(R.string.rotate_90),{commit(draft.rotatedClockwise())},!appState.exporting)
            LabeledIconButton(LumaIcons.Flip,strings.getString(R.string.mirror),{commit(draft.mirrored())},!appState.exporting)
            }}
            item {FilterChip(original, { original = !original }, { Text(if (original) strings.getString(R.string.original) else strings.getString(R.string.compare)) },
                leadingIcon = { Icon(LumaIcons.Compare, null, Modifier.size(16.dp)) })}
        }
        if (appState.exporting) ExportStatus(onCancel = vm::cancelExport, snapshot = appState.exportSnapshot)
        else {
            LazyRow(Modifier.semantics { contentDescription = strings.getString(R.string.photo_tools) }, contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(appState.settings.toolOrder.mapNotNull { name -> PhotoToolPage.entries.find { it.name == name } }) { option ->
                    EditorToolChip(option, tab == option) { tab = option }
                }
            }
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
                when (tab) {
                    PhotoToolPage.LIGHT, PhotoToolPage.COLOR, PhotoToolPage.DETAILS ->
                        PhotoAdjustmentControls(tab, draft, { draft = it }, { commit(draft) }, commit)
                    PhotoToolPage.HSL -> PhotoHslControls(draft, { draft = it }, { commit(draft) })
                    PhotoToolPage.MASKS -> PhotoMaskControls(draft, { draft = it }, { commit(draft) }, commit, selectedMaskId, { selectedMaskId = it })
                    PhotoToolPage.LAYERS -> PhotoLayersControls(draft, { draft = it }, { commit(draft) }, commit)
                    PhotoToolPage.CURVES -> PhotoCurvesControls(draft.curves, { draft = draft.copy(curves = it) },
                        { commit(draft) }, { commit(draft.copy(curves = it)) })
                    PhotoToolPage.TEXT -> PhotoTextControls(draft.texts, selectedText, { selectedTextId = it },
                        { draft = draft.withText(it) }, { commit(draft) }, { commit(draft.copy(texts = it)) })
                    PhotoToolPage.MARKUP -> PhotoMarkupControls(draft.markups, selectedMarkup, markupTool,
                        { markupTool = it; selectedMarkupId = null }, { selectedMarkupId = it },
                        { val mark = draft.markups.lastOrNull { it.kind == markupTool }?.copy(id = UUID.randomUUID().toString(),
                            points = listOf(de.luma.gallery.model.MarkupPoint(.3f, .3f), de.luma.gallery.model.MarkupPoint(.7f, .7f)))
                            ?: PhotoMarkup(UUID.randomUUID().toString(), markupTool, color = if (markupTool == MarkupKind.COVER) 0xFF000000L else 0xFFFF5252L)
                            createMarkup(mark); selectedMarkupId = mark.id },
                        { draft = draft.withMarkup(it) }, { commit(draft) }, { commit(draft.copy(markups = it)) })
                    PhotoToolPage.CROP -> {
                        EditorSlider(strings.getString(R.string.perspective_horizontal), draft.perspectiveX, -.4f.. .4f, { draft = draft.copy(perspectiveX = it) }, { commit(draft) })
                        EditorSlider(strings.getString(R.string.perspective_vertical), draft.perspectiveY, -.4f.. .4f, { draft = draft.copy(perspectiveY = it) }, { commit(draft) })
                        Text(strings.getString(R.string.perspective_hint), style = MaterialTheme.typography.bodySmall)

                        Text(strings.getString(R.string.crop_drag_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (draft.markups.isNotEmpty()) Text(strings.getString(R.string.crop_overlays_hint), style = MaterialTheme.typography.bodySmall)
                        else if (draft.texts.isNotEmpty()) Text(strings.getString(R.string.crop_text_hint), style = MaterialTheme.typography.bodySmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(strings.getString(R.string.free_crop) to null, "1:1" to 1f, "4:3" to 4f/3, "3:4" to 3f/4, "16:9" to 16f/9, "9:16" to 9f/16)) { (label, ratio) ->
                                FilterChip(draft.cropRatio == ratio, { commit(draft.copy(cropRatio = ratio,
                                    cropBounds = if (ratio == null) bounds else CropBounds.centered(imageAspect, ratio))) }, { Text(label) })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ActionButton(LumaIcons.Rotate90DegreesCw, strings.getString(R.string.rotate_90), { commit(draft.rotatedClockwise()) })
                            ActionButton(LumaIcons.Flip, strings.getString(R.string.mirror), { commit(draft.mirrored()) })
                        }
                        TextButton(onClick = { preciseCrop = !preciseCrop }) { Text(if (preciseCrop) strings.getString(R.string.hide_sliders) else strings.getString(R.string.precise_adjustment)) }
                        if (preciseCrop) {
                            val maximum = CropBounds.centered(imageAspect, draft.cropRatio)
                            EditorSlider(if (draft.cropRatio == null) strings.getString(R.string.width) else strings.getString(R.string.size), (bounds.width / maximum.width).coerceIn(.05f, 1f), .05f..1f,
                                onChange = { draft = draft.copy(cropBounds = bounds.withSize(maximum.width * it,
                                    if (draft.cropRatio == null) bounds.height else maximum.height * it)) }, onFinish = { commit(draft) })
                            if (draft.cropRatio == null) EditorSlider(strings.getString(R.string.height), bounds.height, .05f..1f,
                                onChange = { draft = draft.copy(cropBounds = bounds.withSize(bounds.width, it)) }, onFinish = { commit(draft) })
                            EditorSlider(strings.getString(R.string.horizontal_position), if (bounds.width >= 1) 0f else bounds.left / (1 - bounds.width), 0f..1f,
                                onChange = { draft = draft.copy(cropBounds = bounds.move(it * (1 - bounds.width) - bounds.left, 0f)) }, onFinish = { commit(draft) })
                            EditorSlider(strings.getString(R.string.vertical_position), if (bounds.height >= 1) 0f else bounds.top / (1 - bounds.height), 0f..1f,
                                onChange = { draft = draft.copy(cropBounds = bounds.move(0f, it * (1 - bounds.height) - bounds.top)) }, onFinish = { commit(draft) })
                        }
                        TextButton(onClick = { commit(draft.copy(cropBounds = CropBounds.centered(imageAspect, draft.cropRatio))) }) { Text(strings.getString(R.string.reset_crop)) }
                    }
                    PhotoToolPage.FILTERS -> PhotoFilters(source,draft,{draft=it},{commit(draft)},commit)
                }
                TextButton(onClick = { commit(PhotoRecipe()) }) { Text(strings.getString(R.string.reset_all)) }
            }
        }        } }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(LumaIcons.ArrowBack, strings.getString(R.string.back)) }
            Column(Modifier.weight(1f)) {
                Text(strings.getString(R.string.edit_photo), fontWeight = FontWeight.Medium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(project?.name?.let { strings.getString(R.string.autosave_project, it) } ?: strings.getString(R.string.original_preserved), fontSize = 11.sp,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { commit(draft); projectDialog = true }, enabled = source != null && !appState.exporting && !appState.savingProject) { Icon(LumaIcons.Projects, strings.getString(R.string.save_project)) }
            FilledTonalButton(onClick = { exportDialog = true }, enabled = source != null && edited != null && appState.settingsReady && !appState.exporting) { Text(strings.getString(R.string.export)) }
        }
        appState.settingsError?.let { message -> Column(Modifier.padding(horizontal = 24.dp)) {
            Text(message, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = vm::reloadSettings) { Text(strings.getString(R.string.retry_settings)) }
        } }
        EditorPanes(Modifier.weight(1f), previewContent, controlsContent)
    }
    if (exportDialog) PhotoExportDialog(appState.exportOptions, needsMediaLocation = item.uri.authority == "media", onDismiss = { exportDialog = false },
        onExport = { options -> commit(draft); exportDialog = false; vm.exportPhoto(item, draft, options) },
        overwriteItem=item,onOverwrite={options->commit(draft);exportDialog=false;vm.overwritePhoto(item,draft,options)})
    if (projectDialog) SaveProjectDialog(project?.name ?: item.name.substringBeforeLast('.'), session.projectId != null,
        onDismiss = { projectDialog = false }, onSave = { name, copy -> vm.saveProject(name, copy, photo = draft); projectDialog = false })
}

@Composable
fun ExportStatus(onCancel: () -> Unit, snapshot: de.luma.gallery.data.ExportSnapshot? = null) {
    val strings = appStrings()
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        if (snapshot?.percent != null) LinearProgressIndicator(progress = { snapshot.percent / 100f }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(Modifier.fillMaxWidth())
        snapshot?.let { Text(strings.getString(R.string.export_count, it.completed, it.total)) }
        Spacer(Modifier.height(12.dp))
        Text(strings.getString(R.string.saving_copy), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onCancel) { Text(strings.getString(R.string.cancel)) }
    }
}
