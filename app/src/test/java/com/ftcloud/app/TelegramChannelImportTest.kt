package com.ftcloud.app

import com.ftcloud.app.core.crypto.InMemoryCredentialStore
import com.ftcloud.app.core.media.ResolvedStreamTarget
import com.ftcloud.app.core.media.TelegramStreamProxy
import com.ftcloud.app.core.model.DriveFile
import com.ftcloud.app.core.model.FileCategory
import com.ftcloud.app.core.model.FileChunk
import com.ftcloud.app.core.model.FormatUtils
import com.ftcloud.app.core.model.StoredBotToken
import com.ftcloud.app.core.sync.TgDesktopExportRoot
import com.ftcloud.app.core.telegram.MultiBotSwarmManager
import com.ftcloud.app.core.telegram.TelegramBotApiClient
import com.ftcloud.app.core.telegram.TgMessage
import com.ftcloud.app.core.telegram.TgPhotoSize
import com.ftcloud.app.core.telegram.TgVideo
import com.ftcloud.app.core.telegram.TgVideoQuality
import com.ftcloud.app.core.telegram.TokenBucketRateLimiter
import com.ftcloud.app.core.telegram.extractBestMedia
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

class TelegramChannelImportTest {

    private lateinit var server: MockWebServer
    private val botToken = "300003:AAH_import_test_bot_token_12345"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `extractBestMedia picks streamable rendition when original video exceeds 20 MB`() {
        val msg = TgMessage(
            messageId = 4812L,
            date = 1_750_000_000L,
            video = TgVideo(
                fileId = "orig_55mb_video",
                fileUniqueId = "uniq_orig",
                width = 1920,
                height = 1080,
                duration = 120,
                fileName = "clip.mp4",
                mimeType = "video/mp4",
                fileSize = 55L * 1024L * 1024L,
                qualities = listOf(
                    TgVideoQuality(
                        fileId = "rendition_480p",
                        fileUniqueId = "uniq_480",
                        width = 854,
                        height = 480,
                        codec = "h264",
                        fileSize = 8L * 1024L * 1024L,
                    ),
                    TgVideoQuality(
                        fileId = "rendition_720p",
                        fileUniqueId = "uniq_720",
                        width = 1280,
                        height = 720,
                        codec = "h264",
                        fileSize = 16L * 1024L * 1024L,
                    ),
                ),
            ),
        )

        val media = msg.extractBestMedia()
        assertNotNull(media)
        assertEquals("rendition_720p", media!!.fileId)
        assertFalse(media.exceedsBotDownloadLimit)
        assertEquals(16L * 1024L * 1024L, media.sizeBytes)
    }

    @Test
    fun `extractBestMedia flags legacy unsplit video above 20 MB when no rendition exists`() {
        val msg = TgMessage(
            messageId = 5001L,
            video = TgVideo(
                fileId = "huge_legacy_video",
                fileUniqueId = "uniq_huge",
                width = 3840,
                height = 2160,
                duration = 600,
                fileSize = 180L * 1024L * 1024L,
            ),
        )
        val media = msg.extractBestMedia()
        assertNotNull(media)
        assertTrue(media!!.exceedsBotDownloadLimit)
    }

    @Test
    fun `extractBestMedia selects highest resolution photo`() {
        val msg = TgMessage(
            messageId = 99L,
            caption = "Sunset beach",
            photo = listOf(
                TgPhotoSize(fileId = "thumb_id", width = 320, height = 240, fileSize = 14_000L),
                TgPhotoSize(fileId = "full_hd_id", width = 1920, height = 1440, fileSize = 310_000L),
            ),
        )
        val media = msg.extractBestMedia()
        assertNotNull(media)
        assertEquals("full_hd_id", media!!.fileId)
        assertEquals("Sunset beach.jpg", media.fileName)
    }

