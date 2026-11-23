package de.luma.gallery.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*

data class ArchiveMedia(val source: GalleryMedia, val entry: String, val mime: String, val bytes: Long, val hash: String)
data class ArchiveReview(val file: File, val backup: BackupReview, val media: List<ArchiveMedia>) {
    val totalBytes get() = media.sumOf { it.bytes }
}

class MediaArchive(private val context: Context, private val backups: ProjectBackup) {
    private val resolver = context.contentResolver
    suspend fun write(uri: Uri, library: List<GalleryMedia>,snapshot:String?=null,progress:(Int,Int)->Unit={_,_->}) = withContext(Dispatchers.IO) {
        val review = ProjectBackup.decode(snapshot ?: backups.snapshot())
        val referenced = review.projects.flatMap { p -> listOf(p.source) + p.video?.extraClips.orEmpty().map { it.source } +
            listOfNotNull(p.video?.music?.let { GalleryMedia(it.uri, Uri.parse(it.uri), it.name, "", 0, durationMs = it.durationMs) }) +
            p.video?.audioTracks.orEmpty().map { GalleryMedia(it.music.uri, Uri.parse(it.music.uri), it.music.name, "", 0, durationMs = it.music.durationMs) } +
            p.video?.overlays.orEmpty().mapNotNull { o -> o.imageUri?.let { GalleryMedia(it,Uri.parse(it),"Overlay.png","",0) } } }
        val sources = (library + referenced).distinctBy { it.uri.toString() }
        requireMessage(sources.size <= MAX_FILES, R.string.backup_invalid)
        var total = 0L
        val entries = mutableListOf<ArchiveMedia>()
        resolver.openOutputStream(uri, "wt")?.use { raw -> ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
            zip.setLevel(0)
            for ((index, source) in sources.withIndex()) {
                ensureActive()
                val entry = "media/$index"
                val mime = if (source.isDemo) "image/jpeg" else resolver.getType(source.uri).orEmpty()
                requireMessage(mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/"), R.string.backup_invalid)
                zip.putNextEntry(ZipEntry(entry))
                val result = open(source).use { input -> copy(input, zip, MAX_FILE) }
                zip.closeEntry(); total += result.first
                requireMessage(total <= MAX_TOTAL, R.string.backup_invalid)
                entries += ArchiveMedia(source, entry, mime, result.first, result.second)
                progress(index+1,sources.size)
            }
            val manifest = JSONObject().put("format", "luma-media").put("version", 1).put("backup", JSONObject(ProjectBackup.encode(review)))
                .put("media", JSONArray(entries.map { e -> JSONObject().put("source", RecipeJson.media(e.source)).put("entry", e.entry)
                    .put("mime", e.mime).put("bytes", e.bytes).put("sha256", e.hash) })).toString().toByteArray()
            requireMessage(manifest.size <= MAX_MANIFEST, R.string.backup_invalid)
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
        } } ?: throw IOException()
    }
    suspend fun read(uri: Uri,file:File=File(context.cacheDir,"luma_backup_${UUID.randomUUID()}.zip")): ArchiveReview = withContext(Dispatchers.IO) {
        if(file.isFile) {
            try {return@withContext validate(file)} catch(e:CancellationException) {throw e} catch(_:Exception) {file.delete()}
        }
        try {
            resolver.openInputStream(uri)?.use { input -> file.outputStream().use { copy(input, it, MAX_TOTAL + MAX_MANIFEST + 64L * MAX_FILES) } } ?: throw IOException()
            validate(file)
        } catch (e: Throwable) { file.delete(); throw e }
    }
    internal suspend fun validate(file: File): ArchiveReview = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            requireMessage(zip.size() in 1..MAX_FILES + 1, R.string.backup_invalid)
            val names = zip.entries().toList().map { it.name }
            requireMessage(names.size in 1..MAX_FILES + 1 && names.distinct().size == names.size && names.all { it == "manifest.json" || it.matches(Regex("media/[0-9]+")) }, R.string.backup_invalid)
            val manifest = requireNotNull(zip.getEntry("manifest.json"))
            val bytes = zip.getInputStream(manifest).use { boundedBytes(it, MAX_MANIFEST) }
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            requireMessage(json.getString("format") == "luma-media" && json.getInt("version") == 1, R.string.backup_invalid)
            val backup = ProjectBackup.decode(json.getJSONObject("backup").toString())
            val media = json.getJSONArray("media"); requireMessage(media.length() <= MAX_FILES && names.size == media.length() + 1, R.string.backup_invalid)
            var total = 0L
            val entries = (0 until media.length()).map { index ->
                ensureActive()
                val m = media.getJSONObject(index); val entry = m.getString("entry")
                val size = m.getLong("bytes"); val hash = m.getString("sha256"); val mime = m.getString("mime")
                requireMessage(entry == "media/$index" && size in 0..MAX_FILE && hash.matches(Regex("[a-f0-9]{64}")) && mime.length <= 256 &&
                    (mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")), R.string.backup_invalid)
                total += size; requireMessage(total <= MAX_TOTAL, R.string.backup_invalid)
                val result = zip.getInputStream(requireNotNull(zip.getEntry(entry))).use { copy(it, null, size) }
                requireMessage(result.first == size && result.second == hash, R.string.backup_invalid)
                ArchiveMedia(RecipeJson.readMedia(m.getJSONObject("source")), entry, mime, size, hash)
            }
            requireMessage(entries.map { it.source.uri.toString() }.distinct().size == entries.size, R.string.backup_invalid)
            val refs = backup.projects.flatMap { p -> listOf(p.source.uri.toString()) + p.video?.extraClips.orEmpty().map { it.source.uri.toString() } + listOfNotNull(p.video?.music?.uri) + p.video?.audioTracks.orEmpty().map { it.music.uri } + p.video?.overlays.orEmpty().mapNotNull { it.imageUri } }
            requireMessage(refs.all { ref -> entries.any { it.source.uri.toString() == ref } }, R.string.backup_invalid)
            ArchiveReview(file, backup, entries)
        }
    }
    suspend fun restore(review:ArchiveReview,tree:Uri,journal:ArchiveRestoreJournal?=null,progress:(Int,Int)->Unit={_,_->}):List<Uri> = restoreChecked(validate(review.file),tree,journal,progress)
    internal suspend fun restoreFile(file:File,tree:Uri,journal:ArchiveRestoreJournal,progress:(Int,Int)->Unit):List<Uri> = restoreChecked(validate(file),tree,journal,progress)
    private suspend fun restoreChecked(checked:ArchiveReview,tree:Uri,journal:ArchiveRestoreJournal?,progress:(Int,Int)->Unit):List<Uri> = withContext(Dispatchers.IO) {
        val tools = DocumentTools(context)
        val parent = tools.root(tree)
        val name=journal?.folderName ?: DocumentTools.freeName("Gimbo-Galerie-Restore",tools.children(parent).map {it.name.lowercase(java.util.Locale.ROOT)}.toSet())
        val folder=journal?.folder?.let(Uri::parse) ?: tools.children(parent).firstOrNull {journal!=null && it.directory && (it.name==name || it.name=="Gimbo-Restore-${journal.id}")}?.uri ?: tools.createFolder(parent,name)
        journal?.folder=folder.toString()
        val remapped = linkedMapOf<String, GalleryMedia>()
        val ids = mutableMapOf<String, String>()
        var group = folder
        val reservedNames = mutableSetOf<String>()
        ZipFile(checked.file).use { zip -> for ((index, m) in checked.media.withIndex()) {
            ensureActive()
            if (index % 1000 == 0) {
                val groupName=(index / 1000 + 1).toString().padStart(4,'0')
                group=tools.children(folder).firstOrNull {journal!=null && it.directory && it.name==groupName}?.uri ?: tools.createFolder(folder,groupName)
                reservedNames.clear()
            }
            val safeName = runCatching { documentName(m.source.name) }.getOrDefault("Media")
            val filename=journal?.name(index) ?: DocumentTools.freeName(safeName,reservedNames)
            journal?.plan(index,filename)
            val target=journal?.target(index)?.let(Uri::parse) ?: tools.children(group).firstOrNull {journal!=null && !it.directory && it.name==filename}?.uri ?: requireNotNull(DocumentsContract.createDocument(resolver,group,m.mime,filename))
            journal?.target(index,target.toString())
            var verified = false
            try {
                val alreadyVerified=journal!=null && runCatching {tools.fingerprint(target)==DocumentTools.Fingerprint(m.bytes,m.hash)}.getOrDefault(false)
                if(!alreadyVerified) zip.getInputStream(zip.getEntry(m.entry)).use { input -> resolver.openOutputStream(target, "wt")?.use { output ->
                    val result = copy(input, output, m.bytes)
                    requireMessage(result.first == m.bytes && result.second == m.hash, R.string.backup_invalid)
                } ?: throw IOException() }
                requireMessage(tools.fingerprint(target) == DocumentTools.Fingerprint(m.bytes, m.hash), R.string.copy_verification_failed)
                verified = true
                val restored = m.source.copy(id = target.toString(), uri = target, name = tools.describe(target).name, size = m.bytes, isDemo = false)
                reservedNames += restored.name.lowercase(java.util.Locale.ROOT)
                remapped[m.source.uri.toString()] = restored; ids[m.source.id] = restored.id
                progress(index+1,checked.media.size)
            } finally { if (!verified && journal==null) withContext(NonCancellable) { runCatching { DocumentsContract.deleteDocument(resolver, target) } } }
        } }
        val projects = checked.backup.projects.map { p -> p.copy(source = remapped.getValue(p.source.uri.toString()),
            video = p.video?.let { v -> v.copy(extraClips = v.extraClips.map { it.copy(source = remapped.getValue(it.source.uri.toString())) },
                music = v.music?.let { it.copy(uri = remapped.getValue(it.uri).uri.toString()) },
                audioTracks = v.audioTracks.map { t -> t.copy(music = t.music.copy(uri = remapped.getValue(t.music.uri).uri.toString())) },
                overlays = v.overlays.map { o -> o.copy(imageUri = o.imageUri?.let { remapped.getValue(it).uri.toString() }) }) }) }
        for (project in checked.backup.projects) {
            val sources = listOf(project.source) + project.video?.extraClips.orEmpty().map { it.source }
            for (source in sources) ids.putIfAbsent(source.id, remapped.getValue(source.uri.toString()).id)
        }
        val albums = checked.backup.albums.map { a -> a.copy(mediaIds = a.mediaIds.mapNotNull { ids[it] ?: remapped[it]?.id }.toSet()) }
        val uris = checked.media.filter { it.mime.startsWith("image/") || it.mime.startsWith("video/") }.map { remapped.getValue(it.source.uri.toString()).uri }
        GalleryRepository(context).registerGranted(uris)
        backups.restore(BackupReview(projects, albums),journal?.id)
        uris
    }
    private fun open(source: GalleryMedia): InputStream = if (source.isDemo) context.assets.open(source.uri.path!!.removePrefix("/android_asset/")) else resolver.openInputStream(source.uri) ?: throw IOException()
    private suspend fun copy(input: InputStream, output: OutputStream?, limit: Long): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536); var total = 0L
        while (true) { currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break
            total += count; requireMessage(total <= limit, R.string.backup_invalid); output?.write(buffer, 0, count); digest.update(buffer, 0, count) }
        return total to digest.digest().joinToString("") { "%02x".format(it) }
    }
    private suspend fun boundedBytes(input: InputStream, limit: Int): ByteArray { val out = ByteArrayOutputStream(); copy(input, out, limit.toLong()); return out.toByteArray() }
    companion object { const val MAX_FILES = 100_000; const val MAX_MANIFEST = 32 * 1024 * 1024; const val MAX_FILE = 20L * 1024 * 1024 * 1024; const val MAX_TOTAL = 100L * 1024 * 1024 * 1024 }
}
