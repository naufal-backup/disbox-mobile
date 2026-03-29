package com.disbox.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.disbox.mobile.DisboxViewModel
import com.disbox.mobile.NotificationHelper
import com.disbox.mobile.utils.formatTime
import kotlinx.coroutines.delay
import java.io.File

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
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(0)
                if (pos != viewModel.playbackPosition) viewModel.playbackPosition = pos
                if (dur != viewModel.playbackDuration) viewModel.playbackDuration = dur
                if (dur > 0) {
                    val progress = pos.toFloat() / dur
                    viewModel.playbackProgress = progress
                    sliderValue = progress
                }
            }
            delay(1000)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
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
