package com.disbox.mobile

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// [FIX] PRIMARY KEY sekarang (id, hash) — tiap file terikat ke satu webhook
@Entity(
    tableName = "files",
    primaryKeys = ["id", "hash"]
)
data class FileEntity(
    val id: String,
    val hash: String,
    val path: String,
    @ColumnInfo(name = "parent_path") val parentPath: String,
    val name: String,
    val size: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "message_ids") val messageIds: String, // JSON string
    @ColumnInfo(name = "is_locked", defaultValue = "0") val isLocked: Int = 0,
    @ColumnInfo(name = "is_starred", defaultValue = "0") val isStarred: Int = 0
)

@Entity(tableName = "metadata_sync")
data class MetadataSyncEntity(
    @PrimaryKey val hash: String,
    @ColumnInfo(name = "last_msg_id") val lastMsgId: String?,
    @ColumnInfo(name = "snapshot_history") val snapshotHistory: String, // JSON string
    @ColumnInfo(name = "is_dirty") val isDirty: Int, // 0 or 1
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "settings",
    primaryKeys = ["hash", "key"]
)
data class SettingsEntity(
    val hash: String,
    val key: String,
    val value: String?
)

@Entity(tableName = "share_settings")
data class ShareSettingsEntity(
    @PrimaryKey val hash: String,
    val mode: String = "public",
    @ColumnInfo(name = "cf_worker_url") val cf_worker_url: String?,
    @ColumnInfo(name = "cf_api_token") val cf_api_token: String?,
    @ColumnInfo(name = "webhook_url") val webhook_url: String?,
    val enabled: Int = 0
)

@Entity(tableName = "share_links")
data class ShareLinkEntity(
    @PrimaryKey val id: String,
    val hash: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_id") val fileId: String?,
    val token: String,
    val permission: String,
    @ColumnInfo(name = "expires_at") val expiresAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE hash = :hash")
    suspend fun getAllFilesByHash(hash: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE parent_path = :parentPath AND hash = :hash")
    suspend fun getFilesByParent(parentPath: String, hash: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE id = :id AND hash = :hash")
    suspend fun getFileById(id: String, hash: String): FileEntity?

    @Query("SELECT * FROM files WHERE path = :path AND hash = :hash")
    suspend fun getFileByPath(path: String, hash: String): FileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(file: FileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileEntity>)

    @Query("DELETE FROM files WHERE hash = :hash")
    suspend fun deleteAllByHash(hash: String)

    @Query("DELETE FROM files WHERE id = :id AND hash = :hash")
    suspend fun deleteById(id: String, hash: String)

    @Query("DELETE FROM files WHERE (path = :path OR path LIKE :prefix) AND hash = :hash")
    suspend fun deleteByPathPrefix(path: String, prefix: String, hash: String)

    @Query("UPDATE files SET path = :newPath WHERE id = :id AND hash = :hash")
    suspend fun updatePath(id: String, newPath: String, hash: String)

    @Query("UPDATE files SET is_locked = :isLocked WHERE id = :id AND hash = :hash")
    suspend fun setLocked(id: String, hash: String, isLocked: Int)

    @Query("UPDATE files SET is_starred = :isStarred WHERE id = :id AND hash = :hash")
    suspend fun setStarred(id: String, hash: String, isStarred: Int)
}

@Dao
interface MetadataSyncDao {
    @Query("SELECT * FROM metadata_sync WHERE hash = :hash")
    suspend fun getMetadata(hash: String): MetadataSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(metadata: MetadataSyncEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE hash = :hash AND key = :key")
    suspend fun getSetting(hash: String, key: String): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(setting: SettingsEntity)

    @Query("DELETE FROM settings WHERE hash = :hash AND key = :key")
    suspend fun deleteSetting(hash: String, key: String)
}

@Dao
interface ShareSettingsDao {
    @Query("SELECT * FROM share_settings WHERE hash = :hash")
    suspend fun getSettings(hash: String): ShareSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(settings: ShareSettingsEntity)
}

@Dao
interface ShareLinkDao {
    @Query("SELECT * FROM share_links WHERE hash = :hash ORDER BY created_at DESC")
    suspend fun getAllLinksByHash(hash: String): List<ShareLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(link: ShareLinkEntity)

    @Query("DELETE FROM share_links WHERE id = :id AND hash = :hash")
    suspend fun deleteById(id: String, hash: String)

    @Query("DELETE FROM share_links WHERE hash = :hash")
    suspend fun deleteAllByHash(hash: String)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE files RENAME TO files_old")
        database.execSQL("""
            CREATE TABLE files (
                id TEXT NOT NULL,
                hash TEXT NOT NULL,
                path TEXT NOT NULL,
                parent_path TEXT NOT NULL,
                name TEXT NOT NULL,
                size INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL DEFAULT 0,
                message_ids TEXT NOT NULL,
                PRIMARY KEY(id, hash)
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_hash ON files(hash)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_path ON files(path, hash)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_parent ON files(parent_path, hash)")
        database.execSQL("DROP TABLE files_old")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add columns to files table
        database.execSQL("ALTER TABLE files ADD COLUMN is_locked INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE files ADD COLUMN is_starred INTEGER NOT NULL DEFAULT 0")
        
        // Create settings table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS settings (
                hash TEXT NOT NULL,
                key TEXT NOT NULL,
                value TEXT,
                PRIMARY KEY(hash, key)
            )
        """)
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS share_settings (
                hash TEXT NOT NULL,
                mode TEXT NOT NULL DEFAULT 'public',
                cf_worker_url TEXT,
                cf_api_token TEXT,
                webhook_url TEXT,
                enabled INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(hash)
            )
        """)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS share_links (
                id TEXT NOT NULL,
                hash TEXT NOT NULL,
                file_path TEXT NOT NULL,
                file_id TEXT,
                token TEXT NOT NULL,
                permission TEXT NOT NULL,
                expires_at INTEGER,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
        """)
    }
}

@Database(
    entities = [
        FileEntity::class, 
        MetadataSyncEntity::class, 
        SettingsEntity::class,
        ShareSettingsEntity::class,
        ShareLinkEntity::class
    ],
    version = 4
)
abstract class DisboxDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun metadataSyncDao(): MetadataSyncDao
    abstract fun settingsDao(): SettingsDao
    abstract fun shareSettingsDao(): ShareSettingsDao
    abstract fun shareLinkDao(): ShareLinkDao

    companion object {
        @Volatile
        private var INSTANCE: DisboxDatabase? = null

        fun getDatabase(context: Context): DisboxDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DisboxDatabase::class.java,
                    "disbox.db"
                )
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
