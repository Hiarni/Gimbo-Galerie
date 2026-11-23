package de.luma.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.luma.gallery.GalleryState
import de.luma.gallery.R
import de.luma.gallery.model.*

@Composable
fun DuplicatesScreen(state:GalleryState,selection:Set<String>,onSelection:(Set<String>)->Unit,onBack:()->Unit,
    onScan:(Boolean,Boolean)->Unit,onCancel:()->Unit,onReset:()->Unit,onPreview:(DuplicateGroup,GalleryMedia)->Unit,onRemove:(Set<String>)->Unit,checking:Boolean=false,onResume:()->Unit={}) {
    val strings=appStrings()
    var similar by rememberSaveable {mutableStateOf(true)}
    var archived by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(state.duplicateTask?.id) {state.duplicateTask?.let {similar=it.similar;archived=it.archived}}
    var review by remember {mutableStateOf(false)}
    var filter by rememberSaveable {mutableStateOf<DuplicateKind?>(null)}
    val report=state.duplicateReport
    LaunchedEffect(report) {onSelection(selection.intersect(report?.groups.orEmpty().flatMap {it.files}.map {it.media.id}.toSet()))}
    if(review && report!=null) CompactDialog(strings.getString(R.string.duplicates_review_title),{review=false},actions={
        TextButton(onClick={review=false}) {Text(strings.getString(R.string.cancel))}
        Button(onClick={review=false;onRemove(selection)},enabled=selection.isNotEmpty()) {Text(strings.getString(R.string.duplicates_review_action))}
    }) {
        Text(strings.getQuantityString(R.plurals.media_count,selection.size,selection.size))
        Text(strings.getString(R.string.duplicates_review_hint))
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) {Icon(LumaIcons.ArrowBack,strings.getString(R.string.back))}
            Text(strings.getString(R.string.duplicates_title),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Text(strings.getString(R.string.duplicates_hint),style=MaterialTheme.typography.bodySmall)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(strings.getString(R.string.duplicates_include_similar),Modifier.weight(1f))
                    Switch(similar,{similar=it;onReset();onSelection(emptySet())},enabled=!state.duplicateScanning && !checking)
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(strings.getString(R.string.duplicates_include_archive),Modifier.weight(1f))
                    Switch(archived,{archived=it;onReset();onSelection(emptySet())},enabled=!state.duplicateScanning && !checking)
                }
                if(state.duplicateScanning) {
                    Text(strings.getString(if(state.duplicateComparing) R.string.duplicates_comparing else R.string.duplicates_hashing))
                    LinearProgressIndicator(progress={if(state.duplicateTotal==0) 0f else state.duplicateDone.toFloat()/state.duplicateTotal},modifier=Modifier.fillMaxWidth())
                    Text(strings.getString(R.string.review_progress,state.duplicateDone,state.duplicateTotal))
                    Text(strings.getString(R.string.background_continues),style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick=onCancel,modifier=Modifier.fillMaxWidth()) {Text(strings.getString(R.string.background_pause))}
                } else Button(onClick={onSelection(emptySet());onScan(similar,archived)},enabled=!checking && state.media.any {!it.isVideo && !it.isImported && canManageMedia(it)},modifier=Modifier.fillMaxWidth()) {Text(strings.getString(R.string.duplicates_scan))}
                if(state.duplicateTask?.status in listOf(de.luma.gallery.data.AnalysisStatus.PAUSED,de.luma.gallery.data.AnalysisStatus.FAILED)) {
                    OutlinedButton(onClick=onResume,modifier=Modifier.fillMaxWidth()) {Text(strings.getString(R.string.background_resume))}
                    Text(strings.getString(R.string.review_progress,state.duplicateDone,state.duplicateTotal),style=MaterialTheme.typography.bodySmall)
                }
                state.duplicateError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            }
            if(report!=null) item {
                Text(strings.getString(R.string.duplicates_result,report.checked,report.groups.size,report.unavailable),style=MaterialTheme.typography.bodySmall)
                if(report.groups.isEmpty()) Text(strings.getString(R.string.duplicates_none),style=MaterialTheme.typography.titleMedium)
                else {
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        item {FilterChip(filter==null,{filter=null},{Text(strings.getString(R.string.all))})}
                        items(DuplicateKind.entries) {kind->FilterChip(filter==kind,{filter=kind},{Text(strings.getString(kind.labelRes))})}
                    }
                    if(report.groups.any {it.kind==DuplicateKind.EXACT}) TextButton(onClick={onSelection(report.exactExtras())},enabled=!checking) {Text(strings.getString(R.string.duplicates_select_extras))}
                }
            }
            report?.groups.orEmpty().filter {filter==null || it.kind==filter}.forEach {group->
                item(key="group:${group.id}") {
                    Column(Modifier.fillMaxWidth().padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(strings.getString(group.kind.labelRes),style=MaterialTheme.typography.titleMedium)
                        if(group.kind==DuplicateKind.SIMILAR) Text(strings.getString(R.string.duplicates_similar_hint),style=MaterialTheme.typography.bodySmall)
                    }
                }
                val remaining=group.files.count {it.media.id !in selection}
                items(group.files,key={"file:${it.media.id}"}) {file->
                    val id=file.media.id;val checked=id in selection
                    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                            AsyncImage(file.media.uri,file.media.name,Modifier.size(72.dp).clickable {onPreview(group,file.media)},contentScale=ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(horizontal=10.dp).clickable {onPreview(group,file.media)}) {
                                Text(file.media.name,style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(file.media.album,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                                Text(android.text.format.Formatter.formatFileSize(androidx.compose.ui.platform.LocalContext.current,file.bytes),style=MaterialTheme.typography.bodySmall)
                                if(!checked && remaining==1) Text(strings.getString(R.string.duplicates_kept),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                            }
                            RoundCheckbox(checked,{onSelection(if(checked) selection-id else selection+id)},enabled=!checking && (checked || remaining>1 && selection.size<MEDIA_ACTION_LIMIT),modifier=Modifier.semantics {contentDescription=strings.getString(R.string.duplicates_mark_file,file.media.name)})
                        }
                    }
                }
            }
        }
        if(selection.isNotEmpty()) Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(strings.getQuantityString(R.plurals.media_count,selection.size,selection.size))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={onSelection(emptySet())},enabled=!checking) {Text(strings.getString(R.string.clear_selection))}
                    Button(onClick={review=true},enabled=!checking) {Text(strings.getString(R.string.duplicates_review_title))}
                }
                if(checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
