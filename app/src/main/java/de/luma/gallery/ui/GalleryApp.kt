package de.luma.gallery.ui

import de.luma.gallery.R
import de.luma.gallery.localization.appLocale
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.luma.gallery.GalleryState
import de.luma.gallery.GalleryViewModel
import de.luma.gallery.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun GalleryApp(vm: GalleryViewModel) {
    val strings = appStrings()
    val transfer by de.luma.gallery.data.TransferController.state.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(strings.configuration.locales.toLanguageTags()) { vm.refreshLocalizedErrors() }
    val editor = state.editor
    val context = LocalContext.current
    val activity = androidx.activity.compose.LocalActivity.current
    val snackbar = remember { SnackbarHostState() }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var filesOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var projectsOpen by rememberSaveable { mutableStateOf(false) }
    var trashOpen by rememberSaveable { mutableStateOf(false) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(state.exportSnapshot.event) {
        val export=state.exportSnapshot
        if(export.event>0 && export.fileReplaced && !export.running && state.editor?.source?.id==export.overwritingSourceId) {editing=false;vm.closeEditor()}
    }
    var filter by rememberSaveable { mutableStateOf(MediaFilter.PHOTOS) }
    var sessionColumns by remember { mutableIntStateOf(vm.galleryColumns ?: state.settings.columns) }
    LaunchedEffect(state.settingsReady,state.settings.columns) {
        if(state.settingsReady && vm.galleryDefaultColumns != state.settings.columns) {
            vm.galleryDefaultColumns=state.settings.columns;vm.galleryColumns=state.settings.columns;sessionColumns=state.settings.columns
        }
    }
    var sessionAlbumColumns by remember {mutableIntStateOf(vm.albumColumns ?: state.settings.albumColumns)}
    LaunchedEffect(state.settingsReady,state.settings.albumColumns) {
        if(state.settingsReady && vm.albumDefaultColumns!=state.settings.albumColumns) {
            vm.albumDefaultColumns=state.settings.albumColumns;vm.albumColumns=state.settings.albumColumns;sessionAlbumColumns=state.settings.albumColumns
        }
    }
    var duplicateChecking by remember {mutableStateOf(false)}
    var vaultOpen by rememberSaveable {mutableStateOf(false)}
    var vaultRemoval by remember {mutableStateOf(emptyList<de.luma.gallery.data.VaultItem>())}
    var vaultSources by remember {mutableStateOf(emptyList<GalleryMedia>())}
    DisposableEffect(vaultOpen,activity) {
        if(vaultOpen) activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {if(vaultOpen) activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
    }
    var duplicatesOpen by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(Unit) {
        when(activity?.intent?.getStringExtra("gimbo.analysis")) {
            "DUPLICATES"->duplicatesOpen=true
        }
    }
    var duplicatePreviewIds by rememberSaveable {mutableStateOf<List<String>?>(null)}
    var duplicateSelection by rememberSaveable {mutableStateOf(emptyList<String>())}
    var creativeOpen by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(Unit) {if(activity?.intent?.getStringExtra("gimbo.analysis")=="MOODS") creativeOpen=true}
    var creativeIds by rememberSaveable {mutableStateOf<List<String>?>(null)}
    var creativeTitle by rememberSaveable {mutableStateOf("")}
    var collageIds by rememberSaveable {mutableStateOf<List<String>?>(null)}
    var collageBackground by rememberSaveable {mutableLongStateOf(0xFFF8F7FA)}
    val reviewDraft=remember {de.luma.gallery.data.ReviewDraft(context)}
    var savedReview by remember {mutableStateOf(reviewDraft.read())}
    var reviewIds by rememberSaveable {mutableStateOf<List<String>?>(null)}
    val extrasLibrary=remember(state.media,state.archived) {state.media.filter {it.id !in state.archived}}
    ImmersiveMedia(openedId != null || collageIds!=null || reviewIds!=null)
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var importsOpen by rememberSaveable {mutableStateOf(false)}
    var importsToRemove by remember {mutableStateOf<Set<String>?>(null)}
    var virtualAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var managedFolder by remember {mutableStateOf<String?>(null)}
    var managedFolderAction by remember {mutableStateOf<FolderAction?>(null)}
    var sorting by remember {mutableStateOf(false)}
    var query by rememberSaveable { mutableStateOf("") }
    val uiScope = rememberCoroutineScope()
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    var headerVisible by rememberSaveable { mutableStateOf(true) }
    var transferIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var transferMove by rememberSaveable { mutableStateOf(false) }
    var moveDeletion by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(vm.gallerySelection) }
    SideEffect {vm.gallerySelection=selection}
    var batchItems by remember { mutableStateOf<List<GalleryMedia>?>(null) }
    var albumPickerIds by remember { mutableStateOf<Set<String>?>(null) }
    var removeAlbumIds by remember { mutableStateOf<Set<String>?>(null) }
    fun startCollage(photos:List<GalleryMedia>,background:Long) {collageIds=photos.map {it.id};collageBackground=background;selection=emptySet()}
    fun openScreenshots() {creativeOpen=false;creativeIds=null;page=0;filter=MediaFilter.SCREENSHOTS;album=null;virtualAlbumId=null;importsOpen=false;archivedOpen=false;selection=emptySet();headerVisible=true}
    fun openArchive() {creativeIds=null;archivedOpen=true;page=0;filter=MediaFilter.ALL;album=null;virtualAlbumId=null;importsOpen=false;selection=emptySet();headerVisible=true}
    val virtualAlbum = state.albums.find { it.id == virtualAlbumId }
    val locale = strings.appLocale
    val albumLibrary = remember(state.media, state.archived, virtualAlbum, virtualAlbumId, archivedOpen,importsOpen,state.importedReferences,creativeIds,duplicatesOpen,duplicatePreviewIds) {
        val library=state.media.filter { (it.id in state.archived) == archivedOpen }
        if(duplicatesOpen && duplicatePreviewIds!=null) state.media.filter {it.id in duplicatePreviewIds.orEmpty()} else if(creativeIds!=null) library.filter {it.id in creativeIds.orEmpty()} else if(importsOpen) library.filter {it.id in state.importedReferences} else virtualAlbum?.let {albumMedia(it,library)} ?: if(virtualAlbumId==null) library else emptyList()
    }
    val visibleMedia = remember(albumLibrary, filter, archivedOpen, state.favorites, page, query, album, state.settings.newestFirst, state.settings.sort, locale,duplicatesOpen) {
        selectMedia(albumLibrary,if(archivedOpen || duplicatesOpen) MediaFilter.ALL else filter,state.favorites,if(page==4 && !duplicatesOpen) query else "",if(duplicatesOpen) null else album,state.settings.newestFirst,locale,state.settings.sort)
    }
    val scrollStates=rememberSaveableStateHolder()
    val mediaScrollStates=key(state.settings.sort.name,state.settings.newestFirst,state.settings.groupByDay) {rememberSaveableStateHolder()}
    fun collectionKey(targetPage:Int=page,targetFilter:MediaFilter=filter,targetAlbum:String?=album,targetVirtualAlbum:String?=virtualAlbumId,targetImports:Boolean=importsOpen,targetArchive:Boolean=archivedOpen,targetCreative:String?=creativeTitle.takeIf {creativeIds!=null}) =
        listOf("media",targetPage.toString(),targetFilter.name,targetAlbum,targetVirtualAlbum,targetImports.toString(),targetArchive.toString(),targetCreative,state.settings.sort.name,state.settings.newestFirst.toString(),state.settings.groupByDay.toString()).joinToString("|") {value->value?.let {"${it.length}:$it"} ?: "-"}

    LaunchedEffect(virtualAlbumId, state.albumsLoading, state.albumsError, virtualAlbum?.id) {
        if (virtualAlbumId != null && !state.albumsLoading && state.albumsError == null && virtualAlbum == null) virtualAlbumId = null
    }
    LaunchedEffect(virtualAlbum?.mediaIds, state.media) {
        val available=state.media.map {it.id}.toHashSet()
        selection = selection.filter { id -> id in available && (virtualAlbumId == null || id in virtualAlbum?.mediaIds.orEmpty()) }.toSet()
    }
    var permissionsVersion by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsVersion++
        vm.refresh()
    }
    var replaceProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    val replacementLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = replaceProjectId
        replaceProjectId = null
        if (uri != null && id != null) vm.prepareSourceReplacement(id, uri)
    }
    val archiveWriter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) vm.saveMediaArchive(uri) }
    val archiveReader = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.readMediaArchive(uri) }
    val archiveTarget = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.restoreMediaArchive(uri) }
    val backupWriter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.saveBackup(uri) }
    val backupReader = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.readBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.import(uris)
    }
    val transferTarget = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val items = state.media.filter { it.id in transferIds }
            if (items.size == transferIds.size && items.isNotEmpty()) vm.startSelectionTransfer(items, uri, transferMove)
        }
        transferIds = emptyList()
    }
    fun transferSelection(items: List<GalleryMedia>, move: Boolean) {
        transferIds = items.map { it.id }; transferMove = move; transferTarget.launch(null)
    }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val action = vm.state.value.pendingMediaAction?.action
        if (result.resultCode == Activity.RESULT_OK && action != MediaAction.RESTORE) {
            selection = emptySet(); openedId = null; editing = false; vm.closeEditor()
        }
        vm.completeMediaAction(result.resultCode == Activity.RESULT_OK)
        if(duplicatesOpen && result.resultCode==Activity.RESULT_OK) {vm.resetDuplicateResults();duplicateSelection=emptyList()}
        if(moveDeletion) { moveDeletion=false; uiScope.launch { de.luma.gallery.data.TransferController.finishMove(context) } }
    }
    var legacySender by remember { mutableStateOf<android.content.IntentSender?>(null) }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        uiScope.launch {
            if(result.resultCode==Activity.RESULT_OK) legacySender=vm.deleteLegacyNext() else vm.completeMediaAction(false)
            if(vm.state.value.pendingMediaAction==null) {
                if(duplicatesOpen) {vm.resetDuplicateResults();duplicateSelection=emptyList()}
                selection=emptySet();openedId=null;editing=false;vm.closeEditor()
                if(moveDeletion) {moveDeletion=false;de.luma.gallery.data.TransferController.finishMove(context)}
            }
        }
    }
    LaunchedEffect(legacySender) {
        legacySender?.let { sender -> legacySender=null
            try {legacyLauncher.launch(IntentSenderRequest.Builder(sender).build())} catch(_:Exception) {vm.completeMediaAction(false,launchFailed=true)}
        }
    }
    val requestAccess: () -> Unit = {
        val permissions = if (Build.VERSION.SDK_INT >= 34) arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        ) else if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        permissionLauncher.launch(permissions)
    }
    val hasFullAccess = remember(permissionsVersion) {
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { permissionsVersion++; vm.refresh() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(transfer.event) { if (transfer.event > 0) { vm.refresh(); transfer.message?.let { snackbar.showSnackbar(it) } } }
    val opened = state.editor?.source?.takeIf { it.id == openedId } ?: state.media.find { it.id == openedId }
    LaunchedEffect(state.editor?.token) {
        state.editor?.let { openedId = it.source.id; editing = true; settingsOpen = false }
    }
    val leaveEditor: () -> Unit = {
        if (state.editor?.projectId != null) { openedId = null; projectsOpen = true }
        editing = false
        vm.closeEditor()
    }
    var mediaAction by remember { mutableStateOf<PendingMediaAction?>(null) }
    LaunchedEffect(transfer.moveSources) {
        if(transfer.moveSources.isNotEmpty()) {
            moveDeletion=true; mediaAction=PendingMediaAction(MediaAction.DELETE,transfer.moveSources)
        }
    }
    LaunchedEffect(trashOpen) { if (trashOpen) vm.refreshTrash() }
    var trashPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(state.trash,state.trashLoading) {
        if(!state.trashLoading && !trashPrompted && mediaAction==null && vm.dueTrash().isNotEmpty()) {
            trashPrompted=true; mediaAction=PendingMediaAction(MediaAction.DELETE,vm.dueTrash())
        }
    }
    LaunchedEffect(Unit) { vm.refreshTrash() }
    BackHandler(enabled = vaultOpen || duplicatesOpen || creativeOpen || collageIds!=null || reviewIds!=null || creativeIds!=null || (filter==MediaFilter.SCREENSHOTS || filter==MediaFilter.FAVORITES) || importsOpen || archivedOpen || filesOpen || settingsOpen || projectsOpen || trashOpen || opened != null || (album != null || virtualAlbumId != null) || selection.isNotEmpty()) {
        when {
            vaultOpen->{vaultOpen=false;vaultSources=emptyList();de.luma.gallery.data.VaultController.lock(context)}
            editing -> leaveEditor()
            opened != null -> { openedId = null; vm.closeEditor() }
            reviewIds!=null -> reviewIds=null
            collageIds!=null -> collageIds=null
            duplicatesOpen -> {duplicatesOpen=false;duplicatePreviewIds=null}
            creativeOpen -> creativeOpen=false
            filesOpen -> {filesOpen = false;managedFolder=null;managedFolderAction=null}
            trashOpen -> trashOpen = false
            projectsOpen -> { projectsOpen = false; vm.closeEditor() }
            settingsOpen -> settingsOpen = false
            selection.isNotEmpty() -> selection = emptySet()
            archivedOpen -> archivedOpen = false
            creativeIds!=null -> {creativeIds=null;creativeOpen=true}
            (filter==MediaFilter.SCREENSHOTS || filter==MediaFilter.FAVORITES) -> {filter=MediaFilter.PHOTOS;page=0}
            else -> { album = null; virtualAlbumId = null;importsOpen=false }
        }
    }
    importsToRemove?.let {ids->CompactDialog(strings.getString(R.string.remove_import_references),{importsToRemove=null},actions={
        TextButton(onClick={importsToRemove=null}) {Text(strings.getString(R.string.cancel))}
        TextButton(onClick={importsToRemove=null;openedId=null;selection=emptySet();importsOpen=false;vm.forgetImportedReferences(ids)},enabled=!state.removingImports) {Text(strings.getString(R.string.remove_import_references))}
    }) {Text(strings.getString(R.string.remove_import_references_hint));Text(strings.getQuantityString(R.plurals.media_count,ids.size,ids.size))}}
    if(sorting) MediaSortDialog(state.settings,{sorting=false}) {vm.settings(it)}
    fun startReview(items:List<GalleryMedia>) {
        val ids=items.filter(::canManageMedia).take(MEDIA_ACTION_LIMIT).map {it.id}
        if(ids.isEmpty()) uiScope.launch {snackbar.showSnackbar(strings.getString(R.string.review_no_files))}
        else {reviewDraft.save(ReviewSession(ids));savedReview=reviewDraft.read();reviewIds=ids;selection=emptySet()}
    }
    val selectionSummary: @Composable () -> Unit = {
        SelectionSummary(selection.size, {selection=emptySet()}, {
            selection=if(selection.size==visibleMedia.size) emptySet() else visibleMedia.map {it.id}.toSet()
        })
    }
    val chosenMedia=remember(state.media,selection) {state.media.filter {it.id in selection}}
    val selectionActions: @Composable () -> Unit = {
        if(selection.isNotEmpty()) {
            val chosen = chosenMedia
            val transferAllowed = !transfer.running && !transfer.interrupted && transfer.moveSources.isEmpty() && chosen.size in 1..5000
            if(chosen.size>MEDIA_ACTION_LIMIT) Text(strings.getString(R.string.selection_limits),Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.bodySmall)
            LazyRow(modifier=Modifier.semantics {contentDescription=strings.getString(R.string.selection_actions)},contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                item { ActionButton(LumaIcons.DeleteOutline, strings.getString(R.string.delete), {
                    mediaAction=PendingMediaAction(if(state.settings.trashDays==0 || Build.VERSION.SDK_INT<30) MediaAction.DELETE else MediaAction.TRASH,chosen)
                },chosen.size<=MEDIA_ACTION_LIMIT && chosen.all(::canManageMedia) && state.pendingMediaAction==null,destructive=true) }
                item { ActionButton(LumaIcons.Tune, strings.getString(R.string.edit), {
                    if(chosen.size==1) { vm.beginEditor(chosen.first()); openedId=chosen.first().id; editing=true; selection=emptySet() }
                    else batchItems=chosen
                }, !state.exporting && (chosen.size==1 || chosen.size<=100 && chosen.all { !it.isVideo })) }
                item { ActionButton(if(selection.all {it in state.favorites}) LumaIcons.Favorite else LumaIcons.FavoriteBorder,strings.getString(R.string.favorite),{vm.favoriteAll(selection)}) }
                item { ActionButton(LumaIcons.Share,strings.getString(R.string.share),{share(context,chosen.map {it.uri})}) }
                item {ActionButton(LumaIcons.Review,strings.getString(R.string.review_title),{startReview(chosen)},chosen.any(::canManageMedia))}
                item { ActionButton(LumaIcons.Move,strings.getString(R.string.move),{transferSelection(chosen,true)},transferAllowed && chosen.size<=MEDIA_ACTION_LIMIT && chosen.all(::canManageMedia)) }
                item { ActionButton(LumaIcons.Copy,strings.getString(R.string.copy),{transferSelection(chosen,false)},transferAllowed) }
                item { ActionButton(LumaIcons.FolderOpen,strings.getString(R.string.album),{albumPickerIds=selection},!state.savingAlbum) }
                item { ActionButton(LumaIcons.Archive,strings.getString(if(archivedOpen) R.string.unarchive else R.string.library_archive),{
                    vm.archive(selection,!archivedOpen);selection=emptySet()
                }) }
                item {ActionButton(LumaIcons.Lock,strings.getString(R.string.vault_secure),{vaultSources=chosen;vaultOpen=true;selection=emptySet()},chosen.size in 1..5000)}
                item {ActionButton(LumaIcons.Collage,strings.getString(R.string.collage_create),{startCollage(chosen,0xFFF8F7FA)},chosen.size in 2..9 && chosen.all {!it.isVideo} && !state.exporting)}
                val references=selection.intersect(state.importedReferences)
                if(references.isNotEmpty()) item {ActionButton(LumaIcons.Close,strings.getString(R.string.remove_import_references),{importsToRemove=references},!state.removingImports)}
                if(virtualAlbumId!=null) item { ActionButton(LumaIcons.Close,strings.getString(R.string.remove_from_album),{removeAlbumIds=selection}) }
            }

        }
    }
    LumaTheme(state.settings) {
        batchItems?.let { items -> BatchPhotoDialog(items, state.projects, state.exportOptions, { batchItems = null },
            { recipe, options -> vm.exportPhotos(items, recipe, options); batchItems = null; selection = emptySet() }) }

        state.archiveReview?.let { review ->
            AlertDialog(onDismissRequest = vm::dismissArchive, title = { Text(strings.getString(R.string.archive_title)) },
                text = { Text(strings.getString(R.string.archive_review, review.media.size, review.backup.projects.size, review.backup.albums.size)) },
                confirmButton = { TextButton(onClick = { archiveTarget.launch(null) }, enabled = !state.backupBusy) { Text(strings.getString(R.string.archive_destination)) } },
                dismissButton = { TextButton(onClick = vm::dismissArchive, enabled = !state.backupBusy) { Text(strings.getString(R.string.cancel)) } })
        }
        state.backupReview?.let { review ->
            AlertDialog(onDismissRequest = vm::dismissBackup, title = { Text(strings.getString(R.string.backup_title)) },
                text = { Text(strings.getString(R.string.backup_review, review.projects.size, review.albums.size)) },
                confirmButton = { TextButton(onClick = vm::restoreBackup, enabled = !state.backupBusy) { Text(strings.getString(R.string.restore_backup)) } },
                dismissButton = { TextButton(onClick = vm::dismissBackup, enabled = !state.backupBusy) { Text(strings.getString(R.string.cancel)) } })
        }

        state.sourceReplacement?.let { SourceReplacementDialog(it, state.replacingSource, vm::cancelSourceReplacement, vm::confirmSourceReplacement) }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                    if(!filesOpen && !vaultOpen && !duplicatesOpen && !creativeOpen && opened==null && reviewIds==null && collageIds==null) GalleryTasks(state,transfer,vm,
                        onFiles={filesOpen=true},onDuplicates={duplicatesOpen=true},onMoods={creativeOpen=true},onVault={vaultSources=emptyList();vaultOpen=true},onCloud={settingsOpen=true})
                    if (!vaultOpen && !duplicatesOpen && !creativeOpen && collageIds==null && reviewIds==null && !settingsOpen && !projectsOpen && !trashOpen && !filesOpen && opened == null) Column {
                        if(selection.isNotEmpty()) selectionSummary()
                        selectionActions()
                        GalleryNavigation(if(filter==MediaFilter.FAVORITES) -1 else page,
                            reviewEnabled=savedReview!=null || (if(selection.isEmpty()) visibleMedia else chosenMedia).any(::canManageMedia),
                            reviewResume=savedReview!=null) {index->
                            if(index==2) {
                                val draft=reviewDraft.read()
                                if(draft!=null) {savedReview=draft;reviewIds=draft.ids;selection=emptySet()}
                                else startReview(if(selection.isEmpty()) visibleMedia else chosenMedia)
                            } else {
                                if(index!=page || album!=null || virtualAlbumId!=null || importsOpen || archivedOpen || creativeIds!=null || filter==MediaFilter.FAVORITES || filter==MediaFilter.SCREENSHOTS) {
                                    val targetFilter=when(index) {0->MediaFilter.PHOTOS;1->MediaFilter.VIDEOS;else->MediaFilter.ALL}
                                    mediaScrollStates.removeState(collectionKey(index,targetFilter,null,null,false,false,null))
                                    if(index==3) scrollStates.removeState("albums")
                                }
                                page=index;creativeIds=null;archivedOpen=false;importsOpen=false;headerVisible=true;album=null;virtualAlbumId=null;selection=emptySet()
                                filter=when(index) {0->MediaFilter.PHOTOS;1->MediaFilter.VIDEOS;else->MediaFilter.ALL}
                            }
                        }
                    }
                    }
                },
            ) { padding ->
                val screenKey=when {
                    vaultOpen || opened!=null || collageIds!=null || reviewIds!=null || filesOpen->null
                    duplicatesOpen->"duplicates"
                    creativeOpen->"creative"
                    trashOpen->"trash"
                    settingsOpen->"settings"
                    projectsOpen->"projects"
                    page==3 && album==null && virtualAlbumId==null && !importsOpen->"albums"
                    else->collectionKey()
                }
                Box(Modifier.padding(padding).then(if (opened == null) Modifier.statusBarsPadding().then(if(settingsOpen || projectsOpen || filesOpen || trashOpen) Modifier.navigationBarsPadding() else Modifier) else Modifier).fillMaxSize()) {
                    val screenContent: @Composable () -> Unit = {
                    when {
                        vaultOpen -> VaultScreen(state.media.filter {!it.isDemo},vaultSources,{vaultOpen=false;vaultSources=emptyList()},autoPlay=state.settings.autoPlayVideos) {originals->
                            if(originals.isNotEmpty()) {vaultRemoval=originals;mediaAction=PendingMediaAction(MediaAction.DELETE,originals.map {it.media})}
                        }
                        state.settingsLoading && opened == null && !settingsOpen -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        opened != null && editing && editor != null -> key(editor.token) {
                            if (opened.isVideo) VideoEditor(opened, vm, editor, onBack = leaveEditor)
                            else PhotoEditor(opened, vm, editor, onBack = leaveEditor)
                        }
                        opened != null -> MediaViewer(opened, state, onBack = { openedId = null },
                            onEdit = { vm.beginEditor(opened); editing = true }, onFavorite = { vm.favorite(opened.id) },
                            onMove = { offset ->
                                val current = visibleMedia.indexOfFirst { it.id == opened.id }
                                if (current >= 0) visibleMedia.getOrNull(current + offset)?.let { openedId = it.id }
                            }, onTrash = { mediaAction = PendingMediaAction(if(state.settings.trashDays==0 || Build.VERSION.SDK_INT<30) MediaAction.DELETE else MediaAction.TRASH, listOf(opened)) },
                            onArchive = { vm.archive(setOf(opened.id), opened.id !in state.archived); openedId=null },
                            onAlbum = { albumPickerIds = setOf(opened.id) },
                            onRemoveImport=if(opened.id in state.importedReferences) ({importsToRemove=setOf(opened.id)}) else null,
                            canPrevious = visibleMedia.indexOfFirst { it.id == opened.id } > 0,
                            canNext = visibleMedia.indexOfFirst { it.id == opened.id }.let { it >= 0 && it < visibleMedia.lastIndex })
                        collageIds!=null -> {
                            val sources=collageIds.orEmpty().mapNotNull {id->state.media.find {it.id==id}}
                            if(sources.size==collageIds.orEmpty().size) CollageEditor(sources,extrasLibrary,collageBackground,state.exporting,{collageIds=null}) {photos,spec,options->vm.exportCollage(photos,spec,options);collageIds=null}
                            else EmptyState(LumaIcons.Info,strings.getString(R.string.image_open_failed),strings.getString(R.string.review_source_missing),strings.getString(R.string.back),{collageIds=null})
                        }
                        reviewIds!=null -> CompositionLocalProvider(LocalReviewDraft provides reviewDraft) {
                            ReviewScreen(reviewIds.orEmpty(),state.media,state.settings,{reviewIds=null;reviewDraft.clear();savedReview=null}) {session->
                            val archive=session.selected(ReviewAction.ARCHIVE);if(archive.isNotEmpty()) vm.archive(archive,true)
                            val favorites=session.selected(ReviewAction.FAVORITE)-state.favorites;if(favorites.isNotEmpty()) vm.favoriteAll(favorites)
                            val removals=state.media.filter {it.id in session.selected(ReviewAction.REMOVE) && canManageMedia(it)}
                            reviewIds=null;reviewDraft.clear();savedReview=null
                            if(removals.isNotEmpty()) mediaAction=PendingMediaAction(if(state.settings.trashDays==0 || Build.VERSION.SDK_INT<30) MediaAction.DELETE else MediaAction.TRASH,removals)
                        }
                        }
                        duplicatesOpen -> DuplicatesScreen(state,duplicateSelection.toSet(),{duplicateSelection=it.toList()},
                            onBack={duplicatesOpen=false;duplicatePreviewIds=null},onScan=vm::scanDuplicates,onCancel=vm::cancelDuplicateScan,onReset=vm::resetDuplicateResults,onResume=vm::resumeDuplicateScan,
                            onPreview={group,item->duplicatePreviewIds=group.files.map {it.media.id};openedId=item.id},checking=duplicateChecking,onRemove={ids->
                                duplicateChecking=true
                                uiScope.launch {try {vm.verifyDuplicateRemoval(ids)?.let {items->mediaAction=PendingMediaAction(if(state.settings.trashDays==0 || Build.VERSION.SDK_INT<30) MediaAction.DELETE else MediaAction.TRASH,items)}} finally {duplicateChecking=false}}
                            })
                        creativeOpen -> CreativeScreen(extrasLibrary,{creativeOpen=false},::startCollage,state.moodAnalysis,{vm.scanMoods(extrasLibrary)},vm::pauseMoods,vm::resumeMoods) {media,title->
                            creativeIds=media.map {it.id};creativeTitle=title;creativeOpen=false;page=0;filter=MediaFilter.ALL;album=null;virtualAlbumId=null;importsOpen=false;archivedOpen=false;headerVisible=true
                        }
                        trashOpen -> TrashScreen(state.trash, state.trashLoading, state.trashError,
                            state.trashAccessLimited || !hasFullAccess, Build.VERSION.SDK_INT >= 30,
                            state.pendingMediaAction != null, onBack = { trashOpen = false },
                            onRefresh = vm::refreshTrash, onAccess = requestAccess,
                            onAction = { action, items -> mediaAction = PendingMediaAction(action, items) })
                        filesOpen -> FileToolsScreen(onBack = { filesOpen = false;managedFolder=null;managedFolderAction=null }, onChanged = vm::refresh,requestedFolder=managedFolder,requestedAction=managedFolderAction)
                        settingsOpen -> SettingsScreen(state, onCancelBackup = vm::cancelBackup, onSaveArchive = { archiveWriter.launch("Gimbo-Galerie.zip") }, onReadArchive = { archiveReader.launch(arrayOf("application/zip", "application/octet-stream")) }, onBack = { settingsOpen = false }, onSettings = vm::settings,
                            onImport = { importLauncher.launch(arrayOf("image/*", "video/*")) },
                            onTrash = { trashOpen = true }, onRetrySettings = vm::reloadSettings, onFiles = { filesOpen = true },
                            onSaveBackup = { backupWriter.launch("Gimbo-Galerie-Projekte.json") }, onReadBackup = { backupReader.launch(arrayOf("application/json", "application/octet-stream")) })
                        projectsOpen -> ProjectsScreen(state.projects, state.projectsLoading, state.projectsError,
                            onBack = { projectsOpen = false; vm.closeEditor() }, onOpen = vm::openProject,
                            onRename = vm::renameProject, onDelete = vm::deleteProject, revision = state.libraryRevision, onReplace = { project ->
                                replaceProjectId = project.id; replacementLauncher.launch(arrayOf(if (project.source.isVideo) "video/*" else "image/*")) })
                        else -> Column {
                            if (headerVisible) {
                                AppHeader(if(creativeIds!=null) creativeTitle else if(archivedOpen) strings.getString(R.string.library_archive) else if(filter==MediaFilter.FAVORITES) strings.getString(R.string.favorites) else if(filter==MediaFilter.SCREENSHOTS) strings.getString(R.string.screenshots) else if(importsOpen) strings.getString(R.string.imported_media_collection) else if (page == 3 && album == null && virtualAlbumId == null) strings.getString(R.string.your_albums_title) else if (page == 4) strings.getString(R.string.search_title) else virtualAlbum?.name ?: album ?: strings.getString(R.string.your_moments_title),
                                    onFavorites={page=2;filter=MediaFilter.FAVORITES;creativeIds=null;archivedOpen=false;importsOpen=false;headerVisible=true;album=null;virtualAlbumId=null;selection=emptySet()},
                                    favoritesActive=filter==MediaFilter.FAVORITES,onTrash = { trashOpen = true }, onSettings = { settingsOpen = true }, onImport = { importLauncher.launch(arrayOf("image/*", "video/*")) },
                                    onVault={vaultSources=emptyList();vaultOpen=true},onDuplicates={duplicatesOpen=true},onCreative={creativeOpen=true},onScreenshots=::openScreenshots,
                                    onSort = { sorting = true }, onProjects = { projectsOpen = true }, onArchive = ::openArchive)
                            }
                            if(creativeIds!=null) TextButton(onClick={creativeIds=null;creativeOpen=true;selection=emptySet()},modifier=Modifier.padding(start=16.dp)) {Icon(LumaIcons.ArrowBack,null);Text(strings.getString(R.string.creative_title))}
                            if(filter==MediaFilter.SCREENSHOTS) Text(strings.getString(R.string.screenshots_hint),Modifier.padding(horizontal=24.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall)
                            if (album != null || virtualAlbumId != null || importsOpen) TextButton(onClick = { album = null; virtualAlbumId = null;importsOpen=false; selection = emptySet() }, modifier = Modifier.padding(start = 16.dp)) {
                                Icon(LumaIcons.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(strings.getString(R.string.all_albums))
                            }
                            if (page == 4) OutlinedTextField(query, { query = it }, singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                                placeholder = { Text(strings.getString(R.string.search_hint)) }, leadingIcon = { Icon(LumaIcons.Search, null) },
                                trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(LumaIcons.Close, strings.getString(R.string.clear_search)) } },
                                shape = RoundedCornerShape(18.dp))
                            if (virtualAlbum != null && albumLibrary.size < virtualAlbum.mediaIds.size) Text(
                                strings.getQuantityString(R.plurals.missing_assignment_hint, virtualAlbum.mediaIds.size - albumLibrary.size, virtualAlbum.mediaIds.size - albumLibrary.size),
                                Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!hasFullAccess && state.media.isNotEmpty()) TextButton(onClick = requestAccess,
                                modifier = Modifier.padding(start = 16.dp)) { Text(strings.getString(R.string.manage_media_access)) }
                            when {
                                page == 3 && album == null && virtualAlbumId == null && !importsOpen && ((state.albumsLoading && state.albums.isEmpty()) || (state.loading && state.media.isEmpty())) -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
                                page == 3 && album == null && virtualAlbumId == null && !importsOpen -> AlbumsScreen(state.albums, albumLibrary, state.albumsLoading,
                                    state.albumsError, state.savingAlbum, onOpen = { importsOpen=false;virtualAlbumId = it; filter = MediaFilter.ALL; selection = emptySet() },
                                    onFolder = { importsOpen=false;album = it; filter = MediaFilter.ALL; selection = emptySet() }, onCreate = { vm.createAlbum(it) },
                                    onRename = vm::renameAlbum, onDelete = vm::deleteAlbum,
                                    order=state.settings.albumOrder,onOrder={vm.settings(state.settings.copy(albumOrder=it))},
                                    onDeleteAlbums=vm::deleteAlbums,onManageFolder={managedFolder=it;managedFolderAction=null;filesOpen=true},
                                    columns=sessionAlbumColumns,onColumns={sessionAlbumColumns=it;vm.albumColumns=it;if(state.settings.rememberGrid) vm.settings(state.settings.copy(albumColumns=it))},
                                    onFolderAction={name,action->managedFolder=name;managedFolderAction=action;filesOpen=true},onAtTop={headerVisible=it},importedReferences=state.importedReferences,
                                    onVault={vaultSources=emptyList();vaultOpen=true},onSecureMedia={items->vaultSources=items;vaultOpen=true},onScreenshots=::openScreenshots,onCreative={creativeOpen=true},onDuplicates={duplicatesOpen=true},
                                    onTrash={trashOpen=true},onArchive=::openArchive,
                                    onOpenImports={importsOpen=true;album=null;virtualAlbumId=null;filter=MediaFilter.ALL;selection=emptySet()},onRemoveImports={importsToRemove=it})
                                virtualAlbumId != null && state.albumsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                                virtualAlbumId != null && state.albumsError != null -> EmptyState(LumaIcons.Info, strings.getString(R.string.albums_unavailable), state.albumsError!!)
                                state.loading && state.media.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                                state.error != null -> EmptyState(LumaIcons.Info, strings.getString(R.string.check_access), state.error!!, strings.getString(R.string.reload), vm::refresh)
                                state.media.isEmpty() && virtualAlbumId == null -> Welcome(onAccess = requestAccess,
                                    onImport = { importLauncher.launch(arrayOf("image/*", "video/*")) })
                                else -> {
                                    val visible = visibleMedia
                                    if (visible.isEmpty()) EmptyState(LumaIcons.Search, strings.getString(R.string.empty_gallery_title),
                                        if (virtualAlbum != null && virtualAlbum.mediaIds.isEmpty()) strings.getString(R.string.empty_album_hint)
                                        else if (virtualAlbum != null && albumLibrary.isEmpty()) strings.getString(R.string.missing_album_sources_hint)
                                        else if (filter == MediaFilter.FAVORITES) strings.getString(R.string.favorites_hint) else strings.getString(R.string.search_empty_hint))
                                    else key(page, filter, album, virtualAlbumId, query, archivedOpen) {
                                        val galleryGrid=rememberSaveable(query,saver=androidx.compose.foundation.lazy.grid.LazyGridState.Saver) {androidx.compose.foundation.lazy.grid.LazyGridState()}
                                        MediaGrid(visible, sessionColumns, state.favorites, selection,
                                        onAtTop = { if(selection.isEmpty()) headerVisible = it }, onColumns = { sessionColumns = it; vm.galleryColumns = it; if (state.settings.rememberGrid) vm.settings(state.settings.copy(columns = it)) },
                                        onSelection = { selection = it }, animate = state.settings.thumbnailAnimations, revision = state.libraryRevision, gridState=galleryGrid, groupByDay = state.settings.groupByDay && state.settings.sort == MediaSort.DATE,
                                        onOpen = { item -> if (selection.isEmpty()) openedId = item.id else selection = selection.toggle(item.id) },
                                        onSelect = { selection = selection.toggle(it.id) }) }
                                }
                            }
                        }
                    }
                    }
                    if(screenKey==null) screenContent() else (if(screenKey==collectionKey()) mediaScrollStates else scrollStates).SaveableStateProvider(screenKey,screenContent)
                }
            }
            albumPickerIds?.let { ids -> AlbumPickerDialog(state.albums, ids.size, state.albumsLoading, state.albumsError, state.savingAlbum,
                onDismiss = { albumPickerIds = null }, onAdd = { id -> vm.addToAlbum(id, ids); albumPickerIds = null; selection = emptySet() },
                onCreate = { name -> vm.createAlbum(name, ids); albumPickerIds = null; selection = emptySet() }) }
            removeAlbumIds?.let { ids -> AlertDialog(onDismissRequest = { removeAlbumIds = null }, title = { Text(strings.getString(R.string.remove_from_album_title)) },
                text = { Text(strings.getQuantityString(R.plurals.remove_assignments_hint, ids.size, ids.size)) },
                confirmButton = { TextButton(onClick = {
                    virtualAlbumId?.let { vm.removeFromAlbum(it, ids) }; removeAlbumIds = null; selection = emptySet()
                }, enabled = !state.savingAlbum && virtualAlbumId != null) { Text(strings.getString(R.string.remove_assignment)) } },
                dismissButton = { TextButton(onClick = { removeAlbumIds = null }) { Text(strings.getString(R.string.cancel)) } }) }
            mediaAction?.let { pending -> MediaActionDialog(pending, moving=moveDeletion, onDismiss = {
                mediaAction = null;vaultRemoval=emptyList()
                if(moveDeletion) { moveDeletion=false; uiScope.launch { de.luma.gallery.data.TransferController.finishMove(context) } }
            }, onConfirm = {confirmedItems->
                mediaAction = null
                val vaultItems=vaultRemoval;vaultRemoval=emptyList()
                uiScope.launch {
                    try {
                        val items = if(vaultItems.isNotEmpty()) de.luma.gallery.data.VaultController.verifyOriginals(context,vaultItems.filter {it.media.id in confirmedItems.map {media->media.id}.toSet()})
                            else if(duplicatesOpen) vm.verifyDuplicateRemoval(confirmedItems.map {it.id}.toSet()) ?: return@launch
                            else if(moveDeletion) de.luma.gallery.data.TransferController.verifyMove(context).filter {it.id in confirmedItems.map {media->media.id}.toSet()} else confirmedItems
                        if(Build.VERSION.SDK_INT==29 && pending.action==MediaAction.DELETE) {
                            vm.beginLegacyDelete(items);legacySender=vm.deleteLegacyNext()
                            if(vm.state.value.pendingMediaAction==null) {
                                if(duplicatesOpen) {vm.resetDuplicateResults();duplicateSelection=emptyList()}
                                selection=emptySet();openedId=null;editing=false;vm.closeEditor()
                                if(moveDeletion) {moveDeletion=false;de.luma.gallery.data.TransferController.finishMove(context)}
                            }
                        } else vm.prepareMediaAction(pending.action, items)?.let { sender ->
                            try { trashLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                            catch (_: Exception) { vm.completeMediaAction(success = false, launchFailed = true) }
                        }
                    } catch(e: Exception) {
                        if(e is kotlinx.coroutines.CancellationException) throw e
                        snackbar.showSnackbar(strings.getString(R.string.copy_source_changed))
                    }
                }
            }) }
        }
    }
}

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

