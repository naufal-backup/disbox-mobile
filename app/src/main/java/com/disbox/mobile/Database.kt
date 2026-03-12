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
    // [FIX] Kolom hash wajib — digunakan untuk isolasi data per webhook
    val hash: String,
    val path: String,
    @ColumnInfo(name = "parent_path") val parentPath: String,
    val name: String,
    val size: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "message_ids") val messageIds: String // JSON string
)

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
    // [FIX] Semua query wajib filter by hash
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

    // [FIX] Hapus HANYA file milik hash tertentu — bukan semua file
    @Query("DELETE FROM files WHERE hash = :hash")
    suspend fun deleteAllByHash(hash: String)

    @Query("DELETE FROM files WHERE id = :id AND hash = :hash")
    suspend fun deleteById(id: String, hash: String)

    @Query("DELETE FROM files WHERE (path = :path OR path LIKE :prefix) AND hash = :hash")
    suspend fun deleteByPathPrefix(path: String, prefix: String, hash: String)

    @Query("UPDATE files SET path = :newPath WHERE id = :id AND hash = :hash")
    suspend fun updatePath(id: String, newPath: String, hash: String)
}

@Dao
interface MetadataSyncDao {
    @Query("SELECT * FROM metadata_sync WHERE hash = :hash")
    suspend fun getMetadata(hash: String): MetadataSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(metadata: MetadataSyncEntity)
}

// [FIX] Migration dari versi 1 (tanpa hash) ke versi 2 (dengan hash)
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Simpan tabel lama
        database.execSQL("ALTER TABLE files RENAME TO files_old")

        // Buat tabel baru dengan kolom hash dan composite primary key
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

        // Index untuk performa query
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_hash ON files(hash)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_path ON files(path, hash)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_parent ON files(parent_path, hash)")

        // Data lama tidak bisa di-migrate karena tidak ada info hash-nya
        // App akan sync ulang dari Discord saat pertama connect
        database.execSQL("DROP TABLE files_old")
    }
}

// [FIX] Version naik ke 2
@Database(entities = [FileEntity::class, MetadataSyncEntity::class], version = 2)
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
                )
                    .addMigrations(MIGRATION_1_2) // [FIX] Daftarkan migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
