package com.ftcloud.app.ui.screens.settings

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ftcloud.app.BuildConfig
import com.ftcloud.app.core.model.AppSettings
import com.ftcloud.app.core.model.DriveStats
import com.ftcloud.app.core.model.FormatUtils
import com.ftcloud.app.core.pairing.DevicePairingManager
import com.ftcloud.app.core.pairing.GeneratedPairingKit
import com.ftcloud.app.core.sync.ChannelScanScheduler
import com.ftcloud.app.core.update.ApkInstallCompatibility
import com.ftcloud.app.core.update.AppUpdateManager
import com.ftcloud.app.core.update.UpdateRelease
import com.ftcloud.app.core.update.UpdateVersion
import com.ftcloud.app.di.FTCloudContainer
import com.ftcloud.app.ui.components.CategoryIconBadge
import com.ftcloud.app.ui.components.ExpressiveBackTopBar
import com.ftcloud.app.ui.components.ExpressiveHeroHeader
import com.ftcloud.app.ui.components.ExpressiveStat
import com.ftcloud.app.ui.components.MorphingShapeBox
import com.ftcloud.app.ui.components.OrionEmptyState
import com.ftcloud.app.ui.components.OrionSectionCard
import com.ftcloud.app.ui.components.expressivePastel
import com.ftcloud.app.ui.components.readableContentColor
import com.ftcloud.app.ui.components.springPress
import com.ftcloud.app.ui.theme.LocalAnimationsEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val release: UpdateRelease) : UpdateStatus
    data class Downloading(val release: UpdateRelease, val progress: Int?) : UpdateStatus
    data class Verifying(val release: UpdateRelease) : UpdateStatus
    data class NeedsInstallPermission(val release: UpdateRelease) : UpdateStatus
    data class SignatureConflict(
        val release: UpdateRelease,
        val targetPackage: String,
        val runningDebugSideBySide: Boolean,
        val savedDownloadsPath: String?,
    ) : UpdateStatus
    data class Failed(val errorText: String) : UpdateStatus
}

