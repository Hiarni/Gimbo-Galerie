package de.luma.gallery.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "editor_projects", indices = [Index(value = ["updatedAt"])])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceId: String,
    val sourceUri: String,
    val sourceName: String,
    val sourceAlbum: String,
    val sourceTimestamp: Long,
    val isVideo: Boolean,
    val durationMs: Long,
    val size: Long,
    val isDemo: Boolean,
    val recipeJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM editor_projects ORDER BY updatedAt DESC, id ASC")
    fun observeAll(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM editor_projects")
    suspend fun all():List<ProjectEntity>
    @Query("SELECT * FROM editor_projects WHERE id = :id")
    suspend fun find(id: String): ProjectEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity)
    @Query("UPDATE editor_projects SET name = :name, updatedAt = :time WHERE id = :id")
    suspend fun rename(id: String, name: String, time: Long): Int
    @Query("UPDATE editor_projects SET recipeJson = :recipe, updatedAt = :time WHERE id = :id")
    suspend fun updateRecipe(id: String, recipe: String, time: Long): Int
    @Update
    suspend fun replace(project: ProjectEntity): Int
    @Query("DELETE FROM editor_projects WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Database(entities = [ProjectEntity::class, AlbumEntity::class, AlbumMemberEntity::class], version = 2, exportSchema = true)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun albums(): AlbumDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS virtual_albums (id TEXT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_virtual_albums_nameKey ON virtual_albums (nameKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS album_members (albumId TEXT NOT NULL, mediaId TEXT NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(albumId, mediaId), FOREIGN KEY(albumId) REFERENCES virtual_albums(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }
        @Volatile private var instance: ProjectDatabase? = null
        fun open(context: Context, name: String = "luma-projects.db"): ProjectDatabase =
            Room.databaseBuilder(context.applicationContext, ProjectDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        fun get(context: Context): ProjectDatabase = instance ?: synchronized(this) {
            instance ?: open(context).also { instance = it }
        }
    }
}
