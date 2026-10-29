package com.ftcloud.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.ftcloud.app.core.crypto.AesGcmChunkCipher
import com.ftcloud.app.core.crypto.CredentialStore
import com.ftcloud.app.core.database.BackupHistoryDao
import com.ftcloud.app.core.database.ChunkDao
import com.ftcloud.app.core.database.FileDao
import com.ftcloud.app.core.database.FolderDao
import com.ftcloud.app.core.database.FolderEntity
import com.ftcloud.app.core.database.TransferDao
import com.ftcloud.app.core.database.TransferTaskEntity
import com.ftcloud.app.core.media.ChunkDiskCache
import com.ftcloud.app.core.model.DriveFile
import com.ftcloud.app.core.model.DriveFolder
import com.ftcloud.app.core.model.DriveStats
import com.ftcloud.app.core.model.FileCategory
import com.ftcloud.app.core.model.FileChunk
import com.ftcloud.app.core.model.TransferDirection
import com.ftcloud.app.core.model.TransferStatus
import com.ftcloud.app.core.model.TransferTask
import com.ftcloud.app.core.sync.CloudManifestSync
import com.ftcloud.app.core.sync.ManifestRestoreSummary
import com.ftcloud.app.core.telegram.MultiBotSwarmManager
import com.ftcloud.app.core.transfer.ChunkedUploadEngine
import com.ftcloud.app.core.transfer.TransferWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class LocalMediaMetadata(
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val capturedAt: Long? = null,
)

