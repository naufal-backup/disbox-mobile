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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier.fillMaxWidth()
    ) { thumbSize ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            FileThumbnailWrapper(file, isFolder, isLocked, zoom, viewModel, thumbSize)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp * zoom
                )
                Text(
                    if (isFolder) viewModel.t("folder") else formatSize(size),
                    fontSize = 10.sp * zoom,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
