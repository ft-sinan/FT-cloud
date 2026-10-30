package com.ftcloud.app

import com.ftcloud.app.core.update.AppUpdateManager
import com.ftcloud.app.core.update.ChecksumParser
import com.ftcloud.app.core.update.UpdateVersion
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class AppUpdateManagerTest {

    @Test
    fun `version comparison handles v prefix and multi-segment semver`() {
        assertEquals("1.1.0", UpdateVersion.normalize("v1.1.0"))
        assertEquals("2.0.1", UpdateVersion.normalize("V2.0.1-rc1+build4"))

        assertTrue(UpdateVersion.isNewer("v1.1.0", "1.0.0"))
        assertTrue(UpdateVersion.isNewer("1.2.1", "1.2.0"))
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
    fun `parseReleaseJson prefers signed release apk and captures api url for private repos`() {
        val sampleJson = """
            {
              "tag_name": "v1.2.1",
              "name": "FT Cloud v1.2.1",
              "body": "### What's New\n- Private & Public GitHub repo updater support",
              "assets": [
                {
                  "name": "FTCloud-v1.2.1-debug.apk",
                  "url": "https://api.github.com/repos/ft-sinan/FT-cloud/releases/assets/101",
                  "browser_download_url": "https://github.com/ft-sinan/FT-cloud/releases/download/v1.2.1/FTCloud-v1.2.1-debug.apk",
                  "size": 18200000
                },
                {
                  "name": "FTCloud-v1.2.1.apk",
                  "url": "https://api.github.com/repos/ft-sinan/FT-cloud/releases/assets/102",
                  "browser_download_url": "https://github.com/ft-sinan/FT-cloud/releases/download/v1.2.1/FTCloud-v1.2.1.apk",
                  "size": 15400000
                },
                {
                  "name": "FTCloud-v1.2.1.apk.sha256",
                  "url": "https://api.github.com/repos/ft-sinan/FT-cloud/releases/assets/103",
                  "browser_download_url": "https://github.com/ft-sinan/FT-cloud/releases/download/v1.2.1/FTCloud-v1.2.1.apk.sha256",
                  "size": 92
                }
              ]
            }
        """.trimIndent()

        val release = AppUpdateManager.parseReleaseJson(sampleJson)
        assertEquals("1.2.1", release.versionName)
        assertEquals("FTCloud-v1.2.1.apk", release.apkName)
        assertEquals("https://api.github.com/repos/ft-sinan/FT-cloud/releases/assets/102", release.apkApiUrl)
        assertEquals("https://api.github.com/repos/ft-sinan/FT-cloud/releases/assets/103", release.checksumApiUrl)
    }

    @Test
    fun `public repo HTTP 404 surfaces clean message and stale token falls back to public release`() = runBlocking {
        val apiServer = MockWebServer()
        apiServer.start()
        try {
            // 1. Unauthenticated 404 -> clean public-repo release message (no false 'Private Repo' prompt)
            apiServer.enqueue(MockResponse().setResponseCode(404))
            try {
                AppUpdateManager.fetchLatestRelease(url = apiServer.url("/releases/latest").toString())
                fail("Expected HTTP 404 error")
            } catch (err: IllegalStateException) {
                assertTrue(err.message.orEmpty().contains("404"))
                assertFalse(err.message.orEmpty().contains("Private"))
            }
            val unauthReq = apiServer.takeRequest()
            assertNull(unauthReq.getHeader("Authorization"))

            // 2. Stale/expired token returns 401 -> automatically falls back to unauthenticated public release
            apiServer.enqueue(MockResponse().setResponseCode(401))
            apiServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "tag_name": "v1.2.5",
                          "name": "FT Cloud v1.2.5",
                          "assets": [
                            {
                              "name": "FTCloud-v1.2.5.apk",
                              "url": "${apiServer.url("/assets/105")}",
                              "browser_download_url": "${apiServer.url("/download/FTCloud-v1.2.5.apk")}",
                              "size": 4096
                            }
                          ]
                        }
                        """.trimIndent()
                    )
            )
            val release = AppUpdateManager.fetchLatestRelease(
                url = apiServer.url("/releases/latest").toString(),
                githubToken = "github_pat_expired",
            )
            val firstReq = apiServer.takeRequest()
            assertEquals("Bearer github_pat_expired", firstReq.getHeader("Authorization"))
            val fallbackReq = apiServer.takeRequest()
            assertNull(fallbackReq.getHeader("Authorization"))
            assertEquals("1.2.5", release.versionName)
        } finally {
            apiServer.shutdown()
        }
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
