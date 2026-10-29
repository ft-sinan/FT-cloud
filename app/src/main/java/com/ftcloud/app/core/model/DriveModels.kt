package com.ftcloud.app.core.model

import kotlinx.serialization.Serializable
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

enum class FileCategory(val label: String) {
    IMAGE("Images"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    PDF("PDFs"),
    DOCUMENT("Documents"),
    CODE("Code & Text"),
    ARCHIVE("Archives"),
    OTHER("Other");

    companion object {
        private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "svg", "avif")
        private val videoExts = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "ts")
        private val audioExts = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac")
        private val docExts = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "rtf", "epub", "csv")
        private val codeExts = setOf(
            "txt", "md", "json", "xml", "yaml", "yml", "toml", "ini", "cfg", "log",
            "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "html", "css", "sh", "sql", "c", "cpp", "rs", "go"
        )
        private val archiveExts = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "apk", "aab", "iso")

        fun fromMimeAndName(mimeType: String, fileName: String): FileCategory {
            val normalizedMime = mimeType.lowercase(Locale.ROOT).trim()
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return when {
                normalizedMime == "application/pdf" || ext == "pdf" -> PDF
                normalizedMime.startsWith("image/") || ext in imageExts -> IMAGE
                normalizedMime.startsWith("video/") || ext in videoExts -> VIDEO
                normalizedMime.startsWith("audio/") || ext in audioExts -> AUDIO
                ext in archiveExts || normalizedMime.contains("zip") ||
                    normalizedMime.contains("compressed") ||
                    normalizedMime == "application/vnd.android.package-archive" -> ARCHIVE
                ext in codeExts || normalizedMime.startsWith("text/") ||
                    normalizedMime == "application/json" ||
                    normalizedMime == "application/xml" -> CODE
                ext in docExts || normalizedMime.contains("word") ||
                    normalizedMime.contains("spreadsheet") ||
                    normalizedMime.contains("presentation") -> DOCUMENT
                else -> OTHER
            }
        }

        fun guessMimeType(fileName: String): String {
            return when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "heic" -> "image/heic"
                "svg" -> "image/svg+xml"
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "mov" -> "video/quicktime"
                "mp3" -> "audio/mpeg"
                "m4a", "aac" -> "audio/mp4"
                "flac" -> "audio/flac"
                "wav" -> "audio/wav"
                "ogg", "opus" -> "audio/ogg"
                "pdf" -> "application/pdf"
                "json" -> "application/json"
                "xml" -> "application/xml"
                "md" -> "text/markdown"
                "html" -> "text/html"
                "csv" -> "text/csv"
                "txt", "log", "kt", "py", "js", "ts", "sh", "yaml", "yml" -> "text/plain"
                "zip" -> "application/zip"
                "apk" -> "application/vnd.android.package-archive"
                else -> "application/octet-stream"
            }
        }
    }
}

data class DriveFolder(
    val id: String,
    val name: String,
    val parentId: String?,
    val isStarred: Boolean,
    val isTrashed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class DriveFile(
    val id: String,
    val name: String,
    val folderId: String?,
    val mimeType: String,
    val category: FileCategory,
    val sizeBytes: Long,
    val chunkCount: Int,
    val sha256: String,
    val isEncrypted: Boolean,
    val isStarred: Boolean,
    val isTrashed: Boolean,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val capturedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val effectiveMediaTimestamp: Long
        get() = capturedAt ?: createdAt
}

data class FileChunk(
    val id: Long = 0L,
    val driveFileId: String,
    val chunkIndex: Int,
    val byteOffset: Long,
    val rawSize: Long,
    val storedSize: Long,
    val telegramMessageId: Long,
    val telegramFileId: String,
    val telegramFileUniqueId: String,
    val uploaderBotId: String,
    val sha256: String,
    val ivBase64: String?,
)

enum class SortOption(val label: String) {
    DATE_MODIFIED("Date modified"),
    NAME("Name"),
    SIZE("Size"),
}

enum class SortDirection(val label: String) {
    DESC("Descending"),
    ASC("Ascending"),
}

enum class DriveViewMode {
    LIST,
    GRID,
}

enum class AppThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED Pure Black"),
}

