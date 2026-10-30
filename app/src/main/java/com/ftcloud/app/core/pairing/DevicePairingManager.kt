package com.ftcloud.app.core.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.ftcloud.app.core.crypto.AesGcmChunkCipher
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import java.util.EnumMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Serializable
data class DevicePairingPayload(
    val version: Int = 1,
    val channelId: String,
    val channelTitle: String = "",
    val botTokens: List<String>,
    val botScratchChats: Map<String, String> = emptyMap(),
    val encryptionEnabled: Boolean = false,
    val vaultSecretKeyBase64: String? = null,
    val vaultSaltBase64: String? = null,
    val vaultVerifier: String? = null,
    val githubUpdateToken: String = "",
    val pinnedManifestMessageId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class GeneratedPairingKit(
    val pin: String,
    val encryptedPairingCode: String,
    val qrPayload: String,
)

/**
 * Generates and decodes encrypted multi-phone FT Cloud pairing kits:
 * - Side-by-side phones: scan the on-screen QR code (`ftcloud://pair?...`) in one tap.
 * - Remote phones: copy/share the AES-256-GCM encrypted `FTCLOUD1:...` pairing key
 *   (safe to send over Telegram Saved Messages or chat) and unlock with the 6-digit PIN.
 */
object DevicePairingManager {
    const val PAIRING_CODE_PREFIX = "FTCLOUD1:"
    const val QR_SCHEME_PREFIX = "ftcloud://pair?"
    private const val PAIRING_AAD = "ftcloud-device-pair-v1"

    private val secureRandom = SecureRandom()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun generatePin(): String {
        val value = secureRandom.nextInt(900_000) + 100_000
        return value.toString()
    }

    fun createPairingKit(
        payload: DevicePairingPayload,
        cipher: AesGcmChunkCipher,
        pin: String = generatePin(),
    ): GeneratedPairingKit {
        val normalizedPin = normalizePin(pin)
        require(normalizedPin.length == 6) { "Pairing PIN must be 6 digits." }
        require(payload.channelId.isNotBlank() && payload.botTokens.isNotEmpty()) {
            "Connect your Telegram channel and at least one bot before linking another phone."
        }

        val rawBytes = json.encodeToString(payload).encodeToByteArray()
        val compressed = gzip(rawBytes)
        val salt = cipher.generateSalt()
        val key = cipher.deriveKey(("ftcloud-pin:$normalizedPin").toCharArray(), salt)
        val encrypted = cipher.encryptBytes(
            plainBytes = compressed,
            secretKey = key,
            aad = PAIRING_AAD,
        )

        val encoder = Base64.getUrlEncoder().withoutPadding()
        val saltB64 = encoder.encodeToString(salt)
        val ivB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(
            Base64.getDecoder().decode(encrypted.ivBase64)
        )
        val cipherB64 = encoder.encodeToString(encrypted.cipherBytes)

        val pairingCode = "$PAIRING_CODE_PREFIX$saltB64.$ivB64.$cipherB64"
        val encodedKit = URLEncoder.encode(pairingCode, Charsets.UTF_8.name())
        val qrPayload = "${QR_SCHEME_PREFIX}pin=$normalizedPin&kit=$encodedKit"

        return GeneratedPairingKit(
            pin = normalizedPin,
            encryptedPairingCode = pairingCode,
            qrPayload = qrPayload,
        )
    }

    fun requiresManualPin(rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        return !trimmed.startsWith(QR_SCHEME_PREFIX, ignoreCase = true)
    }

    fun parseAndDecrypt(
        rawInput: String,
        manualPin: String = "",
        cipher: AesGcmChunkCipher,
    ): DevicePairingPayload {
        val trimmed = rawInput.trim()
        require(trimmed.isNotBlank()) { "Pairing code is empty." }

        val effectivePin: String
        val effectiveCode: String

        if (trimmed.startsWith(QR_SCHEME_PREFIX, ignoreCase = true)) {
            val query = trimmed.substringAfter('?', "")
            val params = query.split('&')
                .mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx <= 0) null else {
                        val k = part.substring(0, idx)
                        val v = URLDecoder.decode(part.substring(idx + 1), Charsets.UTF_8.name())
                        k to v
                    }
                }
                .toMap()
            effectivePin = normalizePin(params["pin"].orEmpty().ifBlank { manualPin })
            effectiveCode = params["kit"].orEmpty().trim()
        } else {
            // Extract FTCLOUD1:... token even if surrounded by share message text
            val tokenMatch = Regex("FTCLOUD1:[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
                .find(trimmed)?.value ?: trimmed
            effectiveCode = tokenMatch
            effectivePin = normalizePin(manualPin)
        }

        require(effectiveCode.startsWith(PAIRING_CODE_PREFIX)) {
            "Invalid FT Cloud pairing code. Copy the FTCLOUD1:... key or scan the QR code from your first phone."
        }
        require(effectivePin.length == 6) {
            "Enter the 6-digit Pairing PIN shown on your first phone."
        }

        val body = effectiveCode.removePrefix(PAIRING_CODE_PREFIX)
        val segments = body.split('.')
        require(segments.size == 3) { "Malformed pairing code." }

        val urlDecoder = Base64.getUrlDecoder()
        val salt = urlDecoder.decode(segments[0])
        val ivStdB64 = Base64.getEncoder().encodeToString(urlDecoder.decode(segments[1]))
        val cipherBytes = urlDecoder.decode(segments[2])

        val key = cipher.deriveKey(("ftcloud-pin:$effectivePin").toCharArray(), salt)
        val compressed = runCatching {
            cipher.decryptBytes(
                cipherBytes = cipherBytes,
                ivBase64 = ivStdB64,
                secretKey = key,
                aad = PAIRING_AAD,
            )
        }.getOrElse {
            throw IllegalArgumentException("Incorrect 6-digit Pairing PIN or corrupted pairing key.")
        }

        val jsonText = gunzip(compressed).decodeToString()
        return json.decodeFromString(jsonText)
    }

    fun encodeQrMatrix(content: String, size: Int = 512): Array<BooleanArray> {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 2)
        }
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        return Array(bitMatrix.height) { y ->
            BooleanArray(bitMatrix.width) { x -> bitMatrix.get(x, y) }
        }
    }

    fun renderQrBitmap(content: String, sizePx: Int = 640): Bitmap {
        val matrix = encodeQrMatrix(content, sizePx)
        val h = matrix.size
        val w = matrix.firstOrNull()?.size ?: sizePx
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val row = matrix[y]
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (row[x]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    fun decodeQrFromArgb(argbPixels: IntArray, width: Int, height: Int): String? {
        val source = RGBLuminanceSource(width, height, argbPixels)
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }
        val reader = MultiFormatReader().apply { setHints(hints) }
        runCatching {
            return reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        }
        reader.reset()
        return runCatching {
            reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source))).text
        }.getOrNull()
    }

    fun decodeQrBitmap(bitmap: Bitmap): String? {
        val soft = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        val w = soft.width
        val h = soft.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        soft.getPixels(pixels, 0, w, 0, 0, w, h)
        return decodeQrFromArgb(pixels, w, h)
    }

    fun normalizePin(raw: String): String = raw.filter { it.isDigit() }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    }
}
