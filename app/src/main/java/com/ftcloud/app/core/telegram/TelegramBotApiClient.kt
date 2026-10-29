package com.ftcloud.app.core.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.ByteArrayOutputStream
import java.io.IOException

class TelegramBotApiClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val trimmedBase: String = baseUrl.trimEnd('/')

    suspend fun getMe(token: String): TgUser = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$trimmedBase/bot$token/getMe")
            .get()
            .build()
        executeAndDecode<TgUser>(request)
    }

    suspend fun getChat(token: String, chatId: String): TgChat = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("chat_id", chatId)
            .build()
        val request = Request.Builder()
            .url("$trimmedBase/bot$token/getChat")
            .post(form)
            .build()
        executeAndDecode<TgChat>(request)
    }

    suspend fun sendDocument(
        token: String,
        chatId: String,
        partFileName: String,
        mimeType: String,
        bytes: ByteArray,
        caption: String? = null,
        disableNotification: Boolean = true,
        onProgress: ((Long) -> Unit)? = null,
    ): TgMessage = withContext(Dispatchers.IO) {
        val docBody = object : RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
            override fun contentLength(): Long = bytes.size.toLong()
            override fun writeTo(sink: BufferedSink) {
                var offset = 0
                val step = 32 * 1024
                while (offset < bytes.size) {
                    val count = minOf(step, bytes.size - offset)
                    sink.write(bytes, offset, count)
                    offset += count
                    onProgress?.invoke(offset.toLong())
                }
            }
        }

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("disable_notification", disableNotification.toString())
            .addFormDataPart("document", partFileName, docBody)

        if (!caption.isNullOrBlank()) {
            multipartBuilder.addFormDataPart("caption", caption.take(1024))
        }

        val request = Request.Builder()
            .url("$trimmedBase/bot$token/sendDocument")
            .post(multipartBuilder.build())
            .build()

        executeAndDecode<TgMessage>(request)
    }

    suspend fun getFile(token: String, fileId: String): TgFile = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("file_id", fileId)
            .build()
        val request = Request.Builder()
            .url("$trimmedBase/bot$token/getFile")
            .post(form)
            .build()
        executeAndDecode<TgFile>(request)
    }

    suspend fun downloadFileBytes(
        token: String,
        filePath: String,
        rangeStart: Long? = null,
        rangeEndInclusive: Long? = null,
        onProgress: ((Long) -> Unit)? = null,
    ): ByteArray = withContext(Dispatchers.IO) {
        val cleanPath = filePath.trimStart('/')
        val builder = Request.Builder()
            .url("$trimmedBase/file/bot$token/$cleanPath")
            .get()

        if (rangeStart != null && rangeStart >= 0L) {
            val rangeValue = if (rangeEndInclusive != null && rangeEndInclusive >= rangeStart) {
                "bytes=$rangeStart-$rangeEndInclusive"
            } else {
                "bytes=$rangeStart-"
            }
            builder.header("Range", rangeValue)
        }

        httpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw TelegramApiException(
                    errorCode = response.code,
                    apiDescription = "File CDN download failed with HTTP ${response.code}",
                )
            }
            val body = response.body ?: throw IOException("Empty response body from Telegram file CDN")
            val input = body.byteStream()
            val output = ByteArrayOutputStream(
                body.contentLength().takeIf { it in 1..(32 * 1024 * 1024) }?.toInt() ?: (64 * 1024)
            )
            val buffer = ByteArray(32 * 1024)
            var totalRead = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                totalRead += read
                onProgress?.invoke(totalRead)
            }
            output.toByteArray()
        }
    }

    suspend fun pinChatMessage(
        token: String,
        chatId: String,
        messageId: Long,
        disableNotification: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("chat_id", chatId)
            .add("message_id", messageId.toString())
            .add("disable_notification", disableNotification.toString())
            .build()
        val request = Request.Builder()
            .url("$trimmedBase/bot$token/pinChatMessage")
            .post(form)
            .build()
        executeAndDecode<Boolean>(request)
    }

    suspend fun forwardMessage(
        token: String,
        chatId: String,
        fromChatId: String,
        messageId: Long,
        disableNotification: Boolean = true,
    ): TgMessage = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("chat_id", chatId)
            .add("from_chat_id", fromChatId)
            .add("message_id", messageId.toString())
            .add("disable_notification", disableNotification.toString())
            .build()
        val request = Request.Builder()
            .url("$trimmedBase/bot$token/forwardMessage")
            .post(form)
            .build()
        executeAndDecode<TgMessage>(request)
    }

    suspend fun deleteMessage(
        token: String,
        chatId: String,
        messageId: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("chat_id", chatId)
            .add("message_id", messageId.toString())
            .build()
        val request = Request.Builder()
            .url("$trimmedBase/bot$token/deleteMessage")
            .post(form)
            .build()
        executeAndDecode<Boolean>(request)
    }

    private inline fun <reified T> executeAndDecode(request: Request): T {
        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (rawBody.isBlank()) {
                throw TelegramApiException(
                    errorCode = response.code,
                    apiDescription = "Empty response from Telegram API (HTTP ${response.code})",
                )
            }
            val parsed = runCatching {
                json.decodeFromString<TgApiResponse<T>>(rawBody)
            }.getOrElse { parseErr ->
                throw TelegramApiException(
                    errorCode = response.code,
                    apiDescription = "Malformed Telegram response (HTTP ${response.code}): ${parseErr.message}",
                )
            }
            if (!parsed.ok || parsed.result == null) {
                throw TelegramApiException(
                    errorCode = parsed.errorCode ?: response.code,
                    apiDescription = parsed.description ?: "Unknown Telegram API error",
                    retryAfterSeconds = parsed.parameters?.retryAfter,
                )
            }
            return parsed.result
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.telegram.org"

        fun extractBotId(token: String): String? {
            val trimmed = token.trim()
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx <= 0 || colonIdx == trimmed.lastIndex) return null
            val botId = trimmed.substring(0, colonIdx)
            val secret = trimmed.substring(colonIdx + 1)
            if (!botId.all { it.isDigit() } || secret.length < 10) return null
            return botId
        }

        fun normalizeChannelId(rawInput: String): String {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) return ""
            if (trimmed.startsWith("@")) return trimmed
            if (trimmed.startsWith("-")) return trimmed
            if (trimmed.all { it.isDigit() }) {
                return if (trimmed.startsWith("100") && trimmed.length >= 11) {
                    "-$trimmed"
                } else {
                    "-100$trimmed"
                }
            }
            return trimmed
        }
    }
}
