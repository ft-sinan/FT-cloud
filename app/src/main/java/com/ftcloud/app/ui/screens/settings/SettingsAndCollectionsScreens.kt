package com.ftcloud.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ftcloud.app.core.model.AppSettings
import com.ftcloud.app.core.model.AppThemeMode
import com.ftcloud.app.core.model.FormatUtils
import com.ftcloud.app.di.FTCloudContainer
import com.ftcloud.app.ui.components.CategoryIconBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: FTCloudContainer,
    settings: AppSettings,
    onOpenStarred: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val botStatuses by container.swarmManager.runtimeStatuses.collectAsState()

    var cacheUsedBytes by remember { mutableLongStateOf(container.driveRepository.getCurrentCacheBytes()) }
    var showAddBotDialog by remember { mutableStateOf(false) }
    var newBotToken by rememberSaveable { mutableStateOf("") }
    var showEncryptionDialog by remember { mutableStateOf(false) }
    var vaultPassphraseInput by rememberSaveable { mutableStateOf("") }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePassphraseInput by rememberSaveable { mutableStateOf("") }
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("Settings & Swarm") })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Telegram Bot Swarm", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Channel: ${settings.channelTitle} (${settings.channelId})",
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
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (bot.isHealthy) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (bot.username.isNotBlank()) "@${bot.username}" else bot.displayName.ifBlank { "Bot ${bot.botId}" },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    val latencyTag = bot.lastLatencyMs?.let { " • ${it}ms" }.orEmpty()
                                    Text(
                                        text = "${bot.maskedToken}$latencyTag • ↑${FormatUtils.formatBytes(bot.uploadedBytes)} ↓${FormatUtils.formatBytes(bot.downloadedBytes)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (!bot.lastError.isNullOrBlank()) {
                                        Text(
                                            text = bot.lastError,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
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

                        OutlinedButton(
                            onClick = { showAddBotDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add Bot to Swarm")
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Cloud Index Manifest", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (settings.lastManifestSyncAt != null) {
                                "Last pinned snapshot: ${FormatUtils.formatTimestamp(settings.lastManifestSyncAt)}"
                            } else {
                                "Pin an index snapshot in your Telegram channel so any device can restore your entire folder tree."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onOpenStarred, modifier = Modifier.weight(1f)) {
                                Text("Starred Items")
                            }
                            OutlinedButton(onClick = onOpenTrash, modifier = Modifier.weight(1f)) {
                                Text("Trash Bin")
                            }
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Security & Privacy", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AES-256-GCM Upload Encryption", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Encrypt every 19 MiB file chunk client-side before sending to Telegram",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.encryptionEnabled,
                                onCheckedChange = { wantEnabled ->
                                    if (wantEnabled) {
                                        showEncryptionDialog = true
                                    } else {
                                        scope.launch { container.driveRepository.disableVaultEncryption() }
                                    }
                                }
                            )
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Biometric / Device Lock", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Require fingerprint, face, or screen lock when opening FT-Cloud",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.biometricLockEnabled,
                                onCheckedChange = {
                                    scope.launch { container.settingsRepository.setBiometricLockEnabled(it) }
                                }
                            )
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Block Screenshots", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Apply FLAG_SECURE to hide previews from recents & screen capture",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.blockScreenshots,
                                onCheckedChange = {
                                    scope.launch { container.settingsRepository.setBlockScreenshots(it) }
                                }
                            )
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Streaming Cache & Appearance", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Local stream chunk cache: ${FormatUtils.formatBytes(cacheUsedBytes)} / ${settings.streamCacheLimitMb} MB",
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    container.driveRepository.clearLocalCache()
                                    cacheUsedBytes = container.driveRepository.getCurrentCacheBytes()
                                    snackbarHostState.showSnackbar("Cleared local streaming cache")
                                }
                            }
                        ) {
                            Text("Clear Local Stream Cache")
                        }

                        HorizontalDivider()
                        Text("Theme", style = MaterialTheme.typography.bodyLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = {
                                        scope.launch { container.settingsRepository.setThemeMode(mode) }
                                    },
                                    label = { Text(mode.label) },
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Material You Dynamic Wallpaper Colors",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = settings.dynamicColor,
                                onCheckedChange = {
                                    scope.launch { container.settingsRepository.setDynamicColor(it) }
                                }
                            )
                        }
                    }
                }
            }

            item {
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

    if (showAddBotDialog) {
        AlertDialog(
            onDismissRequest = { showAddBotDialog = false },
            title = { Text("Add Swarm Bot") },
            text = {
                OutlinedTextField(
                    value = newBotToken,
                    onValueChange = { newBotToken = it },
                    label = { Text("Bot Token from @BotFather") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newBotToken.isNotBlank(),
                    onClick = {
                        val token = newBotToken
                        newBotToken = ""
                        showAddBotDialog = false
                        scope.launch {
                            runCatching { container.swarmManager.addAndVerifyBot(token) }
                                .onSuccess { snackbarHostState.showSnackbar("Added @${it.username} to Bot Swarm") }
                                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Failed to add bot") }
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
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Starred") },
            )
        }
    ) { innerPadding ->
        if (folders.isEmpty() && files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.StarBorder, contentDescription = null, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No starred items yet", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(folders, key = { "sfolder_${it.id}" }) { folder ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(folder.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { repo.toggleFolderStar(folder.id, false) } }) {
                                Text("Unstar")
                            }
                        }
                    }
                }
                items(files, key = { "sfile_${it.id}" }) { file ->
                    Card(
                        onClick = { onOpenPreview(file.id) },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIconBadge(category = file.category)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    FormatUtils.formatBytes(file.sizeBytes),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Trash") },
                actions = {
                    if (folders.isNotEmpty() || files.isNotEmpty()) {
                        TextButton(onClick = { confirmEmptyTrash = true }) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Empty Trash")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (folders.isEmpty() && files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Trash is empty", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(folders, key = { "tfolder_${it.id}" }) { folder ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Folder, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(folder.name, modifier = Modifier.weight(1f))
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
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIconBadge(category = file.category)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    FormatUtils.formatBytes(file.sizeBytes),
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