    @Test
    fun `lazy imported channel photo resolves file_id via scratch chat and downloads bytes`() = runTest {
        val store = InMemoryCredentialStore()
        store.saveChannel("-1001234567890", "Legacy Media Channel")
        store.saveBots(listOf(StoredBotToken(botId = "300003", token = botToken, username = "import_bot")))

        val apiClient = TelegramBotApiClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
        val swarm = MultiBotSwarmManager(
            apiClient = apiClient,
            credentialStore = store,
            rateLimiter = TokenBucketRateLimiter(minBotIntervalMs = 0L),
        )
        swarm.registerBotScratchChat("300003", "777888999")

        val jpegPayload = ByteArray(2048) { idx -> ((idx * 17) and 0xFF).toByte() }
        val forwardedTargetChat = AtomicReference<String>()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.endsWith("/forwardMessage") -> {
                        val body = request.body.readUtf8()
                        // Verify Turbo DM scratch chat is targeted rather than channel
                        if (body.contains("chat_id=777888999")) {
                            forwardedTargetChat.set("777888999")
                        }
                        MockResponse().setResponseCode(200).setBody(
                            """{"ok":true,"result":{"message_id":9100,"date":1750000000,"photo":[{"file_id":"small_p","width":100,"height":100,"file_size":1000},{"file_id":"resolved_photo_id","width":1280,"height":960,"file_size":2048}]}}"""
                        )
                    }
                    path.endsWith("/deleteMessage") -> {
                        MockResponse().setResponseCode(200).setBody("""{"ok":true,"result":true}""")
                    }
                    path.endsWith("/getFile") -> {
                        MockResponse().setResponseCode(200).setBody(
                            """{"ok":true,"result":{"file_id":"resolved_photo_id","file_size":2048,"file_path":"photos/img_4521.jpg"}}"""
                        )
                    }
                    path.contains("/file/bot$botToken/photos/img_4521.jpg") -> {
                        MockResponse().setResponseCode(200).setBody(Buffer().write(jpegPayload))
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val reboundRef = AtomicReference<Pair<String, String>>()
        val lazyChunk = FileChunk(
            driveFileId = "imported-photo-4521",
            chunkIndex = 0,
            byteOffset = 0L,
            rawSize = 0L,
            storedSize = 0L,
            telegramMessageId = 4521L,
            telegramFileId = "",
            telegramFileUniqueId = "",
            uploaderBotId = "",
            sha256 = "",
            ivBase64 = null,
        )

        val actual = swarm.downloadChunkBytes(
            chunk = lazyChunk,
            onReboundBotFileId = { newFileId, newBotId ->
                reboundRef.set(newFileId to newBotId)
            }
        )

        assertArrayEquals(jpegPayload, actual)
        assertEquals("777888999", forwardedTargetChat.get())
        assertEquals("resolved_photo_id" to "300003", reboundRef.get())
    }

    @Test
    fun `stream proxy eagerly resolves zero-sized single-chunk imported photo`() {
        val tempDir = Files.createTempDirectory("ftcloud_lazy_proxy").toFile()
        val payload = "FTCloudLazyChannelPhotoPayload".encodeToByteArray()
        val sample = tempDir.resolve("photo.jpg").apply { writeBytes(payload) }

        val lazyFile = DriveFile(
            id = "lazy-1",
            name = "photo_4521.jpg",
            folderId = "imported",
            mimeType = "image/jpeg",
            category = FileCategory.IMAGE,
            sizeBytes = 0L,
            chunkCount = 1,
            sha256 = "",
            isEncrypted = false,
            isStarred = false,
            isTrashed = false,
            width = 1280,
            height = 960,
            durationMs = null,
            capturedAt = 1_750_000_000_000L,
            createdAt = 1_750_000_000_000L,
            updatedAt = 1_750_000_000_000L,
        )
        val lazyChunk = FileChunk(
            driveFileId = "lazy-1",
            chunkIndex = 0,
            byteOffset = 0L,
            rawSize = 0L,
            storedSize = 0L,
            telegramMessageId = 4521L,
            telegramFileId = "",
            telegramFileUniqueId = "",
            uploaderBotId = "",
            sha256 = "",
            ivBase64 = null,
        )

        val proxy = TelegramStreamProxy(
            targetResolver = { id -> if (id == "lazy-1") ResolvedStreamTarget(lazyFile, listOf(lazyChunk)) else null },
            chunkFileProvider = { _, _, _ -> sample },
        )
        try {
            val url = proxy.buildStreamUrl("lazy-1")
            OkHttpClient().newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                assertEquals(200, resp.code)
                assertArrayEquals(payload, resp.body!!.bytes())
            }
        } finally {
            proxy.stop()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `telegram desktop export json parses mixed text arrays and deep links format cleanly`() {
        val raw = """
            {
              "name": "Old Cloud Vault",
              "type": "private_channel",
              "id": 1234567890,
              "messages": [
                {
                  "id": 7001,
                  "type": "message",
                  "date_unixtime": "1752344530",
                  "photo": "(File not included. Change data exporting settings to download.)",
                  "width": 1280,
                  "height": 720,
                  "text": ["Trip ", {"type": "bold", "text": "Kerala 2025"}]
                }
              ]
            }
        """.trimIndent()
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<TgDesktopExportRoot>(raw)
        assertEquals(1, parsed.messages.size)
        assertEquals(7001L, parsed.messages.first().id)

        assertEquals(
            "https://t.me/c/1234567890/7001",
            FormatUtils.buildTelegramMessageUrl("-1001234567890", 7001L),
        )
        assertEquals(
            "tg://privatepost?channel=1234567890&post=7001",
            FormatUtils.buildTelegramAppDeepLink("-1001234567890", 7001L),
        )
    }
}
