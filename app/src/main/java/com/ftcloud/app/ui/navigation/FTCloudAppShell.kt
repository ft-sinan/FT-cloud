package com.ftcloud.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ftcloud.app.BuildConfig
import com.ftcloud.app.core.model.AppSettings
import com.ftcloud.app.core.model.FormatUtils
import com.ftcloud.app.core.model.TransferStatus
import com.ftcloud.app.core.update.AppUpdateManager
import com.ftcloud.app.core.update.UpdateRelease
import com.ftcloud.app.core.update.UpdateVersion
import com.ftcloud.app.di.FTCloudContainer
import com.ftcloud.app.ui.components.springPress
import com.ftcloud.app.ui.screens.backup.BackupScreen
import com.ftcloud.app.ui.screens.drive.DriveScreen
import com.ftcloud.app.ui.screens.gallery.GalleryScreen
import com.ftcloud.app.ui.screens.onboarding.OnboardingScreen
import com.ftcloud.app.ui.screens.preview.PreviewScreen
import com.ftcloud.app.ui.screens.settings.SettingsScreen
import com.ftcloud.app.ui.screens.settings.StarredScreen
import com.ftcloud.app.ui.screens.settings.TrashScreen
import com.ftcloud.app.ui.screens.transfers.TransfersScreen
import com.ftcloud.app.ui.theme.LocalAnimationsEnabled
import com.ftcloud.app.ui.theme.LocalGlassBottomBar
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
    TRANSFERS("transfers", "Queue", Icons.Filled.SwapVert, Icons.Outlined.SwapVert),
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
    var holdOnboardingForInitialSync by rememberSaveable { mutableStateOf(false) }

    if (!settings.isConfigured || holdOnboardingForInitialSync) {
        OnboardingScreen(
            container = container,
            onSyncStarted = { holdOnboardingForInitialSync = true },
            onSyncAborted = { holdOnboardingForInitialSync = false },
            onConnected = { holdOnboardingForInitialSync = false },
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val scope = rememberCoroutineScope()
    val glassBottomBar = LocalGlassBottomBar.current

    val transfers by container.driveRepository.observeTransfers().collectAsState(initial = emptyList())
    val activeTransferCount = transfers.count {
        it.status == TransferStatus.RUNNING || it.status == TransferStatus.QUEUED
    }

    var startupChecked by rememberSaveable { mutableStateOf(false) }
    var startupUpdateRelease by remember { mutableStateOf<UpdateRelease?>(null) }
    var lastForegroundSyncMs by rememberSaveable { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, settings.multiDeviceAutoSync, settings.isConfigured) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && settings.isConfigured && settings.multiDeviceAutoSync) {
                val now = System.currentTimeMillis()
                if (now - lastForegroundSyncMs >= 15_000L) {
                    lastForegroundSyncMs = now
                    scope.launch {
                        runCatching { container.driveRepository.syncMultiDeviceCloud(forcePull = false) }
                        runCatching { container.channelMediaImporter.syncPendingBotUpdates() }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(settings.lastUpdateCheckAt) {
        if (!startupChecked && System.currentTimeMillis() - settings.lastUpdateCheckAt >= 86_400_000L) {
            startupChecked = true
            container.settingsRepository.markUpdateChecked()
            val ghToken = container.credentialStore.getGithubUpdateToken()
            runCatching { AppUpdateManager.fetchLatestRelease(githubToken = ghToken) }
                .onSuccess { release ->
                    if (UpdateVersion.isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
                        startupUpdateRelease = release
                    }
                }
        }
    }

    val topRoutes = TopDestination.entries.map { it.route }.toSet()
    val currentRoute = currentDestination?.route
    val showBottomBar = currentRoute in topRoutes

    val dockVisibleState = remember { mutableStateOf(true) }
    LaunchedEffect(currentRoute) {
        dockVisibleState.value = true
    }
    val dockConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                when {
                    available.y < -10f -> dockVisibleState.value = false
                    available.y > 10f -> dockVisibleState.value = true
                }
                return Offset.Zero
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = TopDestination.DRIVE.route,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(dockConnection),
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

        if (showBottomBar) {
            FTCloudFloatingDock(
                visible = dockVisibleState.value,
                glass = glassBottomBar,
                currentDestination = currentDestination,
                activeTransferCount = activeTransferCount,
                onSelectDestination = { dest ->
                    dockVisibleState.value = true
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    val availableRelease = startupUpdateRelease
    if (availableRelease != null) {
        AlertDialog(
            onDismissRequest = { startupUpdateRelease = null },
            title = { Text("Update Available: ${availableRelease.tagName}") },
            text = {
                Column {
                    Text(
                        "${availableRelease.name} (${FormatUtils.formatBytes(availableRelease.apkSizeBytes)}) is ready to install.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (availableRelease.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            availableRelease.notes.take(400),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    startupUpdateRelease = null
                    navController.navigate(TopDestination.SETTINGS.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }) {
                    Text("Open Updater")
                }
            },
            dismissButton = {
                TextButton(onClick = { startupUpdateRelease = null }) {
                    Text("Later")
                }
            }
        )
    }

    if (pendingShare != null && (pendingShare.uris.isNotEmpty() || !pendingShare.sharedText.isNullOrBlank())) {
        AlertDialog(
            onDismissRequest = onClearPendingShare,
            title = { Text("Upload Shared Content to FT Cloud?") },
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

/** Orion-style floating glass pill dock overlaid over scrolling screen content. */
@Composable
private fun FTCloudFloatingDock(
    visible: Boolean,
    glass: Boolean,
    currentDestination: NavDestination?,
    activeTransferCount: Int,
    onSelectDestination: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val translation by animateFloatAsState(
        targetValue = if (visible) 0f else 150f,
        animationSpec = if (animationsEnabled) {
            spring(dampingRatio = 0.82f, stiffness = 300f)
        } else {
            snap()
        },
        label = "dockTranslation",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(180) else snap(),
        label = "dockAlpha",
    )

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .graphicsLayer {
                translationY = translation.dp.toPx()
                this.alpha = alpha
            }
            .shadow(16.dp, CircleShape)
            .clip(CircleShape)
            .background(
                if (glass) {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = CircleShape,
            )
            .padding(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopDestination.entries.forEach { dest ->
                val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                FloatingDockItem(
                    icon = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    label = dest.label,
                    selected = selected,
                    badgeCount = if (dest == TopDestination.TRANSFERS) activeTransferCount else 0,
                    onClick = { onSelectDestination(dest) },
                )
            }
        }
    }
}

@Composable
private fun FloatingDockItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = if (animationsEnabled) tween(220) else snap(),
        label = "dockContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = if (animationsEnabled) tween(220) else snap(),
        label = "dockContent",
    )
    val iconScale = remember { Animatable(1f) }
    LaunchedEffect(selected, animationsEnabled) {
        if (selected && animationsEnabled) {
            iconScale.snapTo(0.68f)
            iconScale.animateTo(1f, spring(dampingRatio = 0.52f, stiffness = 460f))
        } else {
            iconScale.snapTo(1f)
        }
    }

    Box(
        modifier = Modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(container)
            .springPress(CircleShape, role = Role.Tab, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val iconNode: @Composable () -> Unit = {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier
                        .size(23.dp)
                        .graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                        },
                )
            }
            if (badgeCount > 0) {
                BadgedBox(badge = { Badge { Text(badgeCount.toString()) } }) {
                    iconNode()
                }
            } else {
                iconNode()
            }

            AnimatedVisibility(
                visible = selected,
                enter = if (animationsEnabled) {
                    expandHorizontally(spring(dampingRatio = 0.76f, stiffness = 380f)) +
                        fadeIn(tween(160))
                } else {
                    EnterTransition.None
                },
                exit = if (animationsEnabled) {
                    shrinkHorizontally(tween(140)) + fadeOut(tween(100))
                } else {
                    ExitTransition.None
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 82.dp),
                    )
                }
            }
        }
    }
}
