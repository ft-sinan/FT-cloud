package com.ftcloud.app.core.sync

import android.app.Notification
import android.content.ContentUris
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ftcloud.app.FTCloudApplication
import com.ftcloud.app.R
import com.ftcloud.app.core.crypto.HashingUtils
import com.ftcloud.app.core.database.BackedUpItemEntity
import com.ftcloud.app.core.model.AppSettings
import com.ftcloud.app.core.model.FileCategory
import com.ftcloud.app.core.transfer.UploadRequest
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FTCloudApplication).container
        val settings = container.settingsRepository.getSnapshot()
        if (!settings.isConfigured) {
            return Result.success()
        }

        val isManual = inputData.getBoolean(KEY_MANUAL_TRIGGER, false)
        if (!settings.autoBackupEnabled && !isManual) {
            return Result.success()
        }

        runCatching {
            setForeground(buildForegroundInfo("Scanning media & folders for cloud backup…"))
        }

        var newlyUploaded = 0
        var skippedExisting = 0
        var failedCount = 0

        if (settings.backupCameraPhotos) {
            val r = scanMediaStoreCollection(
                collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                cloudPath = listOf("Auto Backup", "Photos"),
                settings = settings,
            )
            newlyUploaded += r.uploaded
            skippedExisting += r.skipped
            failedCount += r.failed
        }

        if (settings.backupCameraVideos) {
            val r = scanMediaStoreCollection(
                collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                cloudPath = listOf("Auto Backup", "Videos"),
                settings = settings,
            )
            newlyUploaded += r.uploaded
            skippedExisting += r.skipped
            failedCount += r.failed
        }

        for (folderRule in settings.backupFolders) {
            if (isStopped) break
            val treeUri = runCatching { Uri.parse(folderRule.uriString) }.getOrNull() ?: continue
            val rootDoc = DocumentFile.fromTreeUri(applicationContext, treeUri) ?: continue
            val r = scanDocumentTreeRecursive(
                directory = rootDoc,
                cloudPath = listOf("Auto Backup", folderRule.cloudSubfolder.ifBlank { folderRule.displayName }),
                settings = settings,
            )
            newlyUploaded += r.uploaded
            skippedExisting += r.skipped
            failedCount += r.failed
        }

        if (newlyUploaded > 0) {
            runCatching { container.driveRepository.syncManifestNow() }
        }

        val summary = buildString {
            append("Uploaded $newlyUploaded new")
            if (skippedExisting > 0) append(" • $skippedExisting up-to-date")
            if (failedCount > 0) append(" • $failedCount failed")
        }
        container.settingsRepository.recordBackupRun(summary)

        return if (failedCount > 0 && newlyUploaded == 0) Result.retry() else Result.success()
    }

    private data class ScanStats(
        val uploaded: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
    )

    private suspend fun scanMediaStoreCollection(
        collectionUri: Uri,
        cloudPath: List<String>,
        settings: AppSettings,
    ): ScanStats {
        val container = (applicationContext as FTCloudApplication).container
        val historyDao = container.database.backupHistoryDao()
        val fileDao = container.database.fileDao()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        var uploaded = 0
        var skipped = 0
        var failed = 0

        val targetFolderId = runCatching {
            container.driveRepository.getOrCreateFolderPath(cloudPath)
        }.getOrNull()

        runCatching {
            applicationContext.contentResolver.query(
                collectionUri,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                var inspected = 0
                while (cursor.moveToNext() && !isStopped && inspected < 250) {
                    inspected++
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "media_$id"
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0L) continue
                    val modifiedMs = cursor.getLong(modCol) * 1000L
                    val mime = cursor.getString(mimeCol) ?: FileCategory.guessMimeType(name)
                    val itemUri = ContentUris.withAppendedId(collectionUri, id)
                    val sourceKey = itemUri.toString()

                    val prev = historyDao.getBySourceKey(sourceKey)
                    if (prev != null && prev.sizeBytes == size && prev.lastModifiedMs == modifiedMs) {
                        skipped++
                        continue
                    }

                    try {
                        val sha256 = applicationContext.contentResolver.openInputStream(itemUri)?.use {
                            HashingUtils.sha256Hex(it)
                        } ?: continue

                        val duplicateInDrive = fileDao.findActiveBySha256(sha256)
                        if (duplicateInDrive != null) {
                            historyDao.upsert(
                                BackedUpItemEntity(
                                    sourceKey = sourceKey,
                                    fileName = name,
                                    sizeBytes = size,
                                    lastModifiedMs = modifiedMs,
                                    sha256 = sha256,
                                    driveFileId = duplicateInDrive.id,
                                    backedUpAt = System.currentTimeMillis(),
                                )
                            )
                            skipped++
                            continue
                        }

                        runCatching {
                            setForeground(buildForegroundInfo("Backing up $name…"))
                        }

                        val meta = container.driveRepository.extractLocalMediaMetadata(itemUri, mime)
                        val createdFile = container.uploadEngine.upload(
                            request = UploadRequest(
                                fileName = name,
                                mimeType = mime,
                                knownSizeBytes = size,
                                folderId = targetFolderId,
                                encrypt = settings.encryptionEnabled,
                                width = meta.width,
                                height = meta.height,
                                durationMs = meta.durationMs,
                                capturedAt = meta.capturedAt ?: modifiedMs.takeIf { it > 0L },
                            ),
                            openStream = {
                                applicationContext.contentResolver.openInputStream(itemUri)
                                    ?: throw IllegalStateException("Cannot open $itemUri")
                            }
                        )

                        historyDao.upsert(
                            BackedUpItemEntity(
                                sourceKey = sourceKey,
                                fileName = name,
                                sizeBytes = size,
                                lastModifiedMs = modifiedMs,
                                sha256 = createdFile.sha256,
                                driveFileId = createdFile.id,
                                backedUpAt = System.currentTimeMillis(),
                            )
                        )
                        uploaded++
                    } catch (_: Exception) {
                        failed++
                    }
                }
            }
        }

        return ScanStats(uploaded, skipped, failed)
    }

    private suspend fun scanDocumentTreeRecursive(
        directory: DocumentFile,
        cloudPath: List<String>,
        settings: AppSettings,
    ): ScanStats {
        val container = (applicationContext as FTCloudApplication).container
        val historyDao = container.database.backupHistoryDao()
        val fileDao = container.database.fileDao()

        var uploaded = 0
        var skipped = 0
        var failed = 0

        val targetFolderId = runCatching {
            container.driveRepository.getOrCreateFolderPath(cloudPath)
        }.getOrNull()

        for (child in directory.listFiles()) {
            if (isStopped) break
            val childName = child.name ?: continue
            if (childName.startsWith(".")) continue

            if (child.isDirectory) {
                val sub = scanDocumentTreeRecursive(
                    directory = child,
                    cloudPath = cloudPath + childName,
                    settings = settings,
                )
                uploaded += sub.uploaded
                skipped += sub.skipped
                failed += sub.failed
            } else if (child.isFile) {
                val size = child.length()
                if (size <= 0L) continue
                val modified = child.lastModified()
                val sourceKey = child.uri.toString()

                val prev = historyDao.getBySourceKey(sourceKey)
                if (prev != null && prev.sizeBytes == size && prev.lastModifiedMs == modified) {
                    skipped++
                    continue
                }

                try {
                    val sha256 = applicationContext.contentResolver.openInputStream(child.uri)?.use {
                        HashingUtils.sha256Hex(it)
                    } ?: continue

                    val dup = fileDao.findActiveBySha256(sha256)
                    if (dup != null) {
                        historyDao.upsert(
                            BackedUpItemEntity(
                                sourceKey = sourceKey,
                                fileName = childName,
                                sizeBytes = size,
                                lastModifiedMs = modified,
                                sha256 = sha256,
                                driveFileId = dup.id,
                                backedUpAt = System.currentTimeMillis(),
                            )
                        )
                        skipped++
                        continue
                    }

                    runCatching {
                        setForeground(buildForegroundInfo("Backing up $childName…"))
                    }

                    val mime = child.type ?: FileCategory.guessMimeType(childName)
                    val meta = container.driveRepository.extractLocalMediaMetadata(child.uri, mime)
                    val createdFile = container.uploadEngine.upload(
                        request = UploadRequest(
                            fileName = childName,
                            mimeType = mime,
                            knownSizeBytes = size,
                            folderId = targetFolderId,
                            encrypt = settings.encryptionEnabled,
                            width = meta.width,
                            height = meta.height,
                            durationMs = meta.durationMs,
                            capturedAt = meta.capturedAt ?: modified.takeIf { it > 0L },
                        ),
                        openStream = {
                            applicationContext.contentResolver.openInputStream(child.uri)
                                ?: throw IllegalStateException("Cannot open ${child.uri}")
                        }
                    )

                    historyDao.upsert(
                        BackedUpItemEntity(
                            sourceKey = sourceKey,
                            fileName = childName,
                            sizeBytes = size,
                            lastModifiedMs = modified,
                            sha256 = createdFile.sha256,
                            driveFileId = createdFile.id,
                            backedUpAt = System.currentTimeMillis(),
                        )
                    )
                    uploaded++
                } catch (_: Exception) {
                    failed++
                }
            }
        }
        return ScanStats(uploaded, skipped, failed)
    }

    private fun buildForegroundInfo(text: String): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(
            applicationContext,
            FTCloudApplication.CHANNEL_BACKUP,
        )
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("FT-Cloud Auto Backup")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_MANUAL_TRIGGER = "manual_trigger"
        const val PERIODIC_WORK_NAME = "ftcloud_periodic_auto_backup"
        const val IMMEDIATE_WORK_NAME = "ftcloud_immediate_auto_backup"
        private const val NOTIFICATION_ID = 4102
    }
}

object BackupScheduler {
    fun syncSchedule(
        context: Context,
        enabled: Boolean,
        wifiOnly: Boolean,
        chargingOnly: Boolean,
    ) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(AutoBackupWorker.PERIODIC_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(chargingOnly)
            .build()

        val periodic = PeriodicWorkRequestBuilder<AutoBackupWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        wm.enqueueUniquePeriodicWork(
            AutoBackupWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
    }

    fun triggerImmediateBackup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(constraints)
            .setInputData(
                androidx.work.workDataOf(AutoBackupWorker.KEY_MANUAL_TRIGGER to true)
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            AutoBackupWorker.IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
