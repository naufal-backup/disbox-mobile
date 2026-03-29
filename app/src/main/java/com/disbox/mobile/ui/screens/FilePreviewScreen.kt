package com.disbox.mobile.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.style.TextOverflow
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
    val videoExts = listOf("mp4", "mkv", "mov", "avi", "webm")
    val textExts = listOf("txt", "md", "json", "js", "py", "rs", "html", "css", "xml", "yml", "yaml", "sql", "sh", "env")

    val navigatableFiles = remember(allFiles) {
        allFiles.filter { f ->
            val name = f.path.split("/").last()
            val fExt = name.split(".").last().lowercase()
            isImageFile(name) || videoExts.contains(fExt) || textExts.contains(fExt) || isPdfFile(name)
        }
    }

    val initialIndex = navigatableFiles.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) {
        navigatableFiles.size
    }

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
    var previewText by remember { mutableStateOf<String?>(null) }
    var previewImageFile by remember { mutableStateOf<File?>(null) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var previewVideoFile by remember { mutableStateOf<File?>(null) }
    var isDownloadingPreview by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    val videoExts = listOf("mp4", "mkv", "mov", "avi", "webm")
    val textExts = listOf("txt", "md", "json", "js", "py", "rs", "html", "css", "xml", "yml", "yaml", "sql", "sh", "env")
    val ext = name.split(".").last().lowercase()

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(file.id, isActive) {
        if (!isActive) return@LaunchedEffect
        
        isDownloadingPreview = false
        errorMsg = null
        previewImageFile = null
        previewPdfFile = null
        previewVideoFile = null
        previewText = null

        try {
            val cacheKey = "session_prev_${file.id}"
            val tempFile = File(context.cacheDir, cacheKey)
            val isMedia = videoExts.contains(ext) || ext == "mp3" || ext == "wav" || ext == "flac" || ext == "ogg"

            if (isMedia) {
                val api = viewModel.api
                if (api != null) {
                    val dataSourceFactory = DiscordDataSourceFactory(api, file)
                    val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri("disbox-stream://${file.id}.$ext"))
                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    previewVideoFile = File("stream")
                }
            } else {
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    isDownloadingPreview = true
                    viewModel.api?.downloadFile(file, tempFile) { }
                    isDownloadingPreview = false
                }

                if (tempFile.exists()) {
                    when {
                        isImageFile(name) -> { previewImageFile = tempFile }
                        isPdfFile(name) -> { previewPdfFile = tempFile }
                        textExts.contains(ext) -> { previewText = tempFile.readText() }
                    }
                }
            }
        } catch (e: Exception) {
            errorMsg = "Gagal memuat: ${e.message}"
            isDownloadingPreview = false
        }
    }

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        if (isDownloadingPreview) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text(viewModel.t("downloading_preview"), fontSize = 12.sp, color = Color.White.copy(0.7f))
            }
        } else if (errorMsg != null) {
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
        } else if (previewImageFile != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(previewImageFile).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        } else if (previewVideoFile != null) {
            VideoPlayer(exoPlayer, isFullscreen = true)
        } else if (previewText != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Text(
                    previewText!!,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.Black
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(getFileIcon(name), fontSize = 64.sp)
                Text(viewModel.t("no_preview"), color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}
