package com.ftcloud.app.di

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.ftcloud.app.core.crypto.AesGcmChunkCipher
import com.ftcloud.app.core.crypto.AndroidKeystoreCredentialStore
import com.ftcloud.app.core.crypto.CredentialStore
import com.ftcloud.app.core.database.FTCloudDatabase
import com.ftcloud.app.core.media.ChunkDiskCache
import com.ftcloud.app.core.media.ResolvedStreamTarget
import com.ftcloud.app.core.media.TelegramStreamProxy
import com.ftcloud.app.core.sync.ChannelMediaImporter
import com.ftcloud.app.core.sync.CloudManifestSync
import com.ftcloud.app.core.telegram.MultiBotSwarmManager
import com.ftcloud.app.core.telegram.TelegramBotApiClient
import com.ftcloud.app.core.telegram.TokenBucketRateLimiter
import com.ftcloud.app.core.transfer.ChunkedDownloadEngine
import com.ftcloud.app.core.transfer.ChunkedUploadEngine
import com.ftcloud.app.data.DriveRepository
import com.ftcloud.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class FTCloudContainer(context: Context) {
    private val appContext = context.applicationContext
    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val cipher: AesGcmChunkCipher = AesGcmChunkCipher()

    val credentialStore: CredentialStore by lazy {
        AndroidKeystoreCredentialStore(appContext)
    }

    val database: FTCloudDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            FTCloudDatabase::class.java,
            "ftcloud_drive.db",
        ).fallbackToDestructiveMigration().build()
    }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val telegramApiClient: TelegramBotApiClient by lazy {
        TelegramBotApiClient(httpClient = httpClient)
    }

    val swarmManager: MultiBotSwarmManager by lazy {
        MultiBotSwarmManager(
            apiClient = telegramApiClient,
            credentialStore = credentialStore,
            rateLimiter = TokenBucketRateLimiter(minBotIntervalMs = 350L),
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            context = appContext,
            credentialStore = credentialStore,
        )
    }

    val chunkDiskCache: ChunkDiskCache by lazy {
        ChunkDiskCache(
            cacheRootDir = File(appContext.cacheDir, "stream_chunks"),
            swarmManager = swarmManager,
            cipher = cipher,
            credentialStore = credentialStore,
            onChunkBotRebound = { fileId, chunkIndex, newFileId, newBotId ->
                database.chunkDao().updateChunkBotFileId(fileId, chunkIndex, newFileId, newBotId)
            },
            onChunkSizeResolved = { fileId, chunkIndex, resolvedPlainBytes, chunkCount ->
                database.chunkDao().updateChunkSize(fileId, chunkIndex, resolvedPlainBytes)
                if (chunkCount == 1) {
                    database.fileDao().updateFileSize(fileId, resolvedPlainBytes, System.currentTimeMillis())
                }
            },
        )
    }

    val streamProxy: TelegramStreamProxy by lazy {
        TelegramStreamProxy(
            targetResolver = { fileId ->
                val file = database.fileDao().getById(fileId)?.toDomain()
                if (file == null) {
                    null
                } else {
                    val chunks = database.chunkDao().getChunksForFile(fileId).map { it.toDomain() }
                    ResolvedStreamTarget(file = file, chunks = chunks)
                }
            },
            chunkFileProvider = { file, chunk, nextChunk ->
                chunkDiskCache.prefetchNextChunkAsync(file, nextChunk)
                chunkDiskCache.getOrFetchPlainChunkFile(file, chunk)
            }
        ).also { proxy ->
            proxy.ensureStarted()
        }
    }

    val uploadEngine: ChunkedUploadEngine by lazy {
        ChunkedUploadEngine(
            swarmManager = swarmManager,
            fileDao = database.fileDao(),
            chunkDao = database.chunkDao(),
            cipher = cipher,
            credentialStore = credentialStore,
        )
    }

    val downloadEngine: ChunkedDownloadEngine by lazy {
        ChunkedDownloadEngine(
            fileDao = database.fileDao(),
            chunkDao = database.chunkDao(),
            chunkDiskCache = chunkDiskCache,
        )
    }

    val manifestSync: CloudManifestSync by lazy {
        CloudManifestSync(
            swarmManager = swarmManager,
            folderDao = database.folderDao(),
            fileDao = database.fileDao(),
            chunkDao = database.chunkDao(),
            cipher = cipher,
            credentialStore = credentialStore,
        )
    }

    val channelMediaImporter: ChannelMediaImporter by lazy {
        ChannelMediaImporter(
            database = database,
            apiClient = telegramApiClient,
            swarmManager = swarmManager,
            credentialStore = credentialStore,
            settingsRepository = settingsRepository,
            manifestSync = manifestSync,
        )
    }

    val driveRepository: DriveRepository by lazy {
        DriveRepository(
            appContext = appContext,
            folderDao = database.folderDao(),
            fileDao = database.fileDao(),
            chunkDao = database.chunkDao(),
            transferDao = database.transferDao(),
            backupHistoryDao = database.backupHistoryDao(),
            swarmManager = swarmManager,
            manifestSync = manifestSync,
            chunkDiskCache = chunkDiskCache,
            settingsRepository = settingsRepository,
            credentialStore = credentialStore,
            cipher = cipher,
        )
    }

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .okHttpClient(httpClient)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    init {
        containerScope.launch {
            runCatching { channelMediaImporter.hydrateSavedScratchChats() }
            runCatching { channelMediaImporter.syncPendingBotUpdates() }
        }
        containerScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                chunkDiskCache.maxCacheBytes = settings.streamCacheLimitMb.toLong() * 1024L * 1024L
            }
        }
    }
}
