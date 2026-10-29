package com.ftcloud.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ftcloud.app.di.FTCloudContainer

class FTCloudApplication : Application(), ImageLoaderFactory {

    lateinit var container: FTCloudContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = FTCloudContainer(this)
        createNotificationChannels()
    }

    override fun newImageLoader(): ImageLoader = container.imageLoader

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val transfersChannel = NotificationChannel(
            CHANNEL_TRANSFERS,
            getString(R.string.notif_channel_transfers),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progress notifications for cloud uploads and downloads"
        }

        val backupChannel = NotificationChannel(
            CHANNEL_BACKUP,
            getString(R.string.notif_channel_backup),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Background photo and folder backup status"
        }

        manager.createNotificationChannels(listOf(transfersChannel, backupChannel))
    }

    companion object {
        const val CHANNEL_TRANSFERS = "ftcloud_transfers"
        const val CHANNEL_BACKUP = "ftcloud_backup"
    }
}
