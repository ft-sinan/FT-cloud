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
import com.ftcloud.app.core.model.AccentColor
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

        val scratchMap = prefs[KEY_BOT_SCRATCH_CHATS_JSON]
            ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
            .orEmpty()
        val activeBotIds = bots.map { it.botId }.toSet()

        AppSettings(
            isConfigured = channelId.isNotBlank() && bots.isNotEmpty(),
            channelId = channelId,
            channelTitle = channelTitle.ifBlank { channelId },
            botCount = bots.size,
            themeMode = prefs[KEY_THEME_MODE]
                ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.SYSTEM,
            accentColor = prefs[KEY_ACCENT_COLOR]
                ?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
                ?: AccentColor.ORION,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
            hapticsEnabled = prefs[KEY_HAPTICS_ENABLED] ?: true,
            reduceAnimations = prefs[KEY_REDUCE_ANIMATIONS] ?: false,
            glassBottomBar = prefs[KEY_GLASS_BOTTOM_BAR] ?: true,
            compactMode = prefs[KEY_COMPACT_MODE] ?: false,
            lastUpdateCheckAt = prefs[KEY_LAST_UPDATE_CHECK_AT] ?: 0L,
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
            multiDeviceAutoSync = prefs[KEY_MULTI_DEVICE_AUTO_SYNC] ?: true,
            pinnedManifestMessageId = prefs[KEY_PINNED_MANIFEST_MSG_ID],
            lastManifestSyncAt = prefs[KEY_LAST_MANIFEST_SYNC_AT],
            lastBackupRunAt = prefs[KEY_LAST_BACKUP_RUN_AT],
            lastBackupSummary = prefs[KEY_LAST_BACKUP_SUMMARY],
            channelScanRunning = prefs[KEY_CHANNEL_SCAN_RUNNING] ?: false,
            channelScanCursorId = prefs[KEY_CHANNEL_SCAN_CURSOR_ID] ?: 0L,
            channelScanMaxId = prefs[KEY_CHANNEL_SCAN_MAX_ID] ?: 0L,
            channelScanCheckedCount = prefs[KEY_CHANNEL_SCAN_CHECKED] ?: 0,
            channelScanImportedCount = prefs[KEY_CHANNEL_SCAN_IMPORTED] ?: 0,
            channelScanStatus = prefs[KEY_CHANNEL_SCAN_STATUS],
            turboDmBotCount = scratchMap.keys.count { it in activeBotIds },
        )
    }

    suspend fun getSnapshot(): AppSettings = settingsFlow.first()

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun selectAccentColor(accent: AccentColor) {
        dataStore.edit {
            it[KEY_ACCENT_COLOR] = accent.name
            it[KEY_DYNAMIC_COLOR] = false
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_HAPTICS_ENABLED] = enabled }
    }

    suspend fun setReduceAnimations(enabled: Boolean) {
        dataStore.edit { it[KEY_REDUCE_ANIMATIONS] = enabled }
    }

    suspend fun setGlassBottomBar(enabled: Boolean) {
        dataStore.edit { it[KEY_GLASS_BOTTOM_BAR] = enabled }
    }

    suspend fun setCompactMode(enabled: Boolean) {
        dataStore.edit { it[KEY_COMPACT_MODE] = enabled }
    }

    suspend fun markUpdateChecked(checkedAtMs: Long = System.currentTimeMillis()) {
        dataStore.edit { it[KEY_LAST_UPDATE_CHECK_AT] = checkedAtMs }
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

    suspend fun setMultiDeviceAutoSync(enabled: Boolean) {
        dataStore.edit { it[KEY_MULTI_DEVICE_AUTO_SYNC] = enabled }
    }

    suspend fun getDeletedTombstones(): Pair<Set<String>, Set<String>> {
        val snap = dataStore.data.first()
        val files = snap[KEY_DELETED_FILE_IDS_JSON]
            ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            .orEmpty()
            .toSet()
        val folders = snap[KEY_DELETED_FOLDER_IDS_JSON]
            ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            .orEmpty()
            .toSet()
        return files to folders
    }

    suspend fun recordDeletedTombstones(
        fileIds: Collection<String> = emptyList(),
        folderIds: Collection<String> = emptyList(),
    ) {
        if (fileIds.isEmpty() && folderIds.isEmpty()) return
        dataStore.edit { prefs ->
            if (fileIds.isNotEmpty()) {
                val cur = prefs[KEY_DELETED_FILE_IDS_JSON]
                    ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                    .orEmpty()
                val merged = (cur + fileIds).distinct().takeLast(MAX_TOMBSTONES)
                prefs[KEY_DELETED_FILE_IDS_JSON] = json.encodeToString(merged)
            }
            if (folderIds.isNotEmpty()) {
                val cur = prefs[KEY_DELETED_FOLDER_IDS_JSON]
                    ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                    .orEmpty()
                val merged = (cur + folderIds).distinct().takeLast(MAX_TOMBSTONES)
                prefs[KEY_DELETED_FOLDER_IDS_JSON] = json.encodeToString(merged)
            }
        }
    }

    suspend fun saveBotScratchChatsAll(chats: Map<String, String>) {
        if (chats.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_BOT_SCRATCH_CHATS_JSON]
                ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                .orEmpty()
                .toMutableMap()
            current.putAll(chats)
            prefs[KEY_BOT_SCRATCH_CHATS_JSON] = json.encodeToString(current)
        }
    }

    suspend fun removeBotScratchChat(botId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BOT_SCRATCH_CHATS_JSON]
                ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                .orEmpty()
                .toMutableMap()
            if (current.remove(botId) != null) {
                prefs[KEY_BOT_SCRATCH_CHATS_JSON] = json.encodeToString(current)
            }
        }
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

    suspend fun updateChannelScanState(
        running: Boolean? = null,
        cursorId: Long? = null,
        maxId: Long? = null,
        checkedCount: Int? = null,
        importedCount: Int? = null,
        status: String? = null,
    ) {
        dataStore.edit { prefs ->
            if (running != null) prefs[KEY_CHANNEL_SCAN_RUNNING] = running
            if (cursorId != null) prefs[KEY_CHANNEL_SCAN_CURSOR_ID] = cursorId
            if (maxId != null) prefs[KEY_CHANNEL_SCAN_MAX_ID] = maxId
            if (checkedCount != null) prefs[KEY_CHANNEL_SCAN_CHECKED] = checkedCount
            if (importedCount != null) prefs[KEY_CHANNEL_SCAN_IMPORTED] = importedCount
            if (status != null) prefs[KEY_CHANNEL_SCAN_STATUS] = status
        }
    }

    suspend fun resetChannelScanProgress() {
        dataStore.edit { prefs ->
            prefs[KEY_CHANNEL_SCAN_RUNNING] = false
            prefs[KEY_CHANNEL_SCAN_CURSOR_ID] = 0L
            prefs[KEY_CHANNEL_SCAN_MAX_ID] = 0L
            prefs[KEY_CHANNEL_SCAN_CHECKED] = 0
            prefs[KEY_CHANNEL_SCAN_IMPORTED] = 0
            prefs.remove(KEY_CHANNEL_SCAN_STATUS)
        }
    }

    suspend fun getBotUpdateOffsets(): Map<String, Long> {
        val raw = dataStore.data.first()[KEY_BOT_UPDATE_OFFSETS_JSON] ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, Long>>(raw) }.getOrDefault(emptyMap())
    }

    suspend fun saveBotUpdateOffset(botId: String, nextOffset: Long) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BOT_UPDATE_OFFSETS_JSON]
                ?.let { runCatching { json.decodeFromString<Map<String, Long>>(it) }.getOrNull() }
                .orEmpty()
                .toMutableMap()
            current[botId] = nextOffset
            prefs[KEY_BOT_UPDATE_OFFSETS_JSON] = json.encodeToString(current)
        }
    }

    suspend fun getBotScratchChats(): Map<String, String> {
        val raw = dataStore.data.first()[KEY_BOT_SCRATCH_CHATS_JSON] ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    suspend fun saveBotScratchChat(botId: String, chatId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BOT_SCRATCH_CHATS_JSON]
                ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                .orEmpty()
                .toMutableMap()
            current[botId] = chatId
            prefs[KEY_BOT_SCRATCH_CHATS_JSON] = json.encodeToString(current)
        }
    }

    suspend fun disconnectAccount() {
        credentialStore.clearAll()
        dataStore.edit {
            it.remove(KEY_PINNED_MANIFEST_MSG_ID)
            it.remove(KEY_LAST_MANIFEST_SYNC_AT)
            it.remove(KEY_BOT_UPDATE_OFFSETS_JSON)
            it.remove(KEY_BOT_SCRATCH_CHATS_JSON)
            it.remove(KEY_DELETED_FILE_IDS_JSON)
            it.remove(KEY_DELETED_FOLDER_IDS_JSON)
            it[KEY_CHANNEL_SCAN_RUNNING] = false
            it[KEY_AUTO_BACKUP_ENABLED] = false
            it[KEY_ENCRYPTION_ENABLED] = false
        }
    }

    companion object {
        private const val MAX_TOMBSTONES = 2000
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        private val KEY_REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        private val KEY_GLASS_BOTTOM_BAR = booleanPreferencesKey("glass_bottom_bar")
        private val KEY_COMPACT_MODE = booleanPreferencesKey("compact_mode")
        private val KEY_LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
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
        private val KEY_MULTI_DEVICE_AUTO_SYNC = booleanPreferencesKey("multi_device_auto_sync")
        private val KEY_DELETED_FILE_IDS_JSON = stringPreferencesKey("deleted_file_ids_json")
        private val KEY_DELETED_FOLDER_IDS_JSON = stringPreferencesKey("deleted_folder_ids_json")
        private val KEY_PINNED_MANIFEST_MSG_ID = longPreferencesKey("pinned_manifest_msg_id")
        private val KEY_LAST_MANIFEST_SYNC_AT = longPreferencesKey("last_manifest_sync_at")
        private val KEY_LAST_BACKUP_RUN_AT = longPreferencesKey("last_backup_run_at")
        private val KEY_LAST_BACKUP_SUMMARY = stringPreferencesKey("last_backup_summary")
        private val KEY_CHANNEL_SCAN_RUNNING = booleanPreferencesKey("channel_scan_running")
        private val KEY_CHANNEL_SCAN_CURSOR_ID = longPreferencesKey("channel_scan_cursor_id")
        private val KEY_CHANNEL_SCAN_MAX_ID = longPreferencesKey("channel_scan_max_id")
        private val KEY_CHANNEL_SCAN_CHECKED = intPreferencesKey("channel_scan_checked")
        private val KEY_CHANNEL_SCAN_IMPORTED = intPreferencesKey("channel_scan_imported")
        private val KEY_CHANNEL_SCAN_STATUS = stringPreferencesKey("channel_scan_status")
        private val KEY_BOT_UPDATE_OFFSETS_JSON = stringPreferencesKey("bot_update_offsets_json")
        private val KEY_BOT_SCRATCH_CHATS_JSON = stringPreferencesKey("bot_scratch_chats_json")
    }
}