class DriveRepository(
    private val appContext: Context,
    private val folderDao: FolderDao,
    private val fileDao: FileDao,
    private val chunkDao: ChunkDao,
    private val transferDao: TransferDao,
    private val backupHistoryDao: BackupHistoryDao,
    private val swarmManager: MultiBotSwarmManager,
    private val manifestSync: CloudManifestSync,
    private val chunkDiskCache: ChunkDiskCache,
    private val settingsRepository: SettingsRepository,
    private val credentialStore: CredentialStore,
    private val cipher: AesGcmChunkCipher,
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debouncedSyncJob: Job? = null

    fun observeFolders(parentId: String?): Flow<List<DriveFolder>> {
        val source = if (parentId == null) {
            folderDao.observeRootFolders()
        } else {
            folderDao.observeChildFolders(parentId)
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    fun observeFiles(folderId: String?): Flow<List<DriveFile>> {
        val source = if (folderId == null) {
            fileDao.observeRootFiles()
        } else {
            fileDao.observeFolderFiles(folderId)
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    fun observeAllActiveFolders(): Flow<List<DriveFolder>> =
        folderDao.observeAllActiveFolders().map { list -> list.map { it.toDomain() } }

    fun observeAllActiveFiles(): Flow<List<DriveFile>> =
        fileDao.observeAllActiveFiles().map { list -> list.map { it.toDomain() } }

    fun observeMediaTimeline(): Flow<List<DriveFile>> =
        fileDao.observeMediaTimeline().map { list -> list.map { it.toDomain() } }

    fun observeStarredFolders(): Flow<List<DriveFolder>> =
        folderDao.observeStarredFolders().map { list -> list.map { it.toDomain() } }

    fun observeStarredFiles(): Flow<List<DriveFile>> =
        fileDao.observeStarredFiles().map { list -> list.map { it.toDomain() } }

    fun observeTrashedFolders(): Flow<List<DriveFolder>> =
        folderDao.observeTrashedFolders().map { list -> list.map { it.toDomain() } }

    fun observeTrashedFiles(): Flow<List<DriveFile>> =
        fileDao.observeTrashedFiles().map { list -> list.map { it.toDomain() } }

    fun searchFolders(query: String): Flow<List<DriveFolder>> =
        folderDao.searchFolders(query).map { list -> list.map { it.toDomain() } }

    fun searchFiles(query: String): Flow<List<DriveFile>> =
        fileDao.searchFiles(query).map { list -> list.map { it.toDomain() } }

    fun observeFileById(fileId: String): Flow<DriveFile?> =
        fileDao.observeById(fileId).map { it?.toDomain() }

    suspend fun getFileById(fileId: String): DriveFile? = fileDao.getById(fileId)?.toDomain()

    suspend fun getChunksForFile(fileId: String): List<FileChunk> =
        chunkDao.getChunksForFile(fileId).map { it.toDomain() }

    fun observeTransfers(): Flow<List<TransferTask>> =
        transferDao.observeAllTransfers().map { list -> list.map { it.toDomain() } }

    fun observeStats(): Flow<DriveStats> = combine(
        combine(
            fileDao.observeActiveFileCount(),
            folderDao.observeActiveFolderCount(),
            fileDao.observeActiveTotalBytes(),
        ) { files, folders, bytes -> Triple(files, folders, bytes) },
        combine(
            fileDao.observeMediaCount(),
            fileDao.observeStarredCount(),
            fileDao.observeTrashedCount(),
            backupHistoryDao.observeBackedUpCount(),
        ) { media, starred, trashed, backedUp ->
            intArrayOf(media, starred, trashed, backedUp)
        }
    ) { (files, folders, bytes), counts ->
        DriveStats(
            totalFiles = files,
            totalFolders = folders,
            totalBytes = bytes,
            mediaCount = counts[0],
            starredCount = counts[1],
            trashedCount = counts[2],
            backedUpCount = counts[3],
        )
    }

    suspend fun getBreadcrumbs(folderId: String?): List<DriveFolder> = withContext(Dispatchers.IO) {
        if (folderId == null) return@withContext emptyList()
        val chain = ArrayDeque<DriveFolder>()
        val visited = mutableSetOf<String>()
        var cursor: String? = folderId
        while (cursor != null && visited.add(cursor)) {
            val entity = folderDao.getById(cursor) ?: break
            chain.addFirst(entity.toDomain())
            cursor = entity.parentId
        }
        chain.toList()
    }

    suspend fun createFolder(name: String, parentId: String?): DriveFolder = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Folder name cannot be empty." }
        val existing = folderDao.findByNameInParent(cleanName, parentId)
        if (existing != null) return@withContext existing.toDomain()

        val now = System.currentTimeMillis()
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            parentId = parentId,
            isStarred = false,
            isTrashed = false,
            createdAt = now,
            updatedAt = now,
        )
        folderDao.upsert(folder)
        scheduleManifestSyncDebounced()
        folder.toDomain()
    }

    suspend fun getOrCreateFolderPath(segments: List<String>): String? = withContext(Dispatchers.IO) {
        var parentId: String? = null
        for (segment in segments.map { it.trim() }.filter { it.isNotEmpty() }) {
            val existing = folderDao.findByNameInParent(segment, parentId)
            parentId = if (existing != null) {
                existing.id
            } else {
                val now = System.currentTimeMillis()
                val created = FolderEntity(
                    id = UUID.randomUUID().toString(),
                    name = segment,
                    parentId = parentId,
                    createdAt = now,
                    updatedAt = now,
                )
                folderDao.upsert(created)
                created.id
            }
        }
        parentId
    }

    suspend fun renameFolder(folderId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        folderDao.rename(folderId, clean, System.currentTimeMillis())
        scheduleManifestSyncDebounced()
    }

    suspend fun renameFile(fileId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        val existing = fileDao.getById(fileId) ?: return
        val mime = FileCategory.guessMimeType(clean).let { guessed ->
            if (guessed == "application/octet-stream") existing.mimeType else guessed
        }
        val cat = FileCategory.fromMimeAndName(mime, clean)
        fileDao.rename(fileId, clean, cat.name, mime, System.currentTimeMillis())
        scheduleManifestSyncDebounced()
    }

    suspend fun moveItems(
        fileIds: Collection<String>,
        folderIds: Collection<String>,
        targetParentId: String?,
    ) {
        val now = System.currentTimeMillis()
        for (fid in folderIds) {
            if (fid != targetParentId) {
                folderDao.move(fid, targetParentId, now)
            }
        }
        for (id in fileIds) {
            fileDao.move(id, targetParentId, now)
        }
        scheduleManifestSyncDebounced()
    }

    suspend fun toggleFileStar(fileId: String, starred: Boolean) {
        fileDao.setStarred(fileId, starred, System.currentTimeMillis())
        scheduleManifestSyncDebounced()
    }

    suspend fun toggleFolderStar(folderId: String, starred: Boolean) {
        folderDao.setStarred(folderId, starred, System.currentTimeMillis())
        scheduleManifestSyncDebounced()
    }

    suspend fun setTrashedForItems(
        fileIds: Collection<String>,
        folderIds: Collection<String>,
        trashed: Boolean,
    ) {
        val now = System.currentTimeMillis()
        for (id in fileIds) {
            fileDao.setTrashed(id, trashed, now)
        }
        for (fid in folderIds) {
            folderDao.setTrashed(fid, trashed, now)
        }
        scheduleManifestSyncDebounced()
    }

    suspend fun permanentlyDeleteFile(fileId: String) = withContext(Dispatchers.IO) {
        val chunks = chunkDao.getChunksForFile(fileId)
        val msgIds = chunks.map { it.telegramMessageId }.filter { it > 0L }
        if (msgIds.isNotEmpty()) {
            swarmManager.deleteMessagesFromChannel(msgIds)
        }
        chunkDao.deleteChunksForFile(fileId)
        fileDao.deleteById(fileId)
        scheduleManifestSyncDebounced()
    }

    suspend fun permanentlyDeleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        val childFiles = fileDao.getFilesInFolderSnapshot(folderId)
        for (file in childFiles) {
            permanentlyDeleteFile(file.id)
        }
        folderDao.deleteById(folderId)
        scheduleManifestSyncDebounced()
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val trashedFiles = fileDao.getTrashedFilesSnapshot()
        for (f in trashedFiles) {
            val chunks = chunkDao.getChunksForFile(f.id)
            val msgIds = chunks.map { it.telegramMessageId }.filter { it > 0L }
            if (msgIds.isNotEmpty()) {
                swarmManager.deleteMessagesFromChannel(msgIds)
            }
            chunkDao.deleteChunksForFile(f.id)
            fileDao.deleteById(f.id)
        }
        folderDao.deleteAllTrashed()
        scheduleManifestSyncDebounced()
    }

    suspend fun enqueueUriUploads(
        uris: List<Uri>,
        targetFolderId: String?,
    ): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (uri in uris) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val meta = resolveDisplayNameAndSize(uri)
            val mime = appContext.contentResolver.getType(uri)
                ?: FileCategory.guessMimeType(meta.first)
            val chunkEst = if (meta.second <= 0L) {
                1
            } else {
                ((meta.second + ChunkedUploadEngine.DEFAULT_CHUNK_SIZE_BYTES - 1L) /
                    ChunkedUploadEngine.DEFAULT_CHUNK_SIZE_BYTES).toInt().coerceAtLeast(1)
            }
            val now = System.currentTimeMillis()
            val taskId = UUID.randomUUID().toString()
            val preAssignedFileId = UUID.randomUUID().toString()
            transferDao.upsert(
                TransferTaskEntity(
                    id = taskId,
                    driveFileId = preAssignedFileId,
                    fileName = meta.first,
                    mimeType = mime,
                    direction = TransferDirection.UPLOAD.name,
                    status = TransferStatus.QUEUED.name,
                    sourceUri = uri.toString(),
                    targetFolderId = targetFolderId,
                    destinationPath = null,
                    totalBytes = meta.second,
                    transferredBytes = 0L,
                    totalChunks = chunkEst,
                    completedChunks = 0,
                    activeBotLabel = null,
                    errorMessage = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            count++
        }
        if (count > 0) {
            TransferWorker.enqueue(appContext)
        }
        count
    }

    suspend fun createAndUploadTextNote(
        fileName: String,
        body: String,
        targetFolderId: String?,
    ) = withContext(Dispatchers.IO) {
        val normalizedName = if (fileName.contains('.')) fileName.trim() else "${fileName.trim()}.md"
        val exportsDir = File(appContext.cacheDir, "exports").apply { mkdirs() }
        val noteFile = File(exportsDir, "${UUID.randomUUID()}_$normalizedName")
        noteFile.writeText(body)
        val uri = Uri.fromFile(noteFile)
        val now = System.currentTimeMillis()
        transferDao.upsert(
            TransferTaskEntity(
                id = UUID.randomUUID().toString(),
                driveFileId = UUID.randomUUID().toString(),
                fileName = normalizedName,
                mimeType = FileCategory.guessMimeType(normalizedName),
                direction = TransferDirection.UPLOAD.name,
                status = TransferStatus.QUEUED.name,
                sourceUri = uri.toString(),
                targetFolderId = targetFolderId,
                destinationPath = null,
                totalBytes = noteFile.length(),
                transferredBytes = 0L,
                totalChunks = 1,
                completedChunks = 0,
                activeBotLabel = null,
                errorMessage = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        TransferWorker.enqueue(appContext)
    }

    suspend fun enqueueDownloadToDevice(file: DriveFile): File = withContext(Dispatchers.IO) {
        val baseDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.filesDir, "downloads")
        baseDir.mkdirs()
        val destFile = File(baseDir, file.name)
        val now = System.currentTimeMillis()
        transferDao.upsert(
            TransferTaskEntity(
                id = UUID.randomUUID().toString(),
                driveFileId = file.id,
                fileName = file.name,
                mimeType = file.mimeType,
                direction = TransferDirection.DOWNLOAD.name,
                status = TransferStatus.QUEUED.name,
                sourceUri = null,
                targetFolderId = file.folderId,
                destinationPath = destFile.absolutePath,
                totalBytes = file.sizeBytes,
                transferredBytes = 0L,
                totalChunks = file.chunkCount.coerceAtLeast(1),
                completedChunks = 0,
                activeBotLabel = null,
                errorMessage = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        TransferWorker.enqueue(appContext)
        destFile
    }

    suspend fun pauseTransfer(taskId: String) {
        transferDao.updateStatus(taskId, TransferStatus.PAUSED.name, null, System.currentTimeMillis())
    }

    suspend fun resumeOrRetryTransfer(taskId: String) {
        transferDao.updateStatus(taskId, TransferStatus.QUEUED.name, null, System.currentTimeMillis())
        TransferWorker.enqueue(appContext)
    }

    suspend fun cancelTransfer(taskId: String) {
        transferDao.updateStatus(taskId, TransferStatus.CANCELLED.name, "Cancelled by user", System.currentTimeMillis())
    }

    suspend fun clearCompletedTransfers() {
        transferDao.clearCompleted()
    }

    suspend fun configureVaultPassphrase(passphrase: String) = withContext(Dispatchers.IO) {
        require(passphrase.length >= 6) { "Passphrase must be at least 6 characters." }
        val salt = cipher.generateSalt()
        val secretKey = cipher.deriveKey(passphrase.toCharArray(), salt)
        val verifier = cipher.createKeyVerifier(secretKey)
        credentialStore.setVaultEncryptionKey(
            secretKey = secretKey,
            passphraseRecordSaltB64 = Base64.getEncoder().encodeToString(salt),
            verifier = verifier,
        )
        settingsRepository.setEncryptionEnabled(true)
    }

    suspend fun disableVaultEncryption() {
        settingsRepository.setEncryptionEnabled(false)
    }

    suspend fun syncManifestNow(passphrase: String? = null): Long {
        val currentSettings = settingsRepository.getSnapshot()
        val msgId = manifestSync.pushAndPinManifest(
            previousManifestMessageId = currentSettings.pinnedManifestMessageId,
            passphraseForEnvelope = passphrase?.takeIf { it.isNotBlank() }?.toCharArray(),
        )
        settingsRepository.recordManifestSync(msgId)
        return msgId
    }

    suspend fun restoreManifestFromChannel(passphrase: String? = null): ManifestRestoreSummary? {
        val summary = manifestSync.restoreFromPinnedManifest(
            passphrase = passphrase?.takeIf { it.isNotBlank() }?.toCharArray(),
        )
        if (summary != null) {
            settingsRepository.recordManifestSync(summary.pinnedMessageId)
        }
        return summary
    }

    fun scheduleManifestSyncDebounced() {
        debouncedSyncJob?.cancel()
        debouncedSyncJob = repoScope.launch {
            delay(4_000L)
            runCatching {
                val snap = settingsRepository.getSnapshot()
                if (snap.isConfigured) {
                    val msgId = manifestSync.pushAndPinManifest(
                        previousManifestMessageId = snap.pinnedManifestMessageId,
                    )
                    settingsRepository.recordManifestSync(msgId)
                }
            }
        }
    }

    fun extractLocalMediaMetadata(uri: Uri, mimeType: String): LocalMediaMetadata {
        val category = FileCategory.fromMimeAndName(mimeType, uri.lastPathSegment.orEmpty())
        if (category == FileCategory.IMAGE) {
            var width: Int? = null
            var height: Int? = null
            var capturedAt: Long? = null
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, opts)
                    if (opts.outWidth > 0 && opts.outHeight > 0) {
                        width = opts.outWidth
                        height = opts.outHeight
                    }
                }
            }
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val dt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    if (!dt.isNullOrBlank()) {
                        val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                            timeZone = TimeZone.getDefault()
                        }
                        capturedAt = fmt.parse(dt)?.time
                    }
                }
            }
            return LocalMediaMetadata(width = width, height = height, capturedAt = capturedAt)
        }

        if (category == FileCategory.VIDEO || category == FileCategory.AUDIO) {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(appContext, uri)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                LocalMediaMetadata(width = w, height = h, durationMs = dur)
            } catch (_: Exception) {
                LocalMediaMetadata()
            } finally {
                runCatching { retriever.release() }
            }
        }

        return LocalMediaMetadata()
    }

    suspend fun clearLocalCache() {
        chunkDiskCache.clearAll()
    }

    fun getCurrentCacheBytes(): Long = chunkDiskCache.currentCacheBytes()

    private fun resolveDisplayNameAndSize(uri: Uri): Pair<String, Long> {
        if (uri.scheme == "file") {
            val f = File(uri.path.orEmpty())
            return f.name.ifBlank { "upload.bin" } to f.length()
        }
        var name = "upload_${System.currentTimeMillis()}"
        var size = 0L
        runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        name = cursor.getString(nameIdx)
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        return name to size
    }
}
