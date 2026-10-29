package com.ftcloud.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ftcloud.app.core.crypto.CredentialStore
import com.ftcloud.app.core.model.AppSettings
import com.ftcloud.app.core.model.AppThemeMode
import com.ftcloud.app.core.model.BackupFolderConfig
import com.ftcloud.app.core.model.DriveViewMode
import com.ftcloud.app.core.model.SortDirection
import com.ftcloud.app.core.model.SortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.ftCloudDataStore: DataStore<Preferences> by preferencesDataStore(name = "ftcloud_preferences")

class SettingsRepository(
    context: Context,
    private val credentialStore: CredentialStore,
) {
    private val dataStore = context.ftCloudDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settingsFlow: Flow<AppSettings> = combine(
        dataStore.data,
        credentialStore.botsFlow,
        credentialStore.channelIdFlow,
        credentialStore.channelTitleFlow,
    ) { prefs, bots, channelId, channelTitle ->
        val foldersRaw = prefs[KEY_BACKUP_FOLDERS_JSON]
        val backupFolders = if (foldersRaw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<BackupFolderConfig>>(foldersRaw) }.getOrDefault(emptyList())
        }

        AppSettings(
            isConfigured = channelId.isNotBlank() && bots.isNotEmpty(),
            channelId = channelId,
            channelTitle = channelTitle.ifBlank { channelId },
            botCount = bots.size,
            themeMode = prefs[KEY_THEME_MODE]
                ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            viewMode = prefs[KEY_VIEW_MODE]
                ?.let { runCatching { DriveViewMode.valueOf(it) }.getOrNull() }
                ?: DriveViewMode.LIST,
            sortOption = prefs[KEY_SORT_OPTION]
                ?.let { runCatching { SortOption.valueOf(it) }.getOrNull() }
                ?: SortOption.DATE_MODIFIED,
            sortDirection = prefs[KEY_SORT_DIRECTION]
                ?.let { runCatching { SortDirection.valueOf(it) }.getOrNull() }
                ?: SortDirection.DESC,
            galleryColumns = (prefs[KEY_GALLERY_COLUMNS] ?: 3).coerceIn(2, 5),
            autoBackupEnabled = prefs[KEY_AUTO_BACKUP_ENABLED] ?: false,
            backupCameraPhotos = prefs[KEY_BACKUP_CAMERA_PHOTOS] ?: true,
            backupCameraVideos = prefs[KEY_BACKUP_CAMERA_VIDEOS] ?: true,
            backupWifiOnly = prefs[KEY_BACKUP_WIFI_ONLY] ?: true,
            backupChargingOnly = prefs[KEY_BACKUP_CHARGING_ONLY] ?: false,
            backupFolders = backupFolders,
            streamCacheLimitMb = (prefs[KEY_STREAM_CACHE_MB] ?: 512).coerceIn(128, 4096),
            biometricLockEnabled = prefs[KEY_BIOMETRIC_LOCK] ?: false,
            blockScreenshots = prefs[KEY_BLOCK_SCREENSHOTS] ?: false,
            encryptionEnabled = prefs[KEY_ENCRYPTION_ENABLED] ?: false,
            pinnedManifestMessageId = prefs[KEY_PINNED_MANIFEST_MSG_ID],
            lastManifestSyncAt = prefs[KEY_LAST_MANIFEST_SYNC_AT],
            lastBackupRunAt = prefs[KEY_LAST_BACKUP_RUN_AT],
            lastBackupSummary = prefs[KEY_LAST_BACKUP_SUMMARY],
        )
    }

    suspend fun getSnapshot(): AppSettings = settingsFlow.first()

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setViewMode(mode: DriveViewMode) {
        dataStore.edit { it[KEY_VIEW_MODE] = mode.name }
    }

    suspend fun setSort(option: SortOption, direction: SortDirection) {
        dataStore.edit {
            it[KEY_SORT_OPTION] = option.name
            it[KEY_SORT_DIRECTION] = direction.name
        }
    }

    suspend fun setGalleryColumns(columns: Int) {
        dataStore.edit { it[KEY_GALLERY_COLUMNS] = columns.coerceIn(2, 5) }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setBackupCameraPhotos(enabled: Boolean) {
        dataStore.edit { it[KEY_BACKUP_CAMERA_PHOTOS] = enabled }
    }

    suspend fun setBackupCameraVideos(enabled: Boolean) {
        dataStore.edit { it[KEY_BACKUP_CAMERA_VIDEOS] = enabled }
    }

    suspend fun setBackupWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { it[KEY_BACKUP_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setBackupChargingOnly(chargingOnly: Boolean) {
        dataStore.edit { it[KEY_BACKUP_CHARGING_ONLY] = chargingOnly }
    }

    suspend fun addBackupFolder(folder: BackupFolderConfig) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BACKUP_FOLDERS_JSON]
                ?.let { runCatching { json.decodeFromString<List<BackupFolderConfig>>(it) }.getOrNull() }
                .orEmpty()
            val updated = (current.filterNot { it.uriString == folder.uriString } + folder)
            prefs[KEY_BACKUP_FOLDERS_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun removeBackupFolder(uriString: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BACKUP_FOLDERS_JSON]
                ?.let { runCatching { json.decodeFromString<List<BackupFolderConfig>>(it) }.getOrNull() }
                .orEmpty()
            val updated = current.filterNot { it.uriString == uriString }
            prefs[KEY_BACKUP_FOLDERS_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun setStreamCacheLimitMb(limitMb: Int) {
        dataStore.edit { it[KEY_STREAM_CACHE_MB] = limitMb.coerceIn(128, 4096) }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setBlockScreenshots(enabled: Boolean) {
        dataStore.edit { it[KEY_BLOCK_SCREENSHOTS] = enabled }
    }

    suspend fun setEncryptionEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENCRYPTION_ENABLED] = enabled }
    }

    suspend fun recordManifestSync(messageId: Long, syncedAtMs: Long = System.currentTimeMillis()) {
        dataStore.edit {
            it[KEY_PINNED_MANIFEST_MSG_ID] = messageId
            it[KEY_LAST_MANIFEST_SYNC_AT] = syncedAtMs
        }
    }

    suspend fun recordBackupRun(summary: String, ranAtMs: Long = System.currentTimeMillis()) {
        dataStore.edit {
            it[KEY_LAST_BACKUP_RUN_AT] = ranAtMs
            it[KEY_LAST_BACKUP_SUMMARY] = summary
        }
    }

    suspend fun disconnectAccount() {
        credentialStore.clearAll()
        dataStore.edit {
            it.remove(KEY_PINNED_MANIFEST_MSG_ID)
            it.remove(KEY_LAST_MANIFEST_SYNC_AT)
            it[KEY_AUTO_BACKUP_ENABLED] = false
            it[KEY_ENCRYPTION_ENABLED] = false
        }
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_VIEW_MODE = stringPreferencesKey("view_mode")
        private val KEY_SORT_OPTION = stringPreferencesKey("sort_option")
        private val KEY_SORT_DIRECTION = stringPreferencesKey("sort_direction")
        private val KEY_GALLERY_COLUMNS = intPreferencesKey("gallery_columns")
        private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_BACKUP_CAMERA_PHOTOS = booleanPreferencesKey("backup_camera_photos")
        private val KEY_BACKUP_CAMERA_VIDEOS = booleanPreferencesKey("backup_camera_videos")
        private val KEY_BACKUP_WIFI_ONLY = booleanPreferencesKey("backup_wifi_only")
        private val KEY_BACKUP_CHARGING_ONLY = booleanPreferencesKey("backup_charging_only")
        private val KEY_BACKUP_FOLDERS_JSON = stringPreferencesKey("backup_folders_json")
        private val KEY_STREAM_CACHE_MB = intPreferencesKey("stream_cache_mb")
        private val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        private val KEY_BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
        private val KEY_ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        private val KEY_PINNED_MANIFEST_MSG_ID = longPreferencesKey("pinned_manifest_msg_id")
        private val KEY_LAST_MANIFEST_SYNC_AT = longPreferencesKey("last_manifest_sync_at")
        private val KEY_LAST_BACKUP_RUN_AT = longPreferencesKey("last_backup_run_at")
        private val KEY_LAST_BACKUP_SUMMARY = stringPreferencesKey("last_backup_summary")
    }
}
