package com.ftcloud.app

import com.ftcloud.app.core.crypto.AesGcmChunkCipher
import com.ftcloud.app.core.crypto.HashingUtils
import com.ftcloud.app.core.model.CloudManifestCodec
import com.ftcloud.app.core.model.CloudManifestPayload
import com.ftcloud.app.core.model.ManifestChunkDto
import com.ftcloud.app.core.model.ManifestFileDto
import com.ftcloud.app.core.model.ManifestFolderDto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException

class AesGcmChunkCipherTest {

    private val cipher = AesGcmChunkCipher()

    @Test
    fun `deriveKey produces deterministic key and valid verifier`() {
        val salt = cipher.generateSalt()
        val key1 = cipher.deriveKey("correct-horse-battery".toCharArray(), salt)
        val key2 = cipher.deriveKey("correct-horse-battery".toCharArray(), salt)
        val wrongKey = cipher.deriveKey("wrong-passphrase".toCharArray(), salt)

        assertArrayEquals(key1.encoded, key2.encoded)

        val verifier = cipher.createKeyVerifier(key1)
        assertTrue(cipher.verifyKey(key2, verifier))
        assertFalse(cipher.verifyKey(wrongKey, verifier))
    }

    @Test
    fun `encryptChunk and decryptChunk roundtrip and reject AAD chunk-index swap`() {
        val salt = cipher.generateSalt()
        val key = cipher.deriveKey("vault-secret-2026".toCharArray(), salt)
        val original = ByteArray(8192) { idx -> ((idx * 31 + 7) and 0xFF).toByte() }

        val encrypted = cipher.encryptChunk(
            plainBytes = original,
            secretKey = key,
            fileId = "file-abc-123",
            chunkIndex = 2,
        )

        assertEquals(original.size + AesGcmChunkCipher.GCM_TAG_BYTES, encrypted.cipherBytes.size)

        val decrypted = cipher.decryptChunk(
            cipherBytes = encrypted.cipherBytes,
            ivBase64 = encrypted.ivBase64,
            secretKey = key,
            fileId = "file-abc-123",
            chunkIndex = 2,
        )
        assertArrayEquals(original, decrypted)

        // Swapping chunkIndex must fail GCM AAD authentication
        try {
            cipher.decryptChunk(
                cipherBytes = encrypted.cipherBytes,
                ivBase64 = encrypted.ivBase64,
                secretKey = key,
                fileId = "file-abc-123",
                chunkIndex = 3,
            )
            fail("Expected AEADBadTagException when chunkIndex AAD is mismatched")
        } catch (_: AEADBadTagException) {
            // Expected
        }
    }

    @Test
    fun `CloudManifestCodec encodes and decodes both plain and encrypted GZIP manifests`() {
        val samplePayload = CloudManifestPayload(
            exportedAt = 1_760_000_000_000L,
            channelId = "-1009876543210",
            folders = listOf(
                ManifestFolderDto(
                    id = "folder-1",
                    name = "Camera Backup",
                    parentId = null,
                    isStarred = true,
                    createdAt = 1_760_000_000_000L,
                    updatedAt = 1_760_000_000_000L,
                )
            ),
            files = listOf(
                ManifestFileDto(
                    id = "file-1",
                    name = "sunset_4k.mp4",
                    folderId = "folder-1",
                    mimeType = "video/mp4",
                    category = "VIDEO",
                    sizeBytes = 39_845_888L,
                    chunkCount = 2,
                    sha256 = HashingUtils.sha256Hex("sample".encodeToByteArray()),
                    isEncrypted = true,
                    chunks = listOf(
                        ManifestChunkDto(
                            chunkIndex = 0,
                            byteOffset = 0L,
                            rawSize = 19_922_944L,
                            storedSize = 19_922_960L,
                            telegramMessageId = 101L,
                            telegramFileId = "BQACAgUAAx01",
                            uploaderBotId = "111111",
                            sha256 = "abc",
                            ivBase64 = "aXYxMjM0NTY3ODkw",
                        ),
                        ManifestChunkDto(
                            chunkIndex = 1,
                            byteOffset = 19_922_944L,
                            rawSize = 19_922_944L,
                            storedSize = 19_922_960L,
                            telegramMessageId = 102L,
                            telegramFileId = "BQACAgUAAx02",
                            uploaderBotId = "222222",
                            sha256 = "def",
                            ivBase64 = "aXYxMjM0NTY3ODkx",
                        ),
                    ),
                    createdAt = 1_760_000_000_000L,
                    updatedAt = 1_760_000_000_000L,
                )
            ),
        )

        val plainEnvelopeBytes = CloudManifestCodec.encode(samplePayload, cipher, passphrase = null)
        val plainHeader = CloudManifestCodec.inspectEnvelope(plainEnvelopeBytes)
        assertNotNull(plainHeader)
        assertFalse(plainHeader!!.encrypted)
        assertEquals(samplePayload, CloudManifestCodec.decode(plainEnvelopeBytes, cipher))

        val encryptedBytes = CloudManifestCodec.encode(
            payload = samplePayload,
            cipher = cipher,
            passphrase = "my-recovery-passphrase".toCharArray(),
        )
        val encHeader = CloudManifestCodec.inspectEnvelope(encryptedBytes)
        assertNotNull(encHeader)
        assertTrue(encHeader!!.encrypted)

        val decodedEncrypted = CloudManifestCodec.decode(
            envelopeBytes = encryptedBytes,
            cipher = cipher,
            passphrase = "my-recovery-passphrase".toCharArray(),
        )
        assertEquals(samplePayload, decodedEncrypted)
    }
}
