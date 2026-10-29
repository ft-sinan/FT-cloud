package com.ftcloud.app.ui.screens.transfers

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ftcloud.app.core.model.FormatUtils
import com.ftcloud.app.core.model.TransferDirection
import com.ftcloud.app.core.model.TransferStatus
import com.ftcloud.app.core.model.TransferTask
import com.ftcloud.app.di.FTCloudContainer
import kotlinx.coroutines.launch

private enum class TransferTab(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    FAILED("Failed"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    container: FTCloudContainer,
) {
    val repo = container.driveRepository
    val scope = rememberCoroutineScope()
    var activeTab by rememberSaveable { mutableStateOf(TransferTab.ALL) }
    val allTransfers by repo.observeTransfers().collectAsState(initial = emptyList())

    val filtered = remember(allTransfers, activeTab) {
        when (activeTab) {
            TransferTab.ALL -> allTransfers
            TransferTab.ACTIVE -> allTransfers.filter {
                it.status in setOf(TransferStatus.RUNNING, TransferStatus.QUEUED, TransferStatus.PAUSED)
            }
            TransferTab.COMPLETED -> allTransfers.filter { it.status == TransferStatus.COMPLETED }
            TransferTab.FAILED -> allTransfers.filter {
                it.status in setOf(TransferStatus.FAILED, TransferStatus.CANCELLED)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfer Manager") },
                actions = {
                    if (allTransfers.any { it.status in setOf(TransferStatus.COMPLETED, TransferStatus.CANCELLED) }) {
                        TextButton(onClick = { scope.launch { repo.clearCompletedTransfers() } }) {
                            Icon(Icons.Outlined.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear done")
                        }
                    }
                }
            )
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
                items(TransferTab.entries) { tab ->
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        label = { Text(tab.label) },
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.SwapVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("No transfers in this view", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Multi-chunk Bot Swarm uploads and downloads appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { task ->
                        TransferTaskCard(
                            task = task,
                            onPause = { scope.launch { repo.pauseTransfer(task.id) } },
                            onResumeOrRetry = { scope.launch { repo.resumeOrRetryTransfer(task.id) } },
                            onCancel = { scope.launch { repo.cancelTransfer(task.id) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferTaskCard(
    task: TransferTask,
    onPause: () -> Unit,
    onResumeOrRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (task.direction == TransferDirection.UPLOAD) {
                        Icons.Outlined.CloudUpload
                    } else {
                        Icons.Outlined.CloudDownload
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${FormatUtils.formatBytes(task.transferredBytes)} / ${FormatUtils.formatBytes(task.totalBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "• Chunks ${task.completedChunks}/${task.totalChunks}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                when (task.status) {
                    TransferStatus.RUNNING, TransferStatus.QUEUED -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Outlined.Pause, contentDescription = "Pause")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Outlined.Cancel, contentDescription = "Cancel")
                        }
                    }

                    TransferStatus.PAUSED -> {
                        IconButton(onClick = onResumeOrRetry) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Outlined.Cancel, contentDescription = "Cancel")
                        }
                    }

                    TransferStatus.FAILED, TransferStatus.CANCELLED -> {
                        IconButton(onClick = onResumeOrRetry) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Retry")
                        }
                    }

                    TransferStatus.COMPLETED -> {}
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { task.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = task.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }

                if (!task.activeBotLabel.isNullOrBlank()) {
                    Text(
                        text = "Swarm node: ${task.activeBotLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (!task.errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = task.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
