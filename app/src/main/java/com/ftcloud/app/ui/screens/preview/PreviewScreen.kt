package com.ftcloud.app.ui.screens.preview

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ftcloud.app.core.model.DriveFile
import com.ftcloud.app.core.model.FileCategory
import com.ftcloud.app.core.model.FormatUtils
import com.ftcloud.app.di.FTCloudContainer
import com.ftcloud.app.ui.components.CategoryIconBadge
import com.ftcloud.app.ui.components.FileDetailsSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    container: FTCloudContainer,
    fileId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val file by container.driveRepository.observeFileById(fileId).collectAsState(initial = null)
    var showDetails by remember { mutableStateOf(false) }
    var isPreparingShare by remember { mutableStateOf(false) }

    val currentFile = file

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = currentFile?.name ?: "Preview",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (currentFile != null) {
                        IconButton(onClick = {
                            scope.launch {
                                container.driveRepository.toggleFileStar(currentFile.id, !currentFile.isStarred)
                            }
                        }) {
                            Icon(
                                imageVector = if (currentFile.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Star",
                                tint = if (currentFile.isStarred) Color(0xFFF9A825) else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val dest = container.driveRepository.enqueueDownloadToDevice(currentFile)
                                snackbarHostState.showSnackbar("Queued download to ${dest.name}")
                            }
                        }) {
                            Icon(Icons.Outlined.Download, contentDescription = "Download")
                        }
                        IconButton(
                            enabled = !isPreparingShare,
                            onClick = {
                                isPreparingShare = true
                                scope.launch {
                                    runCatching {
                                        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                        val target = File(exportDir, currentFile.name)
                                        container.downloadEngine.downloadToFile(currentFile.id, target)
                                        val shareUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            target,
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = currentFile.mimeType.ifBlank { "*/*" }
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, currentFile.name))
                                    }.onFailure { err ->
                                        snackbarHostState.showSnackbar(err.message ?: "Unable to share file")
                                    }
                                    isPreparingShare = false
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { showDetails = true }) {
                            Icon(Icons.Outlined.Info, contentDescription = "Info")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            if (currentFile == null) {
                CircularProgressIndicator()
            } else {
                val streamUrl = remember(currentFile.id) {
                    container.streamProxy.buildStreamUrl(currentFile.id)
                }

                when (currentFile.category) {
                    FileCategory.IMAGE -> ZoomableImagePreview(
                        streamUrl = streamUrl,
                        contentDescription = currentFile.name,
                    )

                    FileCategory.VIDEO, FileCategory.AUDIO -> Media3StreamPlayer(
                        file = currentFile,
                        streamUrl = streamUrl,
                    )

                    FileCategory.PDF -> PdfCloudPreview(
                        container = container,
                        file = currentFile,
                    )

                    FileCategory.CODE -> TextCloudPreview(
                        container = container,
                        file = currentFile,
                        onCopyAll = { text ->
                            clipboard.setText(AnnotatedString(text))
                            scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                        }
                    )

                    else -> BinaryFileSummaryCard(
                        file = currentFile,
                        onDownload = {
                            scope.launch {
                                val dest = container.driveRepository.enqueueDownloadToDevice(currentFile)
                                snackbarHostState.showSnackbar("Queued download to ${dest.name}")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDetails && currentFile != null) {
        FileDetailsSheet(
            file = currentFile,
            onDismiss = { showDetails = false },
        )
    }
}

@Composable
private fun ZoomableImagePreview(
    streamUrl: String,
    contentDescription: String,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    offset = if (newScale <= 1.02f) {
                        Offset.Zero
                    } else {
                        offset + pan
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = streamUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Media3StreamPlayer(
    file: DriveFile,
    streamUrl: String,
) {
    val context = LocalContext.current
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (file.category == FileCategory.AUDIO) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.AudioFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(96.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = file.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Text(
                        text = "Streaming via HTTP 206 Byte-Range Proxy • ${FormatUtils.formatBytes(file.sizeBytes)}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            update = { view ->
                view.player = exoPlayer
            },
            modifier = if (file.category == FileCategory.AUDIO) {
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10141A))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Speed:",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
            )
            listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                FilterChip(
                    selected = playbackSpeed == speed,
                    onClick = { playbackSpeed = speed },
                    label = { Text("${speed}x") },
                )
            }
        }
    }
}

@Composable
private fun PdfCloudPreview(
    container: FTCloudContainer,
    file: DriveFile,
) {
    val context = LocalContext.current
    val pagesState by produceState<Result<List<Bitmap>>?>(initialValue = null, key1 = file.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val pdfFile = File(exportDir, "preview_${file.id}.pdf")
                if (!pdfFile.exists() || pdfFile.length() != file.sizeBytes) {
                    container.downloadEngine.downloadToFile(file.id, pdfFile)
                }
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val rendered = mutableListOf<Bitmap>()
                try {
                    val maxPages = renderer.pageCount.coerceAtMost(30)
                    for (i in 0 until maxPages) {
                        renderer.openPage(i).use { page ->
                            val targetW = 1080
                            val targetH = ((targetW.toFloat() / page.width.coerceAtLeast(1)) * page.height).toInt()
                                .coerceAtLeast(100)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            rendered += bmp
                        }
                    }
                } finally {
                    renderer.close()
                    pfd.close()
                }
                rendered
            }
        }
    }

    val current = pagesState
    when {
        current == null -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(10.dp))
                Text("Streaming PDF pages from Telegram…")
            }
        }

        current.isFailure -> {
            Text(
                text = current.exceptionOrNull()?.message ?: "Failed to render PDF",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(24.dp),
            )
        }

        else -> {
            val pages = current.getOrDefault(emptyList())
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(pages) { idx, bmp ->
                    Card(shape = RoundedCornerShape(10.dp)) {
                        Column {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Page ${idx + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "Page ${idx + 1} of ${pages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextCloudPreview(
    container: FTCloudContainer,
    file: DriveFile,
    onCopyAll: (String) -> Unit,
) {
    val context = LocalContext.current
    val textState by produceState<Result<String>?>(initialValue = null, key1 = file.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (file.sizeBytes > 2 * 1024 * 1024) {
                    throw IllegalStateException("File exceeds 2 MB inline text preview limit. Download to view full file.")
                }
                val temp = File(context.cacheDir, "exports/txt_${file.id}.tmp")
                container.downloadEngine.downloadToFile(file.id, temp)
                val content = temp.readText()
                temp.delete()
                content
            }
        }
    }

    val res = textState
    when {
        res == null -> CircularProgressIndicator()
        res.isFailure -> BinaryFileSummaryCard(file = file, onDownload = {})
        else -> {
            val content = res.getOrDefault("")
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${content.lines().size} lines • ${FormatUtils.formatBytes(file.sizeBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { onCopyAll(content) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy text")
                    }
                }
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = content,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BinaryFileSummaryCard(
    file: DriveFile,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryIconBadge(
                category = file.category,
                modifier = Modifier.size(64.dp),
            )
            Text(file.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${FormatUtils.formatBytes(file.sizeBytes)} • ${file.chunkCount} Telegram ${if (file.chunkCount == 1) "chunk" else "chunks"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onDownload,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save to Device Downloads")
            }
        }
    }
}
