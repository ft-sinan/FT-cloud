package com.ftcloud.app

import com.ftcloud.app.core.update.AppUpdateManager
import com.ftcloud.app.core.update.ChecksumParser
import com.ftcloud.app.core.update.UpdateVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppUpdateManagerTest {

    @Test
    fun `version comparison handles v prefix and multi-segment semver`() {
        assertEquals("1.1.0", UpdateVersion.normalize("v1.1.0"))
        assertEquals("2.0.1", UpdateVersion.normalize("V2.0.1-rc1+build4"))

        assertTrue(UpdateVersion.isNewer("v1.1.0", "1.0.0"))
        assertTrue(UpdateVersion.isNewer("1.2.0", "1.1.9"))
        assertTrue(UpdateVersion.isNewer("1.1.1", "1.1"))
        assertFalse(UpdateVersion.isNewer("v1.1.0", "1.1.0"))
        assertFalse(UpdateVersion.isNewer("v1.0.9", "1.1.0"))
    }

    @Test
    fun `checksum parser extracts sha256 from sha256sum line or bare digest`() {
        val digest = "a3f5c8e1d9b20476581234567890abcdef1234567890abcdef1234567890abcd"
        val otherDigest = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

        val multiLine = """
            $otherDigest  FTCloud-v1.1.0-debug.apk
            ${digest.uppercase()}  FTCloud-v1.1.0.apk
        """.trimIndent()

        assertEquals(digest, ChecksumParser.expectedHash(multiLine, "FTCloud-v1.1.0.apk"))
        assertEquals(digest, ChecksumParser.expectedHash("  $digest  \n", "FTCloud-v1.1.0.apk"))
        assertNull(ChecksumParser.expectedHash("not-a-valid-sha256", "FTCloud-v1.1.0.apk"))
    }

    @Test
    fun `parseReleaseJson prefers signed release apk and finds sha256 sidecar`() {
        val sampleJson = """
            {
              "tag_name": "v1.1.0",
              "name": "FT Cloud v1.1.0 — Orion Expressive & In-App Updater",
              "body": "### What's New\n- Signature Orion Material 3 Expressive theme\n- Seamless in-app updater",
              "assets": [
                {
                  "name": "FTCloud-v1.1.0-debug.apk",
                  "browser_download_url": "https://github.com/ft-sinan/FT-cloud/releases/download/v1.1.0/FTCloud-v1.1.0-debug.apk",
                  "size": 18200000
                },
                {
                  "name": "FTCloud-v1.1.0.apk",
                  "browser_download_url": "https://github.com/ft-sinan/FT-cloud/releases/download/v1.1.0/FTCloud-v1.1.0.apk",
                  "size": 15400000
                },
                {
                  "name": "FTCloud-v1.1.0.apk.sha256",
                  "browser_download_url": "https://github.com/ft-sinan/FT-cloud/releases/download/v1.1.0/FTCloud-v1.1.0.apk.sha256",
                  "size": 92
                }
              ]
            }
        """.trimIndent()

        val release = AppUpdateManager.parseReleaseJson(sampleJson)
        assertEquals("1.1.0", release.versionName)
        assertEquals("v1.1.0", release.tagName)
        assertEquals("FT Cloud v1.1.0 — Orion Expressive & In-App Updater", release.name)
        assertEquals("FTCloud-v1.1.0.apk", release.apkName)
        assertEquals(15_400_000L, release.apkSizeBytes)
        assertEquals(
            "https://github.com/ft-sinan/FT-cloud/releases/download/v1.1.0/FTCloud-v1.1.0.apk.sha256",
            release.checksumDownloadUrl,
        )
        assertTrue(release.notes.contains("Orion Material 3 Expressive"))
    }

    @Test
    fun `sha256 computes exact file digest`() {
        val temp = File.createTempFile("ftcloud-update-test", ".apk")
        try {
            temp.writeBytes("FTCloud-APK-Payload".encodeToByteArray())
            val actual = AppUpdateManager.sha256(temp)
            assertEquals(64, actual.length)
            assertEquals(actual, ChecksumParser.expectedHash("$actual  ${temp.name}", temp.name))
        } finally {
            temp.delete()
        }
    }
}
