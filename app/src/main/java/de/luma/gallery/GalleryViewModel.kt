package de.luma.gallery

import de.luma.gallery.R
import de.luma.gallery.localization.ResourceFailure
import de.luma.gallery.localization.userMessage
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.luma.gallery.model.SourceReplacement
import de.luma.gallery.data.SourceProbe
import de.luma.gallery.localization.requireMessage
import de.luma.gallery.data.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import de.luma.gallery.data.GalleryRepository
import de.luma.gallery.data.VideoExporter
import de.luma.gallery.data.ProjectDatabase
import de.luma.gallery.data.ProjectRepository
import de.luma.gallery.data.TrashRepository
import de.luma.gallery.data.AlbumRepository
import de.luma.gallery.model.VirtualAlbum
import de.luma.gallery.model.TrashMedia
import de.luma.gallery.model.MediaAction
import de.luma.gallery.model.PendingMediaAction
import de.luma.gallery.model.checkedMediaAction
import android.os.Build
import de.luma.gallery.model.AppSettings
import de.luma.gallery.model.orderedClips
import de.luma.gallery.model.GalleryMedia
import de.luma.gallery.model.PhotoRecipe
import de.luma.gallery.model.PhotoExportOptions
import de.luma.gallery.model.VideoRecipe
import de.luma.gallery.model.EditorProject
import de.luma.gallery.model.EditorSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class GalleryState(
    val media: List<GalleryMedia> = emptyList(),
    val libraryRevision: Long = 0,
    val favorites: Set<String> = emptySet(),
    val archived: Set<String> = emptySet(),
    val settings: AppSettings = AppSettings(),
    val exportOptions: PhotoExportOptions = PhotoExportOptions(),
    val settingsLoading: Boolean = false,
    val settingsReady: Boolean = true,
    val settingsError: String? = null,
    val loading: Boolean = true,
    val exporting: Boolean = false,
    val exportSnapshot: de.luma.gallery.data.ExportSnapshot = de.luma.gallery.data.ExportSnapshot(),
    val error: String? = null,
    val projects: List<EditorProject> = emptyList(),
    val projectsLoading: Boolean = true,
    val projectsError: String? = null,
    val editor: EditorSession? = null,
    val savingProject: Boolean = false,
    val backupReview: de.luma.gallery.data.BackupReview? = null,
    val backupBusy: Boolean = false,
    val archiveReview: de.luma.gallery.data.ArchiveReview? = null,
    val sourceReplacement: SourceReplacement? = null,
    val replacingSource: Boolean = false,
    val trash: List<TrashMedia> = emptyList(),
    val trashLoading: Boolean = false,
    val trashError: String? = null,
    val trashAccessLimited: Boolean = false,
    val pendingMediaAction: PendingMediaAction? = null,
    val albums: List<VirtualAlbum> = emptyList(),
    val albumsLoading: Boolean = true,
    val albumsError: String? = null,
    val savingAlbum: Boolean = false,
    val importedReferences:Set<String> = emptySet(),
    val removingImports:Boolean = false,
    val duplicateReport:de.luma.gallery.model.DuplicateReport? = null,
    val duplicateScanning:Boolean = false,
    val backupSnapshot:de.luma.gallery.data.BackupSnapshot = de.luma.gallery.data.BackupSnapshot(),
    val duplicateTask:de.luma.gallery.data.AnalysisTask? = null,
    val moodAnalysis:de.luma.gallery.data.AnalysisSnapshot = de.luma.gallery.data.AnalysisSnapshot(),
    val duplicateComparing:Boolean = false,
    val duplicateDone:Int = 0,
    val duplicateTotal:Int = 0,
    val duplicateError:String? = null,
)

