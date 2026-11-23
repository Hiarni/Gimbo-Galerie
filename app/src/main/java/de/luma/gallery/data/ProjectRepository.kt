package de.luma.gallery.data

import de.luma.gallery.localization.*
import de.luma.gallery.R
import androidx.core.net.toUri
import androidx.room.withTransaction
import de.luma.gallery.model.*
import kotlinx.coroutines.flow.map
import java.util.UUID

class ProjectRepository(internal val database: ProjectDatabase, private val now: () -> Long = System::currentTimeMillis) {
    private val dao = database.projects()
    val projects = dao.observeAll().map { rows -> rows.map { it.toProject() } }

    suspend fun find(id: String): EditorProject? = dao.find(id)?.toProject()

    suspend fun save(source: GalleryMedia, name: String, photo: PhotoRecipe? = null,
        video: VideoRecipe? = null, id: String? = null, restoredId:String?=null): String = database.withTransaction {
        val cleanName = projectName(name)
        val recipe = encoded(source, photo, video)
        if (id == null) {
            val newId = restoredId ?: UUID.randomUUID().toString()
            val time = now()
            dao.insert(ProjectEntity(newId, cleanName, source.id, source.uri.toString(), source.name, source.album,
                source.timestamp, source.isVideo, source.durationMs, source.size, source.isDemo, recipe, time, time))
            newId
        } else {
            val existing = dao.find(id) ?: messageError(R.string.project_missing)
            requireMessage(existing.sourceId == source.id && existing.isVideo == source.isVideo, R.string.project_source_mismatch)
            check(dao.rename(id, cleanName, now()) == 1)
            check(dao.updateRecipe(id, recipe, now()) == 1)
            id
        }
    }

    suspend fun update(id: String, source: GalleryMedia, photo: PhotoRecipe? = null, video: VideoRecipe? = null) {
        database.withTransaction {
            val existing = dao.find(id) ?: messageError(R.string.project_missing)
            require(existing.sourceId == source.id && existing.isVideo == source.isVideo)
            check(dao.updateRecipe(id, encoded(source, photo, video), now()) == 1)
        }
    }

    suspend fun replaceSource(reviewed: EditorProject, replacement: GalleryMedia) = database.withTransaction {
        val row = dao.find(reviewed.id) ?: messageError(R.string.project_missing)
        requireMessage(row.toProject() == reviewed, R.string.source_review_changed)
        requireMessage(reviewed.source.isVideo == replacement.isVideo, R.string.source_wrong_kind)
        reviewed.video?.let { requireMessage(it.isValid(replacement.durationMs), R.string.source_too_short) }
        check(dao.replace(row.copy(sourceId = replacement.id, sourceUri = replacement.uri.toString(), sourceName = replacement.name,
            sourceAlbum = replacement.album, sourceTimestamp = replacement.timestamp, durationMs = replacement.durationMs,
            size = replacement.size, isDemo = replacement.isDemo, updatedAt = now())) == 1)
    }

    suspend fun rename(id: String, name: String) { check(dao.rename(id, projectName(name), now()) == 1) }
    suspend fun delete(id: String) { dao.delete(id) }

    private fun encoded(source: GalleryMedia, photo: PhotoRecipe?, video: VideoRecipe?): String {
        if (source.isVideo) {
            requireMessage(photo == null && video != null && video.isValid(source.durationMs), R.string.invalid_video_clip)
            return RecipeJson.video(video)
        }
        require(photo != null && video == null)
        return RecipeJson.photo(photo)
    }

    private fun ProjectEntity.toProject(): EditorProject {
        val media = GalleryMedia(sourceId, sourceUri.toUri(), sourceName, sourceAlbum, sourceTimestamp, isVideo, durationMs, size, isDemo)
        return EditorProject(id, name, media, if (isVideo) null else RecipeJson.readPhoto(recipeJson),
            if (isVideo) RecipeJson.readVideo(recipeJson) else null, createdAt, updatedAt)
    }
}
