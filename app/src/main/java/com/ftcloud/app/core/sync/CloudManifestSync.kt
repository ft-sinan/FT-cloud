package com.ftcloud.app.core.sync

import com.ftcloud.app.core.crypto.AesGcmChunkCipher
import com.ftcloud.app.core.crypto.CredentialStore
import com.ftcloud.app.core.database.ChunkDao
import com.ftcloud.app.core.database.FileChunkEntity
import com.ftcloud.app.core.database.FileDao
import com.ftcloud.app.core.database.FileEntity
import com.ftcloud.app.core.database.FolderDao
import com.ftcloud.app.core.database.FolderEntity
import com.ftcloud.app.core.model.CloudManifestCodec
import com.ftcloud.app.core.model.CloudManifestEnvelope
import com.ftcloud.app.core.model.CloudManifestPayload
import com.ftcloud.app.core.model.ManifestChunkDto
import com.ftcloud.app.core.model.ManifestFileDto
import com.ftcloud.app.core.model.ManifestFolderDto
import com.ftcloud.app.core.telegram.MultiBotSwarmManager
import com.ftcloud.app.core.telegram.TgMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ManifestRestoreSummary(
    val restoredFolders: Int,
    val restoredFiles: Int,
    val pinnedMessageId: Long,
)

class CloudManifestSync(
    private val swarmManager: MultiBotSwarmManager,
    private val folderDao: FolderDao,
    private val fileDao: FileDao,
    private val chunkDao: ChunkDao,
    private val cipher: AesGcmChunkCipher,
    private val credentialStore: CredentialStore,
) {
    private val syncMutex = Mutex()

    suspend fun pushAndPinManifest(
        previousManifestMessageId: Long? = null,
        passphraseForEnvelope: CharArray? = null,
    ): Long = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val channelId = credentialStore.getChannelId()
            require(channelId.isNotBlank()) { "Telegram storage channel is not configured." }

            val allFolders = folderDao.getAllFoldersSnapshot()
            val allFiles = fileDao.getAllFilesSnapshot()
            val chunksByFile = chunkDao.getAllChunksSnapshot().groupBy { it.driveFileId }

            val payload = CloudManifestPayload(
                schemaVersion = 1,
                exportedAt = System.currentTimeMillis(),
                channelId = channelId,
                folders = allFolders.map { f ->
                    ManifestFolderDto(
                        id = f.id,
                        name = f.name,
                        parentId = f.parentId,
                        isStarred = f.isStarred,
                        isTrashed = f.isTrashed,
                        createdAt = f.createdAt,
                        updatedAt = f.updatedAt,
                    )
                },
                files = allFiles.mapNotNull { file ->
                    val fileChunks = chunksByFile[file.id].orEmpty().sortedBy { it.chunkIndex }
                    if (fileChunks.isEmpty()) return@mapNotNull null
                    ManifestFileDto(
                        id = file.id,
                        name = file.name,
                        folderId = file.folderId,
                        mimeType = file.mimeType,
                        category = file.category,
                        sizeBytes = file.sizeBytes,
                        chunkCount = fileChunks.size,
                        sha256 = file.sha256,
                        isEncrypted = file.isEncrypted,
                        isStarred = file.isStarred,
                        isTrashed = file.isTrashed,
                        width = file.width,
                        height = file.height,
                        durationMs = file.durationMs,
                        capturedAt = file.capturedAt,
                        createdAt = file.createdAt,
                        updatedAt = file.updatedAt,
                        chunks = fileChunks.map { c ->
                            ManifestChunkDto(
                                chunkIndex = c.chunkIndex,
                                byteOffset = c.byteOffset,
                                rawSize = c.rawSize,
                                storedSize = c.storedSize,
                                telegramMessageId = c.telegramMessageId,
                                telegramFileId = c.telegramFileId,
                                telegramFileUniqueId = c.telegramFileUniqueId,
                                uploaderBotId = c.uploaderBotId,
                                sha256 = c.sha256,
                                ivBase64 = c.ivBase64,
                            )
                        }
                    )
                }
            )

            val encodedBytes = CloudManifestCodec.encode(
                payload = payload,
                cipher = cipher,
                passphrase = passphraseForEnvelope,
            )

            val caption = "📌 FT-Cloud Index Snapshot • ${payload.files.size} files, ${payload.folders.size} folders\n#ftcloud_manifest"
            val uploaded = swarmManager.uploadChunkWithSwarm(
                partFileName = CloudManifestEnvelope.MANIFEST_FILE_NAME,
                mimeType = "application/json",
                payload = encodedBytes,
                caption = caption,
            )

            swarmManager.pinMessageInChannel(
                messageId = uploaded.messageId,
                preferredBotId = uploaded.uploaderBotId,
            )

            if (previousManifestMessageId != null && previousManifestMessageId != uploaded.messageId) {
                swarmManager.deleteMessagesFromChannel(listOf(previousManifestMessageId))
            }

            uploaded.messageId
        }
    }

    suspend fun checkPinnedManifestCandidate(): TgMessage? = withContext(Dispatchers.IO) {
        val chat = swarmManager.fetchChatWithPinnedMessage()
        val pinned = chat.pinnedMessage ?: return@withContext null
        val doc = pinned.document ?: return@withContext null
        val looksLikeManifest = doc.fileName == CloudManifestEnvelope.MANIFEST_FILE_NAME ||
            pinned.caption?.contains("#ftcloud_manifest") == true
        if (looksLikeManifest) pinned else null
    }

    suspend fun restoreFromPinnedManifest(
        passphrase: CharArray? = null,
    ): ManifestRestoreSummary? = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val bot = swarmManager.getPrimaryOrBestBotSnapshot()
                ?: throw IllegalStateException("No bots configured in swarm")
            val chat = swarmManager.fetchChatWithPinnedMessage()
            val pinned = chat.pinnedMessage ?: return@withLock null
            val doc = pinned.document ?: return@withLock null

            val isManifest = doc.fileName == CloudManifestEnvelope.MANIFEST_FILE_NAME ||
                pinned.caption?.contains("#ftcloud_manifest") == true
            if (!isManifest) return@withLock null

            val rawBytes = swarmManager.downloadArbitraryBotFile(bot, doc.fileId)
            val payload = CloudManifestCodec.decode(
                envelopeBytes = rawBytes,
                cipher = cipher,
                passphrase = passphrase,
            )

            val folderEntities = payload.folders.map { f ->
                FolderEntity(
                    id = f.id,
                    name = f.name,
                    parentId = f.parentId,
                    isStarred = f.isStarred,
                    isTrashed = f.isTrashed,
                    createdAt = f.createdAt,
                    updatedAt = f.updatedAt,
                )
            }
            if (folderEntities.isNotEmpty()) {
                folderDao.upsertAll(folderEntities)
            }

            val fileEntities = mutableListOf<FileEntity>()
            val chunkEntities = mutableListOf<FileChunkEntity>()

            for (f in payload.files) {
                fileEntities += FileEntity(
                    id = f.id,
                    name = f.name,
                    folderId = f.folderId,
                    mimeType = f.mimeType,
                    category = f.category,
                    sizeBytes = f.sizeBytes,
                    chunkCount = f.chunkCount,
                    sha256 = f.sha256,
                    isEncrypted = f.isEncrypted,
                    isStarred = f.isStarred,
                    isTrashed = f.isTrashed,
                    width = f.width,
                    height = f.height,
                    durationMs = f.durationMs,
                    capturedAt = f.capturedAt,
                    createdAt = f.createdAt,
                    updatedAt = f.updatedAt,
                )
                for (c in f.chunks) {
                    chunkEntities += FileChunkEntity(
                        driveFileId = f.id,
                        chunkIndex = c.chunkIndex,
                        byteOffset = c.byteOffset,
                        rawSize = c.rawSize,
                        storedSize = c.storedSize,
                        telegramMessageId = c.telegramMessageId,
                        telegramFileId = c.telegramFileId,
                        telegramFileUniqueId = c.telegramFileUniqueId,
                        uploaderBotId = c.uploaderBotId,
                        sha256 = c.sha256,
                        ivBase64 = c.ivBase64,
                    )
                }
            }

            if (fileEntities.isNotEmpty()) {
                fileDao.upsertAll(fileEntities)
            }
            if (chunkEntities.isNotEmpty()) {
                chunkDao.upsertChunks(chunkEntities)
            }

            ManifestRestoreSummary(
                restoredFolders = folderEntities.size,
                restoredFiles = fileEntities.size,
                pinnedMessageId = pinned.messageId,
            )
        }
    }
}
