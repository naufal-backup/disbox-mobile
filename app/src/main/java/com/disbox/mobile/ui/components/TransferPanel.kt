package com.disbox.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel

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
