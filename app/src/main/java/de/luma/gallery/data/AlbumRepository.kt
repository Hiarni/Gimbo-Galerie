package de.luma.gallery.data

import de.luma.gallery.localization.*
import de.luma.gallery.R
import androidx.room.withTransaction
import de.luma.gallery.model.*
import kotlinx.coroutines.flow.map
import java.util.UUID

class AlbumRepository(private val database: ProjectDatabase, private val now: () -> Long = System::currentTimeMillis) {
    private val dao = database.albums()
    val albums = dao.observeAll().map { rows -> rows.map { it.toAlbum() } }
    suspend fun find(id: String): VirtualAlbum? = dao.find(id)?.toAlbum()

    suspend fun create(name: String, mediaIds: Set<String> = emptySet(),restoredId:String?=null): String = database.withTransaction {
        val clean = albumName(name)
        val key = albumNameKey(clean)
        requireMessage(dao.idByName(key) == null, R.string.album_duplicate)
        validateIds(mediaIds)
        val id = restoredId ?: UUID.randomUUID().toString()
        val time = now()
        dao.insert(AlbumEntity(id, clean, key, time, time))
        if (mediaIds.isNotEmpty()) dao.add(mediaIds.map { AlbumMemberEntity(id, it, time) })
        id
    }

    suspend fun rename(id: String, name: String) = database.withTransaction {
        val clean = albumName(name)
        val key = albumNameKey(clean)
        requireMessage(dao.idByName(key).let { it == null || it == id }, R.string.album_duplicate)
        checkMessage(dao.rename(id, clean, key, now()) == 1, R.string.album_missing)
    }

    suspend fun add(id: String, mediaIds: Set<String>): Int = database.withTransaction {
        requireMessage(mediaIds.isNotEmpty(), R.string.select_media_first)
        validateIds(mediaIds)
        checkMessage(dao.find(id) != null, R.string.album_missing)
        val time = now()
        val count = dao.add(mediaIds.map { AlbumMemberEntity(id, it, time) }).count { it != -1L }
        if (count > 0) dao.touch(id, time)
        count
    }

    suspend fun remove(id: String, mediaIds: Set<String>): Int = database.withTransaction {
        validateIds(mediaIds)
        checkMessage(dao.find(id) != null, R.string.album_missing)
        val count = mediaIds.sumOf { dao.remove(id, it) }
        if (count > 0) dao.touch(id, now())
        count
    }
    suspend fun deleteMany(ids:Set<String>) = database.withTransaction {ids.forEach {dao.delete(it)}}
    suspend fun delete(id: String) { dao.delete(id) }

    private fun validateIds(ids: Set<String>) { requireMessage(ids.all { it.isNotBlank() }, R.string.invalid_media_reference) }
    private fun AlbumWithMembers.toAlbum() = VirtualAlbum(album.id, album.name, members.map { it.mediaId }.toSet(), album.createdAt, album.updatedAt)
}
