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
import com.disbox.mobile.utils.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    viewModel: DisboxViewModel,
    isLockedView: Boolean = false,
    isStarredView: Boolean = false,
    isRecentView: Boolean = false,
    onOpenDrawer: () -> Unit = {}
) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.uploadFiles(uris)
    }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var previewFile by remember { mutableStateOf<DisboxFile?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showFileActions by remember { mutableStateOf<DisboxFile?>(null) }
    val sheetState = rememberModalBottomSheetState()
    
    val processed = remember(viewModel.allFiles.toList(), viewModel.currentPath, isLockedView, isStarredView, isRecentView, viewModel.sortMode, searchQuery) {
        val fileList = mutableListOf<DisboxFile>()
        val folderList = mutableListOf<Pair<String, String>>()
        val dirPath = viewModel.currentPath.trim('/')
        
        for (f in viewModel.allFiles) {
            val parts = f.path.split("/").filter { s -> s.isNotEmpty() }
            if (parts.isEmpty()) continue
            val name = parts.last()
            
            // Search filter
            if (searchQuery.isNotEmpty() && !name.contains(searchQuery, ignoreCase = true)) continue

            // Special filtering for Starred/Locked/Recent views
            if (isStarredView && !f.isStarred) continue
            if (isLockedView && !f.isLocked) continue
            if (!isLockedView && f.isLocked && !isStarredView && !isRecentView) continue

            val fDir = parts.dropLast(1).joinToString("/")

            if (name == ".keep") {
                if (isStarredView || isRecentView || isLockedView || searchQuery.isNotEmpty()) continue 
                val currentAcc = f.path.removeSuffix("/.keep")
                val pParts = currentAcc.split("/").filter { s -> s.isNotEmpty() }
                val parentPath = pParts.dropLast(1).joinToString("/")
                val dirName = pParts.lastOrNull() ?: ""
                if (parentPath == dirPath) folderList.add(dirName to currentAcc)
            } else {
                if (isStarredView || isRecentView || isLockedView || searchQuery.isNotEmpty()) {
                    fileList.add(f)
                } else if (fDir == dirPath) {
                    fileList.add(f)
                }
            }
        }
        
        val sortedFolders = folderList.distinctBy { it.second }.sortedBy { it.first.lowercase() }
        val sortedFiles = when(viewModel.sortMode) {
            "date" -> fileList.sortedByDescending { it.createdAt }
            "size" -> fileList.sortedByDescending { it.size }
            else -> fileList.sortedBy { it.path.lowercase() }
        }
        sortedFolders to sortedFiles
    }
    val folders = processed.first
    val currentFiles = processed.second

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                if (isSearchActive) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = { /* Done via real-time filtering */ },
                        active = true,
                        onActiveChange = { isSearchActive = it },
                        placeholder = { Text(viewModel.t("search")) },
                        leadingIcon = { IconButton(onClick = { isSearchActive = false; searchQuery = "" }) { Icon(Icons.Default.ArrowBack, null) } },
                        trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {}
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null, tint = MaterialTheme.colorScheme.onSurface) }
                        
                        Box(modifier = Modifier.weight(1f)) {
                            if (viewModel.selectionSet.isNotEmpty()) {
                                Text("${viewModel.selectionSet.size} ${viewModel.t("terpilih")}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            } else {
                                val titleText = when {
                                    isStarredView -> viewModel.t("starred")
                                    isLockedView -> viewModel.t("locked")
                                    isRecentView -> viewModel.t("recent")
                                    else -> null
                                }
                                if (titleText != null) {
                                    Text(titleText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                                } else {
                                    BreadcrumbBar(viewModel.currentPath) { viewModel.navigate(it) }
                                }
                            }
                        }
                        
                        IconButton(onClick = { isSearchActive = true }) { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface) }
                        
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurface) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (viewModel.viewMode == "grid") viewModel.t("sort_name") else viewModel.t("sort_name")) /* Simplified */ },
                                    leadingIcon = { Icon(if (viewModel.viewMode == "grid") Icons.Default.List else Icons.Default.GridView, null) },
                                    onClick = { viewModel.setView(if (viewModel.viewMode == "grid") "list" else "grid"); showMenu = false }
                                )
                                HorizontalDivider()
                                Text("Zoom", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Slider(
                                    value = viewModel.zoomLevel,
                                    onValueChange = { viewModel.zoomLevel = it },
                                    valueRange = 0.6f..1.8f,
                                    modifier = Modifier.width(160.dp).padding(horizontal = 16.dp)
                                )
                            }
                        }
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
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    if (viewModel.isLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(viewModel.t("status_syncing"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    } else {
                        Text(viewModel.t("empty_folder"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
            } else if (viewModel.viewMode == "grid") {
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = (100.dp * viewModel.zoomLevel)), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folders) { (name, path) -> 
                        val isOptimistic = viewModel.allFiles.any { it.path == "$path/.keep" && it.isOptimistic }
                        GridFileItem(
                            file = null, name = name, isFolder = true, size = 0, isSelected = viewModel.selectionSet.contains(path),
                            isSelectionMode = viewModel.selectionSet.isNotEmpty(), isLocked = false, isStarred = false, 
                            zoom = viewModel.zoomLevel, viewModel = viewModel,
                            onLongClick = { 
                                if (viewModel.selectionSet.isEmpty()) {
                                    // Folder actions if needed
                                } else viewModel.toggleSelection(path)
                            },
                            onClick = { if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(path) else viewModel.navigate("/$path") },
                            modifier = if (isOptimistic) Modifier.alpha(0.5f) else Modifier
                        )
                    }
                    items(currentFiles) { f -> 
                        GridFileItem(
                            file = f, name = f.path.split("/").last(), isFolder = false, size = f.size, 
                            isSelected = viewModel.selectionSet.contains(f.id), isSelectionMode = viewModel.selectionSet.isNotEmpty(), 
                            isLocked = f.isLocked, isStarred = f.isStarred, zoom = viewModel.zoomLevel, viewModel = viewModel,
                            onLongClick = { 
                                if (viewModel.selectionSet.isEmpty()) showFileActions = f
                                else viewModel.toggleSelection(f.id)
                            },
                            onClick = {
                                if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(f.id)
                                else if (com.disbox.mobile.utils.isAudioFile(f.path)) viewModel.currentPlayingFile = f else previewFile = f
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
                            onLongClick = { 
                                if (viewModel.selectionSet.isEmpty()) {
                                    // Folder actions
                                } else viewModel.toggleSelection(path)
                            },
                            onClick = { if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(path) else viewModel.navigate("/$path") },
                            modifier = if (isOptimistic) Modifier.alpha(0.5f) else Modifier
                        )
                    }
                    items(currentFiles) { f -> 
                        ListFileItem(
                            file = f, name = f.path.split("/").last(), isFolder = false, size = f.size, 
                            isSelected = viewModel.selectionSet.contains(f.id), isSelectionMode = viewModel.selectionSet.isNotEmpty(), 
                            isLocked = f.isLocked, isStarred = f.isStarred, zoom = viewModel.zoomLevel, viewModel = viewModel,
                            onLongClick = { 
                                if (viewModel.selectionSet.isEmpty()) showFileActions = f
                                else viewModel.toggleSelection(f.id)
                            },
                            onClick = {
                                if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(f.id)
                                else if (com.disbox.mobile.utils.isAudioFile(f.path)) viewModel.currentPlayingFile = f else previewFile = f
                            },
                            modifier = if (f.isOptimistic) Modifier.alpha(0.5f) else Modifier
                        )
                    }
                }
            }
        }
    }

    if (showFileActions != null) {
        val file = showFileActions!!
        ModalBottomSheet(
            onDismissRequest = { showFileActions = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                Text(
                    text = file.path.split("/").last(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                ListItem(
                    headlineContent = { Text(viewModel.t("download")) },
                    leadingContent = { Icon(Icons.Default.Download, null) },
                    modifier = Modifier.clickable { viewModel.downloadFile(file); showFileActions = null }
                )
                ListItem(
                    headlineContent = { Text(if (file.isStarred) viewModel.t("unstar") else viewModel.t("star")) },
                    leadingContent = { Icon(if (file.isStarred) Icons.Default.StarOutline else Icons.Default.Star, null) },
                    modifier = Modifier.clickable { viewModel.toggleBulkStatus(listOf(file.id), isStarred = !file.isStarred); showFileActions = null }
                )
                ListItem(
                    headlineContent = { Text(if (file.isLocked) viewModel.t("unlock") else viewModel.t("lock")) },
                    leadingContent = { Icon(if (file.isLocked) Icons.Default.LockOpen else Icons.Default.Lock, null) },
                    modifier = Modifier.clickable { 
                        viewModel.toggleBulkStatus(listOf(file.id), isLocked = !file.isLocked)
                        showFileActions = null 
                    }
                )
                ListItem(
                    headlineContent = { Text(viewModel.t("rename")) },
                    leadingContent = { Icon(Icons.Default.Edit, null) },
                    modifier = Modifier.clickable { showFileActions = null }
                )
                ListItem(
                    headlineContent = { Text(viewModel.t("delete"), color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { viewModel.deleteItems(listOf(file.id)); showFileActions = null }
                )
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
