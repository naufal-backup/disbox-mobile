package com.disbox.mobile.ui.components

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDialog(filePath: String, fileId: String?, viewModel: DisboxViewModel, onClose: () -> Unit) {
    var expiryDays by remember { mutableStateOf<Long?>(7) }
    var permission by remember { mutableStateOf("download") }
    var generating by remember { mutableStateOf(false) }
    var generatedLink by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(viewModel.t("share_file")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("📄 ${filePath.split("/").last()}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                
                if (generatedLink == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(viewModel.t("valid_until"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1L to "days_1", 7L to "days_7", 30L to "days_30", null to "permanent").forEach { (days, key) ->
                                FilterChip(
                                    selected = expiryDays == days,
                                    onClick = { expiryDays = days },
                                    label = { Text(viewModel.t(key)) }
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(viewModel.t("permission"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = permission == "view",
                                onClick = { permission = "view" },
                                label = { Text(viewModel.t("view_only")) }
                            )
                            FilterChip(
                                selected = permission == "download",
                                onClick = { permission = "download" },
                                label = { Text(viewModel.t("download_perm")) }
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(viewModel.t("link_ready"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = generatedLink!!,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Disbox Link", generatedLink))
                                    android.widget.Toast.makeText(context, viewModel.t("copy_link"), android.widget.Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Default.ContentCopy, null) }
                            }
                        )
                        Text(viewModel.t("link_hint"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = {
            if (generatedLink == null) {
                Button(
                    onClick = {
                        generating = true
                        val expiresAt = if (expiryDays != null) System.currentTimeMillis() + expiryDays!! * 24 * 3600 * 1000 else null
                        viewModel.createShareLink(filePath, fileId, permission, expiresAt) { res ->
                            generatedLink = res["link"] as? String
                            generating = false
                        }
                    },
                    enabled = !generating
                ) {
                    if (generating) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White)
                    else Text(viewModel.t("generate_link"))
                }
            } else {
                Button(onClick = onClose) { Text(viewModel.t("done")) }
            }
        },
        dismissButton = {
            if (generatedLink == null) TextButton(onClick = onClose) { Text(viewModel.t("cancel")) }
        }
    )
}
