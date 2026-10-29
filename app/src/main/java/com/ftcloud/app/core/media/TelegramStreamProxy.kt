package com.ftcloud.app.core.media

import com.ftcloud.app.core.model.DriveFile
import com.ftcloud.app.core.model.FileChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

data class ResolvedStreamTarget(
    val file: DriveFile,
    val chunks: List<FileChunk>,
)

fun interface StreamTargetResolver {
    suspend fun resolve(fileId: String): ResolvedStreamTarget?
}

fun interface ChunkFileProvider {
    suspend fun provideChunkFile(
        file: DriveFile,
        chunk: FileChunk,
        nextChunk: FileChunk?,
    ): File
}

class TelegramStreamProxy(
    private val targetResolver: StreamTargetResolver,
    private val chunkFileProvider: ChunkFileProvider,
) {
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionToken: String = UUID.randomUUID().toString().replace("-", "")

    @Volatile
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val listeningPort: Int
        get() = serverSocket?.localPort ?: -1

    @Synchronized
    fun ensureStarted(): Int {
        val existing = serverSocket
        if (existing != null && !existing.isClosed) {
            return existing.localPort
        }

        val socket = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        acceptJob = proxyScope.launch {
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch {
                    handleClientSocket(client)
                }
            }
        }
        return socket.localPort
    }

    fun buildStreamUrl(fileId: String): String {
        val port = ensureStarted()
        return "http://127.0.0.1:$port/stream/$fileId?token=$sessionToken"
    }

    @Synchronized
    fun stop() {
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        proxyScope.cancel()
    }

    private fun handleClientSocket(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 60_000
            client.tcpNoDelay = true
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return

                val method = parts[0].uppercase(Locale.ROOT)
                val rawTarget = parts[1]

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val headerName = line.substring(0, colon).trim().lowercase(Locale.ROOT)
                        val headerVal = line.substring(colon + 1).trim()
                        headers[headerName] = headerVal
                    }
                }

                val out = BufferedOutputStream(client.getOutputStream(), 64 * 1024)
                if (method != "GET" && method != "HEAD") {
                    writeSimpleResponse(out, "405 Method Not Allowed", "Method Not Allowed")
                    return
                }

                val pathPart = rawTarget.substringBefore('?')
                val queryPart = rawTarget.substringAfter('?', "")
                val queryMap = parseQuery(queryPart)

                if (queryMap["token"] != sessionToken) {
                    writeSimpleResponse(out, "403 Forbidden", "Invalid session token")
                    return
                }

                val fileId = pathPart.removePrefix("/stream/").trim('/')
                if (fileId.isBlank()) {
                    writeSimpleResponse(out, "400 Bad Request", "Missing fileId")
                    return
                }

                val resolved = runBlocking { targetResolver.resolve(fileId) }
                if (resolved == null || resolved.chunks.isEmpty()) {
                    writeSimpleResponse(out, "404 Not Found", "Cloud file not found")
                    return
                }

                val driveFile = resolved.file
                var sortedChunks = resolved.chunks.sortedBy { it.chunkIndex }
                var totalSize = driveFile.sizeBytes.coerceAtLeast(sortedChunks.sumOf { it.rawSize })
                val contentType = driveFile.mimeType.ifBlank { "application/octet-stream" }

                if (
                    totalSize == 0L &&
                    sortedChunks.size == 1 &&
                    (sortedChunks[0].telegramMessageId > 0L || sortedChunks[0].telegramFileId.isNotBlank())
                ) {
                    val eagerFile = runCatching {
                        runBlocking { chunkFileProvider.provideChunkFile(driveFile, sortedChunks[0], null) }
                    }.getOrNull()
                    if (eagerFile != null && eagerFile.length() > 0L) {
                        val realLen = eagerFile.length()
                        totalSize = realLen
                        sortedChunks = listOf(sortedChunks[0].copy(rawSize = realLen, storedSize = realLen))
                    }
                }

                if (totalSize == 0L) {
                    writeHeaders(
                        out = out,
                        status = "200 OK",
                        headers = listOf(
                            "Content-Type" to contentType,
                            "Content-Length" to "0",
                            "Accept-Ranges" to "bytes",
                            "Connection" to "close",
                        )
                    )
                    out.flush()
                    return
                }

                val rangeHeader = headers["range"]
                val byteRange = if (!rangeHeader.isNullOrBlank()) {
                    parseByteRange(rangeHeader, totalSize) ?: run {
                        writeHeaders(
                            out = out,
                            status = "416 Range Not Satisfiable",
                            headers = listOf(
                                "Content-Range" to "bytes */$totalSize",
                                "Content-Length" to "0",
                                "Connection" to "close",
                            )
                        )
                        out.flush()
                        return
                    }
                } else {
                    null
                }

                val startByte = byteRange?.first ?: 0L
                val endByte = byteRange?.second ?: (totalSize - 1L)
                val contentLength = (endByte - startByte + 1L)

                if (byteRange != null) {
                    writeHeaders(
                        out = out,
                        status = "206 Partial Content",
                        headers = listOf(
                            "Content-Type" to contentType,
                            "Accept-Ranges" to "bytes",
                            "Content-Range" to "bytes $startByte-$endByte/$totalSize",
                            "Content-Length" to contentLength.toString(),
                            "Cache-Control" to "no-store",
                            "Connection" to "close",
                        )
                    )
                } else {
                    writeHeaders(
                        out = out,
                        status = "200 OK",
                        headers = listOf(
                            "Content-Type" to contentType,
                            "Accept-Ranges" to "bytes",
                            "Content-Length" to contentLength.toString(),
                            "Cache-Control" to "no-store",
                            "Connection" to "close",
                        )
                    )
                }
                out.flush()

                if (method == "HEAD") {
                    return
                }

                streamByteWindow(
                    out = out,
                    driveFile = driveFile,
                    chunks = sortedChunks,
                    startByte = startByte,
                    endByte = endByte,
                )
                out.flush()
            } catch (_: SocketException) {
                // Normal client cancellation during ExoPlayer scrubbing/seeking
            } catch (_: Exception) {
                // Connection closed or upstream error mid-stream
            }
        }
    }

    private fun streamByteWindow(
        out: OutputStream,
        driveFile: DriveFile,
        chunks: List<FileChunk>,
        startByte: Long,
        endByte: Long,
    ) {
        var cursor = startByte
        val ioBuffer = ByteArray(64 * 1024)

        for (idx in chunks.indices) {
            if (cursor > endByte) break
            val chunk = chunks[idx]
            val chunkStart = chunk.byteOffset
            val chunkEndExclusive = chunkStart + chunk.rawSize
            if (chunkEndExclusive <= cursor) continue
            if (chunkStart > endByte) break

            val nextChunk = chunks.getOrNull(idx + 1)
            val localChunkFile = runBlocking {
                chunkFileProvider.provideChunkFile(driveFile, chunk, nextChunk)
            }

            val offsetInChunk = (cursor - chunkStart).coerceAtLeast(0L)
            val maxFromThisChunk = minOf(chunk.rawSize - offsetInChunk, endByte - cursor + 1L)

            RandomAccessFile(localChunkFile, "r").use { raf ->
                raf.seek(offsetInChunk)
                var remainingInChunk = maxFromThisChunk
                while (remainingInChunk > 0L) {
                    val toRead = minOf(ioBuffer.size.toLong(), remainingInChunk).toInt()
                    val read = raf.read(ioBuffer, 0, toRead)
                    if (read <= 0) break
                    out.write(ioBuffer, 0, read)
                    remainingInChunk -= read
                    cursor += read
                }
            }
        }
    }

    private fun parseByteRange(header: String, totalSize: Long): Pair<Long, Long>? {
        val trimmed = header.trim()
        if (!trimmed.startsWith("bytes=", ignoreCase = true)) return null
        val spec = trimmed.substring(6).substringBefore(',').trim()
        val dash = spec.indexOf('-')
        if (dash < 0) return null

        val startPart = spec.substring(0, dash).trim()
        val endPart = spec.substring(dash + 1).trim()

        return if (startPart.isEmpty()) {
            // Suffix byte-range-spec: bytes=-500
            val suffixLen = endPart.toLongOrNull() ?: return null
            if (suffixLen <= 0L) return null
            val start = (totalSize - suffixLen).coerceAtLeast(0L)
            start to (totalSize - 1L)
        } else {
            val start = startPart.toLongOrNull() ?: return null
            if (start < 0L || start >= totalSize) return null
            val end = if (endPart.isEmpty()) {
                totalSize - 1L
            } else {
                val parsedEnd = endPart.toLongOrNull() ?: return null
                if (parsedEnd < start) return null
                parsedEnd.coerceAtMost(totalSize - 1L)
            }
            start to end
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) null
            else {
                val k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8.name())
                val v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8.name())
                k to v
            }
        }.toMap()
    }

    private fun writeSimpleResponse(out: OutputStream, status: String, message: String) {
        val bytes = message.encodeToByteArray()
        writeHeaders(
            out = out,
            status = status,
            headers = listOf(
                "Content-Type" to "text/plain; charset=utf-8",
                "Content-Length" to bytes.size.toString(),
                "Connection" to "close",
            )
        )
        out.write(bytes)
        out.flush()
    }

    private fun writeHeaders(
        out: OutputStream,
        status: String,
        headers: List<Pair<String, String>>,
    ) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(status).append("\r\n")
        for ((k, v) in headers) {
            sb.append(k).append(": ").append(v).append("\r\n")
        }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(StandardCharsets.US_ASCII))
    }
}
