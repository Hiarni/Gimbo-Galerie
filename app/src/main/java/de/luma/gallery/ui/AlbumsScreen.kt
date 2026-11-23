package de.luma.gallery.ui

import de.luma.gallery.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.luma.gallery.model.*

enum class FolderAction(val label:Int) { DELETE(R.string.delete), RENAME(R.string.rename), MOVE(R.string.move_file), COPY(R.string.copy_file) }

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AlbumsScreen(albums: List<VirtualAlbum>, library: List<GalleryMedia>, loading: Boolean, error: String?, busy: Boolean,
    onOpen: (String) -> Unit, onFolder: (String) -> Unit, onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit, onDelete: (String) -> Unit,
    order:List<String> = emptyList(),onOrder:(List<String>)->Unit={},onDeleteAlbums:(Set<String>)->Unit={ids->ids.forEach(onDelete)},
    onManageFolder:(String)->Unit={},columns:Int=2,onColumns:(Int)->Unit={},
    onFolderAction:(String,FolderAction)->Unit={name,_->onManageFolder(name)},onAtTop:(Boolean)->Unit={},importedReferences:Set<String> = emptySet(),onOpenImports:()->Unit={},onRemoveImports:(Set<String>)->Unit={},onScreenshots:(()->Unit)?=null,onCreative:(()->Unit)?=null,onDuplicates:(()->Unit)?=null,onTrash:(()->Unit)?=null,onArchive:(()->Unit)?=null,onVault:(()->Unit)?=null,onSecureMedia:((List<GalleryMedia>)->Unit)?=null) {
    val strings = appStrings()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<VirtualAlbum?>(null) }
    var deleting by remember { mutableStateOf<VirtualAlbum?>(null) }
    var selection by remember {mutableStateOf(emptySet<String>())}
    androidx.activity.compose.BackHandler(enabled=selection.isNotEmpty()) {selection=emptySet()}
    var removeMany by remember {mutableStateOf(false)}
    var choosingFolder by remember {mutableStateOf(false)}
    val grid=rememberLazyGridState()
    val scope=rememberCoroutineScope()
    val notifyAtTop by rememberUpdatedState(onAtTop)
    LaunchedEffect(grid) {snapshotFlow {Pair(grid.firstVisibleItemIndex==0 && grid.firstVisibleItemScrollOffset==0,selection.isEmpty())}.collect {(atTop,canNotify)->if(canNotify) notifyAtTop(atTop)}}
    val importedFiles=library.filter {it.id in importedReferences || it.isImported}
    val deviceLibrary=library.filterNot {it.isImported}
    val byId=remember(library) {library.associateBy {it.id}}
    val orderedAlbums=albums.sortedWith(compareBy<VirtualAlbum> {order.indexOf("virtual:${it.id}").let {i->if(i<0) Int.MAX_VALUE else i}}.thenBy {it.name.lowercase()})
    fun reorder(key:String,direction:Int) {
        val keys=if(key.startsWith("virtual:")) orderedAlbums.map {"virtual:${it.id}"} else deviceLibrary.map {"folder:${it.album}"}.distinct().sortedWith(compareBy {order.indexOf(it).let {i->if(i<0) Int.MAX_VALUE else i}})
        val list=keys.toMutableList();val i=list.indexOf(key);val j=(i+direction).coerceIn(0,list.lastIndex)
        if(i>=0 && i!=j) {val other=list[j];list[j]=key;list[i]=other;onOrder(order.filter {it.startsWith(if(key.startsWith("virtual:")) "folder:" else "virtual:")}+list)}
    }
    val folders = deviceLibrary.groupBy { it.album }.toList().sortedWith(compareBy<Pair<String,List<GalleryMedia>>> {order.indexOf("folder:${it.first}").let {i->if(i<0) Int.MAX_VALUE else i}}.thenBy {it.first.lowercase()})
    val keys=orderedAlbums.map {"virtual:${it.id}"}+(if(importedReferences.isNotEmpty()) listOf("imports") else emptyList())+folders.map {"folder:${it.first}"}
    LaunchedEffect(keys) {selection=selection.intersect(keys.toSet())}
    val virtualSelection=selection.filter {it.startsWith("virtual:")}.map {it.removePrefix("virtual:")}.toSet()
    val folderSelection=selection.filter {it.startsWith("folder:")}.map {it.removePrefix("folder:")}
    fun toggle(key:String) {selection=if(key in selection) selection-key else selection+key}
    Column(Modifier.fillMaxSize()) {
    LazyVerticalGrid(GridCells.Fixed(columns.coerceIn(2,6)), state=grid,
        modifier=Modifier.weight(1f).semantics {contentDescription=strings.getString(R.string.album_grid)}
            .then(selectableGridGestures(grid,keys,columns,selection,onColumns,{selection=it},scope,6)),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if(onScreenshots!=null || onCreative!=null || onDuplicates!=null || onTrash!=null || onArchive!=null || onVault!=null) item(key="smart",span={GridItemSpan(maxLineSpan)}) {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(strings.getString(R.string.library_shortcuts),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(maxItemsInEachRow=2,horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(Triple(R.string.vault_title,LumaIcons.Lock,onVault),Triple(R.string.trash,LumaIcons.DeleteOutline,onTrash),Triple(R.string.library_archive,LumaIcons.Archive,onArchive),
                        Triple(R.string.screenshots,LumaIcons.Screenshot,onScreenshots),Triple(R.string.creative_short,LumaIcons.Collage,onCreative),
                        Triple(R.string.duplicates_short,LumaIcons.Duplicates,onDuplicates)).forEach {(label,icon,open)->
                        if(open!=null) FilledTonalButton(onClick=open,enabled=selection.isEmpty(),modifier=Modifier.weight(1f).heightIn(min=56.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp),
                            colors=ButtonDefaults.filledTonalButtonColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow,contentColor=MaterialTheme.colorScheme.onSurface)) {
                            Icon(icon,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp))
                            Text(strings.getString(label)+(if(label==R.string.screenshots) " · "+library.count {it.isScreenshot} else ""),style=MaterialTheme.typography.labelMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.getString(R.string.my_albums), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { creating = true }, enabled = !loading && error == null && !busy) { Text(strings.getString(R.string.new_album)) }
                }
                Text(strings.getString(R.string.album_intro),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                error?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
                if (!loading && error == null && albums.isEmpty()) Text(strings.getString(R.string.no_custom_albums),
                    Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(orderedAlbums, key = { "virtual:${it.id}" }) { album ->
            val available = remember(album.mediaIds,byId) {album.mediaIds.mapNotNull {byId[it]}.sortedByDescending {it.timestamp}}
            val key="virtual:${album.id}"
            Column {
                Box(Modifier.border(if(key in selection) 2.dp else 0.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(16.dp)).semantics {selected=key in selection}) {
                    AlbumThumbnail(available.firstOrNull(), album.name,onSelect={toggle(key)}) {
                        if(selection.isEmpty()) onOpen(album.id) else toggle(key)
                    }
                    if(selection.isNotEmpty()) SelectionIndicator(if(key in selection) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,Modifier.align(Alignment.TopStart).padding(8.dp),overMedia=true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(album.name, Modifier.weight(1f).clickable {if(selection.isEmpty()) onOpen(album.id) else toggle(key)}, fontWeight = FontWeight.Medium,
                        maxLines = if(columns>3) 1 else 2, overflow = TextOverflow.Ellipsis)
                    var menu by remember { mutableStateOf(false) }
                    if(columns<=3) Box {
                        IconButton(onClick = { menu = true }, enabled = !busy && error == null) { Icon(LumaIcons.More, strings.getString(R.string.album_actions, album.name)) }
                        DropdownMenu(menu, { menu = false }) {
                            if(onSecureMedia!=null) DropdownMenuItem(text={Text(strings.getString(R.string.vault_secure))},leadingIcon={Icon(LumaIcons.Lock,null)},enabled=available.isNotEmpty(),onClick={menu=false;onSecureMedia(available)})
                            DropdownMenuItem(text={Text(strings.getString(R.string.select_album))},onClick={menu=false;selection=selection+key})
                            DropdownMenuItem(text={Text(strings.getString(R.string.move_up))},onClick={menu=false;reorder("virtual:${album.id}",-1)})
                            DropdownMenuItem(text={Text(strings.getString(R.string.move_down))},onClick={menu=false;reorder("virtual:${album.id}",1)})
                            DropdownMenuItem(text = { Text(strings.getString(R.string.rename)) }, onClick = { menu = false; renaming = album })
                            DropdownMenuItem(text = { Text(strings.getString(R.string.remove_album)) }, onClick = { menu = false; deleting = album })
                        }
                    }
                }
                Text(strings.getString(R.string.album_counts, available.size, album.mediaIds.size), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(importedReferences.isNotEmpty()) item(key="imports") {
            Column {
                Box(Modifier.border(if("imports" in selection) 2.dp else 0.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(16.dp)).semantics {selected="imports" in selection}) {
                    AlbumThumbnail(importedFiles.firstOrNull(),strings.getString(R.string.imported_media_collection),onSelect={toggle("imports")}) {
                        if(selection.isEmpty()) onOpenImports() else toggle("imports")
                    }
                    if(selection.isNotEmpty()) SelectionIndicator(if("imports" in selection) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,Modifier.align(Alignment.TopStart).padding(8.dp),overMedia=true)
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(strings.getString(R.string.imported_media_collection),Modifier.weight(1f).clickable {if(selection.isEmpty()) onOpenImports() else toggle("imports")},fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(columns<=3) {
                        var menu by remember {mutableStateOf(false)}
                        Box {
                            IconButton(onClick={menu=true}) {Icon(LumaIcons.More,strings.getString(R.string.album_actions,strings.getString(R.string.imported_media_collection)))}
                            DropdownMenu(menu,{menu=false}) {
                                DropdownMenuItem(text={Text(strings.getString(R.string.select_album))},onClick={menu=false;selection=selection+"imports"})
                                DropdownMenuItem(text={Text(strings.getString(R.string.remove_import_references))},onClick={menu=false;onRemoveImports(importedReferences)})
                            }
                        }
                    }
                }
                Text(strings.getString(R.string.album_counts,importedFiles.size,importedReferences.size),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(strings.getString(R.string.import_collection_label),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(strings.getString(R.string.device_folders), style = MaterialTheme.typography.titleMedium)
                if (folders.isEmpty()) Text(strings.getString(R.string.no_device_folders),
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(folders, key = { "folder:${it.first}" }) { (name, files) ->
            val key="folder:$name"
            Column {
                Box(Modifier.border(if(key in selection) 2.dp else 0.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(16.dp)).semantics {selected=key in selection}) {
                    AlbumThumbnail(files.firstOrNull(),name,onSelect={toggle(key)}) {
                        if(selection.isEmpty()) onFolder(name) else toggle(key)
                    }
                    if(selection.isNotEmpty()) SelectionIndicator(if(key in selection) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,Modifier.align(Alignment.TopStart).padding(8.dp),overMedia=true)
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(name,Modifier.weight(1f).clickable {if(selection.isEmpty()) onFolder(name) else toggle(key)},fontWeight=FontWeight.Medium,maxLines=if(columns>3) 1 else 2,overflow=TextOverflow.Ellipsis)
                    var folderMenu by remember {mutableStateOf(false)}
                    if(columns<=3) Box {
                        IconButton(onClick={folderMenu=true}) {Icon(LumaIcons.More,strings.getString(R.string.album_actions,name))}
                        DropdownMenu(folderMenu,{folderMenu=false}) {
                            if(onSecureMedia!=null) DropdownMenuItem(text={Text(strings.getString(R.string.vault_secure))},leadingIcon={Icon(LumaIcons.Lock,null)},enabled=files.isNotEmpty(),onClick={folderMenu=false;onSecureMedia(files)})
                            DropdownMenuItem(text={Text(strings.getString(R.string.select_album))},onClick={folderMenu=false;selection=selection+key})
                            DropdownMenuItem(text={Text(strings.getString(R.string.manage_folder))},onClick={folderMenu=false;onManageFolder(name)})
                            FolderAction.entries.forEach {action->DropdownMenuItem(text={Text(strings.getString(action.label))},onClick={folderMenu=false;onFolderAction(name,action)})}
                            DropdownMenuItem(text={Text(strings.getString(R.string.move_up))},onClick={folderMenu=false;reorder(key,-1)})
                            DropdownMenuItem(text={Text(strings.getString(R.string.move_down))},onClick={folderMenu=false;reorder(key,1)})
                        }
                    }
                }
                Text(strings.getQuantityString(R.plurals.media_count, files.size, files.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if(selection.isNotEmpty()) Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh) {Column {
        SelectionSummary(selection.size,{selection=emptySet()},{selection=if(selection.size==keys.size) emptySet() else keys.toSet()})
        androidx.compose.foundation.lazy.LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(virtualSelection.isNotEmpty()) item {TextButton(onClick={removeMany=true},enabled=!busy) {Text(strings.getString(R.string.remove_album))}}
            if(selection.size==1 && virtualSelection.isNotEmpty()) item {TextButton(onClick={renaming=albums.firstOrNull {it.id in virtualSelection}},enabled=!busy) {Text(strings.getString(R.string.rename))}}
            if("imports" in selection) item {TextButton(onClick={onRemoveImports(importedReferences)}) {Text(strings.getString(R.string.remove_import_references))}}
            if(folderSelection.size==1) FolderAction.entries.forEach {action->item {TextButton(onClick={onFolderAction(folderSelection.single(),action)},enabled=!busy) {Text(strings.getString(action.label))}}}
            if(folderSelection.isNotEmpty()) item {TextButton(onClick={if(folderSelection.size==1) onManageFolder(folderSelection.single()) else choosingFolder=true}) {Text(strings.getString(R.string.manage_folder))}}
            if(selection.size==1 && "imports" !in selection) {
                item {TextButton(onClick={reorder(selection.single(),-1)}) {Text(strings.getString(R.string.move_up))}}
                item {TextButton(onClick={reorder(selection.single(),1)}) {Text(strings.getString(R.string.move_down))}}
            }
        }
    }}
    }
    if(choosingFolder) CompactDialog(strings.getString(R.string.manage_folder),{choosingFolder=false},actions={TextButton(onClick={choosingFolder=false}) {Text(strings.getString(R.string.close))}}) {
        folderSelection.forEach {name->TextButton(onClick={choosingFolder=false;onManageFolder(name)},modifier=Modifier.fillMaxWidth()) {Text(name)}}
    }
    if(removeMany) AlertDialog(onDismissRequest={removeMany=false},title={Text(strings.getString(R.string.remove_album_title))},
        text={Text(strings.getString(R.string.remove_selected_albums_hint))},confirmButton={TextButton(onClick={onDeleteAlbums(virtualSelection);selection=selection.filterNot {it.startsWith("virtual:")}.toSet();removeMany=false},enabled=!busy) {Text(strings.getString(R.string.remove_album))}},
        dismissButton={TextButton(onClick={removeMany=false}) {Text(strings.getString(R.string.cancel))}})
    if (creating) AlbumNameDialog("", false, albums.map { albumNameKey(it.name) }.toSet(), busy,
        { creating = false }, { name -> onCreate(name); creating = false })
    renaming?.let { album -> AlbumNameDialog(album.name, true, albums.filter { it.id != album.id }.map { albumNameKey(it.name) }.toSet(), busy,
        { renaming = null }, { name -> onRename(album.id, name); renaming = null }) }
    deleting?.let { album -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(strings.getString(R.string.remove_album_title)) },
        text = { Text(strings.getString(R.string.remove_album_hint, album.name)) },
        confirmButton = { TextButton(onClick = { onDelete(album.id); deleting = null }, enabled = !busy) { Text(strings.getString(R.string.remove_album)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(strings.getString(R.string.cancel)) } }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumThumbnail(media: GalleryMedia?, name: String, onSelect:()->Unit={}, onOpen: () -> Unit) {
    val strings = appStrings()
    Surface(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen).semantics {onLongClick {onSelect();true}},
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (media?.isVideo == true) LumaIcons.PlayArrow else LumaIcons.FolderOpen, null, Modifier.size(36.dp))
            if (media != null) AsyncImage(media.uri, strings.getString(R.string.album_preview, name), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun AlbumNameDialog(initialName: String, existing: Boolean, otherNameKeys: Set<String>, busy: Boolean,
    onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val strings = appStrings()
    var name by remember { mutableStateOf(initialName) }
    val valid = runCatching { albumNameKey(name) !in otherNameKeys }.getOrDefault(false)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing) strings.getString(R.string.rename_album) else strings.getString(R.string.new_album)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(strings.getString(R.string.album_name)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Text(strings.getString(R.string.album_local_hint), style = MaterialTheme.typography.bodySmall)
            if (name.isNotBlank() && !valid) Text(strings.getString(R.string.invalid_album_name_hint), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onSave(albumName(name)) }, enabled = valid && !busy) {
        Text(if (existing) strings.getString(R.string.rename) else strings.getString(R.string.create_album))
    } }, dismissButton = { TextButton(onClick = onDismiss) { Text(strings.getString(R.string.cancel)) } })
}

@Composable
fun AlbumPickerDialog(albums: List<VirtualAlbum>, count: Int, loading: Boolean, error: String?, busy: Boolean,
    onDismiss: () -> Unit, onAdd: (String) -> Unit, onCreate: (String) -> Unit) {
    val strings = appStrings()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val valid = runCatching { albumNameKey(name) !in albums.map { albumNameKey(it.name) } }.getOrDefault(false)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(strings.getString(R.string.add_to_album)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.getQuantityString(R.plurals.selection_location_hint, count, count))
            when {
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                albums.isEmpty() -> Text(strings.getString(R.string.first_album_for_selection))
                else -> LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(albums, key = { it.id }) { album ->
                        TextButton(onClick = { onAdd(album.id) }, enabled = !busy && count > 0, modifier = Modifier.fillMaxWidth()) {
                            Text(album.name, Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (creating || albums.isEmpty()) OutlinedTextField(name, { name = it.take(80) }, label = { Text(strings.getString(R.string.new_album_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy && !loading && error == null)
            else TextButton(onClick = { creating = true }, enabled = !busy && !loading && error == null) { Text(strings.getString(R.string.new_album_for_selection)) }
        }
    }, confirmButton = {
        if (creating || albums.isEmpty()) TextButton(onClick = { onCreate(albumName(name)) },
            enabled = valid && count > 0 && !busy && !loading && error == null) { Text(strings.getString(R.string.create_and_add)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(strings.getString(R.string.cancel)) } })
}
