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

data class PinnedManifestInspection(
    val pinnedMessageId: Long,
    val encrypted: Boolean,
)

data class MultiDeviceSyncResult(
    val upToDate: Boolean,
    val noRemoteManifest: Boolean = false,
    val requiresPassphrase: Boolean = false,
    val pinnedMessageId: Long? = null,
    val addedFiles: Int = 0,
    val updatedFiles: Int = 0,
    val removedFiles: Int = 0,
    val mergedDeletedFileIds: Set<String> = emptySet(),
    val mergedDeletedFolderIds: Set<String> = emptySet(),
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

    suspend fun inspectPinnedManifest(): PinnedManifestInspection? = withContext(Dispatchers.IO) {
        val bot = swarmManager.getPrimaryOrBestBotSnapshot() ?: return@withContext null
        val chat = swarmManager.fetchChatWithPinnedMessage()
        val pinned = chat.pinnedMessage ?: return@withContext null
        val doc = pinned.document ?: return@withContext null
        val isManifest = doc.fileName == CloudManifestEnvelope.MANIFEST_FILE_NAME ||
            pinned.caption?.contains("#ftcloud_manifest") == true
        if (!isManifest) return@withContext null

        val rawBytes = swarmManager.downloadArbitraryBotFile(bot, doc.fileId)
        val envelope = CloudManifestCodec.inspectEnvelope(rawBytes) ?: return@withContext null
        PinnedManifestInspection(
            pinnedMessageId = pinned.messageId,
            encrypted = envelope.encrypted,
        )
    }

    suspend fun syncFromPinnedManifest(
        lastKnownMessageId: Long?,
        forcePull: Boolean = false,
        passphrase: CharArray? = null,
        localDeletedFileIds: Set<String> = emptySet(),
        localDeletedFolderIds: Set<String> = emptySet(),
    ): MultiDeviceSyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            pullAndMergeLocked(
                lastKnownMessageId = lastKnownMessageId,
                forcePull = forcePull,
                passphrase = passphrase,
                localDeletedFileIds = localDeletedFileIds,
                localDeletedFolderIds = localDeletedFolderIds,
            )
        }
    }

    suspend fun pushAndPinManifest(
        previousManifestMessageId: Long? = null,
        passphraseForEnvelope: CharArray? = null,
        deletedFileIds: Set<String> = emptySet(),
        deletedFolderIds: Set<String> = emptySet(),
    ): Long = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val channelId = credentialStore.getChannelId()
            require(channelId.isNotBlank()) { "Telegram storage channel is not configured." }

            // Merge-before-push: if another phone pushed a newer manifest since we last synced,
            // pull and merge its changes FIRST so we never overwrite another phone's uploads/edits.
            var effectivePreviousMsgId = previousManifestMessageId
            var combinedDeletedFiles = deletedFileIds
            var combinedDeletedFolders = deletedFolderIds

            runCatching {
                val mergeRes = pullAndMergeLocked(
                    lastKnownMessageId = previousManifestMessageId,
                    forcePull = false,
                    passphrase = passphraseForEnvelope,
                    localDeletedFileIds = deletedFileIds,
                    localDeletedFolderIds = deletedFolderIds,
                )
                if (mergeRes.pinnedMessageId != null) {
                    effectivePreviousMsgId = mergeRes.pinnedMessageId
                }
                if (mergeRes.mergedDeletedFileIds.isNotEmpty()) {
                    combinedDeletedFiles = combinedDeletedFiles + mergeRes.mergedDeletedFileIds
                }
                if (mergeRes.mergedDeletedFolderIds.isNotEmpty()) {
                    combinedDeletedFolders = combinedDeletedFolders + mergeRes.mergedDeletedFolderIds
                }
            }

            val allFolders = folderDao.getAllFoldersSnapshot()
            val allFiles = fileDao.getAllFilesSnapshot()
            val chunksByFile = chunkDao.getAllChunksSnapshot().groupBy { it.driveFileId }

            val payload = CloudManifestPayload(
                schemaVersion = 2,
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
                },
                deletedFileIds = combinedDeletedFiles.toList().takeLast(2000),
                deletedFolderIds = combinedDeletedFolders.toList().takeLast(2000),
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

            if (effectivePreviousMsgId != null && effectivePreviousMsgId != uploaded.messageId) {
                swarmManager.deleteMessagesFromChannel(listOf(effectivePreviousMsgId))
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
            val res = pullAndMergeLocked(
                lastKnownMessageId = null,
                forcePull = true,
                passphrase = passphrase,
                localDeletedFileIds = emptySet(),
                localDeletedFolderIds = emptySet(),
            )
            if (res.noRemoteManifest || res.pinnedMessageId == null) {
                return@withLock null
            }
            if (res.requiresPassphrase) {
                throw IllegalArgumentException("This cloud manifest is encrypted. Please provide your Vault passphrase.")
            }
            ManifestRestoreSummary(
                restoredFolders = folderDao.getAllFoldersSnapshot().size,
                restoredFiles = fileDao.getAllFilesSnapshot().size,
                pinnedMessageId = res.pinnedMessageId,
            )
        }
    }

    private suspend fun pullAndMergeLocked(
        lastKnownMessageId: Long?,
        forcePull: Boolean,
        passphrase: CharArray?,
        localDeletedFileIds: Set<String>,
        localDeletedFolderIds: Set<String>,
    ): MultiDeviceSyncResult {
        val bot = swarmManager.getPrimaryOrBestBotSnapshot()
            ?: throw IllegalStateException("No bots configured in swarm")
        val chat = swarmManager.fetchChatWithPinnedMessage()
        val pinned = chat.pinnedMessage
            ?: return MultiDeviceSyncResult(upToDate = true, noRemoteManifest = true)
        val doc = pinned.document
            ?: return MultiDeviceSyncResult(upToDate = true, noRemoteManifest = true)

        val isManifest = doc.fileName == CloudManifestEnvelope.MANIFEST_FILE_NAME ||
            pinned.caption?.contains("#ftcloud_manifest") == true
        if (!isManifest) {
            return MultiDeviceSyncResult(upToDate = true, noRemoteManifest = true)
        }

        val localFiles = fileDao.getAllFilesSnapshot()
        if (!forcePull && lastKnownMessageId != null && pinned.messageId == lastKnownMessageId && localFiles.isNotEmpty()) {
            return MultiDeviceSyncResult(
                upToDate = true,
                pinnedMessageId = pinned.messageId,
                mergedDeletedFileIds = localDeletedFileIds,
                mergedDeletedFolderIds = localDeletedFolderIds,
            )
        }

        val rawBytes = swarmManager.downloadArbitraryBotFile(bot, doc.fileId)
        val envelope = CloudManifestCodec.inspectEnvelope(rawBytes)
            ?: return MultiDeviceSyncResult(upToDate = true, noRemoteManifest = true)

        if (envelope.encrypted && (passphrase == null || passphrase.isEmpty())) {
            return MultiDeviceSyncResult(
                upToDate = false,
                requiresPassphrase = true,
                pinnedMessageId = pinned.messageId,
            )
        }

        val payload = CloudManifestCodec.decode(
            envelopeBytes = rawBytes,
            cipher = cipher,
            passphrase = passphrase,
        )

        val allDeletedFileIds = localDeletedFileIds + payload.deletedFileIds
        val allDeletedFolderIds = localDeletedFolderIds + payload.deletedFolderIds

        val localFoldersById = folderDao.getAllFoldersSnapshot().associateBy { it.id }
        val localFilesById = localFiles.associateBy { it.id }
        val localChunksByFile = chunkDao.getAllChunksSnapshot().groupBy { it.driveFileId }

        var removedFiles = 0
        for (delFileId in payload.deletedFileIds) {
            if (delFileId in localFilesById) {
                chunkDao.deleteChunksForFile(delFileId)
                fileDao.deleteById(delFileId)
                removedFiles++
            }
        }
        for (delFolderId in payload.deletedFolderIds) {
            if (delFolderId in localFoldersById) {
                folderDao.deleteById(delFolderId)
            }
        }

        val foldersToUpsert = mutableListOf<FolderEntity>()
        for (rf in payload.folders) {
            if (rf.id in allDeletedFolderIds) continue
            val existing = localFoldersById[rf.id]
            if (existing == null || rf.updatedAt >= existing.updatedAt) {
                foldersToUpsert += FolderEntity(
                    id = rf.id,
                    name = rf.name,
                    parentId = rf.parentId,
                    isStarred = rf.isStarred,
                    isTrashed = rf.isTrashed,
                    createdAt = rf.createdAt,
                    updatedAt = rf.updatedAt,
                )
            }
        }
        if (foldersToUpsert.isNotEmpty()) {
            folderDao.upsertAll(foldersToUpsert)
        }

        val filesToUpsert = mutableListOf<FileEntity>()
        val chunksToUpsert = mutableListOf<FileChunkEntity>()
        var addedFiles = 0
        var updatedFiles = 0

        for (rFile in payload.files) {
            if (rFile.id in allDeletedFileIds) continue
            val existing = localFilesById[rFile.id]
            if (existing == null || rFile.updatedAt >= existing.updatedAt) {
                if (existing == null) addedFiles++ else if (rFile.updatedAt > existing.updatedAt) updatedFiles++

                val resolvedSize = if (rFile.sizeBytes > 0L) rFile.sizeBytes else (existing?.sizeBytes ?: 0L)
                filesToUpsert += FileEntity(
                    id = rFile.id,
                    name = rFile.name,
                    folderId = rFile.folderId,
                    mimeType = rFile.mimeType,
                    category = rFile.category,
                    sizeBytes = resolvedSize,
                    chunkCount = rFile.chunkCount,
                    sha256 = rFile.sha256,
                    isEncrypted = rFile.isEncrypted,
                    isStarred = rFile.isStarred,
                    isTrashed = rFile.isTrashed,
                    width = rFile.width,
                    height = rFile.height,
                    durationMs = rFile.durationMs,
                    capturedAt = rFile.capturedAt,
                    createdAt = rFile.createdAt,
                    updatedAt = rFile.updatedAt,
                )

                val existingChunksByIndex = localChunksByFile[rFile.id].orEmpty().associateBy { it.chunkIndex }
                for (c in rFile.chunks) {
                    val prevChunk = existingChunksByIndex[c.chunkIndex]
                    // Preserve locally resolved bot file_id if remote entry was lazy-imported with empty file_id
                    val bestFileId = c.telegramFileId.ifBlank { prevChunk?.telegramFileId.orEmpty() }
                    val bestBotId = c.uploaderBotId.ifBlank { prevChunk?.uploaderBotId.orEmpty() }
                    val bestRawSize = if (c.rawSize > 0L) c.rawSize else (prevChunk?.rawSize ?: 0L)
                    val bestStoredSize = if (c.storedSize > 0L) c.storedSize else (prevChunk?.storedSize ?: 0L)
                    chunksToUpsert += FileChunkEntity(
                        driveFileId = rFile.id,
                        chunkIndex = c.chunkIndex,
                        byteOffset = c.byteOffset,
                        rawSize = bestRawSize,
                        storedSize = bestStoredSize,
                        telegramMessageId = c.telegramMessageId,
                        telegramFileId = bestFileId,
                        telegramFileUniqueId = c.telegramFileUniqueId.ifBlank { prevChunk?.telegramFileUniqueId.orEmpty() },
                        uploaderBotId = bestBotId,
                        sha256 = c.sha256,
                        ivBase64 = c.ivBase64,
                    )
                }
            }
        }

        if (filesToUpsert.isNotEmpty()) {
            for (batch in filesToUpsert.chunked(500)) {
                fileDao.upsertAll(batch)
            }
        }
        if (chunksToUpsert.isNotEmpty()) {
            for (batch in chunksToUpsert.chunked(500)) {
                chunkDao.upsertChunks(batch)
            }
        }

        return MultiDeviceSyncResult(
            upToDate = addedFiles == 0 && updatedFiles == 0 && removedFiles == 0,
            pinnedMessageId = pinned.messageId,
            addedFiles = addedFiles,
            updatedFiles = updatedFiles,
            removedFiles = removedFiles,
            mergedDeletedFileIds = allDeletedFileIds,
            mergedDeletedFolderIds = allDeletedFolderIds,
        )
    }
}
