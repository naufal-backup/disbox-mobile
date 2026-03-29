package com.disbox.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.disbox.mobile.DisboxViewModel
import com.disbox.mobile.model.DisboxFile
import com.disbox.mobile.utils.*
import java.io.File

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
