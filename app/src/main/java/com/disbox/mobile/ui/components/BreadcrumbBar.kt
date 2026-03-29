package com.disbox.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
