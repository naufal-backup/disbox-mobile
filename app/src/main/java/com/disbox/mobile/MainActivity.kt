package com.disbox.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.disbox.mobile.data.DiscordDataSourceFactory
import com.disbox.mobile.data.repository.DisboxRepository
import com.disbox.mobile.navigation.Screen
import com.disbox.mobile.ui.components.*
import com.disbox.mobile.ui.screens.*
import com.disbox.mobile.ui.theme.DisboxMobileTheme
import com.disbox.mobile.utils.*
import com.disbox.mobile.model.DisboxFile

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: DisboxViewModel = viewModel()
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

@Composable
fun DisboxApp(viewModel: DisboxViewModel, onFinish: () -> Unit) {
    DisboxMobileTheme(darkTheme = viewModel.theme == "dark", accentColor = viewModel.accentColor) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!viewModel.isConnected) {
                if (viewModel.isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LoginScreen(viewModel)
                }
            } else {
                MainNavigation(viewModel, onFinish)
            }
        }
    }
}

@Composable
fun MainNavigation(viewModel: DisboxViewModel, onFinish: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    
    val musicPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    
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
                        2 -> { // Repeat All
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
            val repository = viewModel.repository
            val name = file.path.split("/").last()
            val ext = name.split(".").last().lowercase()
            val dataSourceFactory = DiscordDataSourceFactory(repository, file)
            
            val mimeType = when(ext) {
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "flac" -> "audio/flac"
                "ogg" -> "audio/ogg"
                "m4a" -> "audio/mp4"
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                else -> null
            }

            val mediaItem = MediaItem.Builder()
                .setUri("disbox-music://${file.id}.$ext")
                .setMimeType(mimeType)
                .build()
            
            val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            
            musicPlayer.setMediaSource(mediaSource)
            musicPlayer.prepare()
            musicPlayer.playWhenReady = true
        } else {
            musicPlayer.stop()
        }
    }

    BackHandler {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else if (viewModel.currentPath != "/") {
            val p = viewModel.currentPath.split("/").filter { it.isNotEmpty() }.dropLast(1).joinToString("/")
            viewModel.navigate(if (p.isEmpty()) "/" else "/$p")
        } else {
            onFinish()
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                if (viewModel.currentPlayingFile != null) {
                    MusicPlayerBar(musicPlayer, viewModel)
                }
                
                if (viewModel.transferProgress.isNotEmpty()) {
                    TransferPanel(viewModel.transferProgress, viewModel)
                }

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val items = mutableListOf(
                        Triple(Screen.Drive.route, "Drive", Icons.Default.Cloud),
                        Triple(Screen.Starred.route, "Starred", Icons.Default.Star)
                    )
                    if (viewModel.showRecent) items.add(Triple(Screen.Recent.route, "Recent", Icons.Default.History))
                    items.add(Triple(Screen.Locked.route, "Locked", Icons.Default.Lock))
                    if (viewModel.cloudSaveEnabled) items.add(Triple(Screen.CloudSave.route, "Cloud", Icons.Default.Backup))
                    if (viewModel.shareEnabled) items.add(Triple(Screen.Shared.route, "Shared", Icons.Default.Link))
                    items.add(Triple(Screen.Settings.route, "Settings", Icons.Default.Settings))

                    items.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            selected = currentRoute == route,
                            onClick = {
                                if (currentRoute != route) {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Drive.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Drive.route) { DriveScreen(viewModel) }
            composable(Screen.Recent.route) { DriveScreen(viewModel, isRecentView = true) }
            composable(Screen.Starred.route) { DriveScreen(viewModel, isStarredView = true) }
            composable(Screen.Locked.route) {
                if (viewModel.isVerified) DriveScreen(viewModel, isLockedView = true)
                else LockedGateway(viewModel)
            }
            composable(Screen.CloudSave.route) { CloudSaveScreen(viewModel) }
            composable(Screen.Shared.route) { SharedScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel) }
        }
    }
}
