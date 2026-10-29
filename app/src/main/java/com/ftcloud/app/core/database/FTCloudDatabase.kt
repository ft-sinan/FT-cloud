package com.ftcloud.app.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE parentId IS NULL AND isTrashed = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeRootFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentId = :parentId AND isTrashed = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeChildFolders(parentId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE isStarred = 1 AND isTrashed = 0 ORDER BY updatedAt DESC")
    fun observeStarredFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE isTrashed = 1 ORDER BY updatedAt DESC")
    fun observeTrashedFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE isTrashed = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAllActiveFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersSnapshot(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FolderEntity?

    @Query(
        """
        SELECT * FROM folders
        WHERE name = :name
          AND ((parentId IS NULL AND :parentId IS NULL) OR parentId = :parentId)
          AND isTrashed = 0
        LIMIT 1
        """
    )
    suspend fun findByNameInParent(name: String, parentId: String?): FolderEntity?

    @Query("SELECT * FROM folders WHERE isTrashed = 0 AND name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE ASC")
    fun searchFolders(query: String): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Query("UPDATE folders SET name = :newName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, newName: String, updatedAt: Long)

    @Query("UPDATE folders SET parentId = :targetParentId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun move(id: String, targetParentId: String?, updatedAt: Long)

    @Query("UPDATE folders SET isStarred = :starred, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean, updatedAt: Long)

    @Query("UPDATE folders SET isTrashed = :trashed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTrashed(id: String, trashed: Boolean, updatedAt: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM folders WHERE isTrashed = 1")
    suspend fun deleteAllTrashed()

    @Query("SELECT COUNT(*) FROM folders WHERE isTrashed = 0")
    fun observeActiveFolderCount(): Flow<Int>
}

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE folderId IS NULL AND isTrashed = 0")
    fun observeRootFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE folderId = :folderId AND isTrashed = 0")
    fun observeFolderFiles(folderId: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isTrashed = 0")
    fun observeAllActiveFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isTrashed = 0 AND category IN ('IMAGE', 'VIDEO') ORDER BY COALESCE(capturedAt, createdAt) DESC")
    fun observeMediaTimeline(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isStarred = 1 AND isTrashed = 0 ORDER BY updatedAt DESC")
    fun observeStarredFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isTrashed = 1 ORDER BY updatedAt DESC")
    fun observeTrashedFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isTrashed = 0 AND name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchFiles(query: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FileEntity?

    @Query("SELECT * FROM files WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<FileEntity?>

    @Query("SELECT * FROM files WHERE sha256 = :sha256 AND isTrashed = 0 LIMIT 1")
    suspend fun findActiveBySha256(sha256: String): FileEntity?

    @Query("SELECT * FROM files")
    suspend fun getAllFilesSnapshot(): List<FileEntity>

    @Query("SELECT * FROM files WHERE isTrashed = 1")
    suspend fun getTrashedFilesSnapshot(): List<FileEntity>

    @Query("SELECT * FROM files WHERE folderId = :folderId")
    suspend fun getFilesInFolderSnapshot(folderId: String): List<FileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: FileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(files: List<FileEntity>)

    @Query("UPDATE files SET name = :newName, category = :category, mimeType = :mimeType, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, newName: String, category: String, mimeType: String, updatedAt: Long)

    @Query("UPDATE files SET folderId = :targetFolderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun move(id: String, targetFolderId: String?, updatedAt: Long)

    @Query("UPDATE files SET isStarred = :starred, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean, updatedAt: Long)

    @Query("UPDATE files SET isTrashed = :trashed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTrashed(id: String, trashed: Boolean, updatedAt: Long)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM files WHERE isTrashed = 0")
    fun observeActiveFileCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM files WHERE isTrashed = 0")
    fun observeActiveTotalBytes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM files WHERE isTrashed = 0 AND category IN ('IMAGE', 'VIDEO')")
    fun observeMediaCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM files WHERE isStarred = 1 AND isTrashed = 0")
    fun observeStarredCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM files WHERE isTrashed = 1")
    fun observeTrashedCount(): Flow<Int>
}

@Dao
interface ChunkDao {
    @Query("SELECT * FROM file_chunks WHERE driveFileId = :driveFileId ORDER BY chunkIndex ASC")
    suspend fun getChunksForFile(driveFileId: String): List<FileChunkEntity>

    @Query("SELECT * FROM file_chunks ORDER BY driveFileId, chunkIndex ASC")
    suspend fun getAllChunksSnapshot(): List<FileChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChunk(chunk: FileChunkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChunks(chunks: List<FileChunkEntity>)

    @Query(
        """
        UPDATE file_chunks
        SET telegramFileId = :newFileId, uploaderBotId = :botId
        WHERE driveFileId = :driveFileId AND chunkIndex = :chunkIndex
        """
    )
    suspend fun updateChunkBotFileId(
        driveFileId: String,
        chunkIndex: Int,
        newFileId: String,
        botId: String,
    )

    @Query("DELETE FROM file_chunks WHERE driveFileId = :driveFileId")
    suspend fun deleteChunksForFile(driveFileId: String)
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun observeAllTransfers(): Flow<List<TransferTaskEntity>>

    @Query("SELECT * FROM transfers WHERE status IN ('QUEUED', 'RUNNING') ORDER BY createdAt ASC")
    suspend fun getPendingTransfers(): List<TransferTaskEntity>

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransferTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TransferTaskEntity)

    @Query(
        """
        UPDATE transfers
        SET status = :status,
            transferredBytes = :transferredBytes,
            completedChunks = :completedChunks,
            activeBotLabel = :activeBotLabel,
            errorMessage = :errorMessage,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateProgress(
        id: String,
        status: String,
        transferredBytes: Long,
        completedChunks: Int,
        activeBotLabel: String?,
        errorMessage: String?,
        updatedAt: Long,
    )

    @Query("UPDATE transfers SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, errorMessage: String?, updatedAt: Long)

    @Query("UPDATE transfers SET driveFileId = :driveFileId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun attachDriveFileId(id: String, driveFileId: String, updatedAt: Long)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transfers WHERE status IN ('COMPLETED', 'CANCELLED')")
    suspend fun clearCompleted()
}

@Dao
interface BackupHistoryDao {
    @Query("SELECT * FROM backed_up_items WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getBySourceKey(sourceKey: String): BackedUpItemEntity?

    @Query("SELECT * FROM backed_up_items WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getBySha256(sha256: String): BackedUpItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BackedUpItemEntity)

    @Query("SELECT COUNT(*) FROM backed_up_items")
    fun observeBackedUpCount(): Flow<Int>
}

@Database(
    entities = [
        FolderEntity::class,
        FileEntity::class,
        FileChunkEntity::class,
        TransferTaskEntity::class,
        BackedUpItemEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class FTCloudDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun fileDao(): FileDao
    abstract fun chunkDao(): ChunkDao
    abstract fun transferDao(): TransferDao
    abstract fun backupHistoryDao(): BackupHistoryDao
}
