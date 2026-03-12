package com.disbox.mobile

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// [REFACTOR] Room Entity for Files
@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey val id: String,
    val path: String,
    @ColumnInfo(name = "parent_path") val parentPath: String,
    val name: String,
    val size: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "message_ids") val messageIds: String // JSON string
)

// [REFACTOR] Room Entity for Sync Metadata
@Entity(tableName = "metadata_sync")
data class MetadataSyncEntity(
    @PrimaryKey val hash: String,
    @ColumnInfo(name = "last_msg_id") val lastMsgId: String?,
    @ColumnInfo(name = "snapshot_history") val snapshotHistory: String, // JSON string
    @ColumnInfo(name = "is_dirty") val isDirty: Int, // 0 or 1
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Dao
interface FileDao {
    @Query("SELECT * FROM files")
    suspend fun getAllFiles(): List<FileEntity>

    @Query("SELECT * FROM files WHERE parent_path = :parentPath")
    suspend fun getFilesByParent(parentPath: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getFileById(id: String): FileEntity?

    @Query("SELECT * FROM files WHERE path = :path")
    suspend fun getFileByPath(path: String): FileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(file: FileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileEntity>)

    @Query("DELETE FROM files")
    suspend fun deleteAll()

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM files WHERE path = :path OR path LIKE :prefix")
    suspend fun deleteByPathPrefix(path: String, prefix: String)

    @Query("UPDATE files SET path = :newPath WHERE id = :id")
    suspend fun updatePath(id: String, newPath: String)
}

@Dao
interface MetadataSyncDao {
    @Query("SELECT * FROM metadata_sync WHERE hash = :hash")
    suspend fun getMetadata(hash: String): MetadataSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(metadata: MetadataSyncEntity)
}

@Database(entities = [FileEntity::class, MetadataSyncEntity::class], version = 1)
abstract class DisboxDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun metadataSyncDao(): MetadataSyncDao

    companion object {
        @Volatile
        private var INSTANCE: DisboxDatabase? = null

        fun getDatabase(context: Context): DisboxDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DisboxDatabase::class.java,
                    "disbox.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
