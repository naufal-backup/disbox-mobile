package com.disbox.mobile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.disbox.mobile.ui.theme.DisboxMobileTheme
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun VideoPlayer(exoPlayer: ExoPlayer, isFullscreen: Boolean = false) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                setBackgroundColor(android.graphics.Color.BLACK)
                setPadding(0, 0, 0, 0)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

class MainActivity : ComponentActivity() {
    private val viewModel: DisboxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DisboxApp(viewModel, onFinish = { finish() })
        }
        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}

fun getFileIcon(name: String): String {
    val ext = name.split(".").last().lowercase()
    return when (ext) {
        "pdf" -> "📄"
        "mp4", "mov", "avi", "mkv" -> "🎬"
        "mp3", "wav", "flac", "ogg" -> "🎵"
        "jpg", "jpeg", "png", "gif", "webp", "svg" -> "🖼"
        "zip", "rar", "tar", "gz", "7z" -> "📦"
        "js", "ts", "jsx", "tsx", "py", "rs" -> "⚙️"
        "html" -> "🌐"
        "css" -> "🎨"
        "json" -> "📋"
        "doc", "docx", "txt", "md" -> "📝"
        "xls", "xlsx", "csv" -> "📊"
        else -> "📄"
    }
}

fun isImageFile(name: String): Boolean {
    val ext = name.split(".").last().lowercase()
    return ext in listOf("jpg", "jpeg", "png", "gif", "webp")
}

fun isPdfFile(name: String): Boolean {
    return name.split(".").last().lowercase() == "pdf"
}

fun isVideoFile(name: String): Boolean {
    val ext = name.split(".").last().lowercase()
    return listOf("mp4", "mkv", "mov", "avi", "webm").contains(ext)
}

fun isAudioFile(name: String): Boolean {
    val ext = name.split(".").last().lowercase()
    return listOf("mp3", "wav", "flac", "ogg", "m4a").contains(ext)
}

