package com.ftcloud.app.core.transfer

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ftcloud.app.FTCloudApplication
import com.ftcloud.app.R
import com.ftcloud.app.core.model.TransferDirection
import com.ftcloud.app.core.model.TransferStatus
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

class TransferWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FTCloudApplication).container
        val transferDao = container.database.transferDao()
        val settings = container.settingsRepository.getSnapshot()

        if (!settings.isConfigured) {
            return Result.success()
        }

        runCatching {
            setForeground(buildForegroundInfo("Syncing cloud transfers…"))
        }

        while (!isStopped) {
            val pending = transferDao.getPendingTransfers()
            if (pending.isEmpty()) break

            for (task in pending) {
                if (isStopped) break
                val fresh = transferDao.getById(task.id) ?: continue
                if (fresh.status == TransferStatus.PAUSED.name || fresh.status == TransferStatus.CANCELLED.name) {
                    continue
                }

                runCatching {
                    setForeground(buildForegroundInfo("${fresh.direction.lowercase().replaceFirstChar { it.uppercase() }}: ${fresh.fileName}"))
                }

                transferDao.updateStatus(
                    id = fresh.id,
                    status = TransferStatus.RUNNING.name,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis(),
                )

                try {
                    if (fresh.direction == TransferDirection.UPLOAD.name) {
                        val sourceUriStr = fresh.sourceUri
                            ?: throw IOException("Missing local source URI for upload")
                        val uri = Uri.parse(sourceUriStr)
                        val mediaMeta = container.driveRepository.extractLocalMediaMetadata(uri, fresh.mimeType)
                        val fileId = fresh.driveFileId ?: UUID.randomUUID().toString()
                        transferDao.attachDriveFileId(fresh.id, fileId, System.currentTimeMillis())

                        val currentSettings = container.settingsRepository.getSnapshot()
                        val uploadedFile = container.uploadEngine.upload(
                            request = UploadRequest(
                                fileId = fileId,
                                fileName = fresh.fileName,
                                mimeType = fresh.mimeType,
                                knownSizeBytes = fresh.totalBytes,
                                folderId = fresh.targetFolderId,
                                encrypt = currentSettings.encryptionEnabled,
                                width = mediaMeta.width,
                                height = mediaMeta.height,
                                durationMs = mediaMeta.durationMs,
                                capturedAt = mediaMeta.capturedAt,
                            ),
                            openStream = {
                                openUriStream(uri)
                            },
                            onProgress = { bytes, doneChunks, totalChunks, botLabel ->
                                val latest = transferDao.getById(fresh.id)
                                if (latest?.status == TransferStatus.PAUSED.name ||
                                    latest?.status == TransferStatus.CANCELLED.name
                                ) {
                                    throw kotlinx.coroutines.CancellationException("Transfer paused or cancelled")
                                }
                                transferDao.updateProgress(
                                    id = fresh.id,
                                    status = TransferStatus.RUNNING.name,
                                    transferredBytes = bytes,
                                    completedChunks = doneChunks,
                                    activeBotLabel = botLabel,
                                    errorMessage = null,
                                    updatedAt = System.currentTimeMillis(),
                                )
                            }
                        )

                        transferDao.updateProgress(
                            id = fresh.id,
                            status = TransferStatus.COMPLETED.name,
                            transferredBytes = uploadedFile.sizeBytes,
                            completedChunks = uploadedFile.chunkCount,
                            activeBotLabel = null,
                            errorMessage = null,
                            updatedAt = System.currentTimeMillis(),
                        )
                        container.driveRepository.scheduleManifestSyncDebounced()
                    } else {
                        val driveFileId = fresh.driveFileId
                            ?: throw IOException("Missing cloud file ID for download")
                        val destPath = fresh.destinationPath
                            ?: throw IOException("Missing destination path for download")
                        val destFile = File(destPath)

                        container.downloadEngine.downloadToFile(
                            driveFileId = driveFileId,
                            destinationFile = destFile,
                            onProgress = { bytes, doneChunks, totalChunks ->
                                val latest = transferDao.getById(fresh.id)
                                if (latest?.status == TransferStatus.PAUSED.name ||
                                    latest?.status == TransferStatus.CANCELLED.name
                                ) {
                                    throw kotlinx.coroutines.CancellationException("Transfer paused or cancelled")
                                }
                                transferDao.updateProgress(
                                    id = fresh.id,
                                    status = TransferStatus.RUNNING.name,
                                    transferredBytes = bytes,
                                    completedChunks = doneChunks,
                                    activeBotLabel = null,
                                    errorMessage = null,
                                    updatedAt = System.currentTimeMillis(),
                                )
                            }
                        )

                        transferDao.updateProgress(
                            id = fresh.id,
                            status = TransferStatus.COMPLETED.name,
                            transferredBytes = destFile.length(),
                            completedChunks = fresh.totalChunks.coerceAtLeast(1),
                            activeBotLabel = null,
                            errorMessage = null,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Respect user pause or cancel state already written to DB
                } catch (err: Throwable) {
                    transferDao.updateStatus(
                        id = fresh.id,
                        status = TransferStatus.FAILED.name,
                        errorMessage = err.message ?: "Transfer failed",
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            }
        }

        return Result.success()
    }

    private fun openUriStream(uri: Uri) = if (uri.scheme == "file") {
        FileInputStream(File(requireNotNull(uri.path) { "Empty file path" }))
    } else {
        applicationContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot read stream for URI: $uri")
    }

    private fun buildForegroundInfo(contentText: String): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(
            applicationContext,
            FTCloudApplication.CHANNEL_TRANSFERS,
        )
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(contentText)
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
        private const val UNIQUE_WORK_NAME = "ftcloud_transfer_queue_worker"
        private const val NOTIFICATION_ID = 4101

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TransferWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}
