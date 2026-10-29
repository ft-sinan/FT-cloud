package com.ftcloud.app.core.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException

const val BOT_API_MAX_DOWNLOAD_BYTES: Long = 20L * 1024L * 1024L // 20 MiB Bot API getFile limit

@Serializable
data class TgApiResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    val description: String? = null,
    val parameters: TgResponseParameters? = null,
)

@Serializable
data class TgResponseParameters(
    @SerialName("retry_after") val retryAfter: Int? = null,
    @SerialName("migrate_to_chat_id") val migrateToChatId: Long? = null,
)

@Serializable
data class TgUser(
    val id: Long,
    @SerialName("is_bot") val isBot: Boolean = false,
    @SerialName("first_name") val firstName: String = "",
    val username: String? = null,
)

@Serializable
data class TgChat(
    val id: Long,
    val type: String = "",
    val title: String? = null,
    val username: String? = null,
    @SerialName("pinned_message") val pinnedMessage: TgMessage? = null,
)

@Serializable
data class TgPhotoSize(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("file_size") val fileSize: Long? = null,
)

@Serializable
data class TgVideoQuality(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val codec: String = "",
    @SerialName("file_size") val fileSize: Long? = null,
)

@Serializable
data class TgVideo(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val duration: Int = 0,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    val thumbnail: TgPhotoSize? = null,
    val qualities: List<TgVideoQuality>? = null,
)

@Serializable
data class TgAudio(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String = "",
    val duration: Int = 0,
    val performer: String? = null,
    val title: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
)

@Serializable
data class TgAnimation(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val duration: Int = 0,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
)

@Serializable
data class TgVoice(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String = "",
    val duration: Int = 0,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
)

@Serializable
data class TgDocument(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String = "",
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
)

@Serializable
data class TgForwardOrigin(
    val type: String = "",
    val date: Long = 0L,
    val chat: TgChat? = null,
    @SerialName("message_id") val messageId: Long? = null,
)

@Serializable
data class TgMessage(
    @SerialName("message_id") val messageId: Long,
    val date: Long = 0L,
    val from: TgUser? = null,
    val chat: TgChat? = null,
    val text: String? = null,
    val caption: String? = null,
    val document: TgDocument? = null,
    val photo: List<TgPhotoSize>? = null,
    val video: TgVideo? = null,
    val audio: TgAudio? = null,
    val animation: TgAnimation? = null,
    val voice: TgVoice? = null,
    @SerialName("forward_origin") val forwardOrigin: TgForwardOrigin? = null,
)

@Serializable
data class TgUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TgMessage? = null,
    @SerialName("channel_post") val channelPost: TgMessage? = null,
    @SerialName("edited_channel_post") val editedChannelPost: TgMessage? = null,
)

@Serializable
data class TgFile(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String = "",
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("file_path") val filePath: String? = null,
)

data class ExtractedTelegramMedia(
    val fileId: String,
    val fileUniqueId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val exceedsBotDownloadLimit: Boolean = false,
)

