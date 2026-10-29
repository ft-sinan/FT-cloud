package com.ftcloud.app.ui.screens.gallery

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ftcloud.app.core.model.AppSettings
import com.ftcloud.app.core.model.DriveFile
import com.ftcloud.app.core.model.FileCategory
import com.ftcloud.app.core.model.FormatUtils
import com.ftcloud.app.di.FTCloudContainer
import kotlinx.coroutines.launch

private enum class GalleryMediaFilter(val label: String) {
    ALL("All Media"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    FAVORITES("Favorites"),
}

private sealed interface GalleryGridEntry {
    data class DateHeader(val title: String, val count: Int) : GalleryGridEntry
    data class MediaTile(val file: DriveFile) : GalleryGridEntry
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    container: FTCloudContainer,
    settings: AppSettings,
    onOpenPreview: (String) -> Unit,
    onNavigateToBackup: () -> Unit,
) {
    val repo = container.driveRepository
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var mediaFilter by rememberSaveable { mutableStateOf(GalleryMediaFilter.ALL) }
    val rawMedia by repo.observeMediaTimeline().collectAsState(initial = emptyList())

    val filteredMedia = remember(rawMedia, mediaFilter) {
        when (mediaFilter) {
            GalleryMediaFilter.ALL -> rawMedia
            GalleryMediaFilter.PHOTOS -> rawMedia.filter { it.category == FileCategory.IMAGE }
            GalleryMediaFilter.VIDEOS -> rawMedia.filter { it.category == FileCategory.VIDEO }
            GalleryMediaFilter.FAVORITES -> rawMedia.filter { it.isStarred }
        }
    }

    val entries = remember(filteredMedia) {
        val grouped = filteredMedia.groupBy { file ->
            FormatUtils.formatPhotoTimelineBucket(file.effectiveMediaTimestamp)
        }
        buildList {
            for ((bucket, items) in grouped) {
                add(GalleryGridEntry.DateHeader(bucket, items.size))
                for (item in items) {
                    add(GalleryGridEntry.MediaTile(item))
                }
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val targetFolderId = repo.getOrCreateFolderPath(listOf("Photos"))
                val count = repo.enqueueUriUploads(uris, targetFolderId)
                snackbarHostState.showSnackbar("Queued $count media ${if (count == 1) "item" else "items"} for upload")
            }
        }
    }

    val columns = settings.galleryColumns.coerceIn(2, 4)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Photos & Videos") },
                actions = {
                    AssistChip(
                        onClick = onNavigateToBackup,
                        label = {
                            Text(if (settings.autoBackupEnabled) "Auto-backup On" else "Auto-backup Off")
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Backup,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    IconButton(onClick = {
                        val nextCols = when (columns) {
                            2 -> 3
                            3 -> 4
                            else -> 2
                        }
                        scope.launch { container.settingsRepository.setGalleryColumns(nextCols) }
                    }) {
                        Icon(Icons.Outlined.GridOn, contentDescription = "Change grid size")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    mediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Upload photos or videos")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(GalleryMediaFilter.entries.size) { index ->
                    val filter = GalleryMediaFilter.entries[index]
                    FilterChip(
                        selected = mediaFilter == filter,
                        onClick = { mediaFilter = filter },
                        label = { Text(filter.label) },
                    )
                }
            }

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No cloud photos or videos yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Enable Auto Backup to back up your camera roll in original quality, or tap + to upload.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = entries,
                        key = { entry ->
                            when (entry) {
                                is GalleryGridEntry.DateHeader -> "hdr_${entry.title}"
                                is GalleryGridEntry.MediaTile -> "media_${entry.file.id}"
                            }
                        },
                        span = { entry ->
                            when (entry) {
                                is GalleryGridEntry.DateHeader -> GridItemSpan(columns)
                                is GalleryGridEntry.MediaTile -> GridItemSpan(1)
                            }
                        }
                    ) { entry ->
                        when (entry) {
                            is GalleryGridEntry.DateHeader -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "${entry.count}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            is GalleryGridEntry.MediaTile -> {
                                val file = entry.file
                                val streamUrl = remember(file.id) {
                                    container.streamProxy.buildStreamUrl(file.id)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onOpenPreview(file.id) }
                                ) {
                                    AsyncImage(
                                        model = streamUrl,
                                        contentDescription = file.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    if (file.category == FileCategory.VIDEO) {
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Black.copy(alpha = 0.65f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp),
                                            )
                                            if (file.durationMs != null && file.durationMs > 0L) {
                                                Spacer(Modifier.width(2.dp))
                                                Text(
                                                    text = FormatUtils.formatDuration(file.durationMs),
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }

                                    if (file.isStarred) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = "Favorite",
                                            tint = Color(0xFFFFD54F),
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
