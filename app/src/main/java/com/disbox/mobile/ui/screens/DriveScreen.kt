package com.disbox.mobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel
import com.disbox.mobile.model.DisboxFile
import com.disbox.mobile.ui.components.*
import com.disbox.mobile.utils.isAudioFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(viewModel: DisboxViewModel, isLockedView: Boolean = false, isStarredView = false, isRecentView = false) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.uploadFiles(uris)
    }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var previewFile by remember { mutableStateOf<DisboxFile?>(null) }
    
    val processed = remember(viewModel.allFiles.toList(), viewModel.currentPath, isLockedView, isStarredView, isRecentView, viewModel.sortMode) {
        val fileList = mutableListOf<DisboxFile>()
        val folderList = mutableListOf<Pair<String, String>>()
        val dirPath = viewModel.currentPath.trim('/')
        
        viewModel.allFiles.forEach { f ->
            val parts = f.path.split("/").filter { it.isNotEmpty() }
            val name = parts.last()
            val fDir = parts.dropLast(1).joinToString("/")

            if (name == ".keep") {
                val currentAcc = f.path.removeSuffix("/.keep")
                val parentPath = currentAcc.split("/").dropLast(1).joinToString("/")
                val dirName = currentAcc.split("/").last()
                if (parentPath == dirPath) folderList.add(dirName to currentAcc)
            } else {
                if (fDir == dirPath) fileList.add(f)
            }
        }
        
        val sortedFolders = folderList.distinctBy { it.second }.sortedBy { it.first.lowercase() }
        val sortedFiles = fileList.sortedBy { it.path.lowercase() }
        sortedFolders to sortedFiles
    }
    val folders = processed.first
    val currentFiles = processed.second

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (viewModel.selectionSet.isNotEmpty()) {
                            Text("${viewModel.selectionSet.size} terpilih", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        } else {
                            BreadcrumbBar(viewModel.currentPath) { viewModel.navigate(it) }
                        }
                    }
                    IconButton(onClick = { viewModel.setView(if (viewModel.viewMode == "grid") "list" else "grid") }) { 
                        Icon(if (viewModel.viewMode == "grid") Icons.Default.List else Icons.Default.GridView, null) 
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        },
        floatingActionButton = {
            if (!isStarredView && !isRecentView && !isLockedView) {
                Column(horizontalAlignment = Alignment.End) {
                    SmallFloatingActionButton(onClick = { showCreateFolderDialog = true }, modifier = Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary) }
                    FloatingActionButton(onClick = { filePicker.launch("*/*") }) { Icon(Icons.Default.Add, null, tint = Color.White) }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            if (folders.isEmpty() && currentFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Folder ini kosong", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) }
            } else if (viewModel.viewMode == "grid") {
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = (100.dp * viewModel.zoomLevel)), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folders) { (name, path) -> 
                        val isOptimistic = viewModel.allFiles.any { it.path == "$path/.keep" && it.isOptimistic }
                        GridFileItem(
                            file = null, name = name, isFolder = true, size = 0, isSelected = viewModel.selectionSet.contains(path),
                            isSelectionMode = viewModel.selectionSet.isNotEmpty(), isLocked = false, isStarred = false, 
                            zoom = viewModel.zoomLevel, viewModel = viewModel,
                            onLongClick = { viewModel.toggleSelection(path) },
                            onClick = { if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(path) else viewModel.navigate("/$path") },
                            modifier = if (isOptimistic) Modifier.alpha(0.5f) else Modifier
                        )
                    }
                    items(currentFiles) { f -> 
                        GridFileItem(
                            file = f, name = f.path.split("/").last(), isFolder = false, size = f.size, 
                            isSelected = viewModel.selectionSet.contains(f.id), isSelectionMode = viewModel.selectionSet.isNotEmpty(), 
                            isLocked = f.isLocked, isStarred = f.isStarred, zoom = viewModel.zoomLevel, viewModel = viewModel,
                            onLongClick = { viewModel.toggleSelection(f.id) },
                            onClick = {
                                if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(f.id)
                                else if (isAudioFile(f.path)) viewModel.currentPlayingFile = f else previewFile = f
                            },
                            modifier = if (f.isOptimistic) Modifier.alpha(0.5f) else Modifier
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(folders) { (name, path) -> 
                        val isOptimistic = viewModel.allFiles.any { it.path == "$path/.keep" && it.isOptimistic }
                        ListFileItem(
                            file = null, name = name, isFolder = true, size = 0, isSelected = viewModel.selectionSet.contains(path), 
                            isSelectionMode = viewModel.selectionSet.isNotEmpty(), isLocked = false, isStarred = false, 
                            zoom = viewModel.zoomLevel, viewModel = viewModel,
                            onLongClick = { viewModel.toggleSelection(path) },
                            onClick = { if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(path) else viewModel.navigate("/$path") },
                            modifier = if (isOptimistic) Modifier.alpha(0.5f) else Modifier
                        )
                    }
                    items(currentFiles) { f -> 
                        ListFileItem(
                            file = f, name = f.path.split("/").last(), isFolder = false, size = f.size, 
                            isSelected = viewModel.selectionSet.contains(f.id), isSelectionMode = viewModel.selectionSet.isNotEmpty(), 
                            isLocked = f.isLocked, isStarred = f.isStarred, zoom = viewModel.zoomLevel, viewModel = viewModel,
                            onLongClick = { viewModel.toggleSelection(f.id) },
                            onClick = {
                                if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(f.id)
                                else if (isAudioFile(f.path)) viewModel.currentPlayingFile = f else previewFile = f
                            },
                            modifier = if (f.isOptimistic) Modifier.alpha(0.5f) else Modifier
                        )
                    }
                }
            }
        }
    }
    
    if (showCreateFolderDialog) {
        AlertDialog(onDismissRequest = { showCreateFolderDialog = false }, title = { Text("Folder Baru") }, text = { OutlinedTextField(folderName, { folderName = it }, label = { Text("Nama Folder") }) },
            confirmButton = { Button(onClick = { if (folderName.isNotBlank()) { viewModel.createFolder(folderName); folderName = ""; showCreateFolderDialog = false } }) { Text("Buat") } },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Batal") } })
    }
    if (previewFile != null) {
        FilePreviewScreen(file = previewFile!!, allFiles = currentFiles, viewModel = viewModel, onFileChange = { previewFile = it }, onClose = { previewFile = null })
    }
}
