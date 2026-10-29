package com.ftcloud.app.core.transfer

import com.ftcloud.app.core.crypto.HashingUtils.toHexStringSimple
import com.ftcloud.app.core.database.ChunkDao
import com.ftcloud.app.core.database.FileDao
import com.ftcloud.app.core.media.ChunkDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest

class ChunkedDownloadEngine(
    private val fileDao: FileDao,
    private val chunkDao: ChunkDao,
    private val chunkDiskCache: ChunkDiskCache,
) {
    suspend fun downloadToFile(
        driveFileId: String,
        destinationFile: File,
        onProgress: (suspend (downloadedBytes: Long, completedChunks: Int, totalChunks: Int) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        destinationFile.parentFile?.mkdirs()
        val tempFile = File(
            destinationFile.parentFile ?: destinationFile.absoluteFile.parentFile,
            "${destinationFile.name}.part"
        )
        try {
            FileOutputStream(tempFile).use { out ->
                downloadToStream(
                    driveFileId = driveFileId,
                    outputStream = out,
                    onProgress = onProgress,
                )
            }
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
            destinationFile
        } catch (err: Throwable) {
            tempFile.delete()
            throw err
        }
    }

    suspend fun downloadToStream(
        driveFileId: String,
        outputStream: OutputStream,
        onProgress: (suspend (downloadedBytes: Long, completedChunks: Int, totalChunks: Int) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        val fileEntity = fileDao.getById(driveFileId)
            ?: throw IOException("File $driveFileId no longer exists in local index")
        val driveFile = fileEntity.toDomain()
        val chunks = chunkDao.getChunksForFile(driveFileId)
            .sortedBy { it.chunkIndex }
            .map { it.toDomain() }

        require(chunks.isNotEmpty()) { "File ${driveFile.name} has no recorded cloud chunks" }

        val fullDigest = MessageDigest.getInstance("SHA-256")
        val copyBuffer = ByteArray(64 * 1024)
        var writtenBytes = 0L

        for ((idx, chunk) in chunks.withIndex()) {
            ensureActive()
            chunkDiskCache.prefetchNextChunkAsync(driveFile, chunks.getOrNull(idx + 1))

            val plainChunkFile = chunkDiskCache.getOrFetchPlainChunkFile(driveFile, chunk)
            plainChunkFile.inputStream().use { input ->
                while (true) {
                    val read = input.read(copyBuffer)
                    if (read <= 0) break
                    outputStream.write(copyBuffer, 0, read)
                    fullDigest.update(copyBuffer, 0, read)
                    writtenBytes += read
                }
            }
            onProgress?.invoke(writtenBytes, idx + 1, chunks.size)
        }
        outputStream.flush()

        if (driveFile.sha256.isNotBlank()) {
            val actualFullSha = fullDigest.digest().toHexStringSimple()
            if (!actualFullSha.equals(driveFile.sha256, ignoreCase = true)) {
                throw IOException("End-to-end SHA-256 verification failed for ${driveFile.name}")
            }
        }
    }
}
