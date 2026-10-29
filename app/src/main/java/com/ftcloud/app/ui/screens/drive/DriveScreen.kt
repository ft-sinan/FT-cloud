package com.ftcloud.app.ui.screens.drive

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ftcloud.app.core.model.AppSettings
import com.ftcloud.app.core.model.DriveFile
import com.ftcloud.app.core.model.DriveFolder
import com.ftcloud.app.core.model.DriveStats
import com.ftcloud.app.core.model.DriveViewMode
import com.ftcloud.app.core.model.FileCategory
import com.ftcloud.app.core.model.SortDirection
import com.ftcloud.app.core.model.SortOption
import com.ftcloud.app.di.FTCloudContainer
import com.ftcloud.app.ui.components.BreadcrumbBar
import com.ftcloud.app.ui.components.DriveFileGridCard
import com.ftcloud.app.ui.components.DriveFileRow
import com.ftcloud.app.ui.components.DriveFolderRow
import com.ftcloud.app.ui.components.FileDetailsSheet
import com.ftcloud.app.ui.components.MoveDestinationSheet
import com.ftcloud.app.ui.components.QuickNoteDialog
import com.ftcloud.app.ui.components.SingleInputDialog
import com.ftcloud.app.ui.components.StorageOverviewCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    container: FTCloudContainer,
    settings: AppSettings,
    onOpenFilePreview: (String) -> Unit,
    onOpenStarred: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val repo = container.driveRepository
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var breadcrumbs by remember { mutableStateOf<List<DriveFolder>>(emptyList()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<FileCategory?>(null) }

    var selectedFileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFolderIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedFileIds.isNotEmpty() || selectedFolderIds.isNotEmpty()

    var showAddSheet by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showQuickNoteDialog by remember { mutableStateOf(false) }
    var renameFolderTarget by remember { mutableStateOf<DriveFolder?>(null) }
    var renameFileTarget by remember { mutableStateOf<DriveFile?>(null) }
    var detailsFileTarget by remember { mutableStateOf<DriveFile?>(null) }
    var moveTargetIds by remember { mutableStateOf<Pair<Set<String>, Set<String>>?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var isSyncingManifest by remember { mutableStateOf(false) }

    val stats by repo.observeStats().collectAsState(initial = DriveStats())
    val allFolders by repo.observeAllActiveFolders().collectAsState(initial = emptyList())

    val rawFoldersFlow = remember(currentFolderId, searchQuery) {
        if (searchQuery.isNotBlank()) repo.searchFolders(searchQuery.trim())
        else repo.observeFolders(currentFolderId)
    }
    val rawFilesFlow = remember(currentFolderId, searchQuery) {
        if (searchQuery.isNotBlank()) repo.searchFiles(searchQuery.trim())
        else repo.observeFiles(currentFolderId)
    }

    val rawFolders by rawFoldersFlow.collectAsState(initial = emptyList())
    val rawFiles by rawFilesFlow.collectAsState(initial = emptyList())

    LaunchedEffect(currentFolderId, rawFolders) {
        breadcrumbs = repo.getBreadcrumbs(currentFolderId)
    }

    val visibleFolders = remember(rawFolders, selectedCategory) {
        if (selectedCategory != null) emptyList() else rawFolders
    }

    val visibleFiles = remember(rawFiles, selectedCategory, settings.sortOption, settings.sortDirection) {
        val filtered = if (selectedCategory != null) {
            rawFiles.filter { it.category == selectedCategory }
        } else {
            rawFiles
        }
        val comparator = when (settings.sortOption) {
            SortOption.NAME -> compareBy<DriveFile> { it.name.lowercase() }
            SortOption.SIZE -> compareBy<DriveFile> { it.sizeBytes }
            SortOption.DATE_MODIFIED -> compareBy<DriveFile> { it.updatedAt }
        }
        if (settings.sortDirection == SortDirection.ASC) {
            filtered.sortedWith(comparator)
        } else {
            filtered.sortedWith(comparator.reversed())
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val queued = repo.enqueueUriUploads(uris, currentFolderId)
                snackbarHostState.showSnackbar("Queued $queued ${if (queued == 1) "file" else "files"} for cloud upload")
            }
        }
    }

    BackHandler(enabled = isSelectionMode || currentFolderId != null || searchQuery.isNotEmpty()) {
        when {
            isSelectionMode -> {
                selectedFileIds = emptySet()
                selectedFolderIds = emptySet()
            }
            searchQuery.isNotEmpty() -> {
                searchQuery = ""
            }
            currentFolderId != null -> {
                currentFolderId = breadcrumbs.dropLast(1).lastOrNull()?.id
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedFileIds = emptySet()
                            selectedFolderIds = emptySet()
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear selection")
                        }
                    },
                    title = {
                        Text("${selectedFileIds.size + selectedFolderIds.size} selected")
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedFileIds = visibleFiles.map { it.id }.toSet()
                            selectedFolderIds = visibleFolders.map { it.id }.toSet()
                        }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = {
                            moveTargetIds = selectedFileIds to selectedFolderIds
                        }) {
                            Icon(Icons.Outlined.DriveFileMove, contentDescription = "Move selected")
                        }
                        IconButton(onClick = {
                            val fIds = selectedFileIds
                            val dIds = selectedFolderIds
                            selectedFileIds = emptySet()
                            selectedFolderIds = emptySet()
                            scope.launch {
                                repo.setTrashedForItems(fIds, dIds, trashed = true)
                                snackbarHostState.showSnackbar("Moved items to Trash")
                            }
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Trash selected")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = breadcrumbs.lastOrNull()?.name ?: "FT-Cloud Drive",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            val next = if (settings.viewMode == DriveViewMode.LIST) DriveViewMode.GRID else DriveViewMode.LIST
                            scope.launch { container.settingsRepository.setViewMode(next) }
                        }) {
                            Icon(
                                imageVector = if (settings.viewMode == DriveViewMode.LIST) {
                                    Icons.Outlined.GridView
                                } else {
                                    Icons.AutoMirrored.Outlined.ViewList
                                },
                                contentDescription = "Toggle grid or list view",
                            )
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                SortOption.entries.forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            val suffix = if (settings.sortOption == opt) {
                                                if (settings.sortDirection == SortDirection.ASC) " ↑" else " ↓"
                                            } else ""
                                            Text("${opt.label}$suffix")
                                        },
                                        onClick = {
                                            sortMenuExpanded = false
                                            val nextDir = if (settings.sortOption == opt) {
                                                if (settings.sortDirection == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
                                            } else {
                                                SortDirection.DESC
                                            }
                                            scope.launch {
                                                container.settingsRepository.setSort(opt, nextDir)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSheet = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("New") },
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search files & folders offline…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") },
                    )
                }
                items(
                    listOf(
                        FileCategory.IMAGE,
                        FileCategory.VIDEO,
                        FileCategory.AUDIO,
                        FileCategory.PDF,
                        FileCategory.DOCUMENT,
                        FileCategory.CODE,
                        FileCategory.ARCHIVE,
                    )
                ) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(cat.label) },
                    )
                }
            }

            if (searchQuery.isBlank() && selectedCategory == null) {
                BreadcrumbBar(
                    breadcrumbs = breadcrumbs,
                    onNavigateFolder = { folderId -> currentFolderId = folderId },
                )
            }

            if (visibleFolders.isEmpty() && visibleFiles.isEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (currentFolderId == null && searchQuery.isBlank()) {
                        item {
                            StorageOverviewCard(
                                stats = stats,
                                channelTitle = settings.channelTitle,
                                botCount = settings.botCount,
                                isSyncingManifest = isSyncingManifest,
                                onSyncManifest = {
                                    isSyncingManifest = true
                                    scope.launch {
                                        runCatching { repo.syncManifestNow() }
                                            .onSuccess { snackbarHostState.showSnackbar("Cloud index synced & pinned to Telegram") }
                                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "Manifest sync failed") }
                                        isSyncingManifest = false
                                    }
                                },
                                onOpenStarred = onOpenStarred,
                                onOpenTrash = onOpenTrash,
                            )
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudQueue,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                modifier = Modifier.size(64.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching cloud items" else "This folder is empty",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Tap + New to upload files of any size or create folders.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (settings.viewMode == DriveViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (currentFolderId == null && searchQuery.isBlank() && selectedCategory == null) {
                        item(key = "overview_banner") {
                            StorageOverviewCard(
                                stats = stats,
                                channelTitle = settings.channelTitle,
                                botCount = settings.botCount,
                                isSyncingManifest = isSyncingManifest,
                                onSyncManifest = {
                                    isSyncingManifest = true
                                    scope.launch {
                                        runCatching { repo.syncManifestNow() }
                                            .onSuccess { snackbarHostState.showSnackbar("Cloud index synced & pinned to Telegram") }
                                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "Manifest sync failed") }
                                        isSyncingManifest = false
                                    }
                                },
                                onOpenStarred = onOpenStarred,
                                onOpenTrash = onOpenTrash,
                            )
                        }
                    }

                    items(visibleFolders, key = { "folder_${it.id}" }) { folder ->
                        val isSelected = folder.id in selectedFolderIds
                        DriveFolderRow(
                            folder = folder,
                            selected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedFolderIds = if (isSelected) selectedFolderIds - folder.id else selectedFolderIds + folder.id
                                } else {
                                    currentFolderId = folder.id
                                }
                            },
                            onLongClick = {
                                selectedFolderIds = if (isSelected) selectedFolderIds - folder.id else selectedFolderIds + folder.id
                            },
                            onToggleStar = {
                                scope.launch { repo.toggleFolderStar(folder.id, !folder.isStarred) }
                            },
                            onRename = { renameFolderTarget = folder },
                            onMove = { moveTargetIds = emptySet<String>() to setOf(folder.id) },
                            onMoveToTrash = {
                                scope.launch {
                                    repo.setTrashedForItems(emptyList(), listOf(folder.id), trashed = true)
                                    snackbarHostState.showSnackbar("Moved \"${folder.name}\" to Trash")
                                }
                            }
                        )
                    }

                    items(visibleFiles, key = { "file_${it.id}" }) { file ->
                        val isSelected = file.id in selectedFileIds
                        val thumbUrl = remember(file.id) {
                            if (file.category == FileCategory.IMAGE || file.category == FileCategory.VIDEO) {
                                container.streamProxy.buildStreamUrl(file.id)
                            } else null
                        }
                        DriveFileRow(
                            file = file,
                            thumbnailUrl = thumbUrl,
                            selected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedFileIds = if (isSelected) selectedFileIds - file.id else selectedFileIds + file.id
                                } else {
                                    onOpenFilePreview(file.id)
                                }
                            },
                            onLongClick = {
                                selectedFileIds = if (isSelected) selectedFileIds - file.id else selectedFileIds + file.id
                            },
                            onToggleStar = {
                                scope.launch { repo.toggleFileStar(file.id, !file.isStarred) }
                            },
                            onDownload = {
                                scope.launch {
                                    val dest = repo.enqueueDownloadToDevice(file)
                                    snackbarHostState.showSnackbar("Downloading to ${dest.name}")
                                }
                            },
                            onRename = { renameFileTarget = file },
                            onMove = { moveTargetIds = setOf(file.id) to emptySet() },
                            onDetails = { detailsFileTarget = file },
                            onMoveToTrash = {
                                scope.launch {
                                    repo.setTrashedForItems(listOf(file.id), emptyList(), trashed = true)
                                    snackbarHostState.showSnackbar("Moved \"${file.name}\" to Trash")
                                }
                            }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 92.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = visibleFolders,
                        key = { "gfolder_${it.id}" },
                        span = { GridItemSpan(2) },
                    ) { folder ->
                        val isSelected = folder.id in selectedFolderIds
                        DriveFolderRow(
                            folder = folder,
                            selected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedFolderIds = if (isSelected) selectedFolderIds - folder.id else selectedFolderIds + folder.id
                                } else {
                                    currentFolderId = folder.id
                                }
                            },
                            onLongClick = {
                                selectedFolderIds = if (isSelected) selectedFolderIds - folder.id else selectedFolderIds + folder.id
                            },
                            onToggleStar = {
                                scope.launch { repo.toggleFolderStar(folder.id, !folder.isStarred) }
                            },
                            onRename = { renameFolderTarget = folder },
                            onMove = { moveTargetIds = emptySet<String>() to setOf(folder.id) },
                            onMoveToTrash = {
                                scope.launch {
                                    repo.setTrashedForItems(emptyList(), listOf(folder.id), trashed = true)
                                }
                            }
                        )
                    }

                    items(visibleFiles, key = { "gfile_${it.id}" }) { file ->
                        val isSelected = file.id in selectedFileIds
                        val thumbUrl = remember(file.id) {
                            if (file.category == FileCategory.IMAGE || file.category == FileCategory.VIDEO) {
                                container.streamProxy.buildStreamUrl(file.id)
                            } else null
                        }
                        DriveFileGridCard(
                            file = file,
                            thumbnailUrl = thumbUrl,
                            selected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedFileIds = if (isSelected) selectedFileIds - file.id else selectedFileIds + file.id
                                } else {
                                    onOpenFilePreview(file.id)
                                }
                            },
                            onLongClick = {
                                selectedFileIds = if (isSelected) selectedFileIds - file.id else selectedFileIds + file.id
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Add to FT-Cloud",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                SheetActionRow(
                    icon = Icons.Outlined.UploadFile,
                    title = "Upload files",
                    subtitle = "Any file size (auto-split into ≤19 MiB Telegram chunks)",
                    onClick = {
                        showAddSheet = false
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }
                )
                SheetActionRow(
                    icon = Icons.Outlined.CreateNewFolder,
                    title = "New folder",
                    subtitle = "Organize files inside virtual cloud directories",
                    onClick = {
                        showAddSheet = false
                        showCreateFolderDialog = true
                    }
                )
                SheetActionRow(
                    icon = Icons.Outlined.NoteAdd,
                    title = "New cloud note",
                    subtitle = "Write Markdown or plain text saved directly to Telegram",
                    onClick = {
                        showAddSheet = false
                        showQuickNoteDialog = true
                    }
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showCreateFolderDialog) {
        SingleInputDialog(
            title = "Create Folder",
            label = "Folder name",
            confirmLabel = "Create",
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                showCreateFolderDialog = false
                scope.launch {
                    runCatching { repo.createFolder(folderName, currentFolderId) }
                        .onFailure { snackbarHostState.showSnackbar(it.message ?: "Could not create folder") }
                }
            }
        )
    }

    if (showQuickNoteDialog) {
        QuickNoteDialog(
            onDismiss = { showQuickNoteDialog = false },
            onCreate = { fileName, body ->
                showQuickNoteDialog = false
                scope.launch {
                    repo.createAndUploadTextNote(fileName, body, currentFolderId)
                    snackbarHostState.showSnackbar("Uploading cloud note \"$fileName\"")
                }
            }
        )
    }

    renameFolderTarget?.let { folder ->
        SingleInputDialog(
            title = "Rename Folder",
            label = "Folder name",
            initialValue = folder.name,
            onDismiss = { renameFolderTarget = null },
            onConfirm = { newName ->
                renameFolderTarget = null
                scope.launch { repo.renameFolder(folder.id, newName) }
            }
        )
    }

    renameFileTarget?.let { file ->
        SingleInputDialog(
            title = "Rename File",
            label = "File name",
            initialValue = file.name,
            onDismiss = { renameFileTarget = null },
            onConfirm = { newName ->
                renameFileTarget = null
                scope.launch { repo.renameFile(file.id, newName) }
            }
        )
    }

    detailsFileTarget?.let { file ->
        FileDetailsSheet(
            file = file,
            onDismiss = { detailsFileTarget = null },
        )
    }

    moveTargetIds?.let { (fileIdsToMove, folderIdsToMove) ->
        MoveDestinationSheet(
            allFolders = allFolders,
            excludedFolderIds = folderIdsToMove,
            onDismiss = { moveTargetIds = null },
            onSelectDestination = { destParentId ->
                moveTargetIds = null
                selectedFileIds = emptySet()
                selectedFolderIds = emptySet()
                scope.launch {
                    repo.moveItems(fileIdsToMove, folderIdsToMove, destParentId)
                    snackbarHostState.showSnackbar("Moved items")
                }
            }
        )
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
