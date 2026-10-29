package com.ftcloud.app.core.telegram

import com.ftcloud.app.core.crypto.CredentialStore
import com.ftcloud.app.core.model.BotRuntimeStatus
import com.ftcloud.app.core.model.FileChunk
import com.ftcloud.app.core.model.FormatUtils
import com.ftcloud.app.core.model.StoredBotToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class UploadedChunkResult(
    val messageId: Long,
    val fileId: String,
    val fileUniqueId: String,
    val uploaderBotId: String,
    val uploaderUsername: String,
)

data class ChannelVerificationResult(
    val channelId: String,
    val channelTitle: String,
    val verifiedBots: List<StoredBotToken>,
    val pinnedManifestMessage: TgMessage?,
)

class MultiBotSwarmManager(
    private val apiClient: TelegramBotApiClient,
    private val credentialStore: CredentialStore,
    private val rateLimiter: TokenBucketRateLimiter = TokenBucketRateLimiter(),
) {
    private class MutableBotMetrics {
        val activeRequests = AtomicInteger(0)
        val cooldownUntilMs = AtomicLong(0L)
        val uploadedBytes = AtomicLong(0L)
        val downloadedBytes = AtomicLong(0L)
        @Volatile var isHealthy: Boolean = true
        @Volatile var lastLatencyMs: Long? = null
        @Volatile var lastError: String? = null
    }

    private val metricsByBotId = ConcurrentHashMap<String, MutableBotMetrics>()
    private val scratchChatIdByBotId = ConcurrentHashMap<String, String>()
    private val roundRobinCursor = AtomicInteger(0)
    private val selectionMutex = Mutex()

    private val _runtimeStatuses = MutableStateFlow<List<BotRuntimeStatus>>(emptyList())
    val runtimeStatuses: StateFlow<List<BotRuntimeStatus>> = _runtimeStatuses.asStateFlow()

    init {
        refreshStatusSnapshot()
    }

    fun refreshStatusSnapshot() {
        val now = System.currentTimeMillis()
        val bots = credentialStore.getBots()
        _runtimeStatuses.value = bots.map { bot ->
            val m = metricsByBotId.computeIfAbsent(bot.botId) { MutableBotMetrics() }
            val cooldownRem = (m.cooldownUntilMs.get() - now).coerceAtLeast(0L)
            BotRuntimeStatus(
                botId = bot.botId,
                username = bot.username,
                displayName = bot.displayName,
                maskedToken = FormatUtils.maskBotToken(bot.token),
                isHealthy = m.isHealthy && cooldownRem == 0L,
                activeRequests = m.activeRequests.get(),
                cooldownRemainingMs = cooldownRem,
                uploadedBytes = m.uploadedBytes.get(),
                downloadedBytes = m.downloadedBytes.get(),
                lastLatencyMs = m.lastLatencyMs,
                lastError = m.lastError,
            )
        }
    }

    suspend fun verifyAndConnectSwarm(
        rawChannelId: String,
        rawTokens: List<String>,
    ): ChannelVerificationResult {
        val channelId = TelegramBotApiClient.normalizeChannelId(rawChannelId)
        require(channelId.isNotBlank()) { "Channel ID cannot be empty." }

        val cleanedTokens = rawTokens
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        require(cleanedTokens.isNotEmpty()) { "Please provide at least one Telegram Bot token." }

        val verifiedBots = mutableListOf<StoredBotToken>()
        var resolvedTitle = channelId
        var pinnedMessage: TgMessage? = null

        for (token in cleanedTokens) {
            val botId = TelegramBotApiClient.extractBotId(token)
                ?: throw IllegalArgumentException("Invalid bot token format: ${FormatUtils.maskBotToken(token)}")

            val startMs = System.currentTimeMillis()
            val me = apiClient.getMe(token)
            val chat = apiClient.getChat(token, channelId)
            val latency = System.currentTimeMillis() - startMs

            if (!chat.title.isNullOrBlank()) {
                resolvedTitle = chat.title
            }
            if (pinnedMessage == null && chat.pinnedMessage != null) {
                pinnedMessage = chat.pinnedMessage
            }

            val m = metricsByBotId.computeIfAbsent(botId) { MutableBotMetrics() }
            m.isHealthy = true
            m.lastLatencyMs = latency
            m.lastError = null
            m.cooldownUntilMs.set(0L)

            verifiedBots += StoredBotToken(
                botId = botId,
                token = token,
                username = me.username.orEmpty(),
                displayName = me.firstName,
            )
        }

        credentialStore.saveChannel(channelId, resolvedTitle)
        credentialStore.saveBots(verifiedBots)
        refreshStatusSnapshot()

        return ChannelVerificationResult(
            channelId = channelId,
            channelTitle = resolvedTitle,
            verifiedBots = verifiedBots,
            pinnedManifestMessage = pinnedMessage,
        )
    }

    suspend fun addAndVerifyBot(rawToken: String): StoredBotToken {
        val token = rawToken.trim()
        val botId = TelegramBotApiClient.extractBotId(token)
            ?: throw IllegalArgumentException("Invalid Telegram Bot token format.")
        val channelId = credentialStore.getChannelId()
        require(channelId.isNotBlank()) { "Connect a Telegram channel before adding swarm bots." }

        val startMs = System.currentTimeMillis()
        val me = apiClient.getMe(token)
        apiClient.getChat(token, channelId)
        val latency = System.currentTimeMillis() - startMs

        val m = metricsByBotId.computeIfAbsent(botId) { MutableBotMetrics() }
        m.isHealthy = true
        m.lastLatencyMs = latency
        m.lastError = null
        m.cooldownUntilMs.set(0L)

        val stored = StoredBotToken(
            botId = botId,
            token = token,
            username = me.username.orEmpty(),
            displayName = me.firstName,
        )
        val updated = (credentialStore.getBots().filterNot { it.botId == botId } + stored)
        credentialStore.saveBots(updated)
        refreshStatusSnapshot()
        return stored
    }

    fun removeBot(botId: String) {
        val current = credentialStore.getBots()
        require(current.size > 1) { "At least one bot must remain in the swarm." }
        credentialStore.saveBots(current.filterNot { it.botId == botId })
        metricsByBotId.remove(botId)
        refreshStatusSnapshot()
    }

    suspend fun pingAllBots(): List<BotRuntimeStatus> {
        val channelId = credentialStore.getChannelId()
        for (bot in credentialStore.getBots()) {
            val m = metricsByBotId.computeIfAbsent(bot.botId) { MutableBotMetrics() }
            val start = System.currentTimeMillis()
            runCatching {
                apiClient.getMe(bot.token)
                if (channelId.isNotBlank()) {
                    apiClient.getChat(bot.token, channelId)
                }
            }.onSuccess {
                m.isHealthy = true
                m.lastLatencyMs = System.currentTimeMillis() - start
                m.lastError = null
            }.onFailure { err ->
                m.isHealthy = false
                m.lastError = err.message
            }
        }
        refreshStatusSnapshot()
        return _runtimeStatuses.value
    }

    suspend fun uploadChunkWithSwarm(
        partFileName: String,
        mimeType: String,
        payload: ByteArray,
        caption: String,
        maxAttempts: Int = 5,
        onProgress: ((Long) -> Unit)? = null,
    ): UploadedChunkResult {
        val channelId = credentialStore.getChannelId()
        require(channelId.isNotBlank()) { "Telegram storage channel is not configured." }

        var lastException: Throwable? = null
        repeat(maxAttempts) { attempt ->
            val bot = acquireBestBot()
            val metrics = metricsByBotId.computeIfAbsent(bot.botId) { MutableBotMetrics() }
            metrics.activeRequests.incrementAndGet()
            refreshStatusSnapshot()

            try {
                rateLimiter.acquireSendSlot(bot.botId)
                val startMs = System.currentTimeMillis()
                val message = apiClient.sendDocument(
                    token = bot.token,
                    chatId = channelId,
                    partFileName = partFileName,
                    mimeType = mimeType,
                    bytes = payload,
                    caption = caption,
                    disableNotification = true,
                    onProgress = onProgress,
                )
                val document = message.document
                    ?: throw IOException("Telegram sendDocument succeeded without a document attachment")

                metrics.isHealthy = true
                metrics.lastLatencyMs = System.currentTimeMillis() - startMs
                metrics.lastError = null
                metrics.uploadedBytes.addAndGet(payload.size.toLong())

                return UploadedChunkResult(
                    messageId = message.messageId,
                    fileId = document.fileId,
                    fileUniqueId = document.fileUniqueId,
                    uploaderBotId = bot.botId,
                    uploaderUsername = bot.username.ifBlank { bot.displayName.ifBlank { "Bot ${bot.botId}" } },
                )
            } catch (apiErr: TelegramApiException) {
                lastException = apiErr
                if (apiErr.isFloodWait) {
                    val waitSec = (apiErr.retryAfterSeconds ?: 3).coerceIn(1, 120)
                    metrics.cooldownUntilMs.set(System.currentTimeMillis() + waitSec * 1000L)
                    metrics.lastError = "Rate limited (${waitSec}s cooldown)"
                } else {
                    metrics.lastError = apiErr.apiDescription
                    if (apiErr.errorCode in setOf(401, 403)) {
                        metrics.isHealthy = false
                    }
                    if (attempt < maxAttempts - 1) {
                        delay(300L * (attempt + 1))
                    }
                }
            } catch (ioErr: IOException) {
                lastException = ioErr
                metrics.lastError = ioErr.message
                if (attempt < maxAttempts - 1) {
                    delay(400L * (attempt + 1))
                }
            } finally {
                metrics.activeRequests.decrementAndGet()
                refreshStatusSnapshot()
            }
        }

        throw lastException ?: IOException("Failed to upload chunk across Telegram Bot Swarm")
    }

    suspend fun downloadChunkBytes(
        chunk: FileChunk,
        rangeStart: Long? = null,
        rangeEndInclusive: Long? = null,
        onProgress: ((Long) -> Unit)? = null,
        onReboundBotFileId: (suspend (newFileId: String, newBotId: String) -> Unit)? = null,
    ): ByteArray {
        val bots = credentialStore.getBots()
        require(bots.isNotEmpty()) { "No bots configured in FT-Cloud swarm." }
        val channelId = credentialStore.getChannelId()

        val preferredBot = if (chunk.telegramFileId.isNotBlank()) {
            bots.firstOrNull { it.botId == chunk.uploaderBotId }
        } else {
            null
        }
        if (preferredBot != null) {
            try {
                return downloadWithSpecificBot(
                    bot = preferredBot,
                    telegramFileId = chunk.telegramFileId,
                    rangeStart = rangeStart,
                    rangeEndInclusive = rangeEndInclusive,
                    onProgress = onProgress,
                )
            } catch (apiErr: TelegramApiException) {
                if (apiErr.isFileTooBigForBotApi) {
                    throw TelegramBotSizeLimitException(
                        telegramMessageId = chunk.telegramMessageId,
                        reportedSizeBytes = chunk.rawSize,
                    )
                }
            } catch (_: Exception) {
                // Fall through to cross-bot message recovery
            }
        }

        // Cross-bot failover & lazy message resolution: mint a bot-scoped file_id for chunk.telegramMessageId
        require(channelId.isNotBlank() && chunk.telegramMessageId > 0L) {
            "Original uploader bot (${chunk.uploaderBotId}) unavailable and message ID missing for recovery."
        }
        val (fallbackBot, forwarded) = inspectChannelMessage(chunk.telegramMessageId)
            ?: throw IOException("Telegram channel message #${chunk.telegramMessageId} was deleted or has no forwardable media")

        val extracted = forwarded.extractBestMedia(chunk.telegramMessageId)
            ?: throw IOException("Telegram channel message #${chunk.telegramMessageId} contained no media attachment")

        if (extracted.exceedsBotDownloadLimit) {
            throw TelegramBotSizeLimitException(
                telegramMessageId = chunk.telegramMessageId,
                reportedSizeBytes = extracted.sizeBytes,
            )
        }

        val newFileId = extracted.fileId
        onReboundBotFileId?.invoke(newFileId, fallbackBot.botId)
        try {
            return downloadWithSpecificBot(
                bot = fallbackBot,
                telegramFileId = newFileId,
                rangeStart = rangeStart,
                rangeEndInclusive = rangeEndInclusive,
                onProgress = onProgress,
            )
        } catch (apiErr: TelegramApiException) {
            if (apiErr.isFileTooBigForBotApi) {
                throw TelegramBotSizeLimitException(
                    telegramMessageId = chunk.telegramMessageId,
                    reportedSizeBytes = extracted.sizeBytes.coerceAtLeast(chunk.rawSize),
                )
            }
            throw apiErr
        }
    }

    fun registerBotScratchChat(botId: String, chatId: String) {
        if (botId.isNotBlank() && chatId.isNotBlank()) {
            scratchChatIdByBotId[botId] = chatId
        }
    }

    fun getBotScratchChatsSnapshot(): Map<String, String> = scratchChatIdByBotId.toMap()

    suspend fun probeLatestChannelMessageId(): Long {
        val channelId = credentialStore.getChannelId()
        require(channelId.isNotBlank()) { "Channel ID is not configured." }
        val bot = acquireBestBot()
        rateLimiter.acquireSendSlot(bot.botId)
        val probe = apiClient.sendTextMessage(
            token = bot.token,
            chatId = channelId,
            text = "🔄 FT-Cloud index probe",
            disableNotification = true,
        )
        runCatching {
            apiClient.deleteMessage(bot.token, channelId, probe.messageId)
        }
        return (probe.messageId - 1L).coerceAtLeast(0L)
    }

    /**
     * Inspects a historical message in the configured channel via silent forward + immediate cleanup.
     * Returns null when the message ID was deleted, is a service message, or cannot be forwarded.
     */
    suspend fun inspectChannelMessage(
        messageId: Long,
        maxAttempts: Int = 4,
    ): Pair<StoredBotToken, TgMessage>? {
        val channelId = credentialStore.getChannelId()
        require(channelId.isNotBlank()) { "Channel ID is not configured." }

        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            val bot = acquireBestBot()
            val metrics = metricsByBotId.computeIfAbsent(bot.botId) { MutableBotMetrics() }
            val targetScratchChat = scratchChatIdByBotId[bot.botId] ?: channelId
            metrics.activeRequests.incrementAndGet()
            refreshStatusSnapshot()
            try {
                rateLimiter.acquireSendSlot(bot.botId)
                val forwarded = apiClient.forwardMessage(
                    token = bot.token,
                    chatId = targetScratchChat,
                    fromChatId = channelId,
                    messageId = messageId,
                    disableNotification = true,
                )
                runCatching {
                    apiClient.deleteMessage(bot.token, targetScratchChat, forwarded.messageId)
                }
                metrics.isHealthy = true
                metrics.lastError = null
                return bot to forwarded
            } catch (apiErr: TelegramApiException) {
                lastError = apiErr
                val desc = apiErr.apiDescription.lowercase()
                if (apiErr.isFloodWait) {
                    val waitSec = (apiErr.retryAfterSeconds ?: 5).coerceIn(1, 180)
                    metrics.cooldownUntilMs.set(System.currentTimeMillis() + waitSec * 1000L)
                    metrics.lastError = "Rate limited (${waitSec}s cooldown)"
                } else if (
                    apiErr.errorCode == 400 && (
                        desc.contains("not found") ||
                            desc.contains("can't be forwarded") ||
                            desc.contains("message_id_invalid") ||
                            desc.contains("empty")
                        )
                ) {
                    // Deleted post, service post, or non-existent message gap -> skip immediately
                    return null
                } else if (targetScratchChat != channelId && apiErr.errorCode in setOf(400, 403)) {
                    // User blocked bot DM -> drop scratch chat and retry via channel
                    scratchChatIdByBotId.remove(bot.botId)
                } else if (attempt < maxAttempts - 1) {
                    delay(350L * (attempt + 1))
                }
            } catch (ioErr: IOException) {
                lastError = ioErr
                if (attempt < maxAttempts - 1) {
                    delay(400L * (attempt + 1))
                }
            } finally {
                metrics.activeRequests.decrementAndGet()
                refreshStatusSnapshot()
            }
        }
        throw lastError ?: IOException("Unable to inspect message #$messageId")
    }

    suspend fun fetchChatWithPinnedMessage(): TgChat {
        val channelId = credentialStore.getChannelId()
        require(channelId.isNotBlank()) { "Channel ID is not configured." }
        val bot = acquireBestBot()
        return apiClient.getChat(bot.token, channelId)
    }

    suspend fun downloadArbitraryBotFile(bot: StoredBotToken, telegramFileId: String): ByteArray {
        return downloadWithSpecificBot(bot, telegramFileId, null, null, null)
    }

    suspend fun pinMessageInChannel(messageId: Long, preferredBotId: String? = null) {
        val channelId = credentialStore.getChannelId()
        if (channelId.isBlank()) return
        val bots = credentialStore.getBots()
        val bot = bots.firstOrNull { it.botId == preferredBotId } ?: acquireBestBot()
        runCatching {
            apiClient.pinChatMessage(bot.token, channelId, messageId, disableNotification = true)
        }
    }

    suspend fun deleteMessagesFromChannel(messageIds: List<Long>) {
        val channelId = credentialStore.getChannelId()
        if (channelId.isBlank() || messageIds.isEmpty()) return
        for (msgId in messageIds) {
            if (msgId <= 0L) continue
            val bot = runCatching { acquireBestBot() }.getOrNull() ?: return
            runCatching {
                apiClient.deleteMessage(bot.token, channelId, msgId)
            }
        }
    }

    fun getPrimaryOrBestBotSnapshot(): StoredBotToken? = credentialStore.getBots().firstOrNull()

    private suspend fun downloadWithSpecificBot(
        bot: StoredBotToken,
        telegramFileId: String,
        rangeStart: Long?,
        rangeEndInclusive: Long?,
        onProgress: ((Long) -> Unit)?,
    ): ByteArray {
        val metrics = metricsByBotId.computeIfAbsent(bot.botId) { MutableBotMetrics() }
        metrics.activeRequests.incrementAndGet()
        refreshStatusSnapshot()
        try {
            val startMs = System.currentTimeMillis()
            val tgFile = apiClient.getFile(bot.token, telegramFileId)
            val filePath = tgFile.filePath
                ?: throw IOException("Telegram getFile returned empty file_path (file may exceed 20 MB Bot API limit)")
            val bytes = apiClient.downloadFileBytes(
                token = bot.token,
                filePath = filePath,
                rangeStart = rangeStart,
                rangeEndInclusive = rangeEndInclusive,
                onProgress = onProgress,
            )
            metrics.isHealthy = true
            metrics.lastLatencyMs = System.currentTimeMillis() - startMs
            metrics.lastError = null
            metrics.downloadedBytes.addAndGet(bytes.size.toLong())
            return bytes
        } finally {
            metrics.activeRequests.decrementAndGet()
            refreshStatusSnapshot()
        }
    }

    private suspend fun acquireBestBot(): StoredBotToken {
        while (true) {
            val bots = credentialStore.getBots()
            require(bots.isNotEmpty()) { "No Telegram bots configured in FT-Cloud swarm." }

            val now = System.currentTimeMillis()
            var shortestWaitMs = Long.MAX_VALUE
            val selected: StoredBotToken? = selectionMutex.withLock {
                val startIdx = (roundRobinCursor.getAndIncrement() and Int.MAX_VALUE) % bots.size
                val rotated = (0 until bots.size).map { idx -> bots[(startIdx + idx) % bots.size] }

                val ready = rotated.filter { bot ->
                    val m = metricsByBotId.computeIfAbsent(bot.botId) { MutableBotMetrics() }
                    val rem = m.cooldownUntilMs.get() - now
                    if (rem > 0L && rem < shortestWaitMs) {
                        shortestWaitMs = rem
                    }
                    rem <= 0L
                }
                ready.minByOrNull { bot ->
                    val m = metricsByBotId.computeIfAbsent(bot.botId) { MutableBotMetrics() }
                    val unhealthyPenalty = if (m.isHealthy) 0 else 100
                    m.activeRequests.get() + unhealthyPenalty
                }
            }

            if (selected != null) {
                return selected
            }
            val sleepMs = shortestWaitMs.coerceIn(100L, 15_000L)
            delay(sleepMs)
        }
    }
}
