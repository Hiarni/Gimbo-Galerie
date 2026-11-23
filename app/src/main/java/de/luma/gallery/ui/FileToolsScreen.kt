package de.luma.gallery.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.data.*
import de.luma.gallery.localization.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileToolsScreen(onBack: () -> Unit, onChanged: () -> Unit,requestedFolder:String?=null,requestedAction:FolderAction?=null) {
    val context = LocalContext.current
    val strings = appStrings()
    val tools = remember { DocumentTools(context) }
    val scope = rememberCoroutineScope()
    var handledAction by rememberSaveable(requestedFolder,requestedAction) {mutableStateOf(false)}
    var stack by rememberSaveable { mutableStateOf(emptyList<Uri>()) }
    val folderScrollStates=rememberSaveableStateHolder()
    var entries by remember { mutableStateOf(emptyList<DocumentEntry>()) }
    var parent by remember { mutableStateOf<DocumentEntry?>(null) }
    var localBusy by remember { mutableStateOf(false) }
    val transfer by TransferController.state.collectAsState()
    val busy = localBusy || transfer.running
    LaunchedEffect(Unit) { runCatching { TransferController.inspect(context) } }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var deleting by remember {mutableStateOf<DocumentEntry?>(null)}
    var naming by remember { mutableStateOf(false) }
    var renamed by remember { mutableStateOf<DocumentEntry?>(null) }
    var name by remember { mutableStateOf("") }
    var source by remember { mutableStateOf<DocumentEntry?>(null) }
    var moving by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<DocumentEntry?>(null) }
    var progress by remember { mutableStateOf<DocumentTransferProgress?>(null) }
    fun run(folderTransfer: Boolean = false, operation: suspend () -> Unit) {
        if (busy) return
        localBusy = true; error = null; status = null; progress = null
        job = scope.launch {
            try { operation() } catch (e: Exception) {
                if (e is CancellationException) {
                    status = strings.getString(if (folderTransfer) R.string.folder_transfer_cancelled else R.string.file_transfer_cancelled)
                    throw e
                }
                error = e.userMessage(strings, R.string.file_action_failed) +
                    if (folderTransfer) "\n" + strings.getString(R.string.folder_partial_hint) else ""
            } finally { localBusy = false; progress = null }
        }
    }
    suspend fun reload() {
        stack.lastOrNull()?.let {uri->
            val loadedParent=tools.describe(uri)
            val loadedEntries=tools.children(uri)
            if(stack.lastOrNull()==uri) {entries=loadedEntries;parent=loadedParent}
        }
    }
    LaunchedEffect(Unit) {if(stack.isNotEmpty()) run {reload()}}
    LaunchedEffect(transfer.event) {if(transfer.event>0) {status=transfer.message;runCatching {reload()};onChanged()}}
    fun grant(uri: Uri) {
        try { context.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: SecurityException) { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) run { grant(uri); stack = listOf(tools.root(uri)); reload() }
    }
    val targetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) run { grant(uri); destination = tools.describe(tools.root(uri)) }
        else source = null
    }
    LaunchedEffect(entries,requestedFolder,requestedAction,busy,transfer.interrupted) {
        if(!handledAction && !busy && requestedAction!=null && requestedFolder!=null && stack.size==1 &&
            (requestedAction !in listOf(FolderAction.COPY,FolderAction.MOVE) || !transfer.interrupted)) {
            val matches=entries.filter {it.directory && it.name==requestedFolder}
            if(matches.size==1) {
                val item=matches.single();handledAction=true
                when(requestedAction) {
                    FolderAction.RENAME -> if(item.renameable) {renamed=item;name=item.name;naming=true}
                    FolderAction.DELETE -> if(item.deletable) deleting=item
                    FolderAction.COPY,FolderAction.MOVE -> {source=item;moving=requestedAction==FolderAction.MOVE;targetPicker.launch(null)}
                }
            }
        }
    }
    androidx.activity.compose.BackHandler {
        when { transfer.running -> onBack(); busy -> Unit; stack.size > 1 -> run { stack = stack.dropLast(1); reload() }; else -> onBack() }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            TextButton(onClick = { if (transfer.running) onBack() else if (stack.size > 1) run { stack = stack.dropLast(1); reload() } else onBack() }, enabled = !localBusy) { Text(strings.getString(R.string.back)) }
            Text(strings.getString(R.string.file_tools), style = MaterialTheme.typography.headlineSmall)
        }
        requestedFolder?.let {Text(strings.getString(R.string.folder_parent_grant,it),style=MaterialTheme.typography.bodyMedium)}
        Text(strings.getString(R.string.file_tools_hint), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { treePicker.launch(null) }, enabled = !busy) { Text(strings.getString(R.string.choose_folder)) }
            OutlinedButton(onClick = { renamed = null; name = ""; naming = true }, enabled = !busy && parent?.canCreate == true) { Text(strings.getString(R.string.create_folder)) }
        }
        parent?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            (transfer.progress ?: progress)?.let { current ->
                val phase = strings.getString(when (current.phase) {
                    DocumentTransferPhase.SCANNING -> R.string.folder_scanning
                    DocumentTransferPhase.COPYING -> R.string.folder_copying
                    DocumentTransferPhase.VERIFYING -> R.string.folder_verifying
                    DocumentTransferPhase.REMOVING -> R.string.folder_removing
                })
                Text(if (current.total == 0) phase else strings.getString(R.string.folder_transfer_progress,
                    phase, current.completed, current.total, current.name), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { if (transfer.running) TransferController.cancel(context) else job?.cancel() }) { Text(strings.getString(if (transfer.running) R.string.transfer_pause else R.string.cancel)) }
        }
        if (transfer.interrupted && !transfer.running) {
            Text(strings.getString(R.string.transfer_interrupted))
            Row {
                TextButton(onClick = { run { TransferController.resume(context) } }) { Text(strings.getString(R.string.transfer_resume)) }
                TextButton(onClick = { run { TransferController.discard(context) } }) { Text(strings.getString(R.string.transfer_discard)) }
            }
            Text(strings.getString(R.string.transfer_discard_hint), style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        status?.let { Text(it) }
        if(parent!=null && parent?.uri==stack.lastOrNull()) folderScrollStates.SaveableStateProvider(stack.last().toString()) {
        LazyColumn(Modifier.weight(1f)) {
            items(entries, key = { it.uri.toString() }) { item ->
                Column(Modifier.fillMaxWidth().clickable(enabled = !busy && item.directory) {
                    run { stack = stack + item.uri; reload() }
                }.padding(vertical = 8.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if(item.deletable) TextButton(onClick={deleting=item},enabled=!busy) {Text(strings.getString(R.string.delete))}
                        if (item.renameable) TextButton(onClick = { renamed = item; name = item.name; naming = true }, enabled = !busy) { Text(strings.getString(R.string.rename)) }
                        TextButton(onClick = { source = item; moving = false; targetPicker.launch(null) }, enabled = !busy && !transfer.interrupted) { Text(strings.getString(R.string.copy_file)) }
                        if (item.deletable) TextButton(onClick = { source = item; moving = true; targetPicker.launch(null) }, enabled = !busy && !transfer.interrupted) { Text(strings.getString(R.string.move_file)) }
                    }
                    HorizontalDivider()
                }
            }
        }
        } else Spacer(Modifier.weight(1f))
    }
    deleting?.let {selected->AlertDialog(onDismissRequest={deleting=null},title={Text(strings.getString(R.string.delete))},
        text={Column {Text(strings.getString(if(selected.directory) R.string.delete_folder_review else R.string.delete_file_review,selected.name));Text(selected.name,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}},
        confirmButton={TextButton(onClick={deleting=null;run {TransferController.startDocumentAction(context,de.luma.gallery.data.DocumentAction.DELETE,requireNotNull(stack.lastOrNull()),selected)}},enabled=!busy) {Text(strings.getString(R.string.delete))}},
        dismissButton={TextButton(onClick={deleting=null}) {Text(strings.getString(R.string.cancel))}})}
    if (naming) AlertDialog(onDismissRequest = { naming = false }, title = { Text(strings.getString(if (renamed == null) R.string.create_folder else R.string.rename)) },
        text = { Column {OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(strings.getString(R.string.file_name)) })} },
        confirmButton = { TextButton(onClick = {
            val selected = renamed; val value = name; naming = false
            run {val current=requireNotNull(stack.lastOrNull());TransferController.startDocumentAction(context,if(selected==null) de.luma.gallery.data.DocumentAction.CREATE else de.luma.gallery.data.DocumentAction.RENAME,current,selected,value)}
        }, enabled = runCatching { documentName(name) }.isSuccess) { Text(strings.getString(R.string.save)) } },
        dismissButton = { TextButton(onClick = { naming = false }) { Text(strings.getString(R.string.cancel)) } })
    val selected = source; val target = destination
    if (selected != null && target != null) AlertDialog(onDismissRequest = { destination = null; source = null },
        title = { Text(strings.getString(if (moving) R.string.move_file else R.string.copy_file)) },
        text = { Text(strings.getString(when {
            selected.directory && moving -> R.string.move_folder_review
            selected.directory -> R.string.copy_folder_review
            moving -> R.string.move_review
            else -> R.string.copy_review
        }, selected.name, target.name)) },
        confirmButton = { TextButton(onClick = {
            val move = moving; destination = null; source = null
            run {
                TransferController.start(context, selected.uri, target.uri, move, stack.lastOrNull())
            }
        }, enabled = target.directory && target.canCreate) { Text(strings.getString(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = { destination = null; source = null }) { Text(strings.getString(R.string.cancel)) } })
}
