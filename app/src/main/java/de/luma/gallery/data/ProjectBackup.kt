package de.luma.gallery.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.room.withTransaction
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import de.luma.gallery.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID


data class BackupReview(val projects: List<EditorProject>, val albums: List<VirtualAlbum>)

class ProjectBackup(private val database: ProjectDatabase) {
    suspend fun snapshot(): String = database.withTransaction {
        encode(BackupReview(ProjectRepository(database).projects.first(), AlbumRepository(database).albums.first()))
    }

    suspend fun restore(review: BackupReview,token:String?=null) = database.withTransaction {

        val checked = decode(encode(review))
        val projects = ProjectRepository(database)
        for ((index,project) in checked.projects.withIndex()) {
            currentCoroutineContext().ensureActive()
            val id=token?.let {UUID.nameUUIDFromBytes("$it:project:$index".toByteArray()).toString()}
            if(id==null || database.projects().find(id)==null) projects.save(project.source, project.name, project.photo, project.video,restoredId=id)
        }
        val albums = AlbumRepository(database)
        for ((index,album) in checked.albums.withIndex()) {
            val id=token?.let {UUID.nameUUIDFromBytes("$it:album:$index".toByteArray()).toString()}
            if(id!=null && database.albums().find(id)!=null) continue
            var name = album.name
            var suffix = 2
            while (database.albums().idByName(albumNameKey(name)) != null) {
                name = album.name.take(70) + " (${suffix++})"
            }
            albums.create(name, album.mediaIds,restoredId=id)
        }
    }

    suspend fun write(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val bytes = snapshot().toByteArray(Charsets.UTF_8)
        requireMessage(bytes.size <= MAX_BYTES, R.string.backup_invalid)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw java.io.IOException()
    }
    suspend fun read(context: Context, uri: Uri): BackupReview = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->

            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                requireMessage(out.size() + count <= MAX_BYTES, R.string.backup_invalid)
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } ?: throw java.io.IOException()
        decode(bytes.toString(Charsets.UTF_8))
    }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        fun encode(review: BackupReview): String = JSONObject().put("format", "luma-projects").put("version", 1)
            .put("projects", JSONArray(review.projects.map { p -> JSONObject().put("name", p.name)
                .put("source", JSONObject().put("id", p.source.id).put("uri", p.source.uri.toString()).put("name", p.source.name)
                    .put("album", p.source.album).put("timestamp", p.source.timestamp).put("video", p.source.isVideo)
                    .put("duration", p.source.durationMs).put("size", p.source.size).put("demo", p.source.isDemo))
                .put("recipe", if (p.source.isVideo) RecipeJson.video(requireNotNull(p.video)) else RecipeJson.photo(requireNotNull(p.photo))) }))
            .put("albums", JSONArray(review.albums.map { a -> JSONObject().put("name", a.name).put("members", JSONArray(a.mediaIds.toList())) })).toString()

        fun decode(value: String): BackupReview {
            requireMessage(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, R.string.backup_invalid)
            val json = JSONObject(value)
            requireMessage(json.getString("format") == "luma-projects" && json.getInt("version") == 1, R.string.backup_invalid)
            val projects = json.getJSONArray("projects"); val albums = json.getJSONArray("albums")
            requireMessage(projects.length() <= 1000 && albums.length() <= 1000, R.string.backup_invalid)
            var totalMembers = 0
            return BackupReview((0 until projects.length()).map { index ->
                val p = projects.getJSONObject(index); val s = p.getJSONObject("source")
                val uri = s.getString("uri").toUri()
                val demo = s.getBoolean("demo")
                requireMessage(uri.scheme == "content" || uri.scheme == "file" || (demo && uri.toString().startsWith("file:///android_asset/demo/")), R.string.backup_invalid)
                fun text(key: String) = s.getString(key).also { requireMessage(it.length <= 4096, R.string.backup_invalid) }
                val source = GalleryMedia(text("id"), uri, text("name"), text("album"), s.getLong("timestamp"), s.getBoolean("video"), s.getLong("duration"), s.getLong("size"), demo)
                requireMessage(source.id.isNotBlank() && source.durationMs >= 0 && source.size >= 0, R.string.backup_invalid)
                val photo = if (source.isVideo) null else RecipeJson.readPhoto(p.getString("recipe"))
                val video = if (source.isVideo) RecipeJson.readVideo(p.getString("recipe")) else null
                requireMessage(video == null || video.isValid(source.durationMs), R.string.backup_invalid)
                EditorProject(UUID.randomUUID().toString(), projectName(p.getString("name")), source, photo, video, 0, 0)
            }, (0 until albums.length()).map { index ->
                val a = albums.getJSONObject(index); val members = a.getJSONArray("members")
                totalMembers += members.length()
                requireMessage(totalMembers <= 100_000, R.string.backup_invalid)
                val ids = (0 until members.length()).map { members.getString(it).also { id ->
                    requireMessage(id.isNotBlank() && id.length <= 4096, R.string.backup_invalid)
                } }.toSet()
                VirtualAlbum(UUID.randomUUID().toString(), albumName(a.getString("name")), ids, 0, 0)
            })
        }
    }
}
