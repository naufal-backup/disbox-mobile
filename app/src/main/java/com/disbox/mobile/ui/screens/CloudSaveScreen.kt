package com.disbox.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel
import java.io.File

@Composable
fun CloudSaveScreen(viewModel: DisboxViewModel) {
    val cloudSaveFolders = remember(viewModel.allFiles) {
        viewModel.allFiles
            .filter { it.path.startsWith("cloudsave/") }
            .map { it.path.split("/")[1] }
            .distinct()
            .sorted()
    }

    var folderToExport by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(viewModel.t("cloud_save"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
        }
        
        if (cloudSaveFolders.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudOff, null, Modifier.size(64.dp), Color.Gray.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text(viewModel.t("no_cloud_saves"), color = Color.Gray)
                    }
                }
            }
        } else {
            items(cloudSaveFolders) { folder ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, null, tint = Color(0xFFF0A500))
                        Spacer(Modifier.width(16.dp))
                        Text(folder, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { folderToExport = folder }) {
                            Icon(Icons.Default.Download, viewModel.t("export_zip"))
                        }
                        IconButton(onClick = { folderToDelete = folder }) {
                            Icon(Icons.Default.Delete, viewModel.t("delete"), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(viewModel.t("delete")) },
            text = { Text("${viewModel.t("hapus_item", mapOf("count" to "1"))} ($folderToDelete)?") },
            confirmButton = {
                Button(onClick = {
                    val name = folderToDelete!!
                    viewModel.deletePaths(listOf("cloudsave/$name"))
                    folderToDelete = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(viewModel.t("confirm")) }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text(viewModel.t("cancel")) } }
        )
    }

    if (folderToExport != null) {
        AlertDialog(
            onDismissRequest = { folderToExport = null },
            title = { Text(viewModel.t("export_zip")) },
            text = { Text("${viewModel.t("exporting")} $folderToExport...") },
            confirmButton = {
                Button(onClick = {
                    val name = folderToExport!!
                    folderToExport = null
                    viewModel.exportCloudSaveAsZip(name) { file: File? -> }
                }) { Text(viewModel.t("confirm")) }
            },
            dismissButton = { TextButton(onClick = { folderToExport = null }) { Text(viewModel.t("cancel")) } }
        )
    }
}
