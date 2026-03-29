package com.disbox.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel
import com.disbox.mobile.model.DisboxFile
import com.disbox.mobile.utils.formatSize

@Composable
fun ListFileItem(
    file: DisboxFile?,
    name: String,
    isFolder: Boolean,
    size: Long,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isLocked: Boolean,
    isStarred: Boolean,
    zoom: Float,
    viewModel: DisboxViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    BaseFileItem(
        file = file,
        isFolder = isFolder,
        isSelected = isSelected,
        isSelectionMode = isSelectionMode,
        isLocked = isLocked,
        isStarred = isStarred,
        zoom = zoom,
        viewModel = viewModel,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth().height(64.dp * zoom).padding(horizontal = 12.dp, vertical = 2.dp)
    ) { thumbSize ->
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox space if in selection mode handled by BaseFileItem overlays for now, 
            // but for List we might want it inline. 
            // Let's stick to the current design which has it inline.
            if (isSelectionMode) {
                Spacer(Modifier.width(30.dp)) // Reserve space for checkbox overlay
            }
            
            FileThumbnailWrapper(file, isFolder, isLocked, zoom, viewModel, thumbSize)
            Spacer(modifier = Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp * zoom
                )
                Text(
                    if (isFolder) viewModel.t("folder") else formatSize(size),
                    fontSize = 11.sp * zoom,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
