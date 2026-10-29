package com.ftcloud.app

import com.ftcloud.app.core.media.ResolvedStreamTarget
import com.ftcloud.app.core.media.TelegramStreamProxy
import com.ftcloud.app.core.model.DriveFile
import com.ftcloud.app.core.model.FileCategory
import com.ftcloud.app.core.model.FileChunk
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TelegramStreamProxyTest {

    private lateinit var tempDir: File
    private lateinit var fullContent: ByteArray
    private lateinit var proxy: TelegramStreamProxy
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("ftcloud_proxy_test").toFile()
        // 2,550 bytes split across 3 chunks: 1000 + 1000 + 550
        fullContent = ByteArray(2550) { idx -> ((idx * 17 + 3) and 0xFF).toByte() }

        val chunk0Bytes = fullContent.copyOfRange(0, 1000)
        val chunk1Bytes = fullContent.copyOfRange(1000, 2000)
        val chunk2Bytes = fullContent.copyOfRange(2000, 2550)

        val f0 = File(tempDir, "c0.bin").apply { writeBytes(chunk0Bytes) }
        val f1 = File(tempDir, "c1.bin").apply { writeBytes(chunk1Bytes) }
        val f2 = File(tempDir, "c2.bin").apply { writeBytes(chunk2Bytes) }
        val filesByIndex = mapOf(0 to f0, 1 to f1, 2 to f2)

        val driveFile = DriveFile(
            id = "video-123",
            name = "demo.mp4",
            folderId = null,
            mimeType = "video/mp4",
            category = FileCategory.VIDEO,
            sizeBytes = fullContent.size.toLong(),
            chunkCount = 3,
            sha256 = "",
            isEncrypted = false,
            isStarred = false,
            isTrashed = false,
            width = 1920,
            height = 1080,
            durationMs = 12_000L,
            capturedAt = null,
            createdAt = 1000L,
            updatedAt = 1000L,
        )

        val chunks = listOf(
            FileChunk(0, "video-123", 0, 0L, 1000L, 1000L, 10L, "f0", "u0", "b1", "", null),
            FileChunk(0, "video-123", 1, 1000L, 1000L, 1000L, 11L, "f1", "u1", "b1", "", null),
            FileChunk(0, "video-123", 2, 2000L, 550L, 550L, 12L, "f2", "u2", "b1", "", null),
        )

        proxy = TelegramStreamProxy(
            targetResolver = { id ->
                if (id == "video-123") ResolvedStreamTarget(driveFile, chunks) else null
            },
            chunkFileProvider = { _, chunk, _ ->
                filesByIndex.getValue(chunk.chunkIndex)
            }
        )
        proxy.ensureStarted()
    }

    @After
    fun tearDown() {
        proxy.stop()
        tempDir.deleteRecursively()
    }

    @Test
    fun `serves full HTTP 200 stream and multi-chunk HTTP 206 byte ranges`() {
        val url = proxy.buildStreamUrl("video-123")

        // 1. Full 200 OK request
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            assertEquals(200, resp.code)
            assertEquals("bytes", resp.header("Accept-Ranges"))
            assertArrayEquals(fullContent, resp.body!!.bytes())
        }

        // 2. Partial 206 range spanning across chunk 0 -> chunk 1 -> chunk 2 (850..2120)
        val rangeReq = Request.Builder()
            .url(url)
            .header("Range", "bytes=850-2120")
            .get()
            .build()
        client.newCall(rangeReq).execute().use { resp ->
            assertEquals(206, resp.code)
            assertEquals("bytes 850-2120/2550", resp.header("Content-Range"))
            val expectedSlice = fullContent.copyOfRange(850, 2121)
            assertArrayEquals(expectedSlice, resp.body!!.bytes())
        }

        // 3. Suffix byte range (-200)
        val suffixReq = Request.Builder()
            .url(url)
            .header("Range", "bytes=-200")
            .get()
            .build()
        client.newCall(suffixReq).execute().use { resp ->
            assertEquals(206, resp.code)
            assertEquals("bytes 2350-2549/2550", resp.header("Content-Range"))
            assertArrayEquals(fullContent.copyOfRange(2350, 2550), resp.body!!.bytes())
        }

        // 4. Rejects missing token with 403
        val untokenizedUrl = url.substringBefore("?token=")
        client.newCall(Request.Builder().url(untokenizedUrl).get().build()).execute().use { resp ->
            assertEquals(403, resp.code)
        }
    }
}
