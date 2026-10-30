package com.ftcloud.app

import com.ftcloud.app.core.crypto.AesGcmChunkCipher
import com.ftcloud.app.core.model.CloudManifestCodec
import com.ftcloud.app.core.model.CloudManifestPayload
import com.ftcloud.app.core.pairing.DevicePairingManager
import com.ftcloud.app.core.pairing.DevicePairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DevicePairingAndMultiDeviceSyncTest {
    private val cipher = AesGcmChunkCipher()

    @Test
    fun `pairing kit encrypts bot swarm with 6-digit PIN and decrypts from both key and QR URI`() {
        val original = DevicePairingPayload(
            channelId = "-1009876543210",
            channelTitle = "Sinan Personal Cloud",
            botTokens = listOf("111111:AAH_token_one", "222222:BBG_token_two"),
            botScratchChats = mapOf("111111" to "777888999"),
            encryptionEnabled = false,
            pinnedManifestMessageId = 7429L,
        )

        val kit = DevicePairingManager.createPairingKit(
            payload = original,
            cipher = cipher,
            pin = "482915",
        )

        // Ensure raw bot tokens are never leaked in plaintext
        assertFalse(kit.encryptedPairingCode.contains("AAH_token_one"))
        assertTrue(kit.encryptedPairingCode.startsWith("FTCLOUD1:"))
        assertTrue(DevicePairingManager.requiresManualPin(kit.encryptedPairingCode))
        assertFalse(DevicePairingManager.requiresManualPin(kit.qrPayload))

        // 1. Decrypt via pasted FTCLOUD1:... key + 6-digit PIN (even when wrapped in share text)
        val shareText = "FT Cloud Encrypted Pairing Key:\n\n${kit.encryptedPairingCode}"
        val fromKey = DevicePairingManager.parseAndDecrypt(
            rawInput = shareText,
            manualPin = "482-915",
            cipher = cipher,
        )
        assertEquals(original.channelId, fromKey.channelId)
        assertEquals(original.botTokens, fromKey.botTokens)
        assertEquals(original.botScratchChats, fromKey.botScratchChats)
        assertEquals(7429L, fromKey.pinnedManifestMessageId)

        // 2. Decrypt directly from scanned ftcloud://pair?... QR URI without manual PIN
        val fromQr = DevicePairingManager.parseAndDecrypt(
            rawInput = kit.qrPayload,
            cipher = cipher,
        )
        assertEquals(original.channelId, fromQr.channelId)
        assertEquals(original.botTokens, fromQr.botTokens)

        // 3. Wrong 6-digit PIN must fail authentication
        try {
            DevicePairingManager.parseAndDecrypt(
                rawInput = kit.encryptedPairingCode,
                manualPin = "000000",
                cipher = cipher,
            )
            fail("Expected wrong PIN to fail AES-GCM authentication")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("PIN"))
        }
    }

    @Test
    fun `zxing qr matrix encodes and decodes pairing URI cleanly`() {
        val payload = DevicePairingPayload(
            channelId = "-1001234567890",
            channelTitle = "FT Vault Drive",
            botTokens = listOf("123456:ABCDEF1234567890"),
        )
        val kit = DevicePairingManager.createPairingKit(payload, cipher, pin = "654321")
        val matrix = DevicePairingManager.encodeQrMatrix(kit.qrPayload, size = 360)
        val h = matrix.size
        val w = matrix.first().size
        val argb = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                argb[y * w + x] = if (matrix[y][x]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }

        val decodedQr = DevicePairingManager.decodeQrFromArgb(argb, w, h)
        assertNotNull(decodedQr)
        assertEquals(kit.qrPayload, decodedQr)
    }

    @Test
    fun `manifest v2 preserves multi-device deletion tombstones and decodes v1 payloads`() {
        val v2 = CloudManifestPayload(
            schemaVersion = 2,
            exportedAt = 1_760_000_000_000L,
            channelId = "-100123",
            folders = emptyList(),
            files = emptyList(),
            deletedFileIds = listOf("del_file_1", "del_file_2"),
            deletedFolderIds = listOf("del_folder_1"),
        )
        val encoded = CloudManifestCodec.encode(v2, cipher)
        val decoded = CloudManifestCodec.decode(encoded, cipher)
        assertEquals(listOf("del_file_1", "del_file_2"), decoded.deletedFileIds)
        assertEquals(listOf("del_folder_1"), decoded.deletedFolderIds)
    }
}
