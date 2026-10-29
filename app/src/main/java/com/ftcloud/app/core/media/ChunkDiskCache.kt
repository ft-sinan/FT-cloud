package com.ftcloud.app.core.media

import com.ftcloud.app.core.crypto.AesGcmChunkCipher
import com.ftcloud.app.core.crypto.CredentialStore
import com.ftcloud.app.core.crypto.HashingUtils
import com.ftcloud.app.core.model.DriveFile
import com.ftcloud.app.core.model.FileChunk
import com.ftcloud.app.core.telegram.MultiBotSwarmManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class ChunkDiskCache(
    private val cacheRootDir: File,
    private val swarmManager: MultiBotSwarmManager,
    private val cipher: AesGcmChunkCipher,
    private val credentialStore: CredentialStore,
    private val onChunkBotRebound: (suspend (driveFileId: String, chunkIndex: Int, newFileId: String, newBotId: String) -> Unit)? = null,
    private val onChunkSizeResolved: (suspend (driveFileId: String, chunkIndex: Int, resolvedPlainBytes: Long, chunkCount: Int) -> Unit)? = null,
) {
    private val inFlightLocks = ConcurrentHashMap<String, Mutex>()
    private val evictionMutex = Mutex()
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var maxCacheBytes: Long = 512L * 1024L * 1024L

    init {
        if (!cacheRootDir.exists()) {
            cacheRootDir.mkdirs()
        }
    }

    suspend fun getOrFetchPlainChunkFile(
        driveFile: DriveFile,
        chunk: FileChunk,
    ): File = withContext(Dispatchers.IO) {
        if (!cacheRootDir.exists()) cacheRootDir.mkdirs()

        val key = "${driveFile.id}_${chunk.chunkIndex}"
        val targetFile = File(cacheRootDir, "$key.chunk")

        if (targetFile.exists() && (
                (chunk.rawSize > 0L && targetFile.length() == chunk.rawSize) ||
                    (chunk.rawSize <= 0L && targetFile.length() > 0L)
                )
        ) {
            targetFile.setLastModified(System.currentTimeMillis())
            return@withContext targetFile
        }

        val lock = inFlightLocks.computeIfAbsent(key) { Mutex() }
        lock.withLock {
            if (targetFile.exists() && (
                    (chunk.rawSize > 0L && targetFile.length() == chunk.rawSize) ||
                        (chunk.rawSize <= 0L && targetFile.length() > 0L)
                    )
            ) {
                targetFile.setLastModified(System.currentTimeMillis())
                return@withLock targetFile
            }

            val downloadedBytes = swarmManager.downloadChunkBytes(
                chunk = chunk,
                onReboundBotFileId = { newFileId, newBotId ->
                    onChunkBotRebound?.invoke(driveFile.id, chunk.chunkIndex, newFileId, newBotId)
                }
            )

            val plainBytes = if (driveFile.isEncrypted || !chunk.ivBase64.isNullOrBlank()) {
                val iv = requireNotNull(chunk.ivBase64) {
                    "Chunk ${chunk.chunkIndex} of encrypted file ${driveFile.name} is missing IV"
                }
                val secretKey = requireNotNull(credentialStore.getVaultEncryptionKey()) {
                    "Vault encryption key is locked or missing. Unlock encryption in Settings first."
                }
                cipher.decryptChunk(
                    cipherBytes = downloadedBytes,
                    ivBase64 = iv,
                    secretKey = secretKey,
                    fileId = driveFile.id,
                    chunkIndex = chunk.chunkIndex,
                )
            } else {
                downloadedBytes
            }

            if (chunk.sha256.isNotBlank()) {
                val actualHash = HashingUtils.sha256Hex(plainBytes)
                if (!actualHash.equals(chunk.sha256, ignoreCase = true)) {
                    throw IOException(
                        "Integrity check failed on chunk ${chunk.chunkIndex} of ${driveFile.name}"
                    )
                }
            }

            val tempFile = File(cacheRootDir, "$key.tmp.${System.nanoTime()}")
            tempFile.writeBytes(plainBytes)
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            val actualLen = plainBytes.size.toLong()
            if (actualLen > 0L && (chunk.rawSize <= 0L || (driveFile.chunkCount == 1 && chunk.rawSize != actualLen))) {
                onChunkSizeResolved?.invoke(driveFile.id, chunk.chunkIndex, actualLen, driveFile.chunkCount)
            }

            enforceQuotaIfNeeded()
            targetFile
        }
    }

    fun prefetchNextChunkAsync(driveFile: DriveFile, nextChunk: FileChunk?) {
        if (nextChunk == null) return
        val key = "${driveFile.id}_${nextChunk.chunkIndex}"
        val targetFile = File(cacheRootDir, "$key.chunk")
        if (targetFile.exists() && targetFile.length() == nextChunk.rawSize) return

        prefetchScope.launch {
            runCatching {
                getOrFetchPlainChunkFile(driveFile, nextChunk)
            }
        }
    }

    fun currentCacheBytes(): Long {
        val files = cacheRootDir.listFiles() ?: return 0L
        return files.filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        evictionMutex.withLock {
            cacheRootDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }

    private suspend fun enforceQuotaIfNeeded() {
        evictionMutex.withLock {
            val files = cacheRootDir.listFiles()?.filter { it.isFile && it.name.endsWith(".chunk") } ?: return
            var total = files.sumOf { it.length() }
            if (total <= maxCacheBytes) return

            val sortedOldestFirst = files.sortedBy { it.lastModified() }
            for (candidate in sortedOldestFirst) {
                if (total <= (maxCacheBytes * 85L / 100L)) break
                val len = candidate.length()
                if (candidate.delete()) {
                    total -= len
                }
            }
        }
    }
}
