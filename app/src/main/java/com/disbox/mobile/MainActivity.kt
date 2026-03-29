package com.disbox.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.disbox.mobile.navigation.Screen
import com.disbox.mobile.ui.components.*
import com.disbox.mobile.ui.screens.*
import com.disbox.mobile.ui.theme.DisboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: DisboxViewModel = viewModel()
            val accentColorHex = viewModel.accentColor
            val accentColor = remember(accentColorHex) { 
                try { Color(android.graphics.Color.parseColor(accentColorHex)) } 
                catch (e: Exception) { Color(0xFF5865F2) } 
            }

            DisboxTheme(darkTheme = viewModel.theme == "dark", accentColor = accentColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!viewModel.isConnected) {
                        LoginScreen(viewModel)
                    } else {
                        MainNavigation(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun MainNavigation(viewModel: DisboxViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val exoPlayer = remember {
        ExoPlayer.Builder(viewModel.getApplication()).build()
    }
    
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Scaffold(
        bottomBar = {
            Column {
                if (viewModel.currentPlayingFile != null) {
                    MusicPlayerBar(exoPlayer, viewModel)
                }
                
                if (viewModel.transferProgress.isNotEmpty()) {
                    TransferPanel(viewModel.transferProgress, viewModel)
                }

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val items = mutableListOf(
                        Triple(Screen.Drive.route, viewModel.t("drive"), Icons.Default.Cloud),
                        Triple(Screen.Starred.route, viewModel.t("starred"), Icons.Default.Star)
                    )
                    if (viewModel.showRecent) items.add(Triple(Screen.Recent.route, viewModel.t("recent"), Icons.Default.History))
                    items.add(Triple(Screen.Locked.route, viewModel.t("locked"), Icons.Default.Lock))
                    if (viewModel.cloudSaveEnabled) items.add(Triple(Screen.CloudSave.route, viewModel.t("cloud_save_nav"), Icons.Default.Backup))
                    if (viewModel.shareEnabled) items.add(Triple(Screen.Shared.route, viewModel.t("shared"), Icons.Default.Link))
                    items.add(Triple(Screen.Settings.route, viewModel.t("settings"), Icons.Default.Settings))

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
