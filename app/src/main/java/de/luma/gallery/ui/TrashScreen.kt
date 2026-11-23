package de.luma.gallery.ui

import de.luma.gallery.R
import de.luma.gallery.localization.appLocale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.luma.gallery.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrashScreen(media: List<TrashMedia>, loading: Boolean, error: String?, accessLimited: Boolean,
    supported: Boolean, busy: Boolean, onBack: () -> Unit, onRefresh: () -> Unit, onAccess: () -> Unit,
    onAction: (MediaAction, List<GalleryMedia>) -> Unit) {
    val strings = appStrings()
    val listState = rememberLazyListState()
    val context=androidx.compose.ui.platform.LocalContext.current
    val retention=remember(context) {de.luma.gallery.data.TrashRetention(context)}
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val chosen = media.filter { it.media.id in selected }.map { it.media }
    LaunchedEffect(media) { selected = selected.filter { id -> media.any { it.media.id == id } } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            LabeledIconButton(LumaIcons.ArrowBack,strings.getString(R.string.back),onBack)
            Text(strings.getString(R.string.trash), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
            LabeledIconButton(LumaIcons.Refresh,strings.getString(R.string.refresh),onRefresh,enabled=supported && !loading && !busy)
        }
        when {
            !supported -> EmptyState(LumaIcons.DeleteOutline, strings.getString(R.string.trash_min_android),
                strings.getString(R.string.trash_android_10_hint))
            else -> {
                Text(strings.getString(R.string.trash_intro),
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (accessLimited) Text(strings.getString(R.string.limited_access_hint),
                    Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onAccess, enabled = !busy, modifier = Modifier.padding(start = 16.dp)) { Text(strings.getString(R.string.manage_media_access)) }
                when {
                    loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    error != null -> Box(Modifier.weight(1f)) { EmptyState(LumaIcons.Info, strings.getString(R.string.check_access), error, strings.getString(R.string.reload), onRefresh) }
                    media.isEmpty() -> Box(Modifier.weight(1f)) { EmptyState(LumaIcons.DeleteOutline, strings.getString(R.string.trash_empty_title),
                        strings.getString(R.string.trash_empty_hint)) }
                    else -> {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.getString(R.string.trash_selected_count, chosen.size, media.size), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { selected = if (chosen.isNotEmpty()) emptyList()
                                else media.take(MEDIA_ACTION_LIMIT).map { it.media.id } }, enabled = !busy) {
                                Text(if (chosen.isNotEmpty()) strings.getString(R.string.clear_selection) else strings.getString(R.string.select_all))
                            }
                        }
                        if (media.size > MEDIA_ACTION_LIMIT) Text(strings.getString(R.string.media_action_limit, MEDIA_ACTION_LIMIT),
                            Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall)
                        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(media, key = { it.media.id }) { item ->
                                val checked = item.media.id in selected
                                val enabled = !busy && (checked || chosen.size < MEDIA_ACTION_LIMIT)
                                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,border=if(checked) BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.45f)) else null) {
                                    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox,
                                        onValueChange = { selected = if (checked) selected - item.media.id else selected + item.media.id }).padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        var thumbnailFailed by remember(item.media.id) { mutableStateOf(false) }
                                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
                                            if(thumbnailFailed) Icon(if(item.media.isVideo) LumaIcons.PlayArrow else LumaIcons.PhotoLibrary,null,Modifier.size(28.dp))
                                            else AsyncImage(item.media.uri,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,onError={thumbnailFailed=true})
                                            if(item.media.isVideo && !thumbnailFailed) Surface(Modifier.align(Alignment.BottomEnd).padding(3.dp),shape=RoundedCornerShape(50),color=Color.Black.copy(alpha=.65f)) {Icon(LumaIcons.PlayArrow,null,Modifier.padding(3.dp).size(12.dp),tint=Color.White)}
                                        }
                                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                            Text(item.media.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(item.media.album, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(item.expiresAt?.let { strings.getString(R.string.expiry_date, java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, strings.appLocale).format(Date(it))) }
                                                ?: strings.getString(R.string.expiry_unavailable), style = MaterialTheme.typography.bodySmall)
                                            retention.deadline(item.media)?.let {due->Text(strings.getString(R.string.app_trash_deadline,java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM,strings.appLocale).format(Date(due))),style=MaterialTheme.typography.bodySmall)}
                                        }
                                        RoundCheckbox(checked, onCheckedChange = null, enabled = enabled)
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onAction(MediaAction.RESTORE, chosen) }, enabled = chosen.isNotEmpty() && !busy,
                                modifier = Modifier.weight(1f)) { Text(strings.getString(R.string.restore)) }
                            OutlinedButton(onClick = { onAction(MediaAction.DELETE, chosen) }, enabled = chosen.isNotEmpty() && !busy,
                                modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)) { Text(strings.getString(R.string.permanently_delete)) }
                        }
                    }
                }
                if (busy) Text(strings.getString(R.string.system_confirmation_busy), Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun MediaActionDialog(pending: PendingMediaAction, onDismiss: () -> Unit, onConfirm: (List<de.luma.gallery.model.GalleryMedia>) -> Unit, moving: Boolean = false) {
    val strings = appStrings()
    var selected by rememberSaveable(pending.items.map {it.id},pending.action) {mutableStateOf(pending.items.map {it.id})}
    val chosen=pending.items.filter {it.id in selected}
    val title = when (pending.action) {
        MediaAction.TRASH -> strings.getString(R.string.trash_action_title)
        MediaAction.RESTORE -> strings.getString(R.string.restore_action_title)
        MediaAction.DELETE -> strings.getString(R.string.delete_action_title)
    }
    val detail = when (pending.action) {
        MediaAction.TRASH -> strings.getString(R.string.trash_action_hint)
        MediaAction.RESTORE -> strings.getString(R.string.restore_action_hint)
        MediaAction.DELETE -> strings.getString(R.string.delete_action_hint)
    }
    CompactDialog(if(moving) strings.getString(R.string.move_finish_title) else title,onDismiss,actions={
        TextButton(onClick=onDismiss) {Text(strings.getString(R.string.cancel))}
        val destructive=pending.action==MediaAction.DELETE
        Button(onClick={onConfirm(chosen)},enabled=chosen.isNotEmpty(),colors=ButtonDefaults.buttonColors(containerColor=if(destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor=if(destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary)) {
            Text(strings.getString(when(pending.action) {MediaAction.DELETE->R.string.permanently_delete;MediaAction.RESTORE->R.string.restore;MediaAction.TRASH->R.string.move_to_trash}))
        }
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.getQuantityString(R.plurals.media_count,chosen.size,chosen.size),style=MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=4.dp)) {
                items(pending.items.size,key={pending.items[it].id}) {index->
                    val item=pending.items[index]
                    val checked=item.id in selected
                    var failed by remember(item.uri) {mutableStateOf(false)}
                    Surface(shape=RoundedCornerShape(18.dp),modifier=Modifier.size(width=96.dp,height=120.dp)
                        .semantics {contentDescription=strings.getString(R.string.confirm_select_media,index+1,pending.items.size)+", "+item.name.take(100)}
                        .toggleable(checked,role=androidx.compose.ui.semantics.Role.Checkbox) {selected=if(checked) selected-item.id else selected+item.id},
                        border=androidx.compose.foundation.BorderStroke(if(checked) 2.dp else 1.dp,if(checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Box {
                            AsyncImage(item.uri,null,Modifier.fillMaxSize().then(if(checked) Modifier else Modifier.alpha(.45f)),contentScale=ContentScale.Crop,onError={failed=true},onSuccess={failed=false})
                            if(failed) Icon(if(item.isVideo) LumaIcons.PlayArrow else LumaIcons.PhotoLibrary,null,Modifier.align(Alignment.Center).size(32.dp))
                            Box(Modifier.fillMaxWidth().height(38.dp).align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.scrim.copy(alpha=.65f)))
                            Row(Modifier.align(Alignment.BottomCenter).padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                if(item.isVideo) {Icon(LumaIcons.PlayArrow,null,Modifier.size(16.dp),tint=androidx.compose.ui.graphics.Color.White);Spacer(Modifier.width(4.dp))}
                                Text("${index+1}",style=MaterialTheme.typography.labelMedium,color=androidx.compose.ui.graphics.Color.White)
                            }
                            SelectionIndicator(if(checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,
                                Modifier.align(Alignment.TopEnd).padding(6.dp),overMedia=true)
                        }
                    }
                }
            }
            Text(strings.getString(R.string.confirm_selection_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(moving) strings.getString(R.string.move_verified_short) else detail,style=MaterialTheme.typography.bodyMedium)
        }
    }
}
