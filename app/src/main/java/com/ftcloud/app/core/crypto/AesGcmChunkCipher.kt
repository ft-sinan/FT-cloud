package com.ftcloud.app.core.crypto

import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val cipherBytes: ByteArray,
    val ivBase64: String,
)

class AesGcmChunkCipher {
    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val encoded = factory.generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encryptChunk(
        plainBytes: ByteArray,
        secretKey: SecretKey,
        fileId: String,
        chunkIndex: Int,
    ): EncryptedPayload {
        return encryptBytes(
            plainBytes = plainBytes,
            secretKey = secretKey,
            aad = chunkAad(fileId, chunkIndex),
        )
    }

    fun decryptChunk(
        cipherBytes: ByteArray,
        ivBase64: String,
        secretKey: SecretKey,
        fileId: String,
        chunkIndex: Int,
    ): ByteArray {
        return decryptBytes(
            cipherBytes = cipherBytes,
            ivBase64 = ivBase64,
            secretKey = secretKey,
            aad = chunkAad(fileId, chunkIndex),
        )
    }

    fun encryptBytes(
        plainBytes: ByteArray,
        secretKey: SecretKey,
        aad: String,
    ): EncryptedPayload {
        val iv = ByteArray(GCM_IV_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad.encodeToByteArray())
        val encrypted = cipher.doFinal(plainBytes)
        return EncryptedPayload(
            cipherBytes = encrypted,
            ivBase64 = Base64.getEncoder().encodeToString(iv),
        )
    }

    fun decryptBytes(
        cipherBytes: ByteArray,
        ivBase64: String,
        secretKey: SecretKey,
        aad: String,
    ): ByteArray {
        val iv = Base64.getDecoder().decode(ivBase64)
        require(iv.size == GCM_IV_BYTES) { "Invalid AES-GCM IV length: ${iv.size}" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad.encodeToByteArray())
        return cipher.doFinal(cipherBytes)
    }

    fun createKeyVerifier(secretKey: SecretKey): String {
        val encrypted = encryptBytes(
            plainBytes = VERIFIER_PLAINTEXT.encodeToByteArray(),
            secretKey = secretKey,
            aad = VERIFIER_AAD,
        )
        val cipherB64 = Base64.getEncoder().encodeToString(encrypted.cipherBytes)
        return "${encrypted.ivBase64}:$cipherB64"
    }

    fun verifyKey(secretKey: SecretKey, verifierToken: String): Boolean {
        val parts = verifierToken.split(':')
        if (parts.size != 2) return false
        return runCatching {
            val cipherBytes = Base64.getDecoder().decode(parts[1])
            val plain = decryptBytes(cipherBytes, parts[0], secretKey, VERIFIER_AAD)
            plain.decodeToString() == VERIFIER_PLAINTEXT
        }.getOrDefault(false)
    }

    private fun chunkAad(fileId: String, chunkIndex: Int): String = "$fileId:$chunkIndex"

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = 16
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
        private const val PBKDF2_ITERATIONS = 210_000
        private const val VERIFIER_PLAINTEXT = "FTCLOUD_KEY_OK_V1"
        private const val VERIFIER_AAD = "ftcloud-key-verifier"
    }
}

object HashingUtils {
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.toHexStringSimple()
    }

    fun sha256Hex(inputStream: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0) break
            md.update(buffer, 0, read)
        }
        return md.digest().toHexStringSimple()
    }

    fun ByteArray.toHexStringSimple(): String {
        val chars = CharArray(size * 2)
        val hexDigits = "0123456789abcdef"
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            chars[i * 2] = hexDigits[v ushr 4]
            chars[i * 2 + 1] = hexDigits[v and 0x0F]
        }
        return String(chars)
    }
}