class GalleryViewModel @JvmOverloads constructor(application: Application,
    private val projectRepository: ProjectRepository = ProjectRepository(ProjectDatabase.get(application)),
    private val albumRepository: AlbumRepository = AlbumRepository(projectRepository.database),
    private val settingsRepository: SettingsRepository = SettingsRepository(application)) : AndroidViewModel(application) {
    var gallerySelection: Set<String> = emptySet()
    var galleryColumns: Int? = null
    var galleryDefaultColumns: Int? = null
    var albumColumns: Int? = null
    var albumDefaultColumns: Int? = null
    private val strings get() = de.luma.gallery.localization.localizedResources(getApplication<Application>())
    val repository = GalleryRepository(application)
    val trashRepository = TrashRepository(application)
    private val trashRetention = de.luma.gallery.data.TrashRetention(application)
    private var trashJob: Job? = null
    private var trashVisited = false
    private val projectMutex = Mutex()
    private val albumMutex = Mutex()
    private val mutableState = MutableStateFlow(GalleryState(importedReferences=repository.importedReferences(),favorites = repository.favorites(), archived = repository.archived(), settingsLoading = true, settingsReady = false))
    val state = mutableState.asStateFlow()
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = mutableMessages.asSharedFlow()
    private var loadJob: Job? = null
    private var openProjectJob: Job? = null

    private data class PreferenceWrite(val appearance: AppSettings? = null, val export: PhotoExportOptions? = null)
    private val preferenceWrites = Channel<PreferenceWrite>(Channel.UNLIMITED)
    private var preferencePending = 0
    private var settingsJob: Job? = null

    private var libraryObserver: de.luma.gallery.data.LibraryObserver? = null
    init {
        libraryObserver = de.luma.gallery.data.LibraryObserver(application) { reloadLibrary(true) }
        reloadSettings()
        viewModelScope.launch {
            for (write in preferenceWrites) {
                try {
                    write.appearance?.let { settingsRepository.saveAppearance(it) }
                    write.export?.let { settingsRepository.saveExport(it) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    mutableMessages.tryEmit(error.userMessage(strings, R.string.settings_save_failed))
                } finally { preferencePending-- }

                if (preferencePending == 0) {
                    try { val saved = settingsRepository.values.first()
                        mutableState.update { it.copy(settings = saved.appearance, exportOptions = saved.export) }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        settingsReadFailed()
                    }
                }
            }
        }
        viewModelScope.launch {
            try { de.luma.gallery.data.TransferController.inspect(application) } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableMessages.tryEmit(error.userMessage(strings, R.string.file_action_failed))
            }
            try { de.luma.gallery.data.ExportController.inspect(application) } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableMessages.tryEmit(error.userMessage(strings, R.string.export_failed))
            }
        }
        viewModelScope.launch {
            var lastEvent = 0L
            de.luma.gallery.data.ExportController.state.collect { snapshot ->
                mutableState.update { it.copy(exporting = snapshot.running, exportSnapshot = snapshot) }
                if (snapshot.event != lastEvent) {
                    if(snapshot.overwriting && (snapshot.fileReplaced || !snapshot.interrupted && snapshot.completed==snapshot.total)) mutableState.update {it.copy(libraryRevision=it.libraryRevision+1)}
                    lastEvent = snapshot.event; snapshot.message?.let(mutableMessages::tryEmit); refresh() }
            }
        }
        viewModelScope.launch {
            de.luma.gallery.data.AnalysisController.observe(application,de.luma.gallery.data.AnalysisKind.DUPLICATES).collect {snapshot->
                val task=snapshot.task
                mutableState.update {it.copy(duplicateTask=task,duplicateScanning=task?.active==true,duplicateReport=snapshot.report,
                    duplicateComparing=task?.comparing==true,duplicateDone=task?.done?:0,duplicateTotal=task?.total?:0,duplicateError=task?.error)}
            }
        }
        viewModelScope.launch {
            de.luma.gallery.data.AnalysisController.observe(application,de.luma.gallery.data.AnalysisKind.MOODS).collect {snapshot->mutableState.update {it.copy(moodAnalysis=snapshot)}}
        }
        viewModelScope.launch {
            var lastEvent=0L
            de.luma.gallery.data.BackupController.state.collect {snapshot->
                mutableState.update {it.copy(backupBusy=snapshot.running,backupSnapshot=snapshot,backupReview=snapshot.backup,archiveReview=snapshot.archive)}
                if(snapshot.event!=lastEvent) {lastEvent=snapshot.event;snapshot.message?.let(mutableMessages::tryEmit);refresh()}
            }
        }
        refresh()
        viewModelScope.launch {
            albumRepository.albums.catch {
                mutableState.update { state -> state.copy(albumsLoading = false, albumsError = strings.getString(R.string.album_load_failed)) }
            }.collect { albums -> mutableState.update { it.copy(albums = albums, albumsLoading = false, albumsError = null) } }
        }
        viewModelScope.launch {
            projectRepository.projects.catch {
                mutableState.update { state -> state.copy(projectsLoading = false, projectsError = strings.getString(R.string.project_load_failed)) }
            }.collect { projects -> mutableState.update { it.copy(projects = projects, projectsLoading = false, projectsError = null) } }
        }
    }

    fun refreshLocalizedErrors() {
        mutableState.update { it.copy(
            settingsError = it.settingsError?.let { strings.getString(R.string.settings_load_failed) },
            error = it.error?.let { strings.getString(R.string.media_load_failed) },
            projectsError = it.projectsError?.let { strings.getString(R.string.project_load_failed) },
            albumsError = it.albumsError?.let { strings.getString(R.string.album_load_failed) },
            trashError = it.trashError?.let { strings.getString(R.string.trash_load_failed) },
        ) }
    }

    fun createAlbum(name: String, ids: Set<String> = emptySet()) = albumWrite {
        albumRepository.create(name, ids)
        mutableMessages.emit(if (ids.isEmpty()) strings.getString(R.string.album_created) else strings.getString(R.string.album_created_with_media))
    }
    fun renameAlbum(id: String, name: String) = albumWrite { albumRepository.rename(id, name) }
    fun deleteAlbums(ids:Set<String>) = albumWrite {albumRepository.deleteMany(ids);mutableMessages.emit(strings.getString(R.string.album_removed))}
    fun deleteAlbum(id: String) = albumWrite {
        albumRepository.delete(id); mutableMessages.emit(strings.getString(R.string.album_removed))
    }
    fun addToAlbum(id: String, ids: Set<String>) = albumWrite {
        val count = albumRepository.add(id, ids)
        mutableMessages.emit(if (count == 0) strings.getString(R.string.media_already_assigned) else strings.getQuantityString(R.plurals.album_media_added, count, count))
    }
    fun removeFromAlbum(id: String, ids: Set<String>) = albumWrite {
        albumRepository.remove(id, ids); mutableMessages.emit(strings.getString(R.string.assignment_removed))
    }
    private fun albumWrite(operation: suspend () -> Unit) {
        if (state.value.savingAlbum) return
        mutableState.update { it.copy(savingAlbum = true) }
        viewModelScope.launch {
            try { albumMutex.withLock { operation() } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableMessages.emit(error.userMessage(strings, R.string.album_save_storage_failed))
            } finally { mutableState.update { it.copy(savingAlbum = false) } }
        }
    }

    fun beginEditor(item: GalleryMedia) {
        openProjectJob?.cancel()
        mutableState.update { it.copy(editor = EditorSession(UUID.randomUUID().toString(), item,
            photo = if (item.isVideo) null else repository.loadRecipe(item.id),
            video = if (item.isVideo) repository.loadVideoRecipe(item) else null)) }
    }

    fun closeEditor() { openProjectJob?.cancel(); mutableState.update { it.copy(editor = null) } }

    fun openProject(id: String) {
        openProjectJob?.cancel()
        openProjectJob = viewModelScope.launch {
            try {
                val project = projectMutex.withLock { projectRepository.find(id) } ?: error(strings.getString(R.string.project_not_found))
                if (project.video != null) {

                    if (project.video.includeSource) repository.checkSource(project.source)
                    val missingClip = project.video.extraClips.any { clip ->
                        try { repository.checkSource(clip.source); false }
                        catch (e: Exception) { if (e is CancellationException) throw e; true }
                    }
                    if (missingClip) mutableMessages.emit(strings.getString(R.string.video_missing_clip))
                }
                else repository.checkSource(project.source)
                mutableState.update { it.copy(editor = EditorSession(UUID.randomUUID().toString(), project.source, project.id, project.photo, project.video)) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableMessages.emit(if (error is ResourceFailure && error.messageRes == R.string.source_in_trash)
                    strings.getString(R.string.restore_project_source)
                    else strings.getString(R.string.project_source_unavailable))
            }
        }
    }

    fun photoDraft(session: EditorSession, recipe: PhotoRecipe) {
        mutableState.update { if (it.editor?.token == session.token) it.copy(editor = it.editor.copy(photo = recipe)) else it }
        if (session.projectId == null) repository.saveRecipe(session.source.id, recipe)
        else projectWrite { projectRepository.update(session.projectId, session.source, photo = recipe) }
    }

    fun videoDraft(session: EditorSession, recipe: VideoRecipe) {
        if (!recipe.isValid(session.source.durationMs)) return
        mutableState.update { if (it.editor?.token == session.token) it.copy(editor = it.editor.copy(video = recipe)) else it }
        if (session.projectId == null) repository.saveVideoRecipe(session.source.id, recipe)
        else projectWrite { projectRepository.update(session.projectId, session.source, video = recipe) }
    }

    fun saveProject(name: String, asCopy: Boolean, photo: PhotoRecipe? = null, video: VideoRecipe? = null) {
        val session = state.value.editor ?: return
        if (state.value.savingProject) return
        mutableState.update { it.copy(savingProject = true) }
        projectWrite {
            try {
                val id = projectRepository.save(session.source, name, photo, video, if (asCopy) null else session.projectId)
                mutableState.update { current -> if (current.editor?.token == session.token) current.copy(editor = current.editor.copy(projectId = id)) else current }
                mutableMessages.emit(strings.getString(R.string.project_saved))
            } finally { mutableState.update { it.copy(savingProject = false) } }
        }
    }

    fun saveMediaArchive(uri:Uri) = backupTask {de.luma.gallery.data.BackupController.start(getApplication(),de.luma.gallery.data.BackupOperation.ARCHIVE_WRITE,uri,state.value.media)}
    fun readMediaArchive(uri:Uri) = backupTask {de.luma.gallery.data.BackupController.start(getApplication(),de.luma.gallery.data.BackupOperation.ARCHIVE_READ,uri)}
    fun dismissArchive() = backupTask {de.luma.gallery.data.BackupController.dismiss(getApplication())}
    fun restoreMediaArchive(tree:Uri) = backupTask {
        val review=state.value.archiveReview ?: return@backupTask
        de.luma.gallery.data.BackupController.start(getApplication(),de.luma.gallery.data.BackupOperation.ARCHIVE_RESTORE,tree,archive=review)
    }
    fun saveBackup(uri:Uri) = backupTask {de.luma.gallery.data.BackupController.start(getApplication(),de.luma.gallery.data.BackupOperation.PROJECT_WRITE,uri)}
    fun readBackup(uri:Uri) = backupTask {de.luma.gallery.data.BackupController.start(getApplication(),de.luma.gallery.data.BackupOperation.PROJECT_READ,uri)}
    fun dismissBackup() = backupTask {de.luma.gallery.data.BackupController.dismiss(getApplication())}
    fun restoreBackup() = backupTask {
        val review=state.value.backupReview ?: return@backupTask
        de.luma.gallery.data.BackupController.start(getApplication(),de.luma.gallery.data.BackupOperation.PROJECT_RESTORE,review=review)
    }
    fun cancelBackup() {de.luma.gallery.data.BackupController.pause(getApplication())}
    fun resumeBackup() = backupTask {de.luma.gallery.data.BackupController.resume(getApplication())}
    fun discardBackup() = backupTask {de.luma.gallery.data.BackupController.dismiss(getApplication())}
    private fun backupTask(block:suspend ()->Unit)=viewModelScope.launch {
        try {block()} catch(error:Exception) {if(error is CancellationException) throw error;mutableMessages.emit(error.userMessage(strings,R.string.backup_failed))}
    }

    fun prepareSourceReplacement(id: String, uri: Uri) = viewModelScope.launch {
        if (state.value.replacingSource) return@launch
        mutableState.update { it.copy(replacingSource = true, sourceReplacement = null) }
        try {
            val project = projectMutex.withLock { projectRepository.find(id) } ?: error("missing")
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val source = repository.inspectSource(uri)
            requireMessage(source.isVideo == project.source.isVideo, R.string.source_wrong_kind)
            project.video?.let { requireMessage(it.isValid(source.durationMs), R.string.source_too_short) }
            val dimensions = SourceProbe.inspect(getApplication(), source)
            val old = runCatching { SourceProbe.inspect(getApplication(), project.source) }.getOrElse { error ->
                if (error is CancellationException) throw error; null }
            mutableState.update { it.copy(sourceReplacement = SourceReplacement(project, source, old, dimensions)) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableMessages.emit(error.userMessage(strings, R.string.source_replace_failed))
        } finally { mutableState.update { it.copy(replacingSource = false) } }
    }
    fun cancelSourceReplacement() { mutableState.update { it.copy(sourceReplacement = null) } }
    fun confirmSourceReplacement() {
        val review = state.value.sourceReplacement ?: return
        if (state.value.replacingSource) return
        mutableState.update { it.copy(replacingSource = true) }
        projectWrite {
            try {
                val fresh = repository.inspectSource(review.source.uri)
                requireMessage(fresh.copy(album = review.source.album) == review.source && SourceProbe.inspect(getApplication(), fresh) == review.replacementInfo, R.string.source_review_changed)
                projectRepository.replaceSource(review.project, fresh)
                mutableState.update { current -> current.copy(sourceReplacement = null,
                    editor = current.editor?.let { if (it.projectId == review.project.id)
                        it.copy(token = UUID.randomUUID().toString(), source = review.source) else it }) }
                mutableMessages.emit(strings.getString(R.string.source_replaced))
            } finally { mutableState.update { it.copy(replacingSource = false, sourceReplacement = null) } }
        }
    }

    fun renameProject(id: String, name: String) = projectWrite { projectRepository.rename(id, name) }
    fun deleteProject(id: String) = projectWrite { projectRepository.delete(id); mutableMessages.emit(strings.getString(R.string.project_removed)) }

    private fun projectWrite(operation: suspend () -> Unit) = viewModelScope.launch {
        try { projectMutex.withLock { operation() } }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableMessages.emit(error.userMessage(strings, R.string.project_save_failed))
        }
    }

    fun refresh() = reloadLibrary(false)

    private fun reloadLibrary(invalidate: Boolean) {
        if (trashVisited) refreshTrash()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val library = repository.loadLibrary()
                mutableState.update { it.copy(media = library, importedReferences=repository.importedReferences(),loading = false, libraryRevision = it.libraryRevision + if(invalidate || library != it.media) 1 else 0) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loading = false, error = strings.getString(R.string.media_load_failed)) }
            }
        }
    }

    fun refreshTrash() {
        trashVisited = true
        if (Build.VERSION.SDK_INT < 30) return
        trashJob?.cancel()
        trashJob = viewModelScope.launch {
            mutableState.update { it.copy(trashLoading = true, trashError = null) }
            try {
                val snapshot = trashRepository.load()
                mutableState.update { it.copy(trash = snapshot.items, trashLoading = false,
                    trashAccessLimited = snapshot.accessLimited) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(trashLoading = false, trashError = strings.getString(R.string.trash_load_failed)) }
            }
        }
    }

    fun beginLegacyDelete(items: List<GalleryMedia>) {
        check(Build.VERSION.SDK_INT==29 && state.value.pendingMediaAction==null)
        mutableState.update {it.copy(pendingMediaAction=checkedMediaAction(MediaAction.DELETE,items))}
    }
    suspend fun deleteLegacyNext(): android.content.IntentSender? {
        check(Build.VERSION.SDK_INT==29)
        try {
            while(state.value.pendingMediaAction?.items?.isNotEmpty()==true) {
                val pending=state.value.pendingMediaAction!!
                val item=pending.items.first()
                try {
                    val removed=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {getApplication<Application>().contentResolver.delete(item.uri,null,null)}
                    requireMessage(removed>0,R.string.system_action_unavailable)
                    mutableState.update {it.copy(pendingMediaAction=pending.copy(items=pending.items.drop(1)))}
                } catch(e:android.app.RecoverableSecurityException) {return e.userAction.actionIntent.intentSender}
            }
            completeMediaAction(true)
        } catch(e:Exception) {
            if(e is CancellationException) throw e
            completeMediaAction(false);mutableMessages.emit(e.userMessage(strings,R.string.system_action_unavailable))
        }
        return null
    }
    fun prepareMediaAction(action: MediaAction, items: List<GalleryMedia>): android.content.IntentSender? {
        if (state.value.pendingMediaAction != null || state.value.exporting) return null
        return try {
            val pending = checkedMediaAction(action, items)
            val sender = trashRepository.request(action, pending.items)
            mutableState.update { it.copy(pendingMediaAction = pending) }
            sender
        } catch (error: Exception) {
            mutableMessages.tryEmit(error.userMessage(strings, R.string.system_action_unavailable))
            null
        }
    }

    fun completeMediaAction(success: Boolean, launchFailed: Boolean = false) {
        val pending = state.value.pendingMediaAction
        mutableState.update { it.copy(pendingMediaAction = null) }
        if(success && pending!=null) {
            if(pending.action==MediaAction.TRASH && state.value.settings.trashDays>0) trashRetention.record(pending.items,state.value.settings.trashDays)
            else trashRetention.forget(pending.items)
        }
        refresh()
        if (launchFailed) mutableMessages.tryEmit(strings.getString(R.string.android_confirmation_failed))
        else if (pending != null) mutableMessages.tryEmit(if (success) strings.getString(R.string.system_action_completed) else strings.getString(R.string.action_canceled))
    }

    fun dueTrash(): List<GalleryMedia> = trashRetention.due(state.value.trash)

    fun reloadSettings() {
        settingsJob?.cancel()
        mutableState.update { it.copy(settingsLoading = true, settingsError = null) }
        settingsJob = viewModelScope.launch {
            settingsRepository.values.catch { error ->
                if (error is CancellationException) throw error
                settingsReadFailed()
            }.collect { saved ->
                mutableState.update { if (preferencePending == 0) it.copy(settings = saved.appearance, exportOptions = saved.export,
                    settingsLoading = false, settingsReady = true, settingsError = null)
                    else it.copy(settingsLoading = false, settingsReady = true, settingsError = null) }
            }
        }
    }

    private fun settingsReadFailed() { mutableState.update { it.copy(settingsLoading = false, settingsReady = false,
        settingsError = strings.getString(R.string.settings_load_failed)) } }

    fun settings(settings: AppSettings) {
        if (!state.value.settingsReady) return
        preferencePending++
        mutableState.update { it.copy(settings = settings) }
        preferenceWrites.trySend(PreferenceWrite(appearance = settings))
    }

    fun archive(ids: Set<String>, archived: Boolean) {
        val value = if(archived) state.value.archived + ids else state.value.archived - ids
        repository.saveArchived(value); mutableState.update { it.copy(archived = value) }
    }
    fun favoriteAll(ids: Set<String>) {
        val value = if(ids.all { it in state.value.favorites }) state.value.favorites - ids else state.value.favorites + ids
        repository.saveFavorites(value); mutableState.update { it.copy(favorites=value) }
    }
    fun startSelectionTransfer(items: List<GalleryMedia>, target: Uri, move: Boolean) = viewModelScope.launch {
        try { de.luma.gallery.data.TransferController.startSelection(getApplication(),items,target,move) }
        catch(e: Exception) { if(e is CancellationException) throw e; mutableMessages.emit(e.userMessage(strings,R.string.file_action_failed)) }
    }
    fun favorite(id: String) {
        val favorites = state.value.favorites.let { if (id in it) it - id else it + id }
        repository.saveFavorites(favorites)
        mutableState.update { it.copy(favorites = favorites) }
    }

    fun forgetImportedReferences(ids:Set<String>) {
        if(state.value.removingImports) return
        mutableState.update {it.copy(removingImports=true)}
        viewModelScope.launch {
            try {
                repository.forgetReferences(ids)
                mutableState.update {it.copy(importedReferences=repository.importedReferences(),media=it.media.filterNot {item->item.isImported && item.id in ids})}
                refresh()
                mutableMessages.emit(strings.getString(R.string.import_references_removed))
            } catch(e:Exception) {
                if(e is CancellationException) throw e
                mutableMessages.emit(e.userMessage(strings,R.string.file_action_failed))
            } finally {mutableState.update {it.copy(removingImports=false)}}
        }
    }

    fun import(uris: List<Uri>) = viewModelScope.launch {
        try {
            repository.import(uris)
            refresh()
            mutableMessages.emit(strings.getString(R.string.media_added))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableMessages.emit(strings.getString(R.string.import_failed))
        }
    }

    fun scanDuplicates(similar:Boolean,includeArchived:Boolean) = analysisTask {
        val sources=state.value.media.filter {!it.isVideo && !it.isImported && de.luma.gallery.model.canManageMedia(it) && (includeArchived || it.id !in state.value.archived)}
        de.luma.gallery.data.AnalysisController.start(getApplication(),de.luma.gallery.data.AnalysisKind.DUPLICATES,sources,similar,includeArchived)
    }
    fun cancelDuplicateScan() = analysisTask {de.luma.gallery.data.AnalysisController.pause(getApplication(),de.luma.gallery.data.AnalysisKind.DUPLICATES)}
    fun resumeDuplicateScan() = analysisTask {de.luma.gallery.data.AnalysisController.resume(getApplication(),de.luma.gallery.data.AnalysisKind.DUPLICATES)}
    fun resetDuplicateResults() = analysisTask {de.luma.gallery.data.AnalysisController.reset(getApplication(),de.luma.gallery.data.AnalysisKind.DUPLICATES)}
    fun scanMoods(media:List<GalleryMedia>) = analysisTask {de.luma.gallery.data.AnalysisController.start(getApplication(),de.luma.gallery.data.AnalysisKind.MOODS,media)}
    fun resumeMoods() = analysisTask {de.luma.gallery.data.AnalysisController.resume(getApplication(),de.luma.gallery.data.AnalysisKind.MOODS)}
    fun pauseMoods() = analysisTask {de.luma.gallery.data.AnalysisController.pause(getApplication(),de.luma.gallery.data.AnalysisKind.MOODS)}
    private fun analysisTask(block:suspend ()->Unit) = viewModelScope.launch {
        try {block()} catch(e:Exception) {if(e is CancellationException) throw e;mutableMessages.emit(e.userMessage(strings,R.string.background_task_failed))}
    }
    suspend fun verifyDuplicateRemoval(ids:Set<String>):List<GalleryMedia>? {
        return try {
            val report=state.value.duplicateReport ?: return null
            val items=de.luma.gallery.data.DuplicateScanner.verify(getApplication(),report,ids)
            require(items.all {de.luma.gallery.model.canManageMedia(it)})
            items
        } catch(e:Exception) {if(e is CancellationException) throw e;mutableState.update {it.copy(duplicateError=strings.getString(R.string.duplicates_changed))};mutableMessages.emit(strings.getString(R.string.duplicates_changed));null}
    }

    fun exportPhoto(item: GalleryMedia, recipe: PhotoRecipe, options: PhotoExportOptions) = exportPhotos(listOf(item), recipe, options)
    fun exportPhotos(items: List<GalleryMedia>, recipe: PhotoRecipe, options: PhotoExportOptions) = startPhotoExport(items,recipe,options,false)
    private fun startPhotoExport(items:List<GalleryMedia>,recipe:PhotoRecipe,options:PhotoExportOptions,overwrite:Boolean) {
        if (!state.value.settingsReady || state.value.exporting) return
        preferencePending++
        mutableState.update { it.copy(exportOptions = options) }
        preferenceWrites.trySend(PreferenceWrite(export = options))
        exportOperation { de.luma.gallery.data.ExportController.start(getApplication(), de.luma.gallery.data.ExportRequest(items = items, photo = recipe, options = options,overwrite=overwrite)) }
    }
    fun exportCollage(items:List<GalleryMedia>,spec:de.luma.gallery.model.CollageSpec,options:PhotoExportOptions) {
        if(!state.value.settingsReady || state.value.exporting) return
        exportOperation {de.luma.gallery.data.ExportController.start(getApplication(),de.luma.gallery.data.ExportRequest(items=items,options=options.copy(metadata=de.luma.gallery.model.MetadataMode.NONE),collage=spec))}
    }
    fun overwritePhoto(item:GalleryMedia,recipe:PhotoRecipe,options:PhotoExportOptions) = startPhotoExport(listOf(item),recipe,options,true)
    fun overwriteVideo(item:GalleryMedia,recipe:VideoRecipe) = exportOperation {de.luma.gallery.data.ExportController.start(getApplication(),de.luma.gallery.data.ExportRequest(items=listOf(item),video=recipe,overwrite=true))}
    fun exportVideo(item: GalleryMedia, recipe: VideoRecipe) {
        exportOperation { de.luma.gallery.data.ExportController.start(getApplication(), de.luma.gallery.data.ExportRequest(items = listOf(item), video = recipe)) }
    }
    fun resumeExport() = exportOperation { de.luma.gallery.data.ExportController.resume(getApplication()) }
    fun discardExport() = exportOperation { de.luma.gallery.data.ExportController.discard(getApplication()) }
    private fun exportOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableMessages.emit(error.userMessage(strings, R.string.export_failed))
            }
        }
    }

    override fun onCleared() { libraryObserver?.close(); super.onCleared() }

    fun cancelExport() { de.luma.gallery.data.ExportController.cancel(getApplication()) }
}
