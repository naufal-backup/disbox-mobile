package com.disbox.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.disbox.mobile.data.DiscordDataSourceFactory
import com.disbox.mobile.DisboxViewModel
import com.disbox.mobile.model.DisboxFile
import com.disbox.mobile.ui.components.ShareDialog
import com.disbox.mobile.ui.components.VideoPlayer
import com.disbox.mobile.utils.*
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilePreviewScreen(
    file: DisboxFile,
    allFiles: List<DisboxFile> = emptyList(),
    viewModel: DisboxViewModel,
    onFileChange: (DisboxFile) -> Unit = {},
    onClose: () -> Unit
) {
    val navigatableFiles = remember(allFiles) {
        allFiles.filter { f ->
            val name = f.path.split("/").last()
            isImageFile(name) || isVideoFile(name) || isAudioFile(name) || isPdfFile(name) || 
            listOf("txt", "md", "json", "js", "py", "rs", "html", "css", "xml", "yml", "yaml", "sql", "sh", "env").contains(name.split(".").last().lowercase())
        }
    }

    val initialIndex = navigatableFiles.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex)

    LaunchedEffect(pagerState.currentPage) {
        if (navigatableFiles.isNotEmpty()) {
            onFileChange(navigatableFiles[pagerState.currentPage])
        }
    }

    BackHandler(onBack = onClose)

    var showShareDialogByPreview by remember { mutableStateOf<Pair<String, String?>?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                count = navigatableFiles.size,
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 0.dp,
                userScrollEnabled = true
            ) { pageIndex ->
                val currentFile = navigatableFiles[pageIndex]
                val isActive = pagerState.currentPage == pageIndex
                MediaPreviewItem(currentFile, viewModel, isActive)
            }

            val currentFile = navigatableFiles.getOrNull(pagerState.currentPage) ?: file
            val name = currentFile.path.split("/").last()
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                    Text(
                        formatSize(currentFile.size),
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                if (viewModel.shareEnabled) {
                    IconButton(onClick = { showShareDialogByPreview = currentFile.path to currentFile.id }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = { viewModel.downloadFile(currentFile) }) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    if (showShareDialogByPreview != null) {
        ShareDialog(
            filePath = showShareDialogByPreview!!.first,
            fileId = showShareDialogByPreview!!.second,
            viewModel = viewModel,
            onClose = { showShareDialogByPreview = null }
        )
    }
}

@Composable
fun MediaPreviewItem(file: DisboxFile, viewModel: DisboxViewModel, isActive: Boolean) {
    val name = file.path.split("/").last()
    val context = LocalContext.current
    val ext = name.split(".").last().lowercase()
    
    val isMedia = remember(ext) { isVideoFile(name) || isAudioFile(name) }
    val isImage = remember(ext) { isImageFile(name) }
    val isText = remember(ext) { listOf("txt", "md", "json", "js", "py", "rs", "html", "css", "xml", "yml", "yaml", "sql", "sh", "env").contains(ext) }

    var previewText by remember { mutableStateOf<String?>(null) }
    var previewFileObject by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Lazy initialization of ExoPlayer
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(isActive) {
        if (isActive && isMedia && exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        } else if (!isActive && exoPlayer != null) {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
        }
    }

    LaunchedEffect(file.id, isActive, exoPlayer) {
        if (!isActive) return@LaunchedEffect
        
        errorMsg = null
        if (!isMedia) {
            previewFileObject = null
            previewText = null
        }

        try {
            if (isMedia) {
                val player = exoPlayer ?: return@LaunchedEffect
                val api = viewModel.repository
                val dataSourceFactory = DiscordDataSourceFactory(api, file)
                val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri("disbox-stream://${file.id}.$ext"))
                player.setMediaSource(mediaSource)
                player.prepare()
                player.playWhenReady = true
            } else if (isImage || isText) {
                val cacheKey = "prev_${file.id}_${file.size}"
                val tempFile = File(context.cacheDir, cacheKey)
                
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    isLoading = true
                    viewModel.downloadFileToCache(file, tempFile) { success ->
                        isLoading = false
                        if (success) {
                            if (isImage) previewFileObject = tempFile
                            if (isText) previewText = tempFile.readText()
                        } else {
                            errorMsg = "Gagal mengunduh pratinjau"
                        }
                    }
                } else {
                    if (isImage) previewFileObject = tempFile
                    if (isText) previewText = tempFile.readText()
                }
            }
        } catch (e: Exception) {
            errorMsg = "Error: ${e.message}"
            isLoading = false
        }
    }

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        when {
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Mengunduh...", fontSize = 12.sp, color = Color.White.copy(0.7f))
                }
            }
            errorMsg != null -> {
                Text(errorMsg!!, color = Color.Red, modifier = Modifier.padding(16.dp))
            }
            isMedia && exoPlayer != null -> {
                VideoPlayer(exoPlayer!!, isFullscreen = true)
            }
            isImage && previewFileObject != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(previewFileObject)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
            isText && previewText != null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        previewText!!,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(getFileIcon(name), fontSize = 64.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Pratinjau tidak tersedia", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}
