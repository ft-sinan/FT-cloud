package com.ftcloud.app

import com.ftcloud.app.core.crypto.InMemoryCredentialStore
import com.ftcloud.app.core.model.FileChunk
import com.ftcloud.app.core.model.StoredBotToken
import com.ftcloud.app.core.telegram.MultiBotSwarmManager
import com.ftcloud.app.core.telegram.TelegramBotApiClient
import com.ftcloud.app.core.telegram.TokenBucketRateLimiter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MultiBotSwarmManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var credentialStore: InMemoryCredentialStore
    private lateinit var apiClient: TelegramBotApiClient
    private lateinit var swarmManager: MultiBotSwarmManager

    private val bot1Token = "100001:AAH_first_bot_secret_token_12345"
    private val bot2Token = "200002:AAH_second_bot_secret_token_67890"
    private val bot3Token = "300003:AAH_third_bot_secret_token_24680"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        credentialStore = InMemoryCredentialStore()
        apiClient = TelegramBotApiClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
        swarmManager = MultiBotSwarmManager(
            apiClient = apiClient,
            credentialStore = credentialStore,
            rateLimiter = TokenBucketRateLimiter(minBotIntervalMs = 0L),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `normalizeChannelId normalizes numeric and prefixed channel IDs`() {
        assertEquals("-1001234567890", TelegramBotApiClient.normalizeChannelId("-1001234567890"))
        assertEquals("-1001234567890", TelegramBotApiClient.normalizeChannelId("1001234567890"))
        assertEquals("-1009876543", TelegramBotApiClient.normalizeChannelId("9876543"))
        assertEquals("@my_cloud_vault", TelegramBotApiClient.normalizeChannelId(" @my_cloud_vault "))
    }

    @Test
    fun `uploadChunkWithSwarm fails over immediately when Bot 1 hits HTTP 429 FloodWait`() = runTest {
        credentialStore.saveChannel("-100555666777", "FT Vault")
        credentialStore.saveBots(
            listOf(
                StoredBotToken(botId = "100001", token = bot1Token, username = "ft_bot_1"),
                StoredBotToken(botId = "200002", token = bot2Token, username = "ft_bot_2"),
            )
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains(bot1Token) && path.endsWith("/sendDocument") -> {
                        MockResponse()
                            .setResponseCode(429)
                            .setBody(
                                """{"ok":false,"error_code":429,"description":"Too Many Requests: retry after 45","parameters":{"retry_after":45}}"""
                            )
                    }

                    path.contains(bot2Token) && path.endsWith("/sendDocument") -> {
                        MockResponse()
                            .setResponseCode(200)
                            .setBody(
                                """{"ok":true,"result":{"message_id":777,"date":1760000000,"document":{"file_id":"bot2_doc_id","file_unique_id":"uniq_777","file_size":512}}}"""
                            )
                    }

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val payload = "hello-swarm-cloud".encodeToByteArray()
        val uploaded = swarmManager.uploadChunkWithSwarm(
            partFileName = "hello.txt",
            mimeType = "text/plain",
            payload = payload,
            caption = "test",
        )

        assertEquals(777L, uploaded.messageId)
        assertEquals("bot2_doc_id", uploaded.fileId)
        assertEquals("200002", uploaded.uploaderBotId)
    }

    @Test
    fun `downloadChunkBytes recovers bot-scoped file_id when original uploader bot was removed`() = runTest {
        // Only Bot 2 is currently in the swarm, while chunk was originally uploaded by Bot 999999
        credentialStore.saveChannel("-100555666777", "FT Vault")
        credentialStore.saveBots(
            listOf(StoredBotToken(botId = "200002", token = bot2Token, username = "ft_bot_2"))
        )

        val expectedContent = ByteArray(2048) { (it % 251).toByte() }
        val reboundRef = AtomicReference<Pair<String, String>?>(null)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.endsWith("/forwardMessage") -> {
                        MockResponse().setResponseCode(200).setBody(
                            """{"ok":true,"result":{"message_id":901,"date":1760000100,"document":{"file_id":"minted_bot2_file_id","file_unique_id":"uniq_orig"}}}"""
                        )
                    }

                    path.endsWith("/deleteMessage") -> {
                        MockResponse().setResponseCode(200).setBody("""{"ok":true,"result":true}""")
                    }

                    path.endsWith("/getFile") -> {
                        MockResponse().setResponseCode(200).setBody(
                            """{"ok":true,"result":{"file_id":"minted_bot2_file_id","file_unique_id":"uniq_orig","file_size":2048,"file_path":"documents/part_0001.bin"}}"""
                        )
                    }

                    path.contains("/file/bot$bot2Token/documents/part_0001.bin") -> {
                        MockResponse()
                            .setResponseCode(200)
                            .setBody(Buffer().write(expectedContent))
                    }

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val orphanChunk = FileChunk(
            driveFileId = "file-xyz",
            chunkIndex = 0,
            byteOffset = 0L,
            rawSize = expectedContent.size.toLong(),
            storedSize = expectedContent.size.toLong(),
            telegramMessageId = 500L,
            telegramFileId = "old_removed_bot_file_id",
            telegramFileUniqueId = "uniq_orig",
            uploaderBotId = "999999",
            sha256 = "",
            ivBase64 = null,
        )

        val downloaded = swarmManager.downloadChunkBytes(
            chunk = orphanChunk,
            onReboundBotFileId = { newFileId, newBotId ->
                reboundRef.set(newFileId to newBotId)
            }
        )

        assertArrayEquals(expectedContent, downloaded)
        val rebound = reboundRef.get()
        assertNotNull(rebound)
        assertEquals("minted_bot2_file_id" to "200002", rebound)
    }

    @Test
    fun `three bots distribute concurrent uploads and scans across all 3 bots`() = runTest {
        credentialStore.saveChannel("-100555666777", "FT Vault")
        credentialStore.saveBots(
            listOf(
                StoredBotToken(botId = "100001", token = bot1Token, username = "ft_bot_1"),
                StoredBotToken(botId = "200002", token = bot2Token, username = "ft_bot_2"),
                StoredBotToken(botId = "300003", token = bot3Token, username = "ft_bot_3"),
            )
        )
        swarmManager.registerScratchChatForAllBots("987654321")

        val hitsByBot = ConcurrentHashMap<String, AtomicInteger>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val botKey = when {
                    path.contains(bot1Token) -> "100001"
                    path.contains(bot2Token) -> "200002"
                    path.contains(bot3Token) -> "300003"
                    else -> "unknown"
                }
                return when {
                    path.endsWith("/sendDocument") -> {
                        hitsByBot.computeIfAbsent(botKey) { AtomicInteger(0) }.incrementAndGet()
                        MockResponse().setResponseCode(200).setBody(
                            """{"ok":true,"result":{"message_id":101,"date":1760000000,"document":{"file_id":"doc_$botKey","file_unique_id":"uniq_101","file_size":64}}}"""
                        )
                    }
                    path.endsWith("/forwardMessage") -> {
                        hitsByBot.computeIfAbsent(botKey) { AtomicInteger(0) }.incrementAndGet()
                        MockResponse().setResponseCode(200).setBody(
                            """{"ok":true,"result":{"message_id":202,"date":1760000000,"document":{"file_id":"fwd_$botKey","file_unique_id":"uniq_202","file_size":64}}}"""
                        )
                    }
                    path.endsWith("/deleteMessage") -> {
                        MockResponse().setResponseCode(200).setBody("""{"ok":true,"result":true}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        coroutineScope {
            val uploads = (1..6).map { idx ->
                async {
                    swarmManager.uploadChunkWithSwarm(
                        partFileName = "part_$idx.bin",
                        mimeType = "application/octet-stream",
                        payload = ByteArray(64) { idx.toByte() },
                        caption = "chunk $idx",
                    )
                }
            }
            val scans = (10L..15L).map { msgId ->
                async { swarmManager.inspectChannelMessage(msgId) }
            }
            uploads.awaitAll()
            scans.awaitAll()
        }

        assertEquals(3, hitsByBot.size)
        assertTrue((hitsByBot["100001"]?.get() ?: 0) >= 2)
        assertTrue((hitsByBot["200002"]?.get() ?: 0) >= 2)
        assertTrue((hitsByBot["300003"]?.get() ?: 0) >= 2)
    }

    @Test
    fun `inspectChannelMessage drops unstarted bot DM on 400 chat not found without skipping post`() = runTest {
        credentialStore.saveChannel("-100555666777", "FT Vault")
        credentialStore.saveBots(
            listOf(StoredBotToken(botId = "100001", token = bot1Token, username = "ft_bot_1"))
        )
        swarmManager.registerBotScratchChat("100001", "987654321")

        val forwardAttempts = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.endsWith("/forwardMessage") -> {
                        val attempt = forwardAttempts.incrementAndGet()
                        if (attempt == 1) {
                            MockResponse().setResponseCode(400).setBody(
                                """{"ok":false,"error_code":400,"description":"Bad Request: chat not found"}"""
                            )
                        } else {
                            MockResponse().setResponseCode(200).setBody(
                                """{"ok":true,"result":{"message_id":333,"date":1760000000,"document":{"file_id":"doc_ok","file_unique_id":"uniq_333","file_size":128}}}"""
                            )
                        }
                    }
                    path.endsWith("/deleteMessage") -> {
                        MockResponse().setResponseCode(200).setBody("""{"ok":true,"result":true}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val inspected = swarmManager.inspectChannelMessage(42L)
        assertNotNull(inspected)
        assertEquals(2, forwardAttempts.get())
        assertTrue(swarmManager.getBotScratchChatsSnapshot().isEmpty())
    }
}
