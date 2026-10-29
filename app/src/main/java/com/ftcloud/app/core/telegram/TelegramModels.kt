package com.ftcloud.app.core.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException

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
data class TgDocument(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String = "",
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
)

@Serializable
data class TgMessage(
    @SerialName("message_id") val messageId: Long,
    val date: Long = 0L,
    val caption: String? = null,
    val document: TgDocument? = null,
)

@Serializable
data class TgFile(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String = "",
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("file_path") val filePath: String? = null,
)

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
}
