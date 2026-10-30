package com.ftcloud.app.core.update

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
    val apkApiUrl: String? = null,
    val apkSizeBytes: Long,
    val checksumDownloadUrl: String?,
    val checksumApiUrl: String? = null,
)

sealed interface ApkInstallCompatibility {
    data object Compatible : ApkInstallCompatibility
    data class SignatureConflict(
        val targetPackage: String,
        val runningDifferentPackage: Boolean,
    ) : ApkInstallCompatibility
    data class DowngradeConflict(
        val installedVersionCode: Long,
        val archiveVersionCode: Long,
    ) : ApkInstallCompatibility
}

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
    val url: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
)

/**
 * GitHub Releases in-app update checker, SHA-256 verifier, and APK installer.
 * Supports both Public GitHub repositories (zero config) and Private GitHub repositories
 * (via an optional read-only Fine-Grained Token with `Contents: Read` permission).
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

        val apkAssets = dto.assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) &&
                (it.browserDownloadUrl.isNotBlank() || it.url.isNotBlank())
        }
        val chosenApk = apkAssets.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
            ?: apkAssets.firstOrNull()
        require(chosenApk != null) { "No APK attached to the latest GitHub release" }

        val checksumAsset = dto.assets.firstOrNull { asset ->
            val lower = asset.name.lowercase()
            (lower == "${chosenApk.name.lowercase()}.sha256" ||
                lower.endsWith(".sha256") ||
                lower.contains("checksum")) &&
                (asset.browserDownloadUrl.isNotBlank() || asset.url.isNotBlank())
        }

        return UpdateRelease(
            versionName = UpdateVersion.normalize(tag),
            tagName = tag,
            name = dto.name.trim().ifBlank { "FT Cloud $tag" },
            notes = dto.body?.trim()?.takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
            apkName = chosenApk.name,
            apkDownloadUrl = chosenApk.browserDownloadUrl.ifBlank { chosenApk.url },
            apkApiUrl = chosenApk.url.takeIf { it.isNotBlank() },
            apkSizeBytes = chosenApk.size,
            checksumDownloadUrl = checksumAsset?.let { it.browserDownloadUrl.ifBlank { it.url } },
            checksumApiUrl = checksumAsset?.url?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun fetchLatestRelease(
        url: String = LATEST_RELEASE_URL,
        githubToken: String? = null,
    ): UpdateRelease = withContext(Dispatchers.IO) {
        val token = githubToken?.trim()?.takeIf { it.isNotBlank() }
        parseReleaseJson(
            fetchText(
                url = url,
                accept = "application/vnd.github+json",
                githubToken = token,
            )
        )
    }

    suspend fun downloadReleaseApk(
        context: Context,
        release: UpdateRelease,
        githubToken: String? = null,
        onProgress: (Int?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val safeName = release.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(updatesDir, safeName)
        if (destination.exists()) destination.delete()

        val token = githubToken?.trim()?.takeIf { it.isNotBlank() }
        // Private GitHub repos require downloading via the REST API asset endpoint with Accept: application/octet-stream
        val effectiveUrl = if (token != null && !release.apkApiUrl.isNullOrBlank()) {
            release.apkApiUrl
        } else {
            release.apkDownloadUrl
        }
        val acceptHeader = if (token != null && !release.apkApiUrl.isNullOrBlank()) {
            "application/octet-stream"
        } else {
            "*/*"
        }

        val connection = openFollowingRedirects(
            initialUrl = effectiveUrl,
            accept = acceptHeader,
            githubToken = token,
        )
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException(formatHttpError(responseCode, token != null, forAsset = true))
            }
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
    suspend fun verifyChecksum(
        release: UpdateRelease,
        apk: File,
        githubToken: String? = null,
    ): Boolean? = withContext(Dispatchers.IO) {
        val token = githubToken?.trim()?.takeIf { it.isNotBlank() }
        val checksumUrl = if (token != null && !release.checksumApiUrl.isNullOrBlank()) {
            release.checksumApiUrl
        } else {
            release.checksumDownloadUrl
        } ?: return@withContext null

        val acceptHeader = if (token != null && !release.checksumApiUrl.isNullOrBlank()) {
            "application/octet-stream"
        } else {
            "*/*"
        }

        val expected = ChecksumParser.expectedHash(
            checksumText = fetchText(
                url = checksumUrl,
                accept = acceptHeader,
                githubToken = token,
            ),
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

    /**
     * Pre-checks whether the downloaded APK's X.509 signing certificate matches any already-installed
     * copy of `com.ftcloud.app` on the device, preventing Android's cryptic
     * "App not installed as package conflicts with an existing package" error.
     */
    fun inspectInstallCompatibility(context: Context, apk: File): ApkInstallCompatibility {
        val pm = context.packageManager
        val archiveInfo = readArchivePackageInfo(pm, apk.absolutePath)
            ?: return ApkInstallCompatibility.Compatible
        val targetPkg = archiveInfo.packageName ?: return ApkInstallCompatibility.Compatible

        val installedInfo = readInstalledPackageInfo(pm, targetPkg)
            ?: return ApkInstallCompatibility.Compatible

        val archiveSigners = extractSignerDigests(archiveInfo)
        val installedSigners = extractSignerDigests(installedInfo)
        if (archiveSigners.isNotEmpty() && installedSigners.isNotEmpty() &&
            archiveSigners.intersect(installedSigners).isEmpty()
        ) {
            return ApkInstallCompatibility.SignatureConflict(
                targetPackage = targetPkg,
                runningDifferentPackage = context.packageName != targetPkg,
            )
        }

        val installedCode = getVersionCodeCompat(installedInfo)
        val archiveCode = getVersionCodeCompat(archiveInfo)
        if (archiveCode in 1 until installedCode) {
            return ApkInstallCompatibility.DowngradeConflict(
                installedVersionCode = installedCode,
                archiveVersionCode = archiveCode,
            )
        }

        return ApkInstallCompatibility.Compatible
    }

    suspend fun saveApkToDownloads(context: Context, apk: File, fileName: String): String =
        withContext(Dispatchers.IO) {
            val cleanName = fileName.ifBlank { apk.name }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, cleanName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not save APK into Downloads")
                resolver.openOutputStream(uri)?.use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                } ?: error("Could not write APK into Downloads")
                "Downloads/$cleanName"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                dir.mkdirs()
                val dest = File(dir, cleanName)
                apk.copyTo(dest, overwrite = true)
                dest.absolutePath
            }
        }

    fun requestUninstallPackage(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun readArchivePackageInfo(pm: PackageManager, path: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                path,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
        }
    }.getOrNull()

    private fun readInstalledPackageInfo(pm: PackageManager, pkg: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                pkg,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
        }
    }.getOrNull()

    private fun extractSignerDigests(info: PackageInfo): Set<String> {
        val rawCerts: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            val signers = if (signingInfo == null) {
                emptyArray()
            } else if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signers?.map { it.toByteArray() }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toByteArray() }.orEmpty()
        }
        return rawCerts.map { bytes ->
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { b -> "%02x".format(b) }
        }.toSet()
    }

    private fun getVersionCodeCompat(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun fetchText(
        url: String,
        accept: String,
        githubToken: String?,
    ): String {
        val connection = openFollowingRedirects(
            initialUrl = url,
            accept = accept,
            githubToken = githubToken,
        )
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException(formatHttpError(responseCode, githubToken != null, forAsset = false))
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openFollowingRedirects(
        initialUrl: String,
        accept: String,
        githubToken: String?,
    ): HttpURLConnection {
        var currentUrl = URL(initialUrl)
        val initialHost = currentUrl.host
        var attachAuth = !githubToken.isNullOrBlank()
        var currentAccept = accept

        repeat(6) {
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "FTCloud-Android")
                setRequestProperty("Accept", currentAccept)
                if (attachAuth && !githubToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $githubToken")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                } else if (currentAccept == "application/vnd.github+json") {
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                check(!location.isNullOrBlank()) { "Redirect missing Location header" }
                val nextUrl = URL(currentUrl, location)
                // Never forward GitHub Authorization header to external CDN / S3 / Azure Blob redirect targets
                if (!nextUrl.host.equals(initialHost, ignoreCase = true)) {
                    attachAuth = false
                    currentAccept = "*/*"
                }
                currentUrl = nextUrl
            } else {
                return conn
            }
        }
        error("Too many HTTP redirects while contacting GitHub Releases")
    }

    private fun formatHttpError(code: Int, hadToken: Boolean, forAsset: Boolean): String = when {
        code == 404 && !hadToken -> {
            "GitHub returned HTTP 404 because 'ft-sinan/FT-cloud' is currently Private. " +
                "Either change the repository visibility to Public in GitHub Settings, or tap 'Private Repo Token' below to add a read-only GitHub token (Contents: Read)."
        }
        code in listOf(401, 403, 404) && hadToken -> {
            if (forAsset) {
                "Could not download private release asset (HTTP $code). Verify your GitHub token has 'Contents: Read' access to ft-sinan/FT-cloud."
            } else {
                "GitHub token could not access ft-sinan/FT-cloud releases (HTTP $code). Ensure the token is valid and has 'Contents: Read' permission."
            }
        }
        else -> "GitHub update request failed: HTTP $code"
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