private const val AUTO_UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: FTCloudContainer,
    settings: AppSettings,
    onBack: () -> Unit = {},
    onOpenStarred: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val animationsEnabled = LocalAnimationsEnabled.current

    val botStatuses by container.swarmManager.runtimeStatuses.collectAsState()
    val stats by container.driveRepository.observeStats().collectAsState(initial = DriveStats())
    val starredFolders by container.driveRepository.observeStarredFolders().collectAsState(initial = emptyList())
    val starredFiles by container.driveRepository.observeStarredFiles().collectAsState(initial = emptyList())
    val trashedFolders by container.driveRepository.observeTrashedFolders().collectAsState(initial = emptyList())
    val trashedFiles by container.driveRepository.observeTrashedFiles().collectAsState(initial = emptyList())
    val githubUpdateToken by container.credentialStore.githubUpdateTokenFlow.collectAsState()

    var currentPage by rememberSaveable { mutableStateOf(SettingsPage.HUB) }
    var cacheUsedBytes by remember { mutableLongStateOf(container.driveRepository.getCurrentCacheBytes()) }
    var isImportingJson by remember { mutableStateOf(false) }
    var isSyncingUpdates by remember { mutableStateOf(false) }
    var isSyncingDevices by remember { mutableStateOf(false) }
    var showAddBotDialog by remember { mutableStateOf(false) }
    var newBotToken by rememberSaveable { mutableStateOf("") }
    var showEncryptionDialog by remember { mutableStateOf(false) }
    var vaultPassphraseInput by rememberSaveable { mutableStateOf("") }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePassphraseInput by rememberSaveable { mutableStateOf("") }
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    var activePairingKit by remember { mutableStateOf<GeneratedPairingKit?>(null) }
    var activePairingQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingPairing by remember { mutableStateOf(false) }

    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }
    var downloadedUpdateApk by remember { mutableStateOf<File?>(null) }
    var showUpdateAvailableDialog by remember { mutableStateOf(false) }
    var autoUpdateCheckStarted by rememberSaveable { mutableStateOf(false) }
    var showGithubTokenDialog by remember { mutableStateOf(false) }
    var githubTokenDraft by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = currentPage != SettingsPage.HUB) {
        currentPage = SettingsPage.HUB
    }

    fun runUpdateCheck(automatic: Boolean) {
        if (updateStatus is UpdateStatus.Checking ||
            updateStatus is UpdateStatus.Downloading ||
            updateStatus is UpdateStatus.Verifying
        ) return

        scope.launch {
            container.settingsRepository.markUpdateChecked()
            updateStatus = UpdateStatus.Checking
            val ghToken = container.credentialStore.getGithubUpdateToken()
            runCatching { AppUpdateManager.fetchLatestRelease(githubToken = ghToken) }
                .onSuccess { release ->
                    if (UpdateVersion.isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
                        downloadedUpdateApk = null
                        updateStatus = UpdateStatus.Available(release)
                        if (automatic) showUpdateAvailableDialog = true
                    } else {
                        updateStatus = UpdateStatus.UpToDate
                    }
                }
                .onFailure { err ->
                    updateStatus = if (automatic) {
                        UpdateStatus.Idle
                    } else {
                        UpdateStatus.Failed(err.message ?: "Could not check GitHub Releases right now.")
                    }
                }
        }
    }

    fun installDownloadedUpdate(release: UpdateRelease) {
        val apk = downloadedUpdateApk ?: return
        when (val compat = AppUpdateManager.inspectInstallCompatibility(context, apk)) {
            is ApkInstallCompatibility.SignatureConflict -> {
                scope.launch {
                    runCatching { container.driveRepository.syncManifestNow() }
                    val savedPath = runCatching {
                        AppUpdateManager.saveApkToDownloads(context, apk, release.apkName)
                    }.getOrNull()
                    updateStatus = UpdateStatus.SignatureConflict(
                        release = release,
                        targetPackage = compat.targetPackage,
                        runningDebugSideBySide = compat.runningDifferentPackage,
                        savedDownloadsPath = savedPath,
                    )
                }
                return
            }
            is ApkInstallCompatibility.DowngradeConflict -> {
                updateStatus = UpdateStatus.Failed(
                    "Installed version code (${compat.installedVersionCode}) is newer than ${release.tagName} (${compat.archiveVersionCode})."
                )
                return
            }
            ApkInstallCompatibility.Compatible -> Unit
        }
        if (!AppUpdateManager.canInstallPackages(context)) {
            updateStatus = UpdateStatus.NeedsInstallPermission(release)
            return
        }
        runCatching { AppUpdateManager.openInstaller(context, apk) }
            .onSuccess { updateStatus = UpdateStatus.Available(release) }
            .onFailure { err ->
                updateStatus = UpdateStatus.Failed(err.message ?: "Unable to launch Android package installer.")
            }
    }

    fun downloadAndInstallUpdate(release: UpdateRelease) {
        if (downloadedUpdateApk != null) {
            installDownloadedUpdate(release)
            return
        }
        scope.launch {
            try {
                val ghToken = container.credentialStore.getGithubUpdateToken()
                updateStatus = UpdateStatus.Downloading(release, null)
                val apk = AppUpdateManager.downloadReleaseApk(context, release, githubToken = ghToken) { progress ->
                    updateStatus = UpdateStatus.Downloading(release, progress)
                }
                updateStatus = UpdateStatus.Verifying(release)
                val verified = AppUpdateManager.verifyChecksum(release, apk, githubToken = ghToken)
                if (release.checksumDownloadUrl != null && verified != true) {
                    apk.delete()
                    updateStatus = UpdateStatus.Failed("SHA-256 checksum verification failed for downloaded APK.")
                    return@launch
                }
                downloadedUpdateApk = apk
                if (AppUpdateManager.canInstallPackages(context)) {
                    installDownloadedUpdate(release)
                } else {
                    updateStatus = UpdateStatus.NeedsInstallPermission(release)
                }
            } catch (err: Exception) {
                updateStatus = UpdateStatus.Failed(err.message ?: "APK download failed.")
            }
        }
    }

    LaunchedEffect(settings.lastUpdateCheckAt) {
        if (!autoUpdateCheckStarted &&
            updateStatus == UpdateStatus.Idle &&
            System.currentTimeMillis() - settings.lastUpdateCheckAt >= AUTO_UPDATE_CHECK_INTERVAL_MS
        ) {
            autoUpdateCheckStarted = true
            runUpdateCheck(automatic = true)
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { pickedUri ->
        if (pickedUri != null) {
            isImportingJson = true
            scope.launch {
                runCatching {
                    val stream = context.contentResolver.openInputStream(pickedUri)
                        ?: error("Unable to open selected JSON file")
                    stream.use { container.channelMediaImporter.importTelegramExportJson(it) }
                }.onSuccess { res ->
                    snackbarHostState.showSnackbar(
                        "Imported ${res.importedFiles} media items (${res.skippedExisting} already indexed)"
                    )
                }.onFailure { err ->
                    snackbarHostState.showSnackbar(err.message ?: "Failed to import Telegram export JSON")
                }
                isImportingJson = false
            }
        }
    }

    val starredCount = starredFolders.size + starredFiles.size
    val trashedCount = trashedFolders.size + trashedFiles.size
    val storageSummary = buildString {
        append(FormatUtils.formatBytes(stats.totalBytes))
        append(" cloud · ")
        append(if (updateStatus is UpdateStatus.Available) "Update ready" else "App updates")
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentPage,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            transitionSpec = {
                val duration = if (animationsEnabled) 260 else 0
                val enteringSubpage = targetState != SettingsPage.HUB
                val dir = if (enteringSubpage) 1 else -1
                (fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { w -> dir * w / 10 })
                    .togetherWith(
                        fadeOut(tween(duration)) + slideOutHorizontally(tween(duration)) { w -> -dir * w / 10 }
                    )
            },
            label = "settingsPage",
        ) { page ->
            when (page) {
                SettingsPage.HUB -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        item {
                            SettingsHubHeader(
                                onBack = onBack,
                                channelTitle = settings.channelTitle,
                                botCount = botStatuses.size.coerceAtLeast(settings.botCount),
                                fileCount = stats.totalFiles,
                                starredCount = starredCount,
                                trashedCount = trashedCount,
                                encryptionEnabled = settings.encryptionEnabled,
                                biometricChecked = settings.biometricLockEnabled,
                                onBiometricChange = {
                                    scope.launch { container.settingsRepository.setBiometricLockEnabled(it) }
                                },
                                dynamicChecked = settings.dynamicColor,
                                dynamicEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                onDynamicChange = {
                                    scope.launch { container.settingsRepository.setDynamicColor(it) }
                                },
                                hapticsChecked = settings.hapticsEnabled,
                                onHapticsChange = {
                                    scope.launch { container.settingsRepository.setHapticsEnabled(it) }
                                },
                                animationsChecked = !settings.reduceAnimations,
                                onAnimationsChange = { enabled ->
                                    scope.launch { container.settingsRepository.setReduceAnimations(!enabled) }
                                },
                                compactChecked = settings.compactMode,
                                onCompactChange = {
                                    scope.launch { container.settingsRepository.setCompactMode(it) }
                                },
                                glassChecked = settings.glassBottomBar,
                                onGlassChange = {
                                    scope.launch { container.settingsRepository.setGlassBottomBar(it) }
                                },
                                themeMode = settings.themeMode,
                                accentColor = settings.accentColor,
                                storageSummary = storageSummary,
                                onJump = { currentPage = it },
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }

                        if (updateStatus is UpdateStatus.Available || updateStatus is UpdateStatus.Downloading) {
                            item {
                                AppUpdateCard(
                                    status = updateStatus,
                                    hasDownloadedApk = downloadedUpdateApk != null,
                                    hasPrivateRepoToken = githubUpdateToken.isNotBlank(),
                                    onCheck = { runUpdateCheck(automatic = false) },
                                    onDownload = { downloadAndInstallUpdate(it) },
                                    onAllowInstalls = {
                                        runCatching { AppUpdateManager.openUnknownSourcesSettings(context) }
                                    },
                                    onConfigurePrivateRepoToken = {
                                        githubTokenDraft = githubUpdateToken
                                        showGithubTokenDialog = true
                                    },
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        }

                        item {
                            CreatorSpace(
                                onAcknowledgements = { currentPage = SettingsPage.ACKNOWLEDGEMENTS },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }

                SettingsPage.APPEARANCE -> {
                    SettingsSubpageColumn(
                        title = "Appearance",
                        subtitle = "Theme mode, OLED true black & curated brand palettes",
                        heroIcon = Icons.Filled.Palette,
                        onBack = { currentPage = SettingsPage.HUB },
                    ) {
                        OrionSectionCard(title = "Theme", icon = Icons.Filled.Palette) {
                            AppearancePreviewStrip()
                            Spacer(Modifier.height(14.dp))
                            ThemeModePreviews(
                                selected = settings.themeMode,
                                accentColor = settings.accentColor,
                                dynamicColorEnabled = settings.dynamicColor,
                                onSelect = { mode ->
                                    scope.launch { container.settingsRepository.setThemeMode(mode) }
                                },
                            )
                        }

                        OrionSectionCard(title = "Colour palette", icon = Icons.Filled.Palette) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                FeelToggleRow(
                                    icon = Icons.Filled.Palette,
                                    title = "Wallpaper colours",
                                    subtitle = "Derive accents dynamically from your Android 12+ system wallpaper",
                                    checked = settings.dynamicColor,
                                    onCheckedChange = {
                                        scope.launch { container.settingsRepository.setDynamicColor(it) }
                                    },
                                )
                            }
                            Text(
                                text = "Brand palettes",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(10.dp))
                            PaletteSwatches(
                                selected = settings.accentColor,
                                dynamicColorActive = settings.dynamicColor,
                                onSelect = { accent ->
                                    scope.launch { container.settingsRepository.selectAccentColor(accent) }
                                },
                            )
                        }
                    }
                }

                SettingsPage.MOTION -> {
                    SettingsSubpageColumn(
                        title = "Motion & Feel",
                        subtitle = "Tactile feedback, spring transitions, glass dock & density",
                        heroIcon = Icons.Filled.TouchApp,
                        onBack = { currentPage = SettingsPage.HUB },
                    ) {
                        OrionSectionCard(title = "Interaction & layout", icon = Icons.Filled.TouchApp) {
                            FeelToggleRow(
                                icon = Icons.Filled.Vibration,
                                title = "Haptic feedback",
                                subtitle = "Subtle tactile confirmation when tapping pills, cards, and actions",
                                checked = settings.hapticsEnabled,
                                onCheckedChange = {
                                    scope.launch { container.settingsRepository.setHapticsEnabled(it) }
                                },
                            )
                            FeelToggleRow(
                                icon = Icons.Filled.Animation,
                                title = "Expressive animations",
                                subtitle = "Spring-physics morphing blobs, colour transitions & directional slides",
                                checked = !settings.reduceAnimations,
                                onCheckedChange = { enabled ->
                                    scope.launch { container.settingsRepository.setReduceAnimations(!enabled) }
                                },
                            )
                            FeelToggleRow(
                                icon = Icons.Filled.BlurOn,
                                title = "Floating glass dock",
                                subtitle = "Translucent pill navigation bar hovering above scrolling content",
                                checked = settings.glassBottomBar,
                                onCheckedChange = {
                                    scope.launch { container.settingsRepository.setGlassBottomBar(it) }
                                },
                            )
                            FeelToggleRow(
                                icon = Icons.Filled.Compress,
                                title = "Compact mode",
                                subtitle = "Tighter spacing and 92% density scale to fit more files on screen",
                                checked = settings.compactMode,
                                onCheckedChange = {
                                    scope.launch { container.settingsRepository.setCompactMode(it) }
                                },
                                showDivider = false,
                            )
                        }
                    }
                }

                SettingsPage.SWARM -> {
                    SettingsSubpageColumn(
                        title = "Bot Swarm & Sync",
                        subtitle = "${botStatuses.size.coerceAtLeast(settings.botCount)} parallel bots · Multi-phone QR/PIN & channel scanner",
                        heroIcon = Icons.Filled.CloudSync,
                        onBack = { currentPage = SettingsPage.HUB },
                    ) {
                        OrionSectionCard(
                            title = "Multi-Device & Second-Phone Login",
                            icon = Icons.Outlined.PhonelinkSetup,
                        ) {
                            Text(
                                text = "Use FT Cloud seamlessly across multiple phones. Scan a QR code or share an encrypted 6-digit PIN pairing key — your second phone restores your entire folder tree in ~1s without re-scanning.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                enabled = !isGeneratingPairing,
                                onClick = {
                                    isGeneratingPairing = true
                                    scope.launch {
                                        runCatching { container.driveRepository.syncManifestNow() }
                                        runCatching {
                                            val kit = container.driveRepository.generateDevicePairingKit()
                                            val qrBmp = withContext(Dispatchers.Default) {
                                                DevicePairingManager.renderQrBitmap(kit.qrPayload, sizePx = 640)
                                            }
                                            kit to qrBmp
                                        }.onSuccess { (kit, bmp) ->
                                            activePairingKit = kit
                                            activePairingQrBitmap = bmp
                                        }.onFailure { err ->
                                            snackbarHostState.showSnackbar(err.message ?: "Failed to generate pairing kit")
                                        }
                                        isGeneratingPairing = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.QrCode2, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isGeneratingPairing) {
                                        "Preparing Cloud Sync & QR…"
                                    } else {
                                        "Link a Second Phone (QR Code & PIN)"
                                    }
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Sync Across Phones", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Automatically merge new uploads, folders & edits made on other phones when FT Cloud opens",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = settings.multiDeviceAutoSync,
                                    onCheckedChange = {
                                        scope.launch { container.settingsRepository.setMultiDeviceAutoSync(it) }
                                    },
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                enabled = !isSyncingDevices,
                                onClick = {
                                    isSyncingDevices = true
                                    scope.launch {
                                        runCatching {
                                            val merge = container.driveRepository.syncMultiDeviceCloud(forcePull = false)
                                            val live = container.channelMediaImporter.syncPendingBotUpdates()
                                            container.driveRepository.syncManifestNow()
                                            merge to live
                                        }.onSuccess { (merge, live) ->
                                            val added = merge.addedFiles + live.importedFiles
                                            snackbarHostState.showSnackbar(
                                                if (added > 0) "Synced across devices · +$added new items"
                                                else "All phones are in sync (${stats.totalFiles} files)"
                                            )
                                        }.onFailure { err ->
                                            snackbarHostState.showSnackbar(err.message ?: "Sync failed")
                                        }
                                        isSyncingDevices = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.CloudSync, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (isSyncingDevices) "Syncing Devices…" else "Sync All Phones Now")
                            }
                        }

                        OrionSectionCard(title = "Telegram Bot Swarm", icon = Icons.Outlined.NetworkCheck) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Channel: ${settings.channelTitle}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "ID: ${settings.channelId}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        container.swarmManager.pingAllBots()
                                        snackbarHostState.showSnackbar("Checked health of ${botStatuses.size} swarm bot(s)")
                                    }
                                }) {
                                    Icon(Icons.Outlined.NetworkCheck, contentDescription = "Ping swarm")
                                }
                            }

                            botStatuses.forEach { bot ->
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                val isCoolingDown = bot.cooldownRemainingMs > 0L
                                val statusColor = when {
                                    !bot.isHealthy -> Color(0xFFD32F2F)
                                    isCoolingDown -> Color(0xFFF57C00)
                                    else -> Color(0xFF2E7D32)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(statusColor),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (bot.username.isNotBlank()) "@${bot.username}" else bot.displayName.ifBlank { "Bot ${bot.botId}" },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            if (bot.hasTurboDm) {
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "⚡ Turbo DM",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                            if (bot.activeRequests > 0) {
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "• ${bot.activeRequests} active",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                )
                                            }
                                        }
                                        val latencyTag = bot.lastLatencyMs?.let { " • ${it}ms" }.orEmpty()
                                        val opsTag = if (bot.completedOps > 0L) " • ${bot.completedOps} ops" else ""
                                        Text(
                                            text = "${bot.maskedToken}$latencyTag$opsTag • ↑${FormatUtils.formatBytes(bot.uploadedBytes)} ↓${FormatUtils.formatBytes(bot.downloadedBytes)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (isCoolingDown) {
                                            Text(
                                                text = "⏳ Telegram cooldown (${(bot.cooldownRemainingMs + 999L) / 1000L}s) — other bots continue in parallel",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFF57C00),
                                            )
                                        } else if (!bot.lastError.isNullOrBlank()) {
                                            Text(
                                                text = bot.lastError,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                    if (!bot.hasTurboDm && bot.username.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                runCatching {
                                                    val startUri = Uri.parse("https://t.me/${bot.username}?start=ftcloud")
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, startUri))
                                                }
                                            },
                                        ) {
                                            Text("⚡ Start")
                                        }
                                    }
                                    if (botStatuses.size > 1) {
                                        IconButton(onClick = {
                                            runCatching { container.swarmManager.removeBot(bot.botId) }
                                        }) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove bot")
                                        }
                                    }
                                }
                            }

                            if (botStatuses.any { !it.hasTurboDm }) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Tip: Make sure every bot is a Channel Administrator (Post + Delete + Pin), and tap ⚡ Start once in Telegram so all ${botStatuses.size} bots bypass Telegram's shared 20 msg/min channel quota.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { showAddBotDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add Bot(s) to Swarm")
                            }
                        }

                        OrionSectionCard(title = "Cloud Index Manifest", icon = Icons.Outlined.CloudSync) {
                            Text(
                                text = if (settings.lastManifestSyncAt != null) {
                                    "Last pinned snapshot: ${FormatUtils.formatTimestamp(settings.lastManifestSyncAt)}"
                                } else {
                                    "Pin an index snapshot in your Telegram channel so any phone restores your entire folder tree in ~1 second."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { container.driveRepository.syncManifestNow() }
                                                .onSuccess { snackbarHostState.showSnackbar("Manifest pinned in Telegram channel") }
                                                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Manifest sync failed") }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Outlined.CloudSync, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sync Now")
                                }
                                OutlinedButton(
                                    onClick = { showRestoreDialog = true },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Outlined.Restore, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Restore")
                                }
                            }
                        }

                        OrionSectionCard(
                            title = "Import Pre-Existing Channel Media",
                            icon = Icons.Outlined.CheckCircle,
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = if (stats.totalFiles > 0) {
                                            "One-time import only: ${stats.totalFiles} files are saved in your channel's Pinned Cloud Index. Reinstalls and second phones restore everything automatically without re-scanning!"
                                        } else {
                                            "One-time import only: Once imported, FT Cloud pins the index in your channel so reinstalls and second phones restore everything automatically."
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            FilledTonalButton(
                                enabled = !isImportingJson,
                                onClick = { exportJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (isImportingJson) "Importing result.json…"
                                    else "1. Instant Import Telegram Export (result.json)"
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Fastest for 7,000+ items (~2s, zero rate limits): Telegram Desktop → Export Chat History → JSON only → pick result.json.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            OutlinedButton(
                                enabled = !isSyncingUpdates,
                                onClick = {
                                    isSyncingUpdates = true
                                    scope.launch {
                                        runCatching { container.channelMediaImporter.syncPendingBotUpdates() }
                                            .onSuccess { res ->
                                                snackbarHostState.showSnackbar(
                                                    "Imported ${res.importedFiles} items • Turbo DM ready on ${res.detectedTurboDmBots} bot(s)"
                                                )
                                            }
                                            .onFailure { err ->
                                                snackbarHostState.showSnackbar(err.message ?: "Update sync failed")
                                            }
                                        isSyncingUpdates = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (isSyncingUpdates) "Syncing Bot Updates…"
                                    else "2. Sync Forwarded Batches & /start Turbo DM"
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Forward up to 100 channel posts at a time to any swarm bot, or send /start in Telegram (${settings.turboDmBotCount}/${settings.botCount} Turbo DM active).",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            Text(
                                text = "3. Background Bot Swarm History Scanner (No PC Needed)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (settings.channelScanMaxId > 0L) {
                                Spacer(Modifier.height(6.dp))
                                val progress = (settings.channelScanCheckedCount.toFloat() /
                                    settings.channelScanMaxId.toFloat()).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (!settings.channelScanStatus.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = settings.channelScanStatus,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (settings.channelScanRunning) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                container.settingsRepository.updateChannelScanState(
                                                    running = false,
                                                    status = "Pausing scanner…",
                                                )
                                                ChannelScanScheduler.cancel(context)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Pause Scan")
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                container.settingsRepository.updateChannelScanState(
                                                    running = true,
                                                    status = "Starting newest-first channel scan…",
                                                )
                                                ChannelScanScheduler.startOrResume(context)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(if (settings.channelScanCursorId > 0L) "Resume Scan" else "Scan Channel")
                                    }
                                }

                                if (settings.channelScanCursorId > 0L || settings.channelScanCheckedCount > 0) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                ChannelScanScheduler.cancel(context)
                                                container.settingsRepository.resetChannelScanProgress()
                                                snackbarHostState.showSnackbar("Reset channel scanner cursor")
                                            }
                                        },
                                    ) {
                                        Text("Reset")
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsPage.SECURITY -> {
                    SettingsSubpageColumn(
                        title = "Security & Privacy",
                        subtitle = "Client-side AES-256-GCM encryption, biometric lock & screen shield",
                        heroIcon = Icons.Filled.Security,
                        onBack = { currentPage = SettingsPage.HUB },
                    ) {
                        OrionSectionCard(title = "Vault protection", icon = Icons.Filled.Lock) {
                            FeelToggleRow(
                                icon = Icons.Filled.Lock,
                                title = "AES-256-GCM upload encryption",
                                subtitle = "Encrypt every 19 MiB file chunk client-side before uploading to Telegram",
                                checked = settings.encryptionEnabled,
                                onCheckedChange = { wantEnabled ->
                                    if (wantEnabled) {
                                        showEncryptionDialog = true
                                    } else {
                                        scope.launch { container.driveRepository.disableVaultEncryption() }
                                    }
                                },
                            )
                            FeelToggleRow(
                                icon = Icons.Filled.Fingerprint,
                                title = "Biometric / device lock",
                                subtitle = "Require fingerprint, face, or screen lock when opening FT Cloud",
                                checked = settings.biometricLockEnabled,
                                onCheckedChange = {
                                    scope.launch { container.settingsRepository.setBiometricLockEnabled(it) }
                                },
                            )
                            FeelToggleRow(
                                icon = Icons.Filled.Security,
                                title = "Block screenshots",
                                subtitle = "Apply FLAG_SECURE to hide previews from recents & screen recording",
                                checked = settings.blockScreenshots,
                                onCheckedChange = {
                                    scope.launch { container.settingsRepository.setBlockScreenshots(it) }
                                },
                                showDivider = false,
                            )
                        }

                        OrionSectionCard(title = "Channel session", icon = Icons.AutoMirrored.Outlined.Logout) {
                            Text(
                                text = "Disconnecting signs out this phone only. Your uploaded files and pinned Cloud Index stay untouched inside your private Telegram channel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { showDisconnectConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Disconnect Telegram Channel")
                            }
                        }
                    }
                }

                SettingsPage.COLLECTIONS -> {
                    SettingsSubpageColumn(
                        title = "Collections",
                        subtitle = "$starredCount starred items · $trashedCount items in trash",
                        heroIcon = Icons.Filled.Star,
                        onBack = { currentPage = SettingsPage.HUB },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(136.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SettingsBentoCard(
                                icon = Icons.Filled.Star,
                                title = "Starred",
                                subtitle = "$starredCount bookmarked files & folders",
                                seed = Color(0xFF4C4ECD),
                                onClick = onOpenStarred,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                            )
                            SettingsBentoCard(
                                icon = Icons.Outlined.DeleteOutline,
                                title = "Trash Bin",
                                subtitle = "$trashedCount recoverable items",
                                seed = Color(0xFFC65D00),
                                onClick = onOpenTrash,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                            )
                        }
                    }
                }

                SettingsPage.STORAGE -> {
                    SettingsSubpageColumn(
                        title = "Storage & Updates",
                        subtitle = "${FormatUtils.formatBytes(stats.totalBytes)} stored · v${BuildConfig.VERSION_NAME}",
                        heroIcon = Icons.Filled.Storage,
                        onBack = { currentPage = SettingsPage.HUB },
                    ) {
                        OrionSectionCard(title = "Cloud drive stats", icon = Icons.Filled.Storage) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(vertical = 14.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ExpressiveStat(
                                    value = FormatUtils.formatBytes(stats.totalBytes),
                                    label = "Cloud used",
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    Modifier
                                        .width(1.dp)
                                        .height(34.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                                ExpressiveStat(
                                    value = stats.totalFiles.toString(),
                                    label = "Files",
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    Modifier
                                        .width(1.dp)
                                        .height(34.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                                ExpressiveStat(
                                    value = stats.totalFolders.toString(),
                                    label = "Folders",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        AppUpdateCard(
                            status = updateStatus,
                            hasDownloadedApk = downloadedUpdateApk != null,
                            hasPrivateRepoToken = githubUpdateToken.isNotBlank(),
                            onCheck = { runUpdateCheck(automatic = false) },
                            onDownload = { downloadAndInstallUpdate(it) },
                            onAllowInstalls = {
                                runCatching { AppUpdateManager.openUnknownSourcesSettings(context) }
                            },
                            onConfigurePrivateRepoToken = {
                                githubTokenDraft = githubUpdateToken
                                showGithubTokenDialog = true
                            },
                        )

                        OrionSectionCard(title = "Streaming chunk cache", icon = Icons.Filled.Storage) {
                            Text(
                                text = "Local cache used: ${FormatUtils.formatBytes(cacheUsedBytes)} / ${settings.streamCacheLimitMb} MB",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(256, 512, 1024, 2048, 4096).forEach { limitMb ->
                                    FilterChip(
                                        selected = settings.streamCacheLimitMb == limitMb,
                                        onClick = {
                                            scope.launch { container.settingsRepository.setStreamCacheLimitMb(limitMb) }
                                        },
                                        label = {
                                            Text(if (limitMb >= 1024) "${limitMb / 1024} GB" else "$limitMb MB")
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        container.driveRepository.clearLocalCache()
                                        cacheUsedBytes = container.driveRepository.getCurrentCacheBytes()
                                        snackbarHostState.showSnackbar("Cleared local streaming cache")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Clear Local Stream Cache")
                            }
                        }
                    }
                }

                SettingsPage.ACKNOWLEDGEMENTS -> {
                    SettingsSubpageColumn(
                        title = "Acknowledgements",
                        subtitle = "Design inspiration, open-source libraries & licences",
                        heroIcon = Icons.Filled.WorkspacePremium,
                        onBack = { currentPage = SettingsPage.HUB },
                    ) {
                        AcknowledgementsContent()
                    }
                }
            }
        }
    }

    val pairingKit = activePairingKit
    val pairingBitmap = activePairingQrBitmap
    if (pairingKit != null) {
        AlertDialog(
            onDismissRequest = {
                activePairingKit = null
                activePairingQrBitmap = null
            },
            icon = { Icon(Icons.Outlined.PhonelinkSetup, contentDescription = null) },
            title = { Text("Link a Second Phone") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "On your second phone, open FT Cloud and tap 'Scan QR' — or share the encrypted Pairing Key below and unlock it with this 6-digit PIN:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    if (pairingBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = pairingBitmap.asImageBitmap(),
                                contentDescription = "FT Cloud Second Phone Login QR Code",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "6-DIGIT PAIRING PIN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "${pairingKit.pin.take(3)} • ${pairingKit.pin.takeLast(3)}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(pairingKit.encryptedPairingCode))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Copied encrypted Pairing Key (safe to send via Saved Messages)")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Key")
                        }
                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "FT Cloud Encrypted Pairing Key (unlock with 6-digit PIN shown on Phone 1):\n\n${pairingKit.encryptedPairingCode}"
                                    )
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Encrypted Pairing Key"))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share Key")
                        }
                    }

                    Text(
                        text = "Security: Your Bot Tokens are AES-256-GCM encrypted using the 6-digit PIN above — never sent in plaintext.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    activePairingKit = null
                    activePairingQrBitmap = null
                }) {
                    Text("Done")
                }
            },
        )
    }

    if (showGithubTokenDialog) {
        AlertDialog(
            onDismissRequest = { showGithubTokenDialog = false },
            title = { Text("GitHub Access Token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste an optional read-only GitHub Fine-Grained Token (Contents = Read-only) if authenticated release access is required:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = githubTokenDraft,
                        onValueChange = { githubTokenDraft = it },
                        label = { Text("GitHub Token (github_pat_...)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    container.credentialStore.saveGithubUpdateToken(githubTokenDraft)
                    showGithubTokenDialog = false
                    updateStatus = UpdateStatus.Idle
                    runUpdateCheck(automatic = false)
                }) {
                    Text("Save & Check")
                }
            },
            dismissButton = {
                Row {
                    if (githubUpdateToken.isNotBlank()) {
                        TextButton(onClick = {
                            container.credentialStore.saveGithubUpdateToken("")
                            githubTokenDraft = ""
                            showGithubTokenDialog = false
                        }) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showGithubTokenDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
        )
    }

    val dialogAvailableRelease = (updateStatus as? UpdateStatus.Available)?.release
    if (showUpdateAvailableDialog && dialogAvailableRelease != null) {
        AlertDialog(
            onDismissRequest = { showUpdateAvailableDialog = false },
            icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
            title = { Text("Update Available: ${dialogAvailableRelease.tagName}") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UpdateReleaseDetails(dialogAvailableRelease)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showUpdateAvailableDialog = false
                    downloadAndInstallUpdate(dialogAvailableRelease)
                }) {
                    Text("Download & Install")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateAvailableDialog = false }) {
                    Text("Later")
                }
            },
        )
    }

    if (showAddBotDialog) {
        AlertDialog(
            onDismissRequest = { showAddBotDialog = false },
            title = { Text("Add Swarm Bot(s)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Add each bot as an Administrator in your Telegram channel first (Post + Delete + Pin). You can paste one or multiple tokens separated by newlines:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = newBotToken,
                        onValueChange = { newBotToken = it },
                        label = { Text("Bot Token(s) from @BotFather") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newBotToken.isNotBlank(),
                    onClick = {
                        val rawTokens = newBotToken
                        newBotToken = ""
                        showAddBotDialog = false
                        scope.launch {
                            runCatching {
                                val added = container.swarmManager.addAndVerifyBots(rawTokens)
                                runCatching { container.channelMediaImporter.syncPendingBotUpdates() }
                                added
                            }.onSuccess { added ->
                                val names = added.joinToString(", ") { "@${it.username.ifBlank { it.botId }}" }
                                snackbarHostState.showSnackbar("Added ${added.size} swarm ${if (added.size == 1) "bot" else "bots"}: $names")
                            }.onFailure { snackbarHostState.showSnackbar(it.message ?: "Failed to add bot") }
                        }
                    }
                ) {
                    Text("Verify & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBotDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEncryptionDialog) {
        AlertDialog(
            onDismissRequest = { showEncryptionDialog = false },
            title = { Text("Set Vault Passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Derives an AES-256-GCM key via PBKDF2-HMAC-SHA256 (210,000 iterations). Keep this passphrase safe to restore encrypted files on new devices.")
                    OutlinedTextField(
                        value = vaultPassphraseInput,
                        onValueChange = { vaultPassphraseInput = it },
                        label = { Text("Passphrase (min 6 characters)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = vaultPassphraseInput.length >= 6,
                    onClick = {
                        val pass = vaultPassphraseInput
                        vaultPassphraseInput = ""
                        showEncryptionDialog = false
                        scope.launch {
                            runCatching { container.driveRepository.configureVaultPassphrase(pass) }
                                .onSuccess { snackbarHostState.showSnackbar("AES-256-GCM upload encryption enabled") }
                                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Failed to enable encryption") }
                        }
                    }
                ) {
                    Text("Enable Encryption")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptionDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Pinned Cloud Index") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Downloads the pinned manifest from your Telegram channel and merges folders and files into this device.")
                    OutlinedTextField(
                        value = restorePassphraseInput,
                        onValueChange = { restorePassphraseInput = it },
                        label = { Text("Passphrase (leave blank if unencrypted)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pass = restorePassphraseInput
                    restorePassphraseInput = ""
                    showRestoreDialog = false
                    scope.launch {
                        runCatching { container.driveRepository.restoreManifestFromChannel(pass) }
                            .onSuccess { summary ->
                                if (summary == null) {
                                    snackbarHostState.showSnackbar("No pinned FT-Cloud manifest found in channel")
                                } else {
                                    snackbarHostState.showSnackbar("Restored ${summary.restoredFiles} files & ${summary.restoredFolders} folders")
                                }
                            }
                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "Restore failed") }
                    }
                }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect Channel?") },
            text = {
                Text("Signing out removes bot tokens from this phone. Your uploaded files and pinned manifest remain safely inside your Telegram channel.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirm = false
                    scope.launch { container.settingsRepository.disconnectAccount() }
                }) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSubpageColumn(
    title: String,
    subtitle: String,
    heroIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ExpressiveHeroHeader(
            title = title,
            subtitle = subtitle,
            icon = heroIcon,
            onBack = onBack,
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
            Spacer(Modifier.height(96.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredScreen(
    container: FTCloudContainer,
    onOpenPreview: (String) -> Unit,
    onBack: () -> Unit,
) {
    val repo = container.driveRepository
    val scope = rememberCoroutineScope()
    val folders by repo.observeStarredFolders().collectAsState(initial = emptyList())
    val files by repo.observeStarredFiles().collectAsState(initial = emptyList())

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ExpressiveBackTopBar(
                title = "Starred",
                subtitle = "${folders.size} folders · ${files.size} files",
                icon = Icons.Filled.Star,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        if (folders.isEmpty() && files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                OrionEmptyState(
                    icon = Icons.Outlined.StarBorder,
                    title = "No starred items yet",
                    subtitle = "Bookmark important folders or files in Drive to reach them quickly from here.",
                )
            }
        } else {
            val folderPastel = expressivePastel(Color(0xFF4C4ECD))
            val folderInk = readableContentColor(folderPastel)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(folders, key = { "sfolder_${it.id}" }) { folder ->
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MorphingShapeBox(
                                modifier = Modifier.size(46.dp),
                                color = folderPastel,
                                lobes = 9,
                                wave = 0.13f,
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, tint = folderInk)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { scope.launch { repo.toggleFolderStar(folder.id, false) } }) {
                                Text("Unstar")
                            }
                        }
                    }
                }
                items(files, key = { "sfile_${it.id}" }) { file ->
                    val shape = RoundedCornerShape(24.dp)
                    Card(
                        shape = shape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        modifier = Modifier.springPress(shape) { onOpenPreview(file.id) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIconBadge(category = file.category)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = FormatUtils.formatBytes(file.sizeBytes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { scope.launch { repo.toggleFileStar(file.id, false) } }) {
                                Text("Unstar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    container: FTCloudContainer,
    onBack: () -> Unit,
) {
    val repo = container.driveRepository
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val folders by repo.observeTrashedFolders().collectAsState(initial = emptyList())
    val files by repo.observeTrashedFiles().collectAsState(initial = emptyList())
    var confirmEmptyTrash by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ExpressiveBackTopBar(
                title = "Trash Bin",
                subtitle = "${folders.size} folders · ${files.size} files",
                icon = Icons.Outlined.DeleteOutline,
                onBack = onBack,
                actions = {
                    if (folders.isNotEmpty() || files.isNotEmpty()) {
                        FilledTonalButton(onClick = { confirmEmptyTrash = true }) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Empty")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (folders.isEmpty() && files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                OrionEmptyState(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Trash is empty",
                    subtitle = "Items moved to Trash stay safe here until you permanently delete them from your Telegram channel.",
                )
            }
        } else {
            val trashPastel = expressivePastel(Color(0xFFC65D00))
            val trashInk = readableContentColor(trashPastel)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(folders, key = { "tfolder_${it.id}" }) { folder ->
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MorphingShapeBox(
                                modifier = Modifier.size(46.dp),
                                color = trashPastel,
                                lobes = 9,
                                wave = 0.13f,
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, tint = trashInk)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = {
                                scope.launch { repo.setTrashedForItems(emptyList(), listOf(folder.id), trashed = false) }
                            }) {
                                Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Restore")
                            }
                            IconButton(onClick = {
                                scope.launch { repo.permanentlyDeleteFolder(folder.id) }
                            }) {
                                Icon(Icons.Outlined.DeleteForever, contentDescription = "Delete permanently")
                            }
                        }
                    }
                }
                items(files, key = { "tfile_${it.id}" }) { file ->
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIconBadge(category = file.category)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = FormatUtils.formatBytes(file.sizeBytes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                scope.launch { repo.setTrashedForItems(listOf(file.id), emptyList(), trashed = false) }
                            }) {
                                Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Restore")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    repo.permanentlyDeleteFile(file.id)
                                    snackbarHostState.showSnackbar("Deleted permanently from Telegram")
                                }
                            }) {
                                Icon(Icons.Outlined.DeleteForever, contentDescription = "Delete permanently")
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmptyTrash) {
        AlertDialog(
            onDismissRequest = { confirmEmptyTrash = false },
            title = { Text("Empty Trash Permanently?") },
            text = { Text("This deletes all trashed file chunks directly from your Telegram channel and cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmptyTrash = false
                    scope.launch {
                        repo.emptyTrash()
                        snackbarHostState.showSnackbar("Emptied Trash & purged cloud chunks")
                    }
                }) {
                    Text("Delete Forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmptyTrash = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AppUpdateCard(
    status: UpdateStatus,
    hasDownloadedApk: Boolean,
    hasPrivateRepoToken: Boolean,
    onCheck: () -> Unit,
    onDownload: (UpdateRelease) -> Unit,
    onAllowInstalls: () -> Unit,
    onConfigurePrivateRepoToken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    OrionSectionCard(
        title = "App updates",
        icon = Icons.Filled.SystemUpdate,
        modifier = modifier,
    ) {
        Text(
            text = "Installed: v${BuildConfig.VERSION_NAME} · GitHub Releases",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        when (status) {
            UpdateStatus.Idle -> {
                Text(
                    text = "Check GitHub Releases for new FT Cloud builds with SHA-256 checksum verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onCheck,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Check for Updates")
                }
            }

            UpdateStatus.Checking -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Checking GitHub Releases…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            UpdateStatus.UpToDate -> {
                Text(
                    text = "You're running the latest FT Cloud release (v${BuildConfig.VERSION_NAME}).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCheck,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Check Again")
                }
            }

            is UpdateStatus.Available -> {
                UpdateReleaseDetails(status.release)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onDownload(status.release) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (hasDownloadedApk) {
                            "Install ${status.release.tagName}"
                        } else {
                            "Download & Install ${status.release.tagName}"
                        }
                    )
                }
            }

            is UpdateStatus.Downloading -> {
                Text(
                    text = if (status.progress != null) {
                        "Downloading ${status.release.apkName}… ${status.progress}%"
                    } else {
                        "Downloading ${status.release.apkName}…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                if (status.progress != null) {
                    LinearProgressIndicator(
                        progress = { (status.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            is UpdateStatus.Verifying -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Verifying SHA-256 signature checksum…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is UpdateStatus.NeedsInstallPermission -> {
                Text(
                    text = "${status.release.tagName} is downloaded and SHA-256 verified. Allow 'Install unknown apps' for FT Cloud, then tap Install.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = onAllowInstalls,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Allow Installs")
                    }
                    Button(
                        onClick = { onDownload(status.release) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Install Now")
                    }
                }
            }

            is UpdateStatus.SignatureConflict -> {
                Text(
                    text = if (status.runningDebugSideBySide) {
                        "An older FT Cloud build (${status.targetPackage}) with a different signing key is installed alongside this Debug app. Tap 'Remove Old Package' once, then tap 'Install Now' — all future updates will install in-place!"
                    } else {
                        val dlNote = status.savedDownloadsPath?.let { " Saved verified APK to $it." }.orEmpty()
                        "Your current install was signed with an earlier key, so Android requires a one-time clean install before seamless in-place updates work.$dlNote Your Cloud Index is already synced in Telegram!"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { AppUpdateManager.requestUninstallPackage(context, status.targetPackage) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Remove Old Package")
                    }
                    if (status.runningDebugSideBySide) {
                        Button(
                            onClick = { onDownload(status.release) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Install Now")
                        }
                    }
                }
            }

            is UpdateStatus.Failed -> {
                Text(
                    text = status.errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                val isAuthOrNotFound = status.errorText.contains("401") ||
                    status.errorText.contains("403") ||
                    status.errorText.contains("404")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCheck,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Retry Check")
                    }
                    if (isAuthOrNotFound || hasPrivateRepoToken) {
                        FilledTonalButton(
                            onClick = onConfigurePrivateRepoToken,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (hasPrivateRepoToken) "Edit Token" else "GitHub Token")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateReleaseDetails(release: UpdateRelease) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "${release.name} (${FormatUtils.formatBytes(release.apkSizeBytes)})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        if (release.checksumDownloadUrl != null) {
            Text(
                text = "SHA-256 checksum sidecar attached",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (release.notes.isNotBlank()) {
            Text(
                text = release.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