@Composable
private fun AppHeader(title: String, onFavorites:()->Unit,favoritesActive:Boolean,onTrash:()->Unit, onSettings: () -> Unit, onImport: () -> Unit, onProjects: () -> Unit, onArchive: () -> Unit, onSort: () -> Unit,onCreative:()->Unit,onScreenshots:()->Unit,onDuplicates:()->Unit,onVault:()->Unit) {
    val strings = appStrings()
    var more by remember {mutableStateOf(false)}
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val compact=maxWidth<400.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.35f
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.takeUnless { it == strings.getString(R.string.your_moments_title) || it == strings.getString(R.string.your_albums_title) } ?: strings.getString(R.string.app_name),
            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if(!compact) LabeledIconButton(LumaIcons.Sort,strings.getString(R.string.sort_media),onSort)
        LabeledIconButton(if(favoritesActive) LumaIcons.Favorite else LumaIcons.FavoriteBorder,strings.getString(R.string.favorites),onFavorites,active=favoritesActive)
        LabeledIconButton(LumaIcons.DeleteOutline,strings.getString(R.string.trash),onTrash)
        Box {
            LabeledIconButton(LumaIcons.More,strings.getString(R.string.more_actions),{more=true})
            DropdownMenu(more,{more=false}) {
                if(compact) {
                    DropdownMenuItem(text={Text(strings.getString(R.string.sort_media))},leadingIcon={Icon(LumaIcons.Sort,null)},onClick={more=false;onSort()})
                    HorizontalDivider(Modifier.padding(vertical=4.dp))
                }
                listOf(
                    listOf(Triple(R.string.duplicates_title,LumaIcons.Duplicates,onDuplicates)),
                    listOf(Triple(R.string.vault_title,LumaIcons.Lock,onVault),Triple(R.string.creative_title,LumaIcons.Collage,onCreative),Triple(R.string.projects,LumaIcons.Projects,onProjects)),
                    listOf(Triple(R.string.library_archive,LumaIcons.Archive,onArchive),Triple(R.string.screenshots,LumaIcons.Screenshot,onScreenshots),Triple(R.string.add_media,LumaIcons.Add,onImport))
                ).forEachIndexed {index,group->
                    if(index>0) HorizontalDivider(Modifier.padding(vertical=4.dp))
                    group.forEach {(label,icon,action)->DropdownMenuItem(text={Text(strings.getString(label))},leadingIcon={Icon(icon,null)},onClick={more=false;action()})}
                }
            }
        }
        LabeledIconButton(LumaIcons.Settings,strings.getString(R.string.settings),onSettings)
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaGrid(media: List<GalleryMedia>, columns: Int, favorites: Set<String>, selection: Set<String>,
    onOpen: (GalleryMedia) -> Unit, onSelect: (GalleryMedia) -> Unit, onAtTop: (Boolean) -> Unit, onColumns: (Int) -> Unit, onSelection: (Set<String>) -> Unit, animate: Boolean = true, revision: Long = 0, groupByDay: Boolean = true, gridState:androidx.compose.foundation.lazy.grid.LazyGridState?=null) {
    val strings = appStrings()
    val locale = strings.appLocale
    val timeZone=java.util.TimeZone.getDefault()
    val date = remember(locale,timeZone.id) { SimpleDateFormat(strings.getString(R.string.day_heading_pattern), locale).apply {this.timeZone=timeZone} }
    val groups = remember(media, groupByDay,timeZone.id) {
        val keyFormat=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply {this.timeZone=timeZone}
        if(groupByDay) media.groupBy {if(it.timestamp==0L) "unknown" else keyFormat.format(Date(it.timestamp))} else mapOf("all" to media)
    }
    val fallbackGrid=androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val grid=gridState ?: fallbackGrid
    val atTop by remember { derivedStateOf { grid.firstVisibleItemIndex == 0 } }
    LaunchedEffect(atTop, selection.isEmpty()) { onAtTop(atTop) }
    val scope = rememberCoroutineScope()
    val gestures = galleryGridGestures(grid, media, columns, selection, onColumns, onSelection, scope).semantics {contentDescription=strings.getString(R.string.media_grid)}
    LazyVerticalGrid(GridCells.Fixed(columns), state = grid, modifier = gestures, contentPadding = PaddingValues(4.dp, 0.dp, 4.dp, 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { (key, group) ->
            if(groupByDay) item(key = "date:$key", span = { GridItemSpan(maxLineSpan) }) {
                val ids=remember(group) {group.map {it.id}.toSet()}
                DaySelectionHeader(if(key=="unknown") strings.getString(R.string.date_unknown) else date.format(Date(group.first().timestamp)),
                    ids,selection,onSelection,Modifier.padding(top=12.dp,bottom=4.dp).testTag("day_selection:$key"))
            }
            items(group, key = { it.id }) { item ->
                var thumbnailFailed by remember(item.uri, revision) { mutableStateOf(false) }
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = { onOpen(item) })
                    .semantics { selected = item.id in selection; onLongClick {onSelect(item);true} }) {
                    AsyncImage(ImageRequest.Builder(LocalContext.current).data(item.uri).memoryCacheKey("${item.uri}:$revision:${item.size}").crossfade(animate).build(),
                        item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { thumbnailFailed=true }, onSuccess={thumbnailFailed=false})
                    if (item.id in favorites) Icon(LumaIcons.Favorite, strings.getString(R.string.favorite), Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp), tint = Color.White)
                    if(thumbnailFailed) Icon(if(item.isVideo) LumaIcons.PlayArrow else LumaIcons.PhotoLibrary, strings.getString(R.string.preview_unavailable), Modifier.align(Alignment.Center).size(36.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.isVideo) Surface(Modifier.align(Alignment.BottomEnd).padding(5.dp), color = Color.Black.copy(alpha = .65f), shape = RoundedCornerShape(5.dp)) {
                        Text("▶ ${durationLabel(item.durationMs)}", Modifier.padding(4.dp), fontSize = 12.sp, color = Color.White)
                    }
                    if (item.id in selection) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha=.12f)).border(2.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(12.dp)))
                    }
                    if(selection.isNotEmpty()) SelectionIndicator(if(item.id in selection) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,Modifier.align(Alignment.TopStart).padding(8.dp),overMedia=true,animate=animate)
                }
            }
        }
    }
}

