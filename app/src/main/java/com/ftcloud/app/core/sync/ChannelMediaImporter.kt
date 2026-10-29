package com.ftcloud.app.core.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ftcloud.app.FTCloudApplication
import com.ftcloud.app.core.crypto.CredentialStore
import com.ftcloud.app.core.database.FTCloudDatabase
import com.ftcloud.app.core.database.FileChunkEntity
import com.ftcloud.app.core.database.FileEntity
import com.ftcloud.app.core.database.FolderEntity
import com.ftcloud.app.core.model.FileCategory
import com.ftcloud.app.core.telegram.MultiBotSwarmManager
import com.ftcloud.app.core.telegram.TelegramBotApiClient
import com.ftcloud.app.core.telegram.TgMessage
import com.ftcloud.app.core.telegram.extractBestMedia
import com.ftcloud.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class ChannelImportSummary(
    val importedFiles: Int,
    val skippedExisting: Int,
    val detectedTurboDmBots: Int = 0,
)

@Serializable
internal data class TgDesktopExportRoot(
    val name: String? = null,
    val type: String? = null,
    val id: Long? = null,
    val messages: List<TgDesktopExportMessage> = emptyList(),
)

@Serializable
internal data class TgDesktopExportMessage(
    val id: Long = 0L,
    val type: String = "message",
    val date: String? = null,
    @SerialName("date_unixtime") val dateUnixtime: String? = null,
    val photo: String? = null,
    @SerialName("photo_file_size") val photoFileSize: Long? = null,
    val file: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    val performer: String? = null,
    val title: String? = null,
    val text: JsonElement? = null,
)

