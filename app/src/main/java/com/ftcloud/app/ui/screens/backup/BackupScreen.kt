package com.ftcloud.app.ui.screens.backup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.ftcloud.app.core.model.AppSettings
import com.ftcloud.app.core.model.BackupFolderConfig
import com.ftcloud.app.core.model.DriveStats
import com.ftcloud.app.core.model.FormatUtils
import com.ftcloud.app.core.sync.BackupScheduler
import com.ftcloud.app.di.FTCloudContainer
import com.ftcloud.app.ui.components.ExpressiveHeroHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    container: FTCloudContainer,
    settings: AppSettings,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val stats by container.driveRepository.observeStats().collectAsState(initial = DriveStats())

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        scope.launch {
            container.settingsRepository.setAutoBackupEnabled(true)
            BackupScheduler.syncSchedule(
                context = context,
                enabled = true,
                wifiOnly = settings.backupWifiOnly,
                chargingOnly = settings.backupChargingOnly,
            )
            snackbarHostState.showSnackbar("Automatic cloud backup enabled")
        }
    }

    val folderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val doc = DocumentFile.fromTreeUri(context, uri)
            val folderName = doc?.name?.ifBlank { "Folder" } ?: "CustomFolder"
            scope.launch {
                container.settingsRepository.addBackupFolder(
                    BackupFolderConfig(
                        uriString = uri.toString(),
                        displayName = folderName,
                        cloudSubfolder = folderName,
                    )
                )
                snackbarHostState.showSnackbar("Added \"$folderName\" to automatic backup")
            }
        }
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ExpressiveHeroHeader(
                    title = "Auto Backup",
                    subtitle = "Original-quality camera roll & folder sync to your private Telegram vault.",
                    icon = Icons.Outlined.Backup,
                )
            }

            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Outlined.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Background Cloud Sync",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = "${stats.backedUpCount} items backed up in original quality",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = settings.autoBackupEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            arrayOf(
                                                Manifest.permission.READ_MEDIA_IMAGES,
                                                Manifest.permission.READ_MEDIA_VIDEO,
                                                Manifest.permission.POST_NOTIFICATIONS,
                                            )
                                        } else {
                                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        }
                                        mediaPermissionLauncher.launch(perms)
                                    } else {
                                        scope.launch {
                                            container.settingsRepository.setAutoBackupEnabled(false)
                                            BackupScheduler.syncSchedule(
                                                context = context,
                                                enabled = false,
                                                wifiOnly = settings.backupWifiOnly,
                                                chargingOnly = settings.backupChargingOnly,
                                            )
                                        }
                                    }
                                }
                            )
                        }

                        if (settings.lastBackupRunAt != null) {
                            Text(
                                text = "Last run: ${FormatUtils.formatTimestamp(settings.lastBackupRunAt)} • ${settings.lastBackupSummary.orEmpty()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Button(
                            onClick = {
                                BackupScheduler.triggerImmediateBackup(context)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Started background backup scan…")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Back Up Now")
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Media & Network Rules", style = MaterialTheme.typography.titleMedium)

                        SettingSwitchRow(
                            title = "Back up Camera & Photos",
                            subtitle = "Upload uncompressed photos to /Auto Backup/Photos",
                            checked = settings.backupCameraPhotos,
                            onCheckedChange = {
                                scope.launch { container.settingsRepository.setBackupCameraPhotos(it) }
                            }
                        )
                        HorizontalDivider()
                        SettingSwitchRow(
                            title = "Back up Videos",
                            subtitle = "Automatically chunk & upload large 4K videos",
                            checked = settings.backupCameraVideos,
                            onCheckedChange = {
                                scope.launch { container.settingsRepository.setBackupCameraVideos(it) }
                            }
                        )
                        HorizontalDivider()
                        SettingSwitchRow(
                            title = "Wi-Fi Only",
                            subtitle = "Pause scheduled backups when on mobile data",
                            checked = settings.backupWifiOnly,
                            onCheckedChange = { wifi ->
                                scope.launch {
                                    container.settingsRepository.setBackupWifiOnly(wifi)
                                    BackupScheduler.syncSchedule(
                                        context = context,
                                        enabled = settings.autoBackupEnabled,
                                        wifiOnly = wifi,
                                        chargingOnly = settings.backupChargingOnly,
                                    )
                                }
                            }
                        )
                        HorizontalDivider()
                        SettingSwitchRow(
                            title = "While Charging Only",
                            subtitle = "Run periodic background scans only when plugged in",
                            checked = settings.backupChargingOnly,
                            onCheckedChange = { charging ->
                                scope.launch {
                                    container.settingsRepository.setBackupChargingOnly(charging)
                                    BackupScheduler.syncSchedule(
                                        context = context,
                                        enabled = settings.autoBackupEnabled,
                                        wifiOnly = settings.backupWifiOnly,
                                        chargingOnly = charging,
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Custom Device Folders", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { folderTreeLauncher.launch(null) }) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Folder")
                    }
                }
            }

            if (settings.backupFolders.isEmpty()) {
                item {
                    Text(
                        text = "No extra folders added yet. Add Downloads, Documents, or Notes folders to mirror them into /Auto Backup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                items(settings.backupFolders, key = { it.uriString }) { folder ->
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Cloud target: /Auto Backup/${folder.cloudSubfolder}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                scope.launch { container.settingsRepository.removeBackupFolder(folder.uriString) }
                            }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove folder")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
