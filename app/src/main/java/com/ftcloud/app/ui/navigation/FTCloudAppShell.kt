package com.ftcloud.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ftcloud.app.core.model.AppSettings
import com.ftcloud.app.core.model.TransferStatus
import com.ftcloud.app.di.FTCloudContainer
import com.ftcloud.app.ui.screens.backup.BackupScreen
import com.ftcloud.app.ui.screens.drive.DriveScreen
import com.ftcloud.app.ui.screens.gallery.GalleryScreen
import com.ftcloud.app.ui.screens.onboarding.OnboardingScreen
import com.ftcloud.app.ui.screens.preview.PreviewScreen
import com.ftcloud.app.ui.screens.settings.SettingsScreen
import com.ftcloud.app.ui.screens.settings.StarredScreen
import com.ftcloud.app.ui.screens.settings.TrashScreen
import com.ftcloud.app.ui.screens.transfers.TransfersScreen
import kotlinx.coroutines.launch

data class IncomingSharePayload(
    val uris: List<Uri> = emptyList(),
    val sharedText: String? = null,
)

private enum class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    DRIVE("drive", "Drive", Icons.Filled.Cloud, Icons.Outlined.Cloud),
    PHOTOS("photos", "Photos", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    TRANSFERS("transfers", "Transfers", Icons.Filled.SwapVert, Icons.Outlined.SwapVert),
    BACKUP("backup", "Backup", Icons.Filled.Backup, Icons.Outlined.Backup),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun FTCloudAppShell(
    container: FTCloudContainer,
    settings: AppSettings,
    pendingShare: IncomingSharePayload?,
    onClearPendingShare: () -> Unit,
) {
    if (!settings.isConfigured) {
        OnboardingScreen(
            container = container,
            onConnected = {},
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val scope = rememberCoroutineScope()

    val transfers by container.driveRepository.observeTransfers().collectAsState(initial = emptyList())
    val activeTransferCount = transfers.count {
        it.status == TransferStatus.RUNNING || it.status == TransferStatus.QUEUED
    }

    val topRoutes = TopDestination.entries.map { it.route }.toSet()
    val showBottomBar = currentDestination?.route in topRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (dest == TopDestination.TRANSFERS && activeTransferCount > 0) {
                                    BadgedBox(
                                        badge = { Badge { Text(activeTransferCount.toString()) } }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label,
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                }
                            },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        }
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.DRIVE.route,
            modifier = Modifier.padding(bottom = if (showBottomBar) outerPadding.calculateBottomPadding() else 0.dp),
        ) {
            composable(TopDestination.DRIVE.route) {
                DriveScreen(
                    container = container,
                    settings = settings,
                    onOpenFilePreview = { fileId -> navController.navigate("preview/$fileId") },
                    onOpenStarred = { navController.navigate("starred") },
                    onOpenTrash = { navController.navigate("trash") },
                )
            }

            composable(TopDestination.PHOTOS.route) {
                GalleryScreen(
                    container = container,
                    settings = settings,
                    onOpenPreview = { fileId -> navController.navigate("preview/$fileId") },
                    onNavigateToBackup = {
                        navController.navigate(TopDestination.BACKUP.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable(TopDestination.TRANSFERS.route) {
                TransfersScreen(container = container)
            }

            composable(TopDestination.BACKUP.route) {
                BackupScreen(
                    container = container,
                    settings = settings,
                )
            }

            composable(TopDestination.SETTINGS.route) {
                SettingsScreen(
                    container = container,
                    settings = settings,
                    onOpenStarred = { navController.navigate("starred") },
                    onOpenTrash = { navController.navigate("trash") },
                )
            }

            composable("starred") {
                StarredScreen(
                    container = container,
                    onOpenPreview = { fileId -> navController.navigate("preview/$fileId") },
                    onBack = { navController.popBackStack() },
                )
            }

            composable("trash") {
                TrashScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "preview/{fileId}",
                arguments = listOf(navArgument("fileId") { type = NavType.StringType }),
            ) { entry ->
                val fileId = entry.arguments?.getString("fileId").orEmpty()
                PreviewScreen(
                    container = container,
                    fileId = fileId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    if (pendingShare != null && (pendingShare.uris.isNotEmpty() || !pendingShare.sharedText.isNullOrBlank())) {
        AlertDialog(
            onDismissRequest = onClearPendingShare,
            title = { Text("Upload Shared Content to FT-Cloud?") },
            text = {
                Column {
                    if (pendingShare.uris.isNotEmpty()) {
                        Text("${pendingShare.uris.size} shared ${if (pendingShare.uris.size == 1) "file" else "files"} ready to upload to /Shared.")
                    }
                    if (!pendingShare.sharedText.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Shared text snippet (${pendingShare.sharedText.length} chars) will be saved as a Markdown note.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val snapshot = pendingShare
                    onClearPendingShare()
                    scope.launch {
                        val sharedFolderId = container.driveRepository.getOrCreateFolderPath(listOf("Shared"))
                        if (snapshot.uris.isNotEmpty()) {
                            container.driveRepository.enqueueUriUploads(snapshot.uris, sharedFolderId)
                        }
                        if (!snapshot.sharedText.isNullOrBlank()) {
                            container.driveRepository.createAndUploadTextNote(
                                fileName = "shared_${System.currentTimeMillis()}.md",
                                body = snapshot.sharedText,
                                targetFolderId = sharedFolderId,
                            )
                        }
                        navController.navigate(TopDestination.TRANSFERS.route) {
                            launchSingleTop = true
                        }
                    }
                }) {
                    Text("Upload Now")
                }
            },
            dismissButton = {
                TextButton(onClick = onClearPendingShare) {
                    Text("Cancel")
                }
            }
        )
    }
}