@Composable
private fun Welcome(onAccess: () -> Unit, onImport: () -> Unit) {
    val strings = appStrings()
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(LumaIcons.PhotoLibrary, null, Modifier.padding(26.dp).size(42.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        Text(strings.getString(R.string.welcome_title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(strings.getString(R.string.welcome_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAccess, modifier = Modifier.fillMaxWidth()) { Text(strings.getString(R.string.allow_media_access)) }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(strings.getString(R.string.choose_files)) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, selected: Boolean? = null, destructive:Boolean=false) {
    val strings = appStrings()
    TextButton(onClick = onClick, enabled = enabled, shape=RoundedCornerShape(18.dp), colors=ButtonDefaults.textButtonColors(contentColor=if(destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface), modifier = Modifier.widthIn(min=64.dp,max=132.dp).semantics { selected?.let { stateDescription = strings.getString(if(it) R.string.favorite_is_saved else R.string.favorite_not_saved) } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(23.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, style=MaterialTheme.typography.labelMedium, maxLines=2, overflow=TextOverflow.Ellipsis, textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

fun durationLabel(ms: Long): String = "%d:%02d".format(Locale.ROOT, ms / 60000, (ms / 1000) % 60)

internal fun share(context: android.content.Context, uris: List<Uri>) {
    val strings = context.resources
    if (uris.isEmpty()) return
    val types = uris.map { context.contentResolver.getType(it).orEmpty() }
    val mime = when {
        types.all { it.startsWith("image/") } -> "image/*"
        types.all { it.startsWith("video/") } -> "video/*"
        else -> "*/*"
    }
    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = mime
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        clipData = android.content.ClipData.newUri(context.contentResolver, strings.getString(R.string.media), uris.first()).apply {
            uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
        }
    }
    runCatching { context.startActivity(Intent.createChooser(intent, strings.getString(R.string.share))) }
}