fun TgMessage.extractBestMedia(preferredMessageId: Long = messageId): ExtractedTelegramMedia? {
    document?.let { doc ->
        val size = doc.fileSize ?: 0L
        val ext = doc.mimeType?.substringAfter('/', "")?.takeIf { it.length in 1..6 } ?: "bin"
        val defaultName = "document_${preferredMessageId}.$ext"
        return ExtractedTelegramMedia(
            fileId = doc.fileId,
            fileUniqueId = doc.fileUniqueId,
            fileName = doc.fileName?.takeIf { it.isNotBlank() } ?: defaultName,
            mimeType = doc.mimeType ?: "application/octet-stream",
            sizeBytes = size,
            exceedsBotDownloadLimit = size > BOT_API_MAX_DOWNLOAD_BYTES,
        )
    }

    video?.let { vid ->
        val origSize = vid.fileSize ?: 0L
        val streamableRendition = if (origSize > BOT_API_MAX_DOWNLOAD_BYTES) {
            vid.qualities
                .orEmpty()
                .filter { q -> val s = q.fileSize ?: 0L; s in 1..BOT_API_MAX_DOWNLOAD_BYTES }
                .maxByOrNull { q -> q.width.toLong() * q.height.toLong() }
        } else {
            null
        }
        val chosenFileId = streamableRendition?.fileId ?: vid.fileId
        val chosenUniqueId = streamableRendition?.fileUniqueId ?: vid.fileUniqueId
        val chosenSize = streamableRendition?.fileSize ?: origSize
        val defaultName = "video_${preferredMessageId}.mp4"
        return ExtractedTelegramMedia(
            fileId = chosenFileId,
            fileUniqueId = chosenUniqueId,
            fileName = vid.fileName?.takeIf { it.isNotBlank() } ?: defaultName,
            mimeType = vid.mimeType ?: "video/mp4",
            sizeBytes = chosenSize,
            width = streamableRendition?.width ?: vid.width.takeIf { it > 0 },
            height = streamableRendition?.height ?: vid.height.takeIf { it > 0 },
            durationMs = vid.duration.takeIf { it > 0 }?.let { it * 1000L },
            exceedsBotDownloadLimit = origSize > BOT_API_MAX_DOWNLOAD_BYTES && streamableRendition == null,
        )
    }

    photo?.takeIf { it.isNotEmpty() }?.let { sizes ->
        val best = sizes
            .filter { (it.fileSize ?: 0L) <= BOT_API_MAX_DOWNLOAD_BYTES }
            .maxByOrNull { (it.fileSize ?: 0L).coerceAtLeast(it.width.toLong() * it.height.toLong()) }
            ?: sizes.maxBy { it.width.toLong() * it.height.toLong() }
        return ExtractedTelegramMedia(
            fileId = best.fileId,
            fileUniqueId = best.fileUniqueId,
            fileName = caption?.lineSequence()?.firstOrNull()?.trim()?.take(48)
                ?.replace(Regex("[^a-zA-Z0-9._ -]"), "")
                ?.takeIf { it.isNotBlank() }
                ?.let { if (it.contains('.')) it else "$it.jpg" }
                ?: "photo_${preferredMessageId}.jpg",
            mimeType = "image/jpeg",
            sizeBytes = best.fileSize ?: 0L,
            width = best.width.takeIf { it > 0 },
            height = best.height.takeIf { it > 0 },
            exceedsBotDownloadLimit = (best.fileSize ?: 0L) > BOT_API_MAX_DOWNLOAD_BYTES,
        )
    }

    audio?.let { aud ->
        val size = aud.fileSize ?: 0L
        val builtTitle = listOfNotNull(aud.performer, aud.title)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .takeIf { it.isNotBlank() }
            ?.let { "$it.mp3" }
        return ExtractedTelegramMedia(
            fileId = aud.fileId,
            fileUniqueId = aud.fileUniqueId,
            fileName = aud.fileName?.takeIf { it.isNotBlank() } ?: builtTitle ?: "audio_${preferredMessageId}.mp3",
            mimeType = aud.mimeType ?: "audio/mpeg",
            sizeBytes = size,
            durationMs = aud.duration.takeIf { it > 0 }?.let { it * 1000L },
            exceedsBotDownloadLimit = size > BOT_API_MAX_DOWNLOAD_BYTES,
        )
    }

    animation?.let { anim ->
        val size = anim.fileSize ?: 0L
        return ExtractedTelegramMedia(
            fileId = anim.fileId,
            fileUniqueId = anim.fileUniqueId,
            fileName = anim.fileName?.takeIf { it.isNotBlank() } ?: "animation_${preferredMessageId}.mp4",
            mimeType = anim.mimeType ?: "video/mp4",
            sizeBytes = size,
            width = anim.width.takeIf { it > 0 },
            height = anim.height.takeIf { it > 0 },
            durationMs = anim.duration.takeIf { it > 0 }?.let { it * 1000L },
            exceedsBotDownloadLimit = size > BOT_API_MAX_DOWNLOAD_BYTES,
        )
    }

    voice?.let { vc ->
        val size = vc.fileSize ?: 0L
        return ExtractedTelegramMedia(
            fileId = vc.fileId,
            fileUniqueId = vc.fileUniqueId,
            fileName = "voice_${preferredMessageId}.ogg",
            mimeType = vc.mimeType ?: "audio/ogg",
            sizeBytes = size,
            durationMs = vc.duration.takeIf { it > 0 }?.let { it * 1000L },
            exceedsBotDownloadLimit = size > BOT_API_MAX_DOWNLOAD_BYTES,
        )
    }

    return null
}

class TelegramApiException(
    val errorCode: Int,
    val apiDescription: String,
    val retryAfterSeconds: Int? = null,
) : IOException(
    buildString {
        append("Telegram API error $errorCode: $apiDescription")
        if (retryAfterSeconds != null) {
            append(" (retry_after=${retryAfterSeconds}s)")
        }
    }
) {
    val isFloodWait: Boolean
        get() = errorCode == 429

    val isFileTooBigForBotApi: Boolean
        get() = apiDescription.contains("file is too big", ignoreCase = true)
}

class TelegramBotSizeLimitException(
    val telegramMessageId: Long,
    val reportedSizeBytes: Long,
) : IOException(
    "This legacy channel media exceeds Telegram's 20 MB Bot API download limit. Tap 'Open in Telegram' to view or play the original post."
)