@Serializable
data class StoredBotToken(
    val botId: String,
    val token: String,
    val username: String = "",
    val displayName: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

data class BotRuntimeStatus(
    val botId: String,
    val username: String,
    val displayName: String,
    val maskedToken: String,
    val isHealthy: Boolean,
    val activeRequests: Int,
    val cooldownRemainingMs: Long,
    val uploadedBytes: Long,
    val downloadedBytes: Long,
    val lastLatencyMs: Long?,
    val lastError: String?,
)

enum class TransferDirection {
    UPLOAD,
    DOWNLOAD,
}

enum class TransferStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class TransferTask(
    val id: String,
    val driveFileId: String?,
    val fileName: String,
    val mimeType: String,
    val direction: TransferDirection,
    val status: TransferStatus,
    val sourceUri: String?,
    val targetFolderId: String?,
    val destinationPath: String?,
    val totalBytes: Long,
    val transferredBytes: Long,
    val totalChunks: Int,
    val completedChunks: Int,
    val activeBotLabel: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val progressFraction: Float
        get() = if (totalBytes <= 0L) {
            if (status == TransferStatus.COMPLETED) 1f else 0f
        } else {
            (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        }
}

@Serializable
data class BackupFolderConfig(
    val uriString: String,
    val displayName: String,
    val cloudSubfolder: String,
    val addedAt: Long = System.currentTimeMillis(),
)

data class AppSettings(
    val isConfigured: Boolean = false,
    val channelId: String = "",
    val channelTitle: String = "",
    val botCount: Int = 0,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val viewMode: DriveViewMode = DriveViewMode.LIST,
    val sortOption: SortOption = SortOption.DATE_MODIFIED,
    val sortDirection: SortDirection = SortDirection.DESC,
    val galleryColumns: Int = 3,
    val autoBackupEnabled: Boolean = false,
    val backupCameraPhotos: Boolean = true,
    val backupCameraVideos: Boolean = true,
    val backupWifiOnly: Boolean = true,
    val backupChargingOnly: Boolean = false,
    val backupFolders: List<BackupFolderConfig> = emptyList(),
    val streamCacheLimitMb: Int = 512,
    val biometricLockEnabled: Boolean = false,
    val blockScreenshots: Boolean = false,
    val encryptionEnabled: Boolean = false,
    val pinnedManifestMessageId: Long? = null,
    val lastManifestSyncAt: Long? = null,
    val lastBackupRunAt: Long? = null,
    val lastBackupSummary: String? = null,
    val channelScanRunning: Boolean = false,
    val channelScanCursorId: Long = 0L,
    val channelScanMaxId: Long = 0L,
    val channelScanCheckedCount: Int = 0,
    val channelScanImportedCount: Int = 0,
    val channelScanStatus: String? = null,
    val turboDmBotCount: Int = 0,
)

data class DriveStats(
    val totalFiles: Int = 0,
    val totalFolders: Int = 0,
    val totalBytes: Long = 0L,
    val mediaCount: Int = 0,
    val starredCount: Int = 0,
    val trashedCount: Int = 0,
    val backedUpCount: Int = 0,
)

object FormatUtils {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.US)
    private val dayHeaderFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        val pattern = if (digitGroups == 0) "#,##0" else "#,##0.1"
        return "${DecimalFormat(pattern).format(value)} ${units[digitGroups]}"
    }

    fun formatDuration(durationMs: Long?): String {
        if (durationMs == null || durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatTimestamp(epochMs: Long): String {
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(fullDateFormatter)
    }

    fun formatPhotoTimelineBucket(epochMs: Long, today: LocalDate = LocalDate.now()): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year && date.month == today.month -> date.format(dayHeaderFormatter)
            else -> date.format(monthYearFormatter)
        }
    }

    fun maskBotToken(token: String): String {
        val colonIdx = token.indexOf(':')
        if (colonIdx <= 0 || token.length < colonIdx + 6) return "••••••••"
        val botId = token.substring(0, colonIdx)
        val suffix = token.takeLast(4)
        return "$botId:••••••$suffix"
    }

    fun buildTelegramMessageUrl(channelId: String, messageId: Long): String? {
        if (messageId <= 0L || channelId.isBlank()) return null
        val trimmed = channelId.trim()
        return when {
            trimmed.startsWith("@") -> "https://t.me/${trimmed.removePrefix("@")}/$messageId"
            trimmed.startsWith("-100") -> "https://t.me/c/${trimmed.removePrefix("-100")}/$messageId"
            trimmed.all { it.isDigit() } -> "https://t.me/c/${trimmed.removePrefix("100")}/$messageId"
            else -> null
        }
    }

    fun buildTelegramAppDeepLink(channelId: String, messageId: Long): String? {
        if (messageId <= 0L || channelId.isBlank()) return null
        val trimmed = channelId.trim()
        return when {
            trimmed.startsWith("@") -> "tg://resolve?domain=${trimmed.removePrefix("@")}&post=$messageId"
            trimmed.startsWith("-100") -> "tg://privatepost?channel=${trimmed.removePrefix("-100")}&post=$messageId"
            else -> null
        }
    }
}