class ChannelMediaImporter(
    private val database: FTCloudDatabase,
    private val apiClient: TelegramBotApiClient,
    private val swarmManager: MultiBotSwarmManager,
    private val credentialStore: CredentialStore,
    private val settingsRepository: SettingsRepository,
    private val manifestSync: CloudManifestSync,
) {
    private val exportJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun hydrateSavedScratchChats() {
        val saved = settingsRepository.getBotScratchChats()
        for ((botId, chatId) in saved) {
            swarmManager.registerBotScratchChat(botId, chatId)
        }
    }

    /**
     * Instantly imports 7,000+ historical channel media items from a Telegram Desktop
     * JSON metadata export (`result.json`) in ~1-2 seconds with zero Telegram API calls.
     * Bot-scoped `file_id`s are resolved lazily on demand when a file is viewed or streamed.
     */
    suspend fun importTelegramExportJson(
        inputStream: InputStream,
    ): ChannelImportSummary = withContext(Dispatchers.IO) {
        val rawJson = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val exportRoot = exportJson.decodeFromString<TgDesktopExportRoot>(rawJson)
        val existingMsgIds = database.chunkDao().getAllImportedMessageIds().toHashSet()
        val importedFolderId = ensureImportedChannelFolder()

        data class RawCandidate(
            val messageId: Long,
            val timestampMs: Long,
            val fileName: String,
            val mimeType: String,
            val sizeBytes: Long,
            val width: Int?,
            val height: Int?,
            val durationMs: Long?,
            val captionPlain: String,
        )

        val candidates = ArrayList<RawCandidate>(exportRoot.messages.size.coerceAtMost(16_384))
        var skippedExisting = 0

        for (msg in exportRoot.messages) {
            if (msg.type != "message" || msg.id <= 0L) continue
            if (msg.id in existingMsgIds) {
                skippedExisting++
                continue
            }
            val captionText = flattenExportText(msg.text)
            if (captionText.startsWith("#ftcloud_manifest") || captionText.startsWith("#ftcloud|")) {
                continue
            }

            val hasPhoto = !msg.photo.isNullOrBlank()
            val hasFile = !msg.file.isNullOrBlank() || !msg.fileName.isNullOrBlank() || !msg.mediaType.isNullOrBlank()
            if (!hasPhoto && !hasFile) continue

            val epochMs = parseExportTimestampMs(msg.dateUnixtime, msg.date)
            val resolvedMime = when {
                !msg.mimeType.isNullOrBlank() -> msg.mimeType
                hasPhoto -> "image/jpeg"
                msg.mediaType == "video_file" || msg.mediaType == "animation" || msg.mediaType == "video_message" -> "video/mp4"
                msg.mediaType == "audio_file" -> "audio/mpeg"
                msg.mediaType == "voice_message" -> "audio/ogg"
                else -> guessMimeFromFileName(msg.fileName)
            }
            val resolvedName = buildExportFileName(msg, captionText, resolvedMime)
            val resolvedSize = (msg.fileSize ?: msg.photoFileSize ?: 0L).coerceAtLeast(0L)

            candidates += RawCandidate(
                messageId = msg.id,
                timestampMs = epochMs,
                fileName = resolvedName,
                mimeType = resolvedMime,
                sizeBytes = resolvedSize,
                width = msg.width?.takeIf { it > 0 },
                height = msg.height?.takeIf { it > 0 },
                durationMs = msg.durationSeconds?.takeIf { it > 0 }?.let { it * 1000L },
                captionPlain = captionText,
            )
        }

        // Group sequential multi-part chunks (e.g. movie.mkv.part001, archive.zip.001) when sizes are known
        val partRegex = Regex("""^(.+?)\.(?:part0*(\d+)|0*(\d{3,4}))$""", RegexOption.IGNORE_CASE)
        val groupedParts = LinkedHashMap<String, MutableList<Pair<Int, RawCandidate>>>()
        val standalone = ArrayList<RawCandidate>(candidates.size)

        for (cand in candidates) {
            val match = if (cand.sizeBytes > 0L) partRegex.matchEntire(cand.fileName) else null
            val partNum = match?.let {
                (it.groupValues[2].ifEmpty { it.groupValues[3] }).toIntOrNull()
            }
            if (match != null && partNum != null && partNum >= 1) {
                val baseName = match.groupValues[1]
                groupedParts.getOrPut(baseName) { mutableListOf() }.add(partNum to cand)
            } else {
                standalone += cand
            }
        }

        val filesToInsert = ArrayList<FileEntity>(standalone.size + groupedParts.size)
        val chunksToInsert = ArrayList<FileChunkEntity>(candidates.size)

        // Assemble valid 1-based contiguous multi-part series; fall back to standalone if incomplete
        for ((baseName, indexedList) in groupedParts) {
            val sorted = indexedList.sortedBy { it.first }
            val isContiguous = sorted.size >= 2 && sorted.mapIndexed { idx, pair -> pair.first == idx + 1 }.all { it }
            if (!isContiguous) {
                sorted.forEach { standalone += it.second }
                continue
            }
            val driveFileId = UUID.randomUUID().toString()
            val first = sorted.first().second
            val realMime = guessMimeFromFileName(baseName).takeIf { it != "application/octet-stream" } ?: first.mimeType
            val category = FileCategory.fromMimeAndName(realMime, baseName)
            var offset = 0L
            for ((idx, pair) in sorted.withIndex()) {
                val part = pair.second
                chunksToInsert += FileChunkEntity(
                    driveFileId = driveFileId,
                    chunkIndex = idx,
                    byteOffset = offset,
                    rawSize = part.sizeBytes,
                    storedSize = part.sizeBytes,
                    telegramMessageId = part.messageId,
                    telegramFileId = "",
                    telegramFileUniqueId = "",
                    uploaderBotId = "",
                    sha256 = "",
                    ivBase64 = null,
                )
                offset += part.sizeBytes
            }
            filesToInsert += FileEntity(
                id = driveFileId,
                name = baseName,
                folderId = importedFolderId,
                mimeType = realMime,
                category = category.name,
                sizeBytes = offset,
                chunkCount = sorted.size,
                sha256 = "",
                isEncrypted = false,
                isStarred = false,
                isTrashed = false,
                width = first.width,
                height = first.height,
                durationMs = first.durationMs,
                capturedAt = first.timestampMs,
                createdAt = first.timestampMs,
                updatedAt = first.timestampMs,
            )
        }

        for (cand in standalone) {
            val driveFileId = UUID.randomUUID().toString()
            val category = FileCategory.fromMimeAndName(cand.mimeType, cand.fileName)
            filesToInsert += FileEntity(
                id = driveFileId,
                name = cand.fileName,
                folderId = importedFolderId,
                mimeType = cand.mimeType,
                category = category.name,
                sizeBytes = cand.sizeBytes,
                chunkCount = 1,
                sha256 = "",
                isEncrypted = false,
                isStarred = false,
                isTrashed = false,
                width = cand.width,
                height = cand.height,
                durationMs = cand.durationMs,
                capturedAt = cand.timestampMs,
                createdAt = cand.timestampMs,
                updatedAt = cand.timestampMs,
            )
            chunksToInsert += FileChunkEntity(
                driveFileId = driveFileId,
                chunkIndex = 0,
                byteOffset = 0L,
                rawSize = cand.sizeBytes,
                storedSize = cand.sizeBytes,
                telegramMessageId = cand.messageId,
                telegramFileId = "",
                telegramFileUniqueId = "",
                uploaderBotId = "",
                sha256 = "",
                ivBase64 = null,
            )
        }

        // Batch insert in chunks of 500 for instant persistence even with 10,000+ rows
        if (filesToInsert.isNotEmpty()) {
            val chunksByFileId = chunksToInsert.groupBy { it.driveFileId }
            for (fileBatch in filesToInsert.chunked(500)) {
                val associatedChunks = fileBatch.flatMap { f -> chunksByFileId[f.id].orEmpty() }
                database.withTransaction {
                    database.fileDao().upsertAll(fileBatch)
                    database.chunkDao().upsertChunks(associatedChunks)
                }
            }
            syncManifestQuietly()
        }

        ChannelImportSummary(
            importedFiles = filesToInsert.size,
            skippedExisting = skippedExisting,
        )
    }

    /**
     * Polls pending `getUpdates` across every bot in the swarm:
     * 1. Detects private `/start` messages so Bot Swarm Channel Scanning can use Turbo Bot-DM mode.
     * 2. Imports new `channel_post` media posted directly in the storage channel.
     * 3. Imports batches of up to 100 historical messages forwarded by the user to any swarm bot.
     */
    suspend fun syncPendingBotUpdates(): ChannelImportSummary = withContext(Dispatchers.IO) {
        val bots = credentialStore.getBots()
        val channelId = credentialStore.getChannelId()
        if (bots.isEmpty() || channelId.isBlank()) {
            return@withContext ChannelImportSummary(0, 0, 0)
        }

        val existingMsgIds = database.chunkDao().getAllImportedMessageIds().toHashSet()
        val importedFolderId = ensureImportedChannelFolder()
        val offsets = settingsRepository.getBotUpdateOffsets()
        var imported = 0
        var skipped = 0

        for (bot in bots) {
            var nextOffset = offsets[bot.botId]
            while (true) {
                val updates = runCatching {
                    apiClient.getUpdates(token = bot.token, offset = nextOffset, limit = 100)
                }.getOrDefault(emptyList())

                if (updates.isEmpty()) break

                val batchFiles = mutableListOf<FileEntity>()
                val batchChunks = mutableListOf<FileChunkEntity>()

                for (upd in updates) {
                    nextOffset = upd.updateId + 1L

                    // Capture private user DM chat ID for Turbo Channel Scanning
                    val privMsg = upd.message
                    if (privMsg?.chat?.type == "private" && privMsg.chat.id > 0L) {
                        val dmId = privMsg.chat.id.toString()
                        swarmManager.registerBotScratchChat(bot.botId, dmId)
                        settingsRepository.saveBotScratchChat(bot.botId, dmId)
                    }

                    // Candidate media message: either a live channel_post in our storage channel
                    // or a message forwarded to the bot in private chat
                    val sourceMsg: TgMessage?
                    val canonicalChannelMsgId: Long

                    when {
                        upd.channelPost != null && normalizeChatMatch(upd.channelPost.chat?.id, channelId) -> {
                            sourceMsg = upd.channelPost
                            canonicalChannelMsgId = upd.channelPost.messageId
                        }
                        privMsg != null -> {
                            sourceMsg = privMsg
                            val origin = privMsg.forwardOrigin
                            canonicalChannelMsgId = if (
                                origin?.type == "channel" &&
                                origin.messageId != null &&
                                normalizeChatMatch(origin.chat?.id, channelId)
                            ) {
                                origin.messageId
                            } else {
                                // Forwarded from another chat or sent directly to bot: negative synthetic ID avoids collision
                                -(bot.botId.takeLast(4).toLongOrNull() ?: 1L) * 1_000_000_000L - privMsg.messageId
                            }
                        }
                        else -> {
                            sourceMsg = null
                            canonicalChannelMsgId = 0L
                        }
                    }

                    if (sourceMsg == null) continue
                    val cap = sourceMsg.caption.orEmpty()
                    if (cap.startsWith("#ftcloud_manifest") || cap.startsWith("#ftcloud|")) continue

                    if (canonicalChannelMsgId > 0L && canonicalChannelMsgId in existingMsgIds) {
                        skipped++
                        continue
                    }

                    val media = sourceMsg.extractBestMedia(
                        preferredMessageId = if (canonicalChannelMsgId > 0L) canonicalChannelMsgId else sourceMsg.messageId
                    ) ?: continue

                    val originEpochSec = sourceMsg.forwardOrigin?.date?.takeIf { it > 0L } ?: sourceMsg.date
                    val createdMs = if (originEpochSec > 0L) originEpochSec * 1000L else System.currentTimeMillis()
                    val driveFileId = UUID.randomUUID().toString()
                    val category = FileCategory.fromMimeAndName(media.mimeType, media.fileName)

                    batchFiles += FileEntity(
                        id = driveFileId,
                        name = media.fileName,
                        folderId = importedFolderId,
                        mimeType = media.mimeType,
                        category = category.name,
                        sizeBytes = media.sizeBytes,
                        chunkCount = 1,
                        sha256 = "",
                        isEncrypted = false,
                        isStarred = false,
                        isTrashed = false,
                        width = media.width,
                        height = media.height,
                        durationMs = media.durationMs,
                        capturedAt = createdMs,
                        createdAt = createdMs,
                        updatedAt = createdMs,
                    )
                    batchChunks += FileChunkEntity(
                        driveFileId = driveFileId,
                        chunkIndex = 0,
                        byteOffset = 0L,
                        rawSize = media.sizeBytes,
                        storedSize = media.sizeBytes,
                        telegramMessageId = canonicalChannelMsgId.coerceAtLeast(0L),
                        telegramFileId = if (media.exceedsBotDownloadLimit) "" else media.fileId,
                        telegramFileUniqueId = media.fileUniqueId,
                        uploaderBotId = if (media.exceedsBotDownloadLimit) "" else bot.botId,
                        sha256 = "",
                        ivBase64 = null,
                    )
                    if (canonicalChannelMsgId > 0L) {
                        existingMsgIds += canonicalChannelMsgId
                    }
                    imported++
                }

                if (batchFiles.isNotEmpty()) {
                    database.withTransaction {
                        database.fileDao().upsertAll(batchFiles)
                        database.chunkDao().upsertChunks(batchChunks)
                    }
                }
                if (nextOffset != null) {
                    settingsRepository.saveBotUpdateOffset(bot.botId, nextOffset)
                }
                if (updates.size < 100) break
            }
        }

        if (imported > 0) {
            syncManifestQuietly()
        }

        val turboBots = settingsRepository.getBotScratchChats().size
        ChannelImportSummary(
            importedFiles = imported,
            skippedExisting = skipped,
            detectedTurboDmBots = turboBots,
        )
    }

    /**
     * Background resumable scanner that walks channel message IDs newest-first (`maxId` down to `1`)
     * so recent photos and videos appear immediately in FT-Cloud without needing a PC export.
     */
    suspend fun runResumableChannelHistoryScan() = withContext(Dispatchers.IO) {
        hydrateSavedScratchChats()
        // First pick up any `/start` Turbo DM registrations & forwarded batches
        runCatching { syncPendingBotUpdates() }

        val snap = settingsRepository.getSnapshot()
        if (!snap.isConfigured) {
            settingsRepository.updateChannelScanState(
                running = false,
                status = "Configure Telegram channel & bots first.",
            )
            return@withContext
        }

        val existingMsgIds = database.chunkDao().getAllImportedMessageIds().toHashSet()
        val importedFolderId = ensureImportedChannelFolder()

        var maxId = snap.channelScanMaxId
        var cursorId = snap.channelScanCursorId
        var checked = snap.channelScanCheckedCount
        var imported = snap.channelScanImportedCount

        if (cursorId <= 0L) {
            settingsRepository.updateChannelScanState(
                running = true,
                status = "Probing latest message ID in channel…",
            )
            maxId = swarmManager.probeLatestChannelMessageId()
            cursorId = maxId
            checked = 0
            imported = 0
            settingsRepository.updateChannelScanState(
                running = true,
                cursorId = cursorId,
                maxId = maxId,
                checkedCount = 0,
                importedCount = 0,
                status = if (maxId > 0L) "Scanning newest-first from message #$maxId…" else "Channel is empty.",
            )
        } else {
            settingsRepository.updateChannelScanState(
                running = true,
                status = "Resuming scan at message #$cursorId / #$maxId…",
            )
        }

        if (cursorId <= 0L) {
            settingsRepository.updateChannelScanState(
                running = false,
                status = "Channel has no messages to import.",
            )
            return@withContext
        }

        var unpushedImports = 0

        try {
            while (cursorId >= 1L) {
                coroutineContext.ensureActive()

                val latestSettings = settingsRepository.getSnapshot()
                if (!latestSettings.channelScanRunning) {
                    settingsRepository.updateChannelScanState(
                        running = false,
                        cursorId = cursorId,
                        checkedCount = checked,
                        importedCount = imported,
                        status = "Paused at message #$cursorId ($imported media imported)",
                    )
                    if (unpushedImports > 0) {
                        syncManifestQuietly()
                    }
                    return@withContext
                }

                // Determine parallelism: parallel across Turbo DM bots, or 1-by-1 if using shared channel
                val turboCount = swarmManager.getBotScratchChatsSnapshot().size
                val stepWidth = if (turboCount >= 2) minOf(turboCount, 4) else 1

                val candidateIds = mutableListOf<Long>()
                while (cursorId >= 1L && candidateIds.size < stepWidth) {
                    val id = cursorId--
                    if (id in existingMsgIds) {
                        checked++
                    } else {
                        candidateIds += id
                    }
                }

                if (candidateIds.isEmpty()) {
                    settingsRepository.updateChannelScanState(
                        cursorId = cursorId,
                        checkedCount = checked,
                        importedCount = imported,
                    )
                    continue
                }

                val inspectedBatch = coroutineScope {
                    candidateIds.map { msgId ->
                        async {
                            msgId to runCatching { swarmManager.inspectChannelMessage(msgId) }.getOrNull()
                        }
                    }.awaitAll()
                }

                val newFiles = mutableListOf<FileEntity>()
                val newChunks = mutableListOf<FileChunkEntity>()

                for ((msgId, pair) in inspectedBatch) {
                    checked++
                    val (bot, forwarded) = pair ?: continue
                    val cap = forwarded.caption.orEmpty()
                    if (cap.startsWith("#ftcloud_manifest") || cap.startsWith("#ftcloud|")) continue

                    val media = forwarded.extractBestMedia(preferredMessageId = msgId) ?: continue
                    val originEpochSec = forwarded.forwardOrigin?.date?.takeIf { it > 0L } ?: forwarded.date
                    val createdMs = if (originEpochSec > 0L) originEpochSec * 1000L else System.currentTimeMillis()
                    val driveFileId = UUID.randomUUID().toString()
                    val category = FileCategory.fromMimeAndName(media.mimeType, media.fileName)

                    newFiles += FileEntity(
                        id = driveFileId,
                        name = media.fileName,
                        folderId = importedFolderId,
                        mimeType = media.mimeType,
                        category = category.name,
                        sizeBytes = media.sizeBytes,
                        chunkCount = 1,
                        sha256 = "",
                        isEncrypted = false,
                        isStarred = false,
                        isTrashed = false,
                        width = media.width,
                        height = media.height,
                        durationMs = media.durationMs,
                        capturedAt = createdMs,
                        createdAt = createdMs,
                        updatedAt = createdMs,
                    )
                    newChunks += FileChunkEntity(
                        driveFileId = driveFileId,
                        chunkIndex = 0,
                        byteOffset = 0L,
                        rawSize = media.sizeBytes,
                        storedSize = media.sizeBytes,
                        telegramMessageId = msgId,
                        telegramFileId = if (media.exceedsBotDownloadLimit) "" else media.fileId,
                        telegramFileUniqueId = media.fileUniqueId,
                        uploaderBotId = if (media.exceedsBotDownloadLimit) "" else bot.botId,
                        sha256 = "",
                        ivBase64 = null,
                    )
                    existingMsgIds += msgId
                    imported++
                    unpushedImports++
                }

                if (newFiles.isNotEmpty()) {
                    database.withTransaction {
                        database.fileDao().upsertAll(newFiles)
                        database.chunkDao().upsertChunks(newChunks)
                    }
                }

                settingsRepository.updateChannelScanState(
                    cursorId = cursorId,
                    checkedCount = checked,
                    importedCount = imported,
                    status = "Scanning #$cursorId / #$maxId • Imported $imported media",
                )

                if (unpushedImports >= 50) {
                    unpushedImports = 0
                    syncManifestQuietly()
                }
            }

            if (unpushedImports > 0) {
                syncManifestQuietly()
            }
            settingsRepository.updateChannelScanState(
                running = false,
                cursorId = 0L,
                checkedCount = checked,
                importedCount = imported,
                status = "Scan complete! Imported $imported media items from $maxId channel posts.",
            )
        } catch (err: Throwable) {
            if (unpushedImports > 0) {
                syncManifestQuietly()
            }
            settingsRepository.updateChannelScanState(
                running = false,
                cursorId = cursorId,
                checkedCount = checked,
                importedCount = imported,
                status = "Scan paused at #$cursorId (${err.message ?: "interrupted"}). Tap Resume anytime.",
            )
            throw err
        }
    }

    private suspend fun syncManifestQuietly() {
        runCatching {
            val prevMsgId = settingsRepository.getSnapshot().pinnedManifestMessageId
            val newMsgId = manifestSync.pushAndPinManifest(previousManifestMessageId = prevMsgId)
            settingsRepository.recordManifestSync(newMsgId)
        }
    }

    private suspend fun ensureImportedChannelFolder(): String {
        val existing = database.folderDao().findByNameInParent(IMPORTED_FOLDER_NAME, null)
        if (existing != null) return existing.id
        val now = System.currentTimeMillis()
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = IMPORTED_FOLDER_NAME,
            parentId = null,
            isStarred = false,
            isTrashed = false,
            createdAt = now,
            updatedAt = now,
        )
        database.folderDao().upsert(folder)
        return folder.id
    }

    private fun normalizeChatMatch(chatIdLong: Long?, configuredChannelId: String): Boolean {
        if (chatIdLong == null) return false
        val conf = configuredChannelId.trim()
        if (conf == chatIdLong.toString()) return true
        val stripped = conf.removePrefix("-100")
        val candidateStripped = chatIdLong.toString().removePrefix("-100")
        return stripped.isNotBlank() && stripped == candidateStripped
    }

    private fun flattenExportText(element: JsonElement?): String {
        if (element == null) return ""
        return when (element) {
            is JsonPrimitive -> element.contentOrNull.orEmpty()
            is JsonArray -> buildString {
                for (part in element) {
                    when (part) {
                        is JsonPrimitive -> append(part.contentOrNull.orEmpty())
                        is JsonObject -> append((part["text"] as? JsonPrimitive)?.contentOrNull.orEmpty())
                        else -> Unit
                    }
                }
            }
            is JsonObject -> (element["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        }
    }

    private fun parseExportTimestampMs(unixStr: String?, isoDate: String?): Long {
        unixStr?.toLongOrNull()?.takeIf { it > 0L }?.let { return it * 1000L }
        if (!isoDate.isNullOrBlank()) {
            runCatching {
                return LocalDateTime.parse(isoDate).toInstant(ZoneOffset.UTC).toEpochMilli()
            }
        }
        return System.currentTimeMillis()
    }

    private fun buildExportFileName(
        msg: TgDesktopExportMessage,
        captionText: String,
        mimeType: String,
    ): String {
        msg.fileName?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

        val performerTitle = listOfNotNull(msg.performer, msg.title)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" - ")
        if (performerTitle.isNotBlank()) {
            return "$performerTitle.${defaultExtensionForMime(mimeType)}"
        }

        // Extract clean basename from exported relative path if present (unless placeholder notice)
        val pathCandidate = (msg.photo ?: msg.file).orEmpty()
        if (pathCandidate.isNotBlank() && !pathCandidate.startsWith("(File not included")) {
            val leaf = pathCandidate.substringAfterLast('/').substringAfterLast('\\').trim()
            if (leaf.isNotBlank()) return leaf
        }

        val cleanCaption = captionText.lineSequence().firstOrNull()?.trim()
            ?.replace(Regex("[^a-zA-Z0-9._ -]"), "")
            ?.take(48)
            ?.trim()
        if (!cleanCaption.isNullOrBlank()) {
            val ext = defaultExtensionForMime(mimeType)
            return if (cleanCaption.contains('.')) cleanCaption else "$cleanCaption.$ext"
        }

        val prefix = when {
            mimeType.startsWith("image/") -> "photo"
            mimeType.startsWith("video/") -> "video"
            mimeType.startsWith("audio/") -> "audio"
            else -> "file"
        }
        return "${prefix}_${msg.id}.${defaultExtensionForMime(mimeType)}"
    }

    private fun guessMimeFromFileName(name: String?): String {
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heic"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "m4a", "aac" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "txt", "md", "json", "xml", "csv", "log" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun defaultExtensionForMime(mimeType: String): String {
        val m = mimeType.lowercase()
        return when {
            m == "image/png" -> "png"
            m == "image/webp" -> "webp"
            m == "image/gif" -> "gif"
            m.startsWith("image/") -> "jpg"
            m == "video/x-matroska" -> "mkv"
            m == "video/webm" -> "webm"
            m.startsWith("video/") -> "mp4"
            m == "audio/ogg" -> "ogg"
            m == "audio/flac" -> "flac"
            m.startsWith("audio/") -> "mp3"
            m == "application/pdf" -> "pdf"
            else -> "bin"
        }
    }

    companion object {
        const val IMPORTED_FOLDER_NAME = "Imported from Channel"
    }
}

class ChannelScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FTCloudApplication
        return runCatching {
            app.container.channelMediaImporter.runResumableChannelHistoryScan()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}

object ChannelScanScheduler {
    private const val UNIQUE_SCAN_WORK = "ftcloud_channel_history_scan"

    fun startOrResume(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ChannelScanWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_SCAN_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_SCAN_WORK)
    }
}
