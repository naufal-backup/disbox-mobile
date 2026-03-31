package com.disbox.mobile.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel
import com.disbox.mobile.utils.getFileIcon

@Composable
fun SharedScreen(viewModel: DisboxViewModel) {
    var showRevokeAllConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Dibagikan oleh Saya", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (viewModel.shareLinks.isNotEmpty()) {
                    TextButton(
                        onClick = { showRevokeAllConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Revoke Semua")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (viewModel.shareLinks.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LinkOff, null, Modifier.size(64.dp), Color.Gray.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text("Belum ada link bersama", fontWeight = FontWeight.Bold)
                        Text("Klik kanan/tahan file untuk membagikan.", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        } else {
            items(viewModel.shareLinks) { link ->
                val fileName = link.file_path.split("/").last()
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape), Alignment.Center) {
                            Text(getFileIcon(fileName), fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (link.permission == "download") "Download" else "Lihat Saja",
                                    fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                                )
                                val expiryText = if (link.expires_at == null) "Permanen"
                                    else {
                                        val diff = link.expires_at - System.currentTimeMillis()
                                        if (diff <= 0) "Expired"
                                        else "Sisa hari lagi"
                                    }
                                Text(expiryText, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                        IconButton(onClick = {
                            val fullUrl = "${viewModel.cfWorkerUrl}/share/${link.token}"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Disbox Link", fullUrl))
                            android.widget.Toast.makeText(context, "Link disalin!", android.widget.Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)) }
                        IconButton(onClick = { viewModel.revokeShareLink(link.id, link.token) }) {
                            Icon(Icons.Default.LinkOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showRevokeAllConfirm) {
        AlertDialog(
            onDismissRequest = { showRevokeAllConfirm = false },
            title = { Text("Revoke Semua Link?") },
            text = { Text("Semua link yang telah dibagikan tidak akan bisa diakses lagi.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.revokeAllLinks(); showRevokeAllConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Ya, Revoke Semua") }
            },
            dismissButton = { TextButton(onClick = { showRevokeAllConfirm = false }) { Text("Batal") } }
        )
    }
}
