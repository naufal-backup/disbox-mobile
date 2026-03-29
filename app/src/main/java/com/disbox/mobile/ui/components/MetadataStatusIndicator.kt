package com.disbox.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel

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
