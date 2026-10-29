package com.ftcloud.app.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Pure semantic version helpers kept Android-free for unit testing. */
object UpdateVersion {
    fun normalize(version: String): String = version
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')

    fun isNewer(latest: String, current: String): Boolean {
        val latestParts = normalize(latest).split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = normalize(current).split('.').map { it.toIntOrNull() ?: 0 }
        val max = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until max) {
            val newest = latestParts.getOrElse(index) { 0 }
            val installed = currentParts.getOrElse(index) { 0 }
            if (newest != installed) return newest > installed
        }
        return false
    }
}

/** SHA-256 sidecar parser supporting both bare hex digests and `sha256sum` output lines. */
object ChecksumParser {
    private val sha256Regex = Regex("\\b[a-fA-F0-9]{64}\\b")

    fun expectedHash(checksumText: String, apkName: String): String? {
        checksumText.lineSequence()
            .firstOrNull { line -> line.contains(apkName) && sha256Regex.containsMatchIn(line) }
            ?.let { line -> return sha256Regex.find(line)?.value?.lowercase() }
        return sha256Regex.find(checksumText)?.value?.lowercase()
    }
}

data class UpdateRelease(
    val versionName: String,
    val tagName: String,
    val name: String,
    val notes: String,
    val apkName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val checksumDownloadUrl: String?,
)

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String? = null,
    val assets: List<GitHubReleaseAssetDto> = emptyList(),
)

@Serializable
internal data class GitHubReleaseAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
)

/**
 * GitHub Releases in-app update checker, SHA-256 verifier, and APK installer
 * following the FT Vault / FT Cloud update architecture.
 */
object AppUpdateManager {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/ft-sinan/FT-cloud/releases/latest"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    internal fun parseReleaseJson(rawJson: String): UpdateRelease {
        val dto = json.decodeFromString<GitHubReleaseDto>(rawJson)
        val tag = dto.tagName.trim()
        require(tag.isNotBlank()) { "Release tag missing" }

        // Prefer release APK over debug APK if multiple APKs are attached
        val apkAssets = dto.assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
        }
        val chosenApk = apkAssets.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
            ?: apkAssets.firstOrNull()
        require(chosenApk != null) { "No APK attached to the latest GitHub release" }

        val checksumAsset = dto.assets.firstOrNull { asset ->
            val lower = asset.name.lowercase()
            (lower == "${chosenApk.name.lowercase()}.sha256" ||
                lower.endsWith(".sha256") ||
                lower.contains("checksum")) && asset.browserDownloadUrl.isNotBlank()
        }

        return UpdateRelease(
            versionName = UpdateVersion.normalize(tag),
            tagName = tag,
            name = dto.name.trim().ifBlank { "FT Cloud $tag" },
            notes = dto.body?.trim()?.takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
            apkName = chosenApk.name,
            apkDownloadUrl = chosenApk.browserDownloadUrl,
            apkSizeBytes = chosenApk.size,
            checksumDownloadUrl = checksumAsset?.browserDownloadUrl,
        )
    }

    suspend fun fetchLatestRelease(url: String = LATEST_RELEASE_URL): UpdateRelease =
        withContext(Dispatchers.IO) {
            parseReleaseJson(fetchText(url, githubApi = true))
        }

    suspend fun downloadReleaseApk(
        context: Context,
        release: UpdateRelease,
        onProgress: (Int?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val safeName = release.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(updatesDir, safeName)
        if (destination.exists()) destination.delete()

        val connection = openConnection(release.apkDownloadUrl, githubApi = false)
        try {
            val responseCode = connection.responseCode
            check(responseCode in 200..299) { "APK download failed: HTTP $responseCode" }
            val total = connection.contentLengthLong
                .takeIf { it > 0L }
                ?: release.apkSizeBytes.takeIf { it > 0L }
                ?: -1L

            connection.inputStream.buffered().use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0L) {
                            val percent = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        } else {
                            onProgress(null)
                        }
                    }
                }
            }
            check(destination.length() > 0L) { "Downloaded APK is empty" }
            destination
        } finally {
            connection.disconnect()
        }
    }

    /** Returns null when no checksum sidecar was published, or true/false on SHA-256 comparison. */
    suspend fun verifyChecksum(release: UpdateRelease, apk: File): Boolean? =
        withContext(Dispatchers.IO) {
            val checksumUrl = release.checksumDownloadUrl ?: return@withContext null
            val expected = ChecksumParser.expectedHash(
                checksumText = fetchText(checksumUrl, githubApi = false),
                apkName = release.apkName,
            ) ?: return@withContext null
            expected.equals(sha256(apk), ignoreCase = true)
        }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun fetchText(url: String, githubApi: Boolean): String {
        val connection = openConnection(url, githubApi)
        return try {
            val responseCode = connection.responseCode
            check(responseCode in 200..299) { "Request failed: HTTP $responseCode" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, githubApi: Boolean): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FTCloud-Android")
            if (githubApi) {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
        }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