@Composable
fun FileThumbnail(file: DisboxFile, viewModel: DisboxViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val name = file.path.split("/").last()
    val isImage = isImageFile(name)
    val isVideo = isVideoFile(name)
    val isAudio = isAudioFile(name)
    var thumbFile by remember { mutableStateOf<File?>(null) }
    var audioArt by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val isPreviewEnabled = viewModel.showPreviews && (
        (isImage && viewModel.showImagePreviews) || (isVideo && viewModel.showVideoPreviews) || (isAudio && viewModel.showMusicPreviews)
    )

    val cacheKey = "thumb_${file.id}"
    val targetFile = File(context.cacheDir, cacheKey)

    LaunchedEffect(file.id, isPreviewEnabled) {
        if (!isPreviewEnabled) {
            thumbFile = null
            audioArt = null
            return@LaunchedEffect
        }
        if (targetFile.exists()) {
            thumbFile = targetFile
            if (isAudio && audioArt == null) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(targetFile.absolutePath)
                    val art = retriever.embeddedPicture
                    if (art != null) {
                        audioArt = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
                    }
                    retriever.release()
                } catch (e: Exception) { e.printStackTrace() }
            }
            return@LaunchedEffect
        }
        isLoading = true
        try {
            viewModel.api?.downloadFile(file, targetFile) { }
            if (targetFile.exists()) {
                thumbFile = targetFile
                if (isAudio) {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(targetFile.absolutePath)
                    val art = retriever.embeddedPicture
                    if (art != null) {
                        audioArt = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
                    }
                    retriever.release()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (thumbFile != null) {
            Box(contentAlignment = Alignment.Center) {
                if (isAudio && audioArt != null) {
                    androidx.compose.foundation.Image(
                        bitmap = audioArt!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbFile)
                            .decoderFactory(VideoFrameDecoder.Factory())
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                
                if (isVideo || isAudio) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (isVideo) Icons.Default.PlayArrow else Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        } else if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Text(getFileIcon(name), fontSize = 24.sp)
        }
    }
}

@Composable
fun MusicPlayerBar(exoPlayer: ExoPlayer, viewModel: DisboxViewModel) {
    val currentFile = viewModel.currentPlayingFile ?: return
    val context = LocalContext.current
    val fileName = currentFile.path.split("/").last()
    var songTitle by remember { mutableStateOf(fileName) }
    var albumArt by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf(0f) }
    val notificationHelper = remember { NotificationHelper(context) }
    
    LaunchedEffect(currentFile.id) {
        // Try to get title and art from metadata
        val cacheKey = "thumb_${currentFile.id}"
        val targetFile = File(context.cacheDir, cacheKey)
        if (targetFile.exists()) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(targetFile.absolutePath)
                val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val art = retriever.embeddedPicture
                if (art != null) {
                    albumArt = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
                } else {
                    albumArt = null
                }
                if (!title.isNullOrBlank()) {
                    songTitle = if (!artist.isNullOrBlank()) "$title - $artist" else title
                } else {
                    songTitle = fileName
                }
                retriever.release()
            } catch (e: Exception) { 
                songTitle = fileName
                albumArt = null
                e.printStackTrace() 
            }
        } else {
            songTitle = fileName
            albumArt = null
        }
    }

    LaunchedEffect(songTitle, viewModel.isPlaying, albumArt) {
        notificationHelper.showMediaNotification(songTitle, viewModel.isPlaying, albumArt)
    }

    DisposableEffect(Unit) {
        onDispose {
            notificationHelper.cancelMediaNotification()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying && !isSeeking) {
                viewModel.playbackPosition = exoPlayer.currentPosition
                viewModel.playbackDuration = exoPlayer.duration.coerceAtLeast(0)
                if (viewModel.playbackDuration > 0) {
                    viewModel.playbackProgress = viewModel.playbackPosition.toFloat() / viewModel.playbackDuration
                    sliderValue = viewModel.playbackProgress
                }
            }
            delay(500)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface, // Opaque background
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        key(currentFile.id) {
                            FileThumbnail(currentFile, viewModel, Modifier.fillMaxSize())
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = songTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatTime(viewModel.playbackPosition)} / ${formatTime(viewModel.playbackDuration)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            viewModel.updateRepeatMode((viewModel.repeatMode + 1) % 3)
                        }) {
                            Icon(
                                imageVector = when(viewModel.repeatMode) {
                                    1 -> Icons.Default.RepeatOne
                                    2 -> Icons.Default.Repeat
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat",
                                tint = if (viewModel.repeatMode > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }) {
                            Icon(
                                imageVector = if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (viewModel.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(onClick = {
                            exoPlayer.stop()
                            viewModel.currentPlayingFile = null
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Slider(
                    value = if (isSeeking) sliderValue else viewModel.playbackProgress,
                    onValueChange = {
                        isSeeking = true
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        val targetPos = (sliderValue * exoPlayer.duration).toLong()
                        exoPlayer.seekTo(targetPos)
                        isSeeking = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                )
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatSize(size: Long): String {
    return if (size >= 1024 * 1024) {
        "%.2f MB".format(size.toFloat() / (1024 * 1024))
    } else {
        "${size / 1024} KB"
    }
}

@Composable
fun MetadataStatusIndicator(status: String, viewModel: DisboxViewModel) {
    val (color, label, icon) = when (status) {
        "synced" -> Triple(Color(0xFF00D4AA), viewModel.t("synced"), Icons.Default.CheckCircle)
        "uploading" -> Triple(Color(0xFF5865F2), viewModel.t("syncing_items", mapOf("count" to "")).trim(), Icons.Default.Refresh)
        "dirty" -> Triple(Color(0xFFF0A500), viewModel.t("waiting_sync"), Icons.Default.History)
        "error" -> Triple(Color(0xFFED4245), viewModel.t("sync_error"), Icons.Default.Error)
        else -> Triple(Color.Gray, "", Icons.Default.Info)
    }
    if (label.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun BreadcrumbBar(currentPath: String, onNavigate: (String) -> Unit) {
    val parts = if (currentPath == "/") emptyList() else currentPath.trim('/').split("/").filter { it.isNotEmpty() }
    val scrollState = rememberScrollState()
    LaunchedEffect(currentPath) { scrollState.animateScrollTo(scrollState.maxValue) }
    Row(modifier = Modifier.horizontalScroll(scrollState), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Home, contentDescription = "Root",
            tint = if (parts.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp).clickable { onNavigate("/") }
        )
        if (parts.isNotEmpty()) {
            val visibleParts = when {
                parts.size <= 3 -> parts.mapIndexed { i, name -> i to name }
                else -> listOf(0 to parts.first(), -1 to "...", parts.size - 1 to parts.last())
            }
            visibleParts.forEach { (idx, name) ->
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                if (idx == -1) {
                    Text("...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 2.dp))
                } else {
                    val isLast = idx == parts.size - 1
                    val targetPath = "/" + parts.take(idx + 1).joinToString("/")
                    Text(
                        name, fontSize = 12.sp, fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 2.dp).then(if (!isLast) Modifier.clickable { onNavigate(targetPath) } else Modifier)
                    )
                }
            }
        }
    }
}

@Composable
fun DisboxApp(viewModel: DisboxViewModel, onFinish: () -> Unit) {
    val allTabIds = listOf("drive", "recent", "starred", "locked", "cloud-save", "shared", "settings")
    val allTabs = listOf(viewModel.t("drive"), viewModel.t("recent"), viewModel.t("starred"), viewModel.t("locked"), viewModel.t("cloud_save"), "Shared", viewModel.t("settings"))
    val allIcons = listOf(Icons.Default.Storage, Icons.Default.History, Icons.Default.Star, Icons.Default.Lock, Icons.Default.Cloud, Icons.Default.Link, Icons.Default.Settings)
    
    val filteredIndices = allTabIds.indices.filter { i -> 
        val id = allTabIds[i]
        (id != "recent" || viewModel.showRecent) && 
        (id != "cloud-save" || viewModel.cloudSaveEnabled) &&
        (id != "shared" || viewModel.shareEnabled)
    }
    val tabIds = filteredIndices.map { allTabIds[it] }
    val tabs = filteredIndices.map { allTabs[it] }
    val icons = filteredIndices.map { allIcons[it] }

    BackHandler {
        when {
            viewModel.activePage != "drive" -> viewModel.setPage("drive")
            viewModel.currentPath != "/" -> {
                val p = viewModel.currentPath.split("/").filter { it.isNotEmpty() }.dropLast(1).joinToString("/")
                viewModel.navigate(if (p.isEmpty()) "/" else "/$p")
            }
            else -> onFinish()
        }
    }
    DisboxMobileTheme(darkTheme = viewModel.theme == "dark", accentColor = viewModel.accentColor) {
        val context = LocalContext.current
        val musicPlayer = remember { ExoPlayer.Builder(context).build() }
        
        DisposableEffect(musicPlayer) {
            onDispose { musicPlayer.release() }
        }

        LaunchedEffect(musicPlayer) {
            musicPlayer.addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    viewModel.isPlaying = isPlaying
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_ENDED) {
                        when (viewModel.repeatMode) {
                            1 -> { // Repeat One
                                musicPlayer.seekTo(0)
                                musicPlayer.play()
                            }
                            2 -> { // Repeat All (for mobile, usually same as one if only one file, but let's just repeat for now)
                                musicPlayer.seekTo(0)
                                musicPlayer.play()
                            }
                            else -> {
                                viewModel.currentPlayingFile = null
                            }
                        }
                    }
                }
            })
        }

        LaunchedEffect(viewModel.currentPlayingFile) {
            val file = viewModel.currentPlayingFile
            if (file != null) {
                val api = viewModel.api
                if (api != null) {
                    val name = file.path.split("/").last()
                    val ext = name.split(".").last().lowercase()
                    val dataSourceFactory = DiscordDataSourceFactory(api, file)
                    val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri("disbox-music://${file.id}.$ext"))
                    musicPlayer.setMediaSource(mediaSource)
                    musicPlayer.prepare()
                    musicPlayer.playWhenReady = true
                }
            } else {
                musicPlayer.stop()
            }
        }

        if (!viewModel.isConnected && !viewModel.isLoading) LoginScreen(viewModel)
        else if (viewModel.isLoading && !viewModel.isConnected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(viewModel.t("connecting"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background, // Match background to prevent "bar" effect
                        tonalElevation = 0.dp
                    ) {
                        tabIds.forEachIndexed { index, id ->
                            NavigationBarItem(
                                icon = { Icon(icons[index], contentDescription = tabs[index]) },
                                label = { Text(tabs[index], fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                selected = viewModel.activePage == id,
                                onClick = { viewModel.setPage(id) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Column(Modifier.fillMaxSize()) {
                        // Content Area
                        Box(Modifier.weight(1f)) {
                            when (viewModel.activePage) {
                                "drive" -> DriveScreen(viewModel)
                                "recent" -> DriveScreen(viewModel, isRecentView = true)
                                "starred" -> DriveScreen(viewModel, isStarredView = true)
                                "locked" -> if (viewModel.isVerified) DriveScreen(viewModel, isLockedView = true) else LockedGateway(viewModel)
                                "cloud-save" -> CloudSaveScreen(viewModel)
                                "shared" -> SharedScreen(viewModel)
                                "settings" -> SettingsScreen(viewModel)
                                else -> PlaceholderScreen(viewModel.activePage)
                            }
                        }
                        
                        // Floating Music Bar Space (internal to scaffold body)
                        if (viewModel.currentPlayingFile != null) {
                            Box(Modifier.padding(bottom = 12.dp)) {
                                MusicPlayerBar(musicPlayer, viewModel)
                            }
                        }
                    }
                    
                    if (viewModel.isLoading) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(48.dp))
                        }
                    }
                    
                    if (viewModel.progressMap.isNotEmpty()) TransferPanel(viewModel.progressMap, viewModel)
                }
            }
        }
    }
}

@Composable
fun CloudSaveScreen(viewModel: DisboxViewModel) {
    val cloudSaveFolders = remember(viewModel.allFiles) {
        viewModel.allFiles
            .filter { it.path.startsWith("cloudsave/") }
            .map { it.path.split("/")[1] }
            .distinct()
            .sorted()
    }

    var folderToExport by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(viewModel.t("cloud_save"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
        }
        
        if (cloudSaveFolders.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudOff, null, Modifier.size(64.dp), Color.Gray.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text(viewModel.t("no_cloud_saves"), color = Color.Gray)
                    }
                }
            }
        } else {
            items(cloudSaveFolders) { folder ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, null, tint = Color(0xFFF0A500))
                        Spacer(Modifier.width(16.dp))
                        Text(folder, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { folderToExport = folder }) {
                            Icon(Icons.Default.Download, viewModel.t("export_zip"))
                        }
                        IconButton(onClick = { folderToDelete = folder }) {
                            Icon(Icons.Default.Delete, viewModel.t("delete"), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(viewModel.t("delete")) },
            text = { Text("${viewModel.t("hapus_item", mapOf("count" to "1"))} ($folderToDelete)?") },
            confirmButton = {
                Button(onClick = {
                    val name = folderToDelete!!
                    viewModel.deletePaths(listOf("cloudsave/$name"))
                    folderToDelete = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(viewModel.t("confirm")) }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text(viewModel.t("cancel")) } }
        )
    }

    if (folderToExport != null) {
        AlertDialog(
            onDismissRequest = { folderToExport = null },
            title = { Text(viewModel.t("export_zip")) },
            text = { Text("${viewModel.t("exporting")} $folderToExport...") },
            confirmButton = {
                Button(onClick = {
                    val name = folderToExport!!
                    folderToExport = null
                    viewModel.exportCloudSaveAsZip(name) { file: File? ->
                        // In a real app, we might want to show a success dialog or open the file
                    }
                }) { Text(viewModel.t("confirm")) }
            },
            dismissButton = { TextButton(onClick = { folderToExport = null }) { Text(viewModel.t("cancel")) } }
        )
    }
}

@Composable
fun LockedGateway(viewModel: DisboxViewModel) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var hasPin by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { viewModel.checkHasPin { hasPin = it; checking = false } }
    if (checking) return
    
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Lock, 
                    contentDescription = null, 
                    modifier = Modifier.size(64.dp), 
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                
                if (!hasPin) {
                    Text(viewModel.t("pin_not_set"), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        viewModel.t("pin_not_set_desc"), 
                        textAlign = TextAlign.Center, 
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.setPage("settings") },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { 
                        Text(viewModel.t("settings"), fontWeight = FontWeight.Bold) 
                    }
                } else {
                    Text(viewModel.t("locked_area"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        viewModel.t("locked_area_desc"), 
                        textAlign = TextAlign.Center, 
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(32.dp))
                    
                    OutlinedTextField(
                        value = pin, 
                        onValueChange = { if (it.length <= 8) pin = it },
                        label = { Text("Master PIN") }, 
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), 
                        shape = RoundedCornerShape(16.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, 
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        singleLine = true
                    )
                    
                    if (error.isNotEmpty()) {
                        Text(
                            error, 
                            color = MaterialTheme.colorScheme.error, 
                            fontSize = 12.sp, 
                            modifier = Modifier.padding(top = 12.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    
                    Button(
                        onClick = { 
                            viewModel.verifyPin(pin) { 
                                if (!it) { 
                                    error = viewModel.t("pin_error_wrong")
                                    pin = "" 
                                } 
                            } 
                        }, 
                        enabled = pin.length >= 4, 
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) { 
                        Icon(Icons.Default.Key, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(viewModel.t("unlock_access"), fontWeight = FontWeight.Bold) 
                    }
                }
            }
        }
    }
}

@Composable
fun PinPromptModal(title: String, onVerified: () -> Unit, onCancel: () -> Unit, viewModel: DisboxViewModel) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it }, label = { Text(viewModel.t("pin_current_placeholder")) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                )
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = { viewModel.verifyPin(pin) { if (it) onVerified() else { error = viewModel.t("pin_error_wrong"); pin = "" } } }) { Text(viewModel.t("confirm")) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(viewModel.t("cancel")) } }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(viewModel: DisboxViewModel) {
    var url by remember { mutableStateOf(viewModel.webhookUrl) }
    var msgId by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary), Alignment.Center) {
            Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(24.dp)); Text("Disbox", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        Text(viewModel.t("subtitle"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        
        if (viewModel.savedWebhooks.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Text(viewModel.t("saved_webhooks_count", mapOf("count" to viewModel.savedWebhooks.size.toString())), 
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, 
                modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            
            viewModel.savedWebhooks.forEach { savedUrl ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
                        onClick = { url = savedUrl; viewModel.connect(savedUrl) },
                        onLongClick = { viewModel.removeWebhook(savedUrl) }
                    ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            savedUrl.take(40) + if(savedUrl.length > 40) "..." else "", 
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if(url == savedUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(viewModel.t("webhook_url")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
        
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Hide Advanced" else "Advanced Options (Manual Sync)", fontSize = 12.sp)
            }
        }
        
        if (showAdvanced) {
            OutlinedTextField(
                value = msgId, 
                onValueChange = { msgId = it }, 
                label = { Text("Metadata Message ID (Optional)") }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("e.g. 123456789012345678") }
            )
            Text("Use this if your file list is empty or doesn't sync automatically.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(16.dp))
        }

        Button(onClick = { viewModel.connect(url, msgId.ifBlank { null }) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(10.dp), enabled = !viewModel.isLoading) {
            if (viewModel.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text(viewModel.t("connect_drive"), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FolderSelectionDialog(
    allFiles: List<DisboxFile>,
    viewModel: DisboxViewModel,
    onFolderSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val folders = remember(allFiles) {
        val set = mutableSetOf("/")
        allFiles.forEach { f ->
            val parts = f.path.split("/").filter { it.isNotEmpty() }
            var current = ""
            parts.dropLast(1).forEach { p ->
                current = if (current.isEmpty()) p else "$current/$p"
                set.add("/$current")
            }
            if (f.path.endsWith(".keep")) {
                set.add("/" + f.path.removeSuffix("/.keep"))
            }
        }
        set.toList().sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(viewModel.t("pilih_tujuan")) },
        text = {
            Box(Modifier.heightIn(max = 300.dp)) {
                LazyColumn {
                    items(folders) { folder ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onFolderSelected(folder) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, null, tint = Color(0xFFF0A500), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(folder, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(viewModel.t("cancel")) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(viewModel: DisboxViewModel, isLockedView: Boolean = false, isStarredView: Boolean = false, isRecentView: Boolean = false) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.uploadFiles(uris)
    }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var previewFile by remember { mutableStateOf<DisboxFile?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<List<String>?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var itemToRename by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var pinPrompt by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showFolderPickerForUnlock by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSelectionMenu by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val processed = remember(viewModel.allFiles, viewModel.currentPath, isLockedView, isStarredView, isRecentView, viewModel.sortMode) {
        val fileList = mutableListOf<DisboxFile>(); val folderList = mutableListOf<Pair<String, String>>()
        val dirPath = viewModel.currentPath.trim('/')
        val folderLockStatus = mutableMapOf<String, Pair<Int, Int>>()
        viewModel.allFiles.forEach { f ->
            val parts = f.path.split("/").filter { it.isNotEmpty() }; var temp = ""
            parts.dropLast(1).forEach { p ->
                temp = if (temp.isEmpty()) p else "$temp/$p"
                val s = folderLockStatus.getOrPut(temp) { 0 to 0 }
                folderLockStatus[temp] = (s.first + 1) to (s.second + (if (f.isLocked) 1 else 0))
            }
        }
        viewModel.allFiles.forEach { f ->
            val parts = f.path.split("/").filter { it.isNotEmpty() }; val name = parts.last()
            var shouldIncludeFile = false
            when {
                isStarredView -> if (f.isStarred && !f.isLocked && name != ".keep") shouldIncludeFile = true
                isRecentView -> if ((System.currentTimeMillis() - f.createdAt) < 7*24*3600*1000 && !f.isLocked && name != ".keep") shouldIncludeFile = true
                isLockedView -> if (f.isLocked && name != ".keep") shouldIncludeFile = true
                else -> if (!f.isLocked && name != ".keep") shouldIncludeFile = true
            }
            if (shouldIncludeFile) {
                val fDir = parts.dropLast(1).joinToString("/"); if (isStarredView || isRecentView || fDir == dirPath) fileList.add(f)
            }
            var currentAcc = ""
            parts.dropLast(1).forEach { dirName ->
                val parentPath = currentAcc; currentAcc = if (currentAcc.isEmpty()) dirName else "$currentAcc/$dirName"
                val status = folderLockStatus[currentAcc]; val folderIsLocked = status != null && status.first > 0 && status.first == status.second
                var shouldIncludeDir = false
                when {
                    isStarredView -> if (name == ".keep" && f.isStarred && f.path.removeSuffix("/.keep") == currentAcc) shouldIncludeDir = true
                    isRecentView -> shouldIncludeDir = false
                    isLockedView -> if (folderIsLocked) shouldIncludeDir = true
                    else -> if (!folderIsLocked) shouldIncludeDir = true
                }
                if (shouldIncludeDir && (isStarredView || parentPath == dirPath)) folderList.add(dirName to currentAcc)
            }
        }
        
        val sortedFolders = folderList.distinctBy { it.second }.sortedWith { a, b ->
            when (viewModel.sortMode) {
                "name" -> a.first.lowercase().compareTo(b.first.lowercase())
                else -> a.first.lowercase().compareTo(b.first.lowercase())
            }
        }
        
        val sortedFiles = fileList.filter { !it.path.endsWith(".keep") }.sortedWith { a, b ->
            if (isRecentView) {
                b.createdAt.compareTo(a.createdAt)
            } else {
                when (viewModel.sortMode) {
                    "name" -> a.path.split("/").last().lowercase().compareTo(b.path.split("/").last().lowercase())
                    "date" -> b.createdAt.compareTo(a.createdAt)
                    "size" -> b.size.compareTo(a.size)
                    else -> 0
                }
            }
        }
        sortedFolders to sortedFiles
    }
    val folders = processed.first; val currentFiles = processed.second

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (viewModel.selectionSet.isNotEmpty()) {
                            Text("${viewModel.selectionSet.size} ${viewModel.t("terpilih")}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        } else if (isStarredView) {
                            Text(viewModel.t("starred"), Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                        } else if (isRecentView) {
                            Text(viewModel.t("recent"), Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                        } else {
                            BreadcrumbBar(viewModel.currentPath) { viewModel.navigate(it) }
                        }
                    }
                    
                    if (viewModel.selectionSet.isNotEmpty()) {
                        val selectedItems = viewModel.allFiles.filter { f: DisboxFile ->
                            viewModel.selectionSet.contains(f.id) || 
                            viewModel.selectionSet.contains(f.path) || 
                            (f.path.endsWith("/.keep") && viewModel.selectionSet.contains(f.path.removeSuffix("/.keep"))) ||
                            (f.path == ".keep" && viewModel.selectionSet.contains(""))
                        }
                        val isSingleSelect = viewModel.selectionSet.size == 1
                        val firstFile = selectedItems.find { !it.path.endsWith(".keep") }
                        
                        val allStarred = selectedItems.isNotEmpty() && selectedItems.all { it.isStarred }
                        val allLocked = selectedItems.isNotEmpty() && selectedItems.all { it.isLocked }

                        Box {
                            IconButton(onClick = { showSelectionMenu = true }) {
                                Icon(Icons.Default.MoreVert, null)
                            }
                            DropdownMenu(
                                expanded = showSelectionMenu,
                                onDismissRequest = { showSelectionMenu = false }
                            ) {
                                if (isSingleSelect && selectedItems.isNotEmpty()) {
                                    val item = selectedItems.first()
                                    val isFolder = item.path.endsWith("/.keep") || item.path == ".keep"
                                    
                                    if (!isFolder) {
                                        DropdownMenuItem(
                                            text = { Text(viewModel.t("download")) },
                                            leadingIcon = { Icon(Icons.Default.Download, null) },
                                            onClick = {
                                                showSelectionMenu = false
                                                viewModel.downloadFile(item)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(viewModel.t("rename")) },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                        onClick = {
                                            showSelectionMenu = false
                                            val oldPath = if (isFolder) item.path.removeSuffix("/.keep").ifEmpty { "" } else item.path
                                            val oldName = if (oldPath.isEmpty()) "/" else oldPath.split("/").last()
                                            
                                            itemToRename = oldName to oldPath
                                            newName = oldName
                                            showRenameDialog = true
                                        }
                                    )
                                    if (viewModel.shareEnabled) {
                                        DropdownMenuItem(
                                            text = { Text("Share") },
                                            leadingIcon = { Icon(Icons.Default.Link, null) },
                                            onClick = {
                                                showSelectionMenu = false
                                                showShareDialog = item.path to item.id
                                            }
                                        )
                                    }
                                }
                                DropdownMenuItem(
                                    text = { Text(if (allStarred) viewModel.t("unstar") else viewModel.t("star")) },
                                    leadingIcon = { Icon(if (allStarred) Icons.Default.StarBorder else Icons.Default.Star, null) },
                                    onClick = {
                                        showSelectionMenu = false
                                        viewModel.toggleBulkStatus(viewModel.selectionSet, isStarred = !allStarred)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (allLocked) viewModel.t("unlock") else viewModel.t("lock")) },
                                    leadingIcon = { Icon(if (isLockedView || allLocked) Icons.Default.LockOpen else Icons.Default.Lock, null) },
                                    onClick = {
                                        showSelectionMenu = false
                                        if (isLockedView) showFolderPickerForUnlock = true 
                                        else viewModel.toggleBulkStatus(viewModel.selectionSet, isLocked = !allLocked)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(viewModel.t("move")) },
                                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                                    onClick = {
                                        showSelectionMenu = false
                                        viewModel.startMove(viewModel.selectionSet)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(viewModel.t("copy")) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                    onClick = {
                                        showSelectionMenu = false
                                        viewModel.startCopy(viewModel.selectionSet)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(viewModel.t("delete")) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showSelectionMenu = false
                                        showDeleteConfirm = viewModel.selectionSet.toList()
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.clearSelection() }) { Icon(Icons.Default.Close, null) }
                    } else {
                        MetadataStatusIndicator(viewModel.metadataStatus, viewModel)
                        IconButton(onClick = { viewModel.setView(if (viewModel.viewMode == "grid") "list" else "grid") }) { 
                            Icon(if (viewModel.viewMode == "grid") Icons.Default.List else Icons.Default.GridView, null) 
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Inline Sort Options
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        listOf("name" to Icons.Default.SortByAlpha, "date" to Icons.Default.Schedule, "size" to Icons.Default.Scale).forEach { (mode, icon) ->
                            val isSelected = viewModel.sortMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateSortMode(mode) },
                                label = { Text(viewModel.t("sort_$mode"), fontSize = 10.sp) },
                                leadingIcon = { Icon(icon, null, Modifier.size(14.dp)) },
                                modifier = Modifier.padding(end = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    // Zoom Slider
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ZoomIn, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.width(100.dp), contentAlignment = Alignment.Center) {
                            Slider(
                                value = viewModel.zoomLevel,
                                onValueChange = { viewModel.setZoom(it) },
                                valueRange = 0.6f..1.5f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        },
        floatingActionButton = {
            if (!isStarredView && !isRecentView && !isLockedView) {
                Column(horizontalAlignment = Alignment.End) {
                    if (viewModel.moveCopyMode != null) {
                        ExtendedFloatingActionButton(onClick = { viewModel.paste(viewModel.currentPath) }, icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) }, text = { Text(viewModel.t("save")) })
                    } else {
                        SmallFloatingActionButton(onClick = { showCreateFolderDialog = true }, modifier = Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        FloatingActionButton(onClick = { filePicker.launch("*/*") }) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) }
                    }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            if (folders.isEmpty() && currentFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text(viewModel.t("empty_folder"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) }
            } else if (viewModel.viewMode == "grid") {
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = (100.dp * viewModel.zoomLevel)), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folders) { (name, path) -> FolderItemGrid(name, path, viewModel) {
                        if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(path)
                        else {
                            val status = viewModel.allFiles.filter { it.path.startsWith("$path/") }; val isLocked = status.isNotEmpty() && status.all { it.isLocked }
                            if (isLocked && !viewModel.isVerified) pinPrompt = { viewModel.navigate("/$path") } else viewModel.navigate("/$path")
                        }
                    } }
                    items(currentFiles) { f -> FileItemGrid(f, viewModel) {
                        if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(f.id)
                        else if (f.isLocked && !viewModel.isVerified) pinPrompt = { 
                            if (isAudioFile(f.path)) viewModel.currentPlayingFile = f else previewFile = f 
                        } else {
                            if (isAudioFile(f.path)) viewModel.currentPlayingFile = f else previewFile = f
                        }
                    } }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(folders) { (name, path) -> FolderItemList(name, path, viewModel) {
                        if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(path)
                        else {
                            val status = viewModel.allFiles.filter { it.path.startsWith("$path/") }; val isLocked = status.isNotEmpty() && status.all { it.isLocked }
                            if (isLocked && !viewModel.isVerified) pinPrompt = { viewModel.navigate("/$path") } else viewModel.navigate("/$path")
                        }
                    } }
                    items(currentFiles) { f -> FileItemList(f, viewModel) {
                        if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(f.id)
                        else if (f.isLocked && !viewModel.isVerified) pinPrompt = { 
                            if (isAudioFile(f.path)) viewModel.currentPlayingFile = f else previewFile = f 
                        } else {
                            if (isAudioFile(f.path)) viewModel.currentPlayingFile = f else previewFile = f
                        }
                    } }
                }
            }
        }
        
        if (pinPrompt != null) PinPromptModal(viewModel.t("locked_area"), { val a = pinPrompt; pinPrompt = null; a?.invoke() }, { pinPrompt = null }, viewModel)
        
        if (showFolderPickerForUnlock) {
            FolderSelectionDialog(
                allFiles = viewModel.allFiles,
                viewModel = viewModel,
                onFolderSelected = { dest ->
                    viewModel.unlockTo(viewModel.selectionSet, dest)
                    showFolderPickerForUnlock = false
                },
                onDismiss = { showFolderPickerForUnlock = false }
            )
        }
    }
    if (showCreateFolderDialog) {
        AlertDialog(onDismissRequest = { showCreateFolderDialog = false }, title = { Text(viewModel.t("new_folder")) }, text = { OutlinedTextField(folderName, { folderName = it }, label = { Text(viewModel.t("folder_name_placeholder")) }) },
            confirmButton = { Button(onClick = { if (folderName.isNotBlank()) { viewModel.createFolder(folderName); folderName = ""; showCreateFolderDialog = false } }) { Text(viewModel.t("create")) } },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text(viewModel.t("cancel")) } })
    }
    if (showDeleteConfirm != null) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = null }, title = { Text(viewModel.t("delete")) }, text = { Text(viewModel.t("hapus_item", mapOf("count" to showDeleteConfirm!!.size.toString()))) },
            confirmButton = { Button(onClick = { viewModel.deletePaths(showDeleteConfirm!!); showDeleteConfirm = null }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text(viewModel.t("confirm")) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text(viewModel.t("cancel")) } })
    }
    if (showRenameDialog && itemToRename != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(viewModel.t("rename")) },
            text = { OutlinedTextField(newName, { newName = it }, label = { Text(viewModel.t("folder_name_placeholder")) }) },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank() && newName != itemToRename!!.first) {
                        val target = itemToRename!!.second!!
                        val isId = target.contains("-") && target.length > 30
                        viewModel.renamePath(target, newName, if (isId) target else null)
                    }
                    showRenameDialog = false
                }) { Text(viewModel.t("save")) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text(viewModel.t("cancel")) } }
        )
    }
    if (showShareDialog != null) {
        ShareDialog(
            filePath = showShareDialog!!.first,
            fileId = showShareDialog!!.second,
            viewModel = viewModel,
            onClose = { showShareDialog = null }
        )
    }
    if (previewFile != null) {
        FilePreviewScreen(
            file = previewFile!!,
            allFiles = currentFiles,
            viewModel = viewModel,
            onFileChange = { previewFile = it },
            onClose = { previewFile = null }
        )
    }
}

@Composable
fun ShareDialog(filePath: String, fileId: String?, viewModel: DisboxViewModel, onClose: () -> Unit) {
    var expiryDays by remember { mutableStateOf<Long?>(7) }
    var permission by remember { mutableStateOf("download") }
    var generating by remember { mutableStateOf(false) }
    var generatedLink by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(viewModel.t("share_file")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("📄 ${filePath.split("/").last()}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                
                if (generatedLink == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(viewModel.t("valid_until"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1L to "days_1", 7L to "days_7", 30L to "days_30", null to "permanent").forEach { (days, key) ->
                                FilterChip(
                                    selected = expiryDays == days,
                                    onClick = { expiryDays = days },
                                    label = { Text(viewModel.t(key)) }
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(viewModel.t("permission"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = permission == "view",
                                onClick = { permission = "view" },
                                label = { Text(viewModel.t("view_only")) }
                            )
                            FilterChip(
                                selected = permission == "download",
                                onClick = { permission = "download" },
                                label = { Text(viewModel.t("download_perm")) }
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(viewModel.t("link_ready"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = generatedLink!!,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Disbox Link", generatedLink))
                                    android.widget.Toast.makeText(context, viewModel.t("copy_link"), android.widget.Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Default.ContentCopy, null) }
                            }
                        )
                        Text(viewModel.t("link_hint"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = {
            if (generatedLink == null) {
                Button(
                    onClick = {
                        generating = true
                        val expiresAt = if (expiryDays != null) System.currentTimeMillis() + expiryDays!! * 24 * 3600 * 1000 else null
                        viewModel.createShareLink(filePath, fileId, permission, expiresAt) { res ->
                            generatedLink = res["link"] as? String
                            generating = false
                        }
                    },
                    enabled = !generating
                ) {
                    if (generating) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White)
                    else Text(viewModel.t("generate_link"))
                }
            } else {
                Button(onClick = onClose) { Text(viewModel.t("done")) }
            }
        },
        dismissButton = {
            if (generatedLink == null) TextButton(onClick = onClose) { Text(viewModel.t("cancel")) }
        }
    )
}

@Composable
fun SharedScreen(viewModel: DisboxViewModel) {
    var showRevokeAllConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(viewModel.t("shared_by_me"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (viewModel.shareLinks.isNotEmpty()) {
                    TextButton(
                        onClick = { showRevokeAllConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(viewModel.t("revoke_all"))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (viewModel.shareLinks.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LinkOff, null, Modifier.size(64.dp), Color.Gray.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text(viewModel.t("no_shared_links"), fontWeight = FontWeight.Bold)
                        Text(viewModel.t("shared_hint"), fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        } else {
            items(viewModel.shareLinks) { link ->
                val fileName = link.file_path.split("/").last()
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape), Alignment.Center) {
                            Text(getFileIcon(fileName), fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (link.permission == "download") viewModel.t("download_perm") else viewModel.t("view_only"),
                                    fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                                )
                                val expiryText = if (link.expires_at == null) viewModel.t("permanent")
                                    else {
                                        val diff = link.expires_at - System.currentTimeMillis()
                                        if (diff <= 0) viewModel.t("expired")
                                        else viewModel.t("days_left", mapOf("days" to (Math.ceil(diff.toDouble() / (24 * 3600 * 1000)).toInt().toString())))
                                    }
                                Text(expiryText, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                        IconButton(onClick = {
                            val fullUrl = "${viewModel.cfWorkerUrl}/share/${link.token}"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Disbox Link", fullUrl))
                            android.widget.Toast.makeText(context, viewModel.t("copy_link"), android.widget.Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)) }
                        IconButton(onClick = { viewModel.revokeShareLink(link.id, link.token) }) {
                            Icon(Icons.Default.LinkOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showRevokeAllConfirm) {
        AlertDialog(
            onDismissRequest = { showRevokeAllConfirm = false },
            title = { Text(viewModel.t("revoke_all_confirm")) },
            text = { Text(viewModel.t("revoke_all_desc")) },
            confirmButton = {
                Button(
                    onClick = { viewModel.revokeAllLinks(); showRevokeAllConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(viewModel.t("confirm")) }
            },
            dismissButton = { TextButton(onClick = { showRevokeAllConfirm = false }) { Text(viewModel.t("cancel")) } }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FilePreviewScreen(
    file: DisboxFile,
    allFiles: List<DisboxFile> = emptyList(),
    viewModel: DisboxViewModel,
    onFileChange: (DisboxFile) -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
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
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialIndex) {
        navigatableFiles.size
    }

    LaunchedEffect(pagerState.currentPage) {
        onFileChange(navigatableFiles[pagerState.currentPage])
    }

    BackHandler(onBack = onClose)

    var showShareDialogByPreview by remember { mutableStateOf<Pair<String, String?>?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(Modifier.fillMaxSize()) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 0.dp,
                userScrollEnabled = true
            ) { pageIndex ->
                val currentFile = navigatableFiles[pageIndex]
                val isActive = pagerState.currentPage == pageIndex
                MediaPreviewItem(currentFile, viewModel, isActive)
            }

            // Top Bar
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
                // Use custom DataSource for seamless streaming
                val api = viewModel.api
                if (api != null) {
                    val dataSourceFactory = DiscordDataSourceFactory(api, file)
                    val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri("disbox-stream://${file.id}.$ext"))
                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    previewVideoFile = File("stream") // Just to trigger the UI to show video player
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FolderItemGrid(name: String, path: String, viewModel: DisboxViewModel, onClick: () -> Unit) {
    val isSelected = viewModel.selectionSet.contains(path); val status = viewModel.allFiles.filter { it.path.startsWith("$path/") }
    val isLocked = status.isNotEmpty() && status.all { it.isLocked }; val isStarred = viewModel.allFiles.any { it.path == "$path/.keep" && it.isStarred }
    
    Box(Modifier.fillMaxWidth()) {
        GridFileItem(
            file = null, 
            name = name, 
            isFolder = true, 
            size = 0, 
            isSelected = isSelected, 
            isSelectionMode = viewModel.selectionSet.isNotEmpty(), 
            isLocked = isLocked, 
            isStarred = isStarred, 
            zoom = viewModel.zoomLevel, 
            viewModel = viewModel, 
            onLongClick = { viewModel.toggleSelection(path) }, 
            onClick = onClick
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileItemGrid(file: DisboxFile, viewModel: DisboxViewModel, onClick: () -> Unit) {
    val isSelected = viewModel.selectionSet.contains(file.id); val name = file.path.split("/").last()

    Box(Modifier.fillMaxWidth()) {
        GridFileItem(
            file = file, 
            name = name, 
            isFolder = false, 
            size = file.size, 
            isSelected = isSelected, 
            isSelectionMode = viewModel.selectionSet.isNotEmpty(), 
            isLocked = file.isLocked, 
            isStarred = file.isStarred, 
            zoom = viewModel.zoomLevel, 
            viewModel = viewModel, 
            onLongClick = { viewModel.toggleSelection(file.id) }, 
            onClick = onClick
        )
    }
}

@Composable
fun FolderItemList(name: String, path: String, viewModel: DisboxViewModel, onClick: () -> Unit) {
    val isSelected = viewModel.selectionSet.contains(path); val status = viewModel.allFiles.filter { it.path.startsWith("$path/") }
    val isLocked = status.isNotEmpty() && status.all { it.isLocked }; val isStarred = viewModel.allFiles.any { it.path == "$path/.keep" && it.isStarred }
    ListFileItem(null, name, true, 0, isSelected, viewModel.selectionSet.isNotEmpty(), isLocked, isStarred, viewModel.zoomLevel, viewModel, onClick, { viewModel.toggleSelection(path) })
}

@Composable
fun FileItemList(file: DisboxFile, viewModel: DisboxViewModel, onClick: () -> Unit) {
    ListFileItem(file, file.path.split("/").last(), false, file.size, viewModel.selectionSet.contains(file.id), viewModel.selectionSet.isNotEmpty(), file.isLocked, file.isStarred, viewModel.zoomLevel, viewModel, onClick, { viewModel.toggleSelection(file.id) })
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ListFileItem(file: DisboxFile?, name: String, isFolder: Boolean, size: Long = 0, isSelected: Boolean, isSelectionMode: Boolean, isLocked: Boolean = false, isStarred: Boolean = false, zoom: Float, viewModel: DisboxViewModel, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    Row(modifier = Modifier.fillMaxWidth().height(64.dp * zoom).padding(horizontal = 12.dp, vertical = 2.dp).clip(RoundedCornerShape(10.dp)).then(if(isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(10.dp)) else Modifier).combinedClickable(onClick = onClick, onLongClick = onLongClick).background(bgColor).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isSelectionMode) { Box(modifier = Modifier.size(18.dp * zoom).clip(RoundedCornerShape(4.dp)).border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent), contentAlignment = Alignment.Center) { if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp * zoom)) }; Spacer(Modifier.width(12.dp)) }
        Box(modifier = Modifier.size(36.dp * zoom).clip(RoundedCornerShape(8.dp)).background(if (isFolder) Color(0xFFF0A500).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            if (isFolder || file == null) Icon(if (isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile, contentDescription = null, tint = if (isFolder) Color(0xFFF0A500) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp * zoom))
            else FileThumbnail(file, viewModel, Modifier.fillMaxSize())
            if (isLocked) Box(modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp)) { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp * zoom)) }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp * zoom, modifier = Modifier.weight(1f, false))
                if (isStarred) { Spacer(modifier = Modifier.width(4.dp)); Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF0A500), modifier = Modifier.size(12.dp * zoom)) }
            }
            Text(if (isFolder) viewModel.t("folder") else formatSize(size), fontSize = 11.sp * zoom, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GridFileItem(file: DisboxFile?, name: String, isFolder: Boolean, size: Long = 0, isSelected: Boolean, isSelectionMode: Boolean, isLocked: Boolean = false, isStarred: Boolean = false, zoom: Float, viewModel: DisboxViewModel, onLongClick: () -> Unit, onClick: () -> Unit) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if(isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp)) else Modifier).combinedClickable(onClick = onClick, onLongClick = onLongClick).background(bgColor).padding(12.dp)) {
        if (isSelectionMode) Box(modifier = Modifier.align(Alignment.TopEnd).size(18.dp * zoom).clip(RoundedCornerShape(4.dp)).border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent), contentAlignment = Alignment.Center) { if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp * zoom)) }
        if (isStarred && !isSelectionMode) Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF0A500), modifier = Modifier.align(Alignment.TopEnd).size(14.dp * zoom))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.size(44.dp * zoom).clip(RoundedCornerShape(10.dp)).background(if (isFolder) Color(0xFFF0A500).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                if (isFolder || file == null) Icon(if (isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile, contentDescription = null, tint = if (isFolder) Color(0xFFF0A500) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp * zoom))
                else FileThumbnail(file, viewModel, Modifier.fillMaxSize())
                if (isLocked) Box(modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp)) { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp * zoom)) }
            }
            Spacer(Modifier.height(6.dp))
            Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 11.sp * zoom, textAlign = TextAlign.Center)
            Text(if (isFolder) viewModel.t("folder") else formatSize(size), fontSize = 9.sp * zoom, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun TransferPanel(progressMap: Map<String, Float>, viewModel: DisboxViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 200.dp), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(viewModel.t("transfers"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                progressMap.forEach { (name, p) -> item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(name, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (p >= 1f) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00D4AA), modifier = Modifier.size(14.dp)); Text(viewModel.t("transfers_done", mapOf("count" to "")).trim(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00D4AA)) }
                        else { LinearProgressIndicator(progress = {p}, modifier = Modifier.width(80.dp).height(4.dp).clip(CircleShape)); Text("${(p * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    }
                } }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(viewModel: DisboxViewModel) {
    val CHUNK_OPTIONS = listOf(Triple("Free (10MB)", 10 * 1024 * 1024, viewModel.t("chunk_free_desc")), Triple("Nitro (25MB)", 25 * 1024 * 1024, viewModel.t("chunk_nitro_desc")), Triple("Premium (500MB)", 500 * 1024 * 1024, viewModel.t("chunk_premium_desc")))
    val currentIndex = CHUNK_OPTIONS.indexOfFirst { it.second == viewModel.chunkSize }.coerceAtLeast(0)
    var showDisconnectConfirm by remember { mutableStateOf(false) }; var hasPin by remember { mutableStateOf(false) }; var showPinDialog by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.checkHasPin { hasPin = it } }
    
    val totalSize = remember(viewModel.allFiles) { viewModel.allFiles.sumOf { it.size } }
    val formattedSize = remember(totalSize) { 
        val gb = totalSize.toDouble() / (1024 * 1024 * 1024)
        if (gb < 1.0) "${totalSize / (1024 * 1024)} MB"
        else "%.2f GB".format(gb)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(viewModel.t("settings"), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text(viewModel.t("storage"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                Text(formattedSize, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Discord Unlimited ∞", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text(viewModel.t("language"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("id" to "ID", "en" to "EN", "zh" to "ZH").forEach { (code, label) ->
                        Button(
                            onClick = { viewModel.updateLanguage(code) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.language == code) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text(label) }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text(viewModel.t("accent_color"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("#5865F2", "#00D4AA", "#F0A500", "#ED4245", "#EB459E", "#9B59B6").forEach { colorHex ->
                        val color = Color(android.graphics.Color.parseColor(colorHex))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(2.dp, if(viewModel.accentColor == colorHex) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .clickable { viewModel.updateAccentColor(colorHex) }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text(viewModel.t("app_behavior"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text(viewModel.t("dark"), fontWeight = FontWeight.Bold); Text(viewModel.t("theme"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.theme == "dark", { viewModel.toggleTheme() })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text(viewModel.t("previews"), fontWeight = FontWeight.Bold); Text(viewModel.t("previews_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.showPreviews, { viewModel.updatePreviews(it) })
                }
                if (viewModel.showPreviews) {
                    Column(Modifier.padding(start = 24.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(viewModel.t("image_previews"), fontSize = 13.sp)
                            Switch(viewModel.showImagePreviews, { viewModel.updateImagePreviews(it) }, modifier = Modifier.scale(0.8f))
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(viewModel.t("video_previews"), fontSize = 13.sp)
                            Switch(viewModel.showVideoPreviews, { viewModel.updateVideoPreviews(it) }, modifier = Modifier.scale(0.8f))
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(viewModel.t("music_previews"), fontSize = 13.sp)
                            Switch(viewModel.showMusicPreviews, { viewModel.updateMusicPreviews(it) }, modifier = Modifier.scale(0.8f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text(viewModel.t("show_recent"), fontWeight = FontWeight.Bold); Text(viewModel.t("show_recent_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.showRecent, { viewModel.updateRecent(it) })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text(viewModel.t("cloud_save"), fontWeight = FontWeight.Bold); Text(viewModel.t("cloud_save_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.cloudSaveEnabled, { viewModel.updateCloudSaveEnabled(it) })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text(viewModel.t("animations"), fontWeight = FontWeight.Bold); Text(viewModel.t("animations_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.animationsEnabled, { viewModel.updateAnimationsEnabled(it) })
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text("Share & Privacy", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text("Enable Share", fontWeight = FontWeight.Bold); Text("Bagikan file via link ke siapapun", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.shareEnabled, { viewModel.saveShareSettings(it, viewModel.shareMode, viewModel.cfWorkerUrl) })
                }
                
                if (viewModel.shareEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text("Select Worker", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.6f), modifier = Modifier.padding(bottom = 8.dp))
                    
                    val workers = listOf(
                        "Main" to "https://disbox-shared-link.naufal-backup.workers.dev",
                        "New" to "https://disbox-shared-link.alamsyahnaufal453.workers.dev",
                        "Public #3" to "https://disbox-worker-2.naufal-backup.workers.dev",
                        "Public #4" to "https://disbox-worker-3.naufal-backup.workers.dev"
                    )
                    
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val currentLabel = workers.find { it.second == viewModel.cfWorkerUrl }?.first ?: "Custom"
                            Text(currentLabel, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            workers.forEach { (label, url) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.saveShareSettings(true, viewModel.shareMode, url)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text(viewModel.t("security"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Master PIN", fontWeight = FontWeight.Bold); Text(if (hasPin) viewModel.t("pin_active") else viewModel.t("pin_not_set_security"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    if (!hasPin) Button(onClick = { showPinDialog = "set" }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(viewModel.t("set_pin"), fontSize = 12.sp) }
                    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
                        OutlinedButton(onClick = { showPinDialog = "change" }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(viewModel.t("change_pin"), fontSize = 12.sp) }
                        OutlinedButton(onClick = { showPinDialog = "remove" }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), contentPadding = PaddingValues(horizontal = 12.dp)) { Text(viewModel.t("delete"), fontSize = 12.sp) }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text(viewModel.t("ui_scale"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(viewModel.zoomLevel, { viewModel.setZoom(it) }, valueRange = 0.6f..1.5f, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text("${(viewModel.zoomLevel * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text(viewModel.t("chunk_size"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Text("${CHUNK_OPTIONS[currentIndex].first} - ${CHUNK_OPTIONS[currentIndex].third}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(currentIndex.toFloat(), { viewModel.setChunk(CHUNK_OPTIONS[it.toInt()].second) }, valueRange = 0f..2f, steps = 1)
            }
        }

        if (viewModel.savedWebhooks.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(20.dp)) {
                    Text(viewModel.t("saved_webhooks_count", mapOf("count" to viewModel.savedWebhooks.size.toString())), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                    viewModel.savedWebhooks.forEach { savedUrl ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
                                onClick = { viewModel.connect(savedUrl) },
                                onLongClick = { viewModel.removeWebhook(savedUrl) }
                            ).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Link, null, tint = if(viewModel.webhookUrl == savedUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                savedUrl.take(30) + if(savedUrl.length > 30) "..." else "",
                                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if(viewModel.webhookUrl == savedUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (viewModel.webhookUrl == savedUrl) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text(viewModel.t("account"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Text("Webhook: ${viewModel.webhookUrl.take(20)}...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showDisconnectConfirm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text(viewModel.t("disconnect")) }
            }
        }
        
        Text("Disbox Mobile ${viewModel.latestVersion}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
        Spacer(Modifier.height(100.dp))
    }
    if (showPinDialog != null) PinSettingsDialog(showPinDialog!!, { showPinDialog = null; viewModel.checkHasPin { hasPin = it } }, viewModel)
    if (showDisconnectConfirm) AlertDialog(onDismissRequest = { showDisconnectConfirm = false }, title = { Text(viewModel.t("disconnect")) }, text = { Text(viewModel.t("confirm_disconnect")) }, confirmButton = { Button(onClick = { viewModel.disconnect(); showDisconnectConfirm = false }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text(viewModel.t("confirm")) } }, dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text(viewModel.t("cancel")) } })
}

@Composable
fun PinSettingsDialog(mode: String, onClose: () -> Unit, viewModel: DisboxViewModel) {
    var step by remember { mutableStateOf(if (mode == "set") "new" else "verify") }; var currentPin by remember { mutableStateOf("") }; var newPin by remember { mutableStateOf("") }; var confirmPin by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text(when(mode) { "set" -> viewModel.t("set_pin"); "change" -> viewModel.t("change_pin"); else -> viewModel.t("remove_pin") }) },
        text = { Column {
            if (step == "verify") OutlinedTextField(currentPin, { currentPin = it }, label = { Text(viewModel.t("pin_current_placeholder")) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword))
            else { OutlinedTextField(newPin, { newPin = it }, label = { Text(viewModel.t("pin_new_placeholder")) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)); OutlinedTextField(confirmPin, { confirmPin = it }, label = { Text(viewModel.t("pin_confirm_placeholder")) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)) }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { Button(onClick = { if (step == "verify") viewModel.verifyPin(currentPin) { if (it) { if (mode == "remove") viewModel.removePin { onClose() } else { step = "new"; error = "" } } else error = viewModel.t("pin_error_wrong") } else { if (newPin.length < 4) error = viewModel.t("pin_error_min_length"); else if (newPin != confirmPin) error = viewModel.t("pin_error_mismatch"); else viewModel.setPin(newPin) { onClose() } } }) { Text(if (step == "verify" && mode != "remove") viewModel.t("next") else viewModel.t("save")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(viewModel.t("cancel")) } })
}

@Composable
fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(name, fontWeight = FontWeight.Bold); Text("Coming Soon", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) } }
}
