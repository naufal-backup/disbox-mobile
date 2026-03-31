package com.disbox.mobile.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel
import com.disbox.mobile.model.DisboxFile

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BaseFileItem(
    file: DisboxFile?,
    isFolder: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isLocked: Boolean,
    isStarred: Boolean,
    zoom: Float,
    viewModel: DisboxViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (thumbSize: Dp) -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(10.dp)
    
    Box(
        modifier = modifier
            .clip(shape)
            .then(if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), shape) else Modifier)
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        content(if (isFolder) 44.dp * zoom else 36.dp * zoom)

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(18.dp * zoom)
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        1.5.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp * zoom))
            }
        } else if (isStarred) {
            Icon(
                Icons.Default.Star,
                null,
                tint = Color(0xFFF0A500),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(14.dp * zoom)
            )
        }

        // --- OPTIMISTIC OVERLAY ---
        if (file?.isOptimistic == true) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (file.progress > 0f) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(4.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { file.progress },
                            modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                        Text("${(file.progress * 100).toInt()}%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun FileThumbnailWrapper(
    file: DisboxFile?,
    isFolder: Boolean,
    isLocked: Boolean,
    zoom: Float,
    viewModel: DisboxViewModel,
    size: Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFolder) Color(0xFFF0A500).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        if (isFolder || file == null) {
            Icon(
                if (isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (isFolder) Color(0xFFF0A500) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size((size.value * 0.6f).dp)
            )
        } else {
            FileThumbnail(file, viewModel, Modifier.fillMaxSize())
        }
        
        if (isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size((size.value * 0.3f).dp)
                )
            }
        }
    }
}
