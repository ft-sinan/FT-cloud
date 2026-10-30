package com.ftcloud.app.core.transfer

import com.ftcloud.app.core.crypto.AesGcmChunkCipher
import com.ftcloud.app.core.crypto.CredentialStore
import com.ftcloud.app.core.crypto.HashingUtils
import com.ftcloud.app.core.crypto.HashingUtils.toHexStringSimple
import com.ftcloud.app.core.database.ChunkDao
import com.ftcloud.app.core.database.FileDao
import com.ftcloud.app.core.database.FileChunkEntity
import com.ftcloud.app.core.database.FileEntity
import com.ftcloud.app.core.model.DriveFile
import com.ftcloud.app.core.model.FileCategory
import com.ftcloud.app.core.telegram.MultiBotSwarmManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class UploadRequest(
    val fileId: String = UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String,
    val knownSizeBytes: Long,
    val folderId: String?,
    val encrypt: Boolean,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val capturedAt: Long? = null,
)

class ChunkedUploadEngine(
    private val swarmManager: MultiBotSwarmManager,
    private val fileDao: FileDao,
    private val chunkDao: ChunkDao,
    private val cipher: AesGcmChunkCipher,
    private val credentialStore: CredentialStore,
    val chunkSizeBytes: Int = DEFAULT_CHUNK_SIZE_BYTES,
) {
    suspend fun upload(
        request: UploadRequest,
        openStream: () -> InputStream,
        onProgress: (suspend (transferredBytes: Long, completedChunks: Int, totalChunks: Int, activeBotLabel: String?) -> Unit)? = null,
    ): DriveFile = withContext(Dispatchers.IO) {
        val existingChunks = chunkDao.getChunksForFile(request.fileId).associateBy { it.chunkIndex }
        val secretKey = if (request.encrypt) {
            requireNotNull(credentialStore.getVaultEncryptionKey()) {
                "Encryption is enabled, but Vault encryption passphrase has not been set."
            }
        } else {
            null
        }

        val estimatedTotalChunks = if (request.knownSizeBytes <= 0L) {
            1
        } else {
            ((request.knownSizeBytes + chunkSizeBytes - 1L) / chunkSizeBytes).toInt().coerceAtLeast(1)
        }

        val fullFileDigest = MessageDigest.getInstance("SHA-256")
        val completedChunksCounter = AtomicInteger(existingChunks.size)
        val transferredBytesCounter = AtomicLong(existingChunks.values.sumOf { it.rawSize })
        val progressMutex = Mutex()

        val maxParallel = credentialStore.getBots().size.coerceIn(1, 6)
        val semaphore = Semaphore(maxParallel)

        var totalBytesRead = 0L
        var chunkIndex = 0
        val pendingJobs = mutableListOf<kotlinx.coroutines.Deferred<FileChunkEntity>>()

        coroutineScope {
            openStream().use { input ->
                while (true) {
                    ensureActive()
                    val chunkBytes = readUpToChunkSize(input, chunkSizeBytes)
                    if (chunkBytes.isEmpty() && chunkIndex > 0) break

                    val currentIndex = chunkIndex
                    val currentOffset = totalBytesRead
                    totalBytesRead += chunkBytes.size
                    fullFileDigest.update(chunkBytes)

                    val alreadyUploaded = existingChunks[currentIndex]
                    if (alreadyUploaded != null && alreadyUploaded.rawSize == chunkBytes.size.toLong()) {
                        pendingJobs += async { alreadyUploaded }
                    } else {
                        pendingJobs += async {
                            semaphore.withPermit {
                                ensureActive()
                                val chunkSha = HashingUtils.sha256Hex(chunkBytes)
                                val (payloadToUpload, ivBase64) = if (secretKey != null) {
                                    val enc = cipher.encryptChunk(
                                        plainBytes = chunkBytes,
                                        secretKey = secretKey,
                                        fileId = request.fileId,
                                        chunkIndex = currentIndex,
                                    )
                                    enc.cipherBytes to enc.ivBase64
                                } else {
                                    chunkBytes to null
                                }

                                val partName = if (estimatedTotalChunks > 1 || currentIndex > 0) {
                                    "${request.fileName}.part${String.format(Locale.US, "%04d", currentIndex + 1)}"
                                } else if (request.encrypt) {
                                    "${request.fileName}.ftenc"
                                } else {
                                    request.fileName
                                }

                                val caption = buildChunkCaption(
                                    fileName = request.fileName,
                                    fileId = request.fileId,
                                    chunkIndex = currentIndex,
                                    estimatedTotalChunks = estimatedTotalChunks,
                                    encrypted = request.encrypt,
                                )

                                var lastReportedInChunk = 0L
                                val uploaded = swarmManager.uploadChunkWithSwarm(
                                    partFileName = partName,
                                    mimeType = if (request.encrypt) "application/octet-stream" else request.mimeType,
                                    payload = payloadToUpload,
                                    caption = caption,
                                    onProgress = { sentPayloadBytes ->
                                        val scaledRawBytes = if (payloadToUpload.isEmpty()) {
                                            0L
                                        } else {
                                            (sentPayloadBytes * chunkBytes.size) / payloadToUpload.size
                                        }
                                        val delta = (scaledRawBytes - lastReportedInChunk).coerceAtLeast(0L)
                                        if (delta > 0L) {
                                            lastReportedInChunk += delta
                                            transferredBytesCounter.addAndGet(delta)
                                        }
                                    }
                                )

                                // Reconcile any rounding delta
                                val residual = chunkBytes.size.toLong() - lastReportedInChunk
                                if (residual != 0L) {
                                    transferredBytesCounter.addAndGet(residual)
                                }

                                val entity = FileChunkEntity(
                                    driveFileId = request.fileId,
                                    chunkIndex = currentIndex,
                                    byteOffset = currentOffset,
                                    rawSize = chunkBytes.size.toLong(),
                                    storedSize = payloadToUpload.size.toLong(),
                                    telegramMessageId = uploaded.messageId,
                                    telegramFileId = uploaded.fileId,
                                    telegramFileUniqueId = uploaded.fileUniqueId,
                                    uploaderBotId = uploaded.uploaderBotId,
                                    sha256 = chunkSha,
                                    ivBase64 = ivBase64,
                                )
                                chunkDao.upsertChunk(entity)
                                val doneCount = completedChunksCounter.incrementAndGet()
                                progressMutex.withLock {
                                    onProgress?.invoke(
                                        transferredBytesCounter.get(),
                                        doneCount,
                                        maxOf(estimatedTotalChunks, currentIndex + 1),
                                        "@${uploaded.uploaderUsername.trimStart('@')}",
                                    )
                                }
                                entity
                            }
                        }
                    }

                    chunkIndex++
                    if (chunkBytes.size < chunkSizeBytes) {
                        // Check if EOF reached cleanly
                        val nextByte = input.read()
                        if (nextByte == -1) {
                            break
                        } else {
                            // Stream returned a short read without EOF; push into next chunk via wrapper
                            val remainingChunks = drainRemainingWithFirstByte(
                                firstByte = nextByte,
                                input = input,
                                startChunkIndex = chunkIndex,
                                startOffset = totalBytesRead,
                                fullDigest = fullFileDigest,
                                existingChunks = existingChunks,
                                secretKey = secretKey,
                                request = request,
                                estimatedTotalChunks = estimatedTotalChunks,
                                semaphore = semaphore,
                                transferredBytesCounter = transferredBytesCounter,
                                completedChunksCounter = completedChunksCounter,
                                progressMutex = progressMutex,
                                onProgress = onProgress,
                            )
                            totalBytesRead = remainingChunks.finalTotalBytes
                            chunkIndex = remainingChunks.finalChunkCount
                            pendingJobs.addAll(remainingChunks.jobs)
                            break
                        }
                    }
                }
            }

            val allChunks = try {
                pendingJobs.awaitAll().sortedBy { it.chunkIndex }
            } catch (ce: CancellationException) {
                throw ce
            }

            val now = System.currentTimeMillis()
            val fullSha256 = fullFileDigest.digest().toHexStringSimple()
            val resolvedMime = request.mimeType.ifBlank { FileCategory.guessMimeType(request.fileName) }
            val resolvedCategory = FileCategory.fromMimeAndName(resolvedMime, request.fileName)

            val fileEntity = FileEntity(
                id = request.fileId,
                name = request.fileName,
                folderId = request.folderId,
                mimeType = resolvedMime,
                category = resolvedCategory.name,
                sizeBytes = totalBytesRead,
                chunkCount = allChunks.size,
                sha256 = fullSha256,
                isEncrypted = request.encrypt,
                isStarred = false,
                isTrashed = false,
                width = request.width,
                height = request.height,
                durationMs = request.durationMs,
                capturedAt = request.capturedAt,
                createdAt = now,
                updatedAt = now,
            )
            fileDao.upsert(fileEntity)

            onProgress?.invoke(totalBytesRead, allChunks.size, allChunks.size, null)
            fileEntity.toDomain()
        }
    }

    private data class RemainingDrainResult(
        val finalTotalBytes: Long,
        val finalChunkCount: Int,
        val jobs: List<kotlinx.coroutines.Deferred<FileChunkEntity>>,
    )

    private suspend fun kotlinx.coroutines.CoroutineScope.drainRemainingWithFirstByte(
        firstByte: Int,
        input: InputStream,
        startChunkIndex: Int,
        startOffset: Long,
        fullDigest: MessageDigest,
        existingChunks: Map<Int, FileChunkEntity>,
        secretKey: javax.crypto.SecretKey?,
        request: UploadRequest,
        estimatedTotalChunks: Int,
        semaphore: Semaphore,
        transferredBytesCounter: AtomicLong,
        completedChunksCounter: AtomicInteger,
        progressMutex: Mutex,
        onProgress: (suspend (Long, Int, Int, String?) -> Unit)?,
    ): RemainingDrainResult {
        var idx = startChunkIndex
        var offset = startOffset
        var bufferedLead: Int? = firstByte
        val extraJobs = mutableListOf<kotlinx.coroutines.Deferred<FileChunkEntity>>()

        while (true) {
            val out = ByteArrayOutputStream(minOf(chunkSizeBytes, 256 * 1024))
            if (bufferedLead != null) {
                out.write(bufferedLead)
                bufferedLead = null
            }
            val buf = ByteArray(64 * 1024)
            while (out.size() < chunkSizeBytes) {
                val want = minOf(buf.size, chunkSizeBytes - out.size())
                val r = input.read(buf, 0, want)
                if (r <= 0) break
                out.write(buf, 0, r)
            }
            val chunkBytes = out.toByteArray()
            if (chunkBytes.isEmpty()) break

            val currentIndex = idx
            val currentOffset = offset
            offset += chunkBytes.size
            fullDigest.update(chunkBytes)

            val already = existingChunks[currentIndex]
            if (already != null && already.rawSize == chunkBytes.size.toLong()) {
                extraJobs += async { already }
            } else {
                extraJobs += async {
                    semaphore.withPermit {
                        val chunkSha = HashingUtils.sha256Hex(chunkBytes)
                        val (payload, ivB64) = if (secretKey != null) {
                            val enc = cipher.encryptChunk(chunkBytes, secretKey, request.fileId, currentIndex)
                            enc.cipherBytes to enc.ivBase64
                        } else {
                            chunkBytes to null
                        }
                        val partName = "${request.fileName}.part${String.format(Locale.US, "%04d", currentIndex + 1)}"
                        val caption = buildChunkCaption(
                            fileName = request.fileName,
                            fileId = request.fileId,
                            chunkIndex = currentIndex,
                            estimatedTotalChunks = maxOf(estimatedTotalChunks, currentIndex + 1),
                            encrypted = request.encrypt,
                        )
                        val uploaded = swarmManager.uploadChunkWithSwarm(
                            partFileName = partName,
                            mimeType = if (request.encrypt) "application/octet-stream" else request.mimeType,
                            payload = payload,
                            caption = caption,
                        )
                        transferredBytesCounter.addAndGet(chunkBytes.size.toLong())
                        val entity = FileChunkEntity(
                            driveFileId = request.fileId,
                            chunkIndex = currentIndex,
                            byteOffset = currentOffset,
                            rawSize = chunkBytes.size.toLong(),
                            storedSize = payload.size.toLong(),
                            telegramMessageId = uploaded.messageId,
                            telegramFileId = uploaded.fileId,
                            telegramFileUniqueId = uploaded.fileUniqueId,
                            uploaderBotId = uploaded.uploaderBotId,
                            sha256 = chunkSha,
                            ivBase64 = ivB64,
                        )
                        chunkDao.upsertChunk(entity)
                        val done = completedChunksCounter.incrementAndGet()
                        progressMutex.withLock {
                            onProgress?.invoke(
                                transferredBytesCounter.get(),
                                done,
                                maxOf(estimatedTotalChunks, currentIndex + 1),
                                "@${uploaded.uploaderUsername.trimStart('@')}",
                            )
                        }
                        entity
                    }
                }
            }
            idx++
            if (chunkBytes.size < chunkSizeBytes) break
        }
        return RemainingDrainResult(offset, idx, extraJobs)
    }

    private fun readUpToChunkSize(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(limit, 256 * 1024))
        val buffer = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val toRead = minOf(buffer.size, limit - out.size())
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun buildChunkCaption(
        fileName: String,
        fileId: String,
        chunkIndex: Int,
        estimatedTotalChunks: Int,
        encrypted: Boolean,
    ): String {
        val badge = if (encrypted) "🔒 FT-Cloud Encrypted" else "☁️ FT-Cloud"
        val displayPart = if (estimatedTotalChunks > 1 || chunkIndex > 0) {
            " [Part ${chunkIndex + 1}/$estimatedTotalChunks]"
        } else {
            ""
        }
        val shownName = if (encrypted) "vault-object-${fileId.take(8)}" else fileName
        return "$badge • $shownName$displayPart\n#ftcloud id:$fileId idx:$chunkIndex"
    }

    companion object {
        // 19 MiB safely fits inside Telegram Bot API's 20 MB getFile download ceiling
        const val DEFAULT_CHUNK_SIZE_BYTES: Int = 19 * 1024 * 1024
    }
}
