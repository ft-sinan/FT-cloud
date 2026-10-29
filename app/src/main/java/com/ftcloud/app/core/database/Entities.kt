package com.ftcloud.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ftcloud.app.core.model.DriveFile
import com.ftcloud.app.core.model.DriveFolder
import com.ftcloud.app.core.model.FileCategory
import com.ftcloud.app.core.model.FileChunk
import com.ftcloud.app.core.model.TransferDirection
import com.ftcloud.app.core.model.TransferStatus
import com.ftcloud.app.core.model.TransferTask

@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["isTrashed"]),
        Index(value = ["isStarred"]),
    ]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?,
    val isStarred: Boolean = false,
    val isTrashed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toDomain(): DriveFolder = DriveFolder(
        id = id,
        name = name,
        parentId = parentId,
        isStarred = isStarred,
        isTrashed = isTrashed,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

@Entity(
    tableName = "files",
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["category"]),
        Index(value = ["sha256"]),
        Index(value = ["isTrashed"]),
        Index(value = ["isStarred"]),
        Index(value = ["createdAt"]),
    ]
)
data class FileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val folderId: String?,
    val mimeType: String,
    val category: String,
    val sizeBytes: Long,
    val chunkCount: Int,
    val sha256: String,
    val isEncrypted: Boolean = false,
    val isStarred: Boolean = false,
    val isTrashed: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val capturedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toDomain(): DriveFile = DriveFile(
        id = id,
        name = name,
        folderId = folderId,
        mimeType = mimeType,
        category = runCatching { FileCategory.valueOf(category) }.getOrDefault(
            FileCategory.fromMimeAndName(mimeType, name)
        ),
        sizeBytes = sizeBytes,
        chunkCount = chunkCount,
        sha256 = sha256,
        isEncrypted = isEncrypted,
        isStarred = isStarred,
        isTrashed = isTrashed,
        width = width,
        height = height,
        durationMs = durationMs,
        capturedAt = capturedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

@Entity(
    tableName = "file_chunks",
    indices = [
        Index(value = ["driveFileId", "chunkIndex"], unique = true),
    ]
)
data class FileChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val driveFileId: String,
    val chunkIndex: Int,
    val byteOffset: Long,
    val rawSize: Long,
    val storedSize: Long,
    val telegramMessageId: Long,
    val telegramFileId: String,
    val telegramFileUniqueId: String,
    val uploaderBotId: String,
    val sha256: String,
    val ivBase64: String? = null,
) {
    fun toDomain(): FileChunk = FileChunk(
        id = id,
        driveFileId = driveFileId,
        chunkIndex = chunkIndex,
        byteOffset = byteOffset,
        rawSize = rawSize,
        storedSize = storedSize,
        telegramMessageId = telegramMessageId,
        telegramFileId = telegramFileId,
        telegramFileUniqueId = telegramFileUniqueId,
        uploaderBotId = uploaderBotId,
        sha256 = sha256,
        ivBase64 = ivBase64,
    )
}

@Entity(
    tableName = "transfers",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
    ]
)
data class TransferTaskEntity(
    @PrimaryKey val id: String,
    val driveFileId: String?,
    val fileName: String,
    val mimeType: String,
    val direction: String,
    val status: String,
    val sourceUri: String?,
    val targetFolderId: String?,
    val destinationPath: String?,
    val totalBytes: Long,
    val transferredBytes: Long,
    val totalChunks: Int,
    val completedChunks: Int,
    val activeBotLabel: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toDomain(): TransferTask = TransferTask(
        id = id,
        driveFileId = driveFileId,
        fileName = fileName,
        mimeType = mimeType,
        direction = runCatching { TransferDirection.valueOf(direction) }.getOrDefault(TransferDirection.UPLOAD),
        status = runCatching { TransferStatus.valueOf(status) }.getOrDefault(TransferStatus.QUEUED),
        sourceUri = sourceUri,
        targetFolderId = targetFolderId,
        destinationPath = destinationPath,
        totalBytes = totalBytes,
        transferredBytes = transferredBytes,
        totalChunks = totalChunks,
        completedChunks = completedChunks,
        activeBotLabel = activeBotLabel,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

@Entity(
    tableName = "backed_up_items",
    indices = [
        Index(value = ["sha256"]),
    ]
)
data class BackedUpItemEntity(
    @PrimaryKey val sourceKey: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val sha256: String,
    val driveFileId: String,
    val backedUpAt: Long,
)
