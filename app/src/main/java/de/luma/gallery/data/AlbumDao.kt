package de.luma.gallery.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "virtual_albums", indices = [Index(value = ["nameKey"], unique = true)])
data class AlbumEntity(@PrimaryKey val id: String, val name: String, val nameKey: String, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "album_members", primaryKeys = ["albumId", "mediaId"],
    foreignKeys = [ForeignKey(entity = AlbumEntity::class, parentColumns = ["id"], childColumns = ["albumId"], onDelete = ForeignKey.CASCADE)])
data class AlbumMemberEntity(val albumId: String, val mediaId: String, val addedAt: Long)

data class AlbumWithMembers(@Embedded val album: AlbumEntity,
    @Relation(parentColumn = "id", entityColumn = "albumId") val members: List<AlbumMemberEntity>)

@Dao
interface AlbumDao {
    @Transaction @Query("SELECT * FROM virtual_albums ORDER BY nameKey ASC, id ASC")
    fun observeAll(): Flow<List<AlbumWithMembers>>
    @Transaction @Query("SELECT * FROM virtual_albums WHERE id = :id")
    suspend fun find(id: String): AlbumWithMembers?
    @Query("SELECT id FROM virtual_albums WHERE nameKey = :key LIMIT 1") suspend fun idByName(key: String): String?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(album: AlbumEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun add(members: List<AlbumMemberEntity>): List<Long>
    @Query("UPDATE virtual_albums SET name = :name, nameKey = :key, updatedAt = :time WHERE id = :id")
    suspend fun rename(id: String, name: String, key: String, time: Long): Int
    @Query("UPDATE virtual_albums SET updatedAt = :time WHERE id = :id") suspend fun touch(id: String, time: Long): Int
    @Query("DELETE FROM album_members WHERE albumId = :albumId AND mediaId = :mediaId") suspend fun remove(albumId: String, mediaId: String): Int
    @Query("DELETE FROM virtual_albums WHERE id = :id") suspend fun delete(id: String): Int
}
