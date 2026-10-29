package com.ftcloud.app.core.model

import com.ftcloud.app.core.crypto.AesGcmChunkCipher
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.SecretKey

@Serializable
data class ManifestFolderDto(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val isStarred: Boolean = false,
    val isTrashed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ManifestChunkDto(
    val chunkIndex: Int,
    val byteOffset: Long,
    val rawSize: Long,
    val storedSize: Long,
    val telegramMessageId: Long,
    val telegramFileId: String,
    val telegramFileUniqueId: String = "",
    val uploaderBotId: String,
    val sha256: String,
    val ivBase64: String? = null,
)

@Serializable
data class ManifestFileDto(
    val id: String,
    val name: String,
    val folderId: String? = null,
    val mimeType: String,
    val category: String,
    val sizeBytes: Long,
    val chunkCount: Int,
    val sha256: String,
    val isEncrypted: Boolean = false,
    val isStarred: Boolean = false,
    val isTrashed: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val capturedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val chunks: List<ManifestChunkDto>,
)

@Serializable
data class CloudManifestPayload(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val channelId: String,
    val folders: List<ManifestFolderDto>,
    val files: List<ManifestFileDto>,
)

@Serializable
data class CloudManifestEnvelope(
    val magic: String = MANIFEST_MAGIC,
    val version: Int = 1,
    val encrypted: Boolean = false,
    val saltBase64: String? = null,
    val ivBase64: String? = null,
    val payloadBase64: String,
) {
    companion object {
        const val MANIFEST_MAGIC = "FTCLOUD_MANIFEST_V1"
        const val MANIFEST_FILE_NAME = "ftcloud-manifest-v1.json"
    }
}

object CloudManifestCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val MANIFEST_AAD = "ftcloud-manifest-v1"

    fun encode(
        payload: CloudManifestPayload,
        cipher: AesGcmChunkCipher,
        passphrase: CharArray? = null,
    ): ByteArray {
        val rawJsonBytes = json.encodeToString(payload).encodeToByteArray()
        val gzippedBytes = gzipCompress(rawJsonBytes)

        val envelope = if (passphrase != null && passphrase.isNotEmpty()) {
            val salt = cipher.generateSalt()
            val secretKey: SecretKey = cipher.deriveKey(passphrase, salt)
            val encrypted = cipher.encryptBytes(
                plainBytes = gzippedBytes,
                secretKey = secretKey,
                aad = MANIFEST_AAD,
            )
            CloudManifestEnvelope(
                encrypted = true,
                saltBase64 = Base64.getEncoder().encodeToString(salt),
                ivBase64 = encrypted.ivBase64,
                payloadBase64 = Base64.getEncoder().encodeToString(encrypted.cipherBytes),
            )
        } else {
            CloudManifestEnvelope(
                encrypted = false,
                payloadBase64 = Base64.getEncoder().encodeToString(gzippedBytes),
            )
        }

        return json.encodeToString(envelope).encodeToByteArray()
    }

    fun inspectEnvelope(envelopeBytes: ByteArray): CloudManifestEnvelope? {
        return runCatching {
            val parsed = json.decodeFromString<CloudManifestEnvelope>(envelopeBytes.decodeToString())
            if (parsed.magic == CloudManifestEnvelope.MANIFEST_MAGIC) parsed else null
        }.getOrNull()
    }

    fun decode(
        envelopeBytes: ByteArray,
        cipher: AesGcmChunkCipher,
        passphrase: CharArray? = null,
    ): CloudManifestPayload {
        val envelope = json.decodeFromString<CloudManifestEnvelope>(envelopeBytes.decodeToString())
        require(envelope.magic == CloudManifestEnvelope.MANIFEST_MAGIC) {
            "Unrecognized manifest header: ${envelope.magic}"
        }

        val payloadBytes = Base64.getDecoder().decode(envelope.payloadBase64)
        val gzippedBytes = if (envelope.encrypted) {
            require(passphrase != null && passphrase.isNotEmpty()) {
                "This cloud manifest is encrypted. Please provide your Vault passphrase."
            }
            val salt = Base64.getDecoder().decode(
                requireNotNull(envelope.saltBase64) { "Encrypted manifest missing salt" }
            )
            val ivBase64 = requireNotNull(envelope.ivBase64) { "Encrypted manifest missing IV" }
            val secretKey = cipher.deriveKey(passphrase, salt)
            cipher.decryptBytes(
                cipherBytes = payloadBytes,
                ivBase64 = ivBase64,
                secretKey = secretKey,
                aad = MANIFEST_AAD,
            )
        } else {
            payloadBytes
        }

        val rawJson = gzipDecompress(gzippedBytes).decodeToString()
        return json.decodeFromString<CloudManifestPayload>(rawJson)
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gzipDecompress(compressed: ByteArray): ByteArray {
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            return gis.readBytes()
        }
    }
}
