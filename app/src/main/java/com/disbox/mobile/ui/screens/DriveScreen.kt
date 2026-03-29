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
fun DriveScreen(viewModel: DisboxViewModel, isLockedView: Boolean = false, isStarredView: Boolean = false, isRecentView: Boolean = false) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.uploadFiles(uris)
    }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var previewFile by remember { mutableStateOf<DisboxFile?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<List<String>?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var itemToRename by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var pinPrompt by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showFolderPickerForUnlock by remember { mutableStateOf(false) }
    var showSelectionMenu by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf<Pair<String, String?>?>(null) }
    
    val processed = remember(viewModel.allFiles, viewModel.currentPath, isLockedView, isStarredView, isRecentView, viewModel.sortMode) {
        val fileList = mutableListOf<DisboxFile>()
        val folderList = mutableListOf<Pair<String, String>>()
        val dirPath = viewModel.currentPath.trim('/')
        val folderLockStatus = mutableMapOf<String, Pair<Int, Int>>()
        
        viewModel.allFiles.forEach { f ->
            val parts = f.path.split("/").filter { it.isNotEmpty() }
            var temp = ""
            parts.dropLast(1).forEach { p ->
                temp = if (temp.isEmpty()) p else "$temp/$p"
                val s = folderLockStatus.getOrPut(temp) { 0 to 0 }
                folderLockStatus[temp] = (s.first + 1) to (s.second + (if (f.isLocked) 1 else 0))
            }
        }
        
        viewModel.allFiles.forEach { f ->
            val parts = f.path.split("/").filter { it.isNotEmpty() }
            val name = parts.last()
            var shouldIncludeFile = false
            when {
                isStarredView -> if (f.isStarred && !f.isLocked && name != ".keep") shouldIncludeFile = true
                isRecentView -> if ((System.currentTimeMillis() - f.createdAt) < 7*24*3600*1000 && !f.isLocked && name != ".keep") shouldIncludeFile = true
                isLockedView -> if (f.isLocked && name != ".keep") shouldIncludeFile = true
                else -> if (!f.isLocked && name != ".keep") shouldIncludeFile = true
            }
            if (shouldIncludeFile) {
                val fDir = parts.dropLast(1).joinToString("/")
                if (isStarredView || isRecentView || fDir == dirPath) fileList.add(f)
            }
            var currentAcc = ""
            parts.dropLast(1).forEach { dirName ->
                val parentPath = currentAcc
                currentAcc = if (currentAcc.isEmpty()) dirName else "$currentAcc/$dirName"
                val status = folderLockStatus[currentAcc]
                val folderIsLocked = status != null && status.first > 0 && status.first == status.second
                var shouldIncludeDir = false
                when {
                    isStarredView -> if (name == ".keep" && f.isStarred && f.path.removeSuffix("/.keep") == currentAcc) shouldIncludeDir = true
                    isRecentView -> shouldIncludeDir = false
                    isLockedView -> if (folderIsLocked) shouldIncludeDir = true
                    else -> if (!folderIsLocked) shouldIncludeDir = true
                }
                if (shouldIncludeDir && (isStarredView || parentPath == dirPath)) folderList.add(dirName to currentAcc)
            }
        }
        
        val sortedFolders = folderList.distinctBy { it.second }.sortedWith { a, b ->
            when (viewModel.sortMode) {
                "name" -> a.first.lowercase().compareTo(b.first.lowercase())
                else -> a.first.lowercase().compareTo(b.first.lowercase())
            }
        }
        
        val sortedFiles = fileList.filter { !it.path.endsWith(".keep") }.sortedWith { a, b ->
            if (isRecentView) {
                b.createdAt.compareTo(a.createdAt)
            } else {
                when (viewModel.sortMode) {
                    "name" -> a.path.split("/").last().lowercase().compareTo(b.path.split("/").last().lowercase())
                    "date" -> b.createdAt.compareTo(a.createdAt)
                    "size" -> b.size.compareTo(a.size)
                    else -> 0
                }
            }
        }
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
                            Text("${viewModel.selectionSet.size} ${viewModel.t("terpilih")}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        } else if (isStarredView) {
                            Text(viewModel.t("starred"), Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                        } else if (isRecentView) {
                            Text(viewModel.t("recent"), Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                        } else {
                            BreadcrumbBar(viewModel.currentPath) { viewModel.navigate(it) }
                        }
                    }
                    
                    if (viewModel.selectionSet.isNotEmpty()) {
                        val selectedItems = viewModel.allFiles.filter { f: DisboxFile ->
                            viewModel.selectionSet.contains(f.id) || 
                            viewModel.selectionSet.contains(f.path) || 
                            (f.path.endsWith("/.keep") && viewModel.selectionSet.contains(f.path.removeSuffix("/.keep"))) ||
                            (f.path == ".keep" && viewModel.selectionSet.contains(""))
                        }
                        val isSingleSelect = viewModel.selectionSet.size == 1
                        val allStarred = selectedItems.isNotEmpty() && selectedItems.all { it.isStarred }
                        val allLocked = selectedItems.isNotEmpty() && selectedItems.all { it.isLocked }

                        Box {
                            IconButton(onClick = { showSelectionMenu = true }) {
                                Icon(Icons.Default.MoreVert, null)
                            }
                            DropdownMenu(
                                expanded = showSelectionMenu,
                                onDismissRequest = { showSelectionMenu = false }
                            ) {
                                if (isSingleSelect && selectedItems.isNotEmpty()) {
                                    val item = selectedItems.first()
                                    val isFolder = item.path.endsWith("/.keep") || item.path == ".keep"
                                    
                                    if (!isFolder) {
                                        DropdownMenuItem(
                                            text = { Text(viewModel.t("download")) },
                                            leadingIcon = { Icon(Icons.Default.Download, null) },
                                            onClick = {
                                                showSelectionMenu = false
                                                viewModel.downloadFile(item)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(viewModel.t("rename")) },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                        onClick = {
                                            showSelectionMenu = false
                                            val oldPath = if (isFolder) item.path.removeSuffix("/.keep").ifEmpty { "" } else item.path
                                            val oldName = if (oldPath.isEmpty()) "/" else oldPath.split("/").last()
                                            
                                            itemToRename = oldName to oldPath
                                            newName = oldName
                                            showRenameDialog = true
                                        }
                                    )
                                    if (viewModel.shareEnabled) {
                                        DropdownMenuItem(
                                            text = { Text("Share") },
                                            leadingIcon = { Icon(Icons.Default.Link, null) },
                                            onClick = {
                                                showSelectionMenu = false
                                                showShareDialog = item.path to item.id
                                            }
                                        )
                                    }
                                }
                                DropdownMenuItem(
                                    text = { Text(if (allStarred) viewModel.t("unstar") else viewModel.t("star")) },
                                    leadingIcon = { Icon(if (allStarred) Icons.Default.StarBorder else Icons.Default.Star, null) },
                                    onClick = {
                                        showSelectionMenu = false
                                        viewModel.toggleBulkStatus(viewModel.selectionSet, isStarred = !allStarred)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (allLocked) viewModel.t("unlock") else viewModel.t("lock")) },
                                    leadingIcon = { Icon(if (isLockedView || allLocked) Icons.Default.LockOpen else Icons.Default.Lock, null) },
                                    onClick = {
                                        showSelectionMenu = false
                                        if (isLockedView) showFolderPickerForUnlock = true 
                                        else viewModel.toggleBulkStatus(viewModel.selectionSet, isLocked = !allLocked)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(viewModel.t("move")) },
                                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                                    onClick = {
                                        showSelectionMenu = false
                                        viewModel.startMove(viewModel.selectionSet)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(viewModel.t("copy")) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                    onClick = {
                                        showSelectionMenu = false
                                        viewModel.startCopy(viewModel.selectionSet)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(viewModel.t("delete")) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showSelectionMenu = false
                                        showDeleteConfirm = viewModel.selectionSet.toList()
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.clearSelection() }) { Icon(Icons.Default.Close, null) }
                    } else {
                        MetadataStatusIndicator(viewModel.metadataStatus, viewModel)
                        IconButton(onClick = { viewModel.setView(if (viewModel.viewMode == "grid") "list" else "grid") }) { 
                            Icon(if (viewModel.viewMode == "grid") Icons.Default.List else Icons.Default.GridView, null) 
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        listOf("name" to Icons.Default.SortByAlpha, "date" to Icons.Default.Schedule, "size" to Icons.Default.Scale).forEach { (mode, icon) ->
                            val isSelected = viewModel.sortMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateSortMode(mode) },
                                label = { Text(viewModel.t("sort_$mode"), fontSize = 10.sp) },
                                leadingIcon = { Icon(icon, null, Modifier.size(14.dp)) },
                                modifier = Modifier.padding(end = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ZoomIn, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.width(100.dp), contentAlignment = Alignment.Center) {
                            Slider(
                                value = viewModel.zoomLevel,
                                onValueChange = { viewModel.setZoom(it) },
                                valueRange = 0.6f..1.5f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        },
        floatingActionButton = {
            if (!isStarredView && !isRecentView && !isLockedView) {
                Column(horizontalAlignment = Alignment.End) {
                    if (viewModel.moveCopyMode != null) {
                        ExtendedFloatingActionButton(onClick = { viewModel.paste(viewModel.currentPath) }, icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) }, text = { Text(viewModel.t("save")) })
                    } else {
                        SmallFloatingActionButton(onClick = { showCreateFolderDialog = true }, modifier = Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        FloatingActionButton(onClick = { filePicker.launch("*/*") }) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) }
                    }
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
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text(viewModel.t("empty_folder"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) }
            } else if (viewModel.viewMode == "grid") {
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = (100.dp * viewModel.zoomLevel)), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folders) { (name, path) -> 
                        val isFolderSelected = viewModel.selectionSet.contains(path)
                        val folderFiles = viewModel.allFiles.filter { it.path.startsWith("$path/") }
                        val isLocked = folderFiles.isNotEmpty() && folderFiles.all { it.isLocked }
                        val isStarred = viewModel.allFiles.any { it.path == "$path/.keep" && it.isStarred }
                        
                        GridFileItem(
                            file = null, name = name, isFolder = true, size = 0, isSelected = isFolderSelected, 
                            isSelectionMode = viewModel.selectionSet.isNotEmpty(), isLocked = isLocked, isStarred = isStarred, 
                            zoom = viewModel.zoomLevel, viewModel = viewModel,
                            onLongClick = { viewModel.toggleSelection(path) },
                            onClick = {
                                if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(path)
                                else {
                                    if (isLocked && !viewModel.isVerified) pinPrompt = { viewModel.navigate("/$path") } 
                                    else viewModel.navigate("/$path")
                                }
                            }
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
                                else if (f.isLocked && !viewModel.isVerified) pinPrompt = { 
                                    if (isAudioFile(f.path)) viewModel.currentPlayingFile = f else previewFile = f 
                                } else {
                                    if (isAudioFile(f.path)) viewModel.currentPlayingFile = f else previewFile = f
                                }
                            }
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(folders) { (name, path) -> 
                        val isFolderSelected = viewModel.selectionSet.contains(path)
                        val folderFiles = viewModel.allFiles.filter { it.path.startsWith("$path/") }
                        val isLocked = folderFiles.isNotEmpty() && folderFiles.all { it.isLocked }
                        val isStarred = viewModel.allFiles.any { it.path == "$path/.keep" && it.isStarred }
                        
                        ListFileItem(
                            file = null, name = name, isFolder = true, size = 0, isSelected = isFolderSelected, 
                            isSelectionMode = viewModel.selectionSet.isNotEmpty(), isLocked = isLocked, isStarred = isStarred, 
                            zoom = viewModel.zoomLevel, viewModel = viewModel,
                            onLongClick = { viewModel.toggleSelection(path) },
                            onClick = {
                                if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(path)
                                else {
                                    if (isLocked && !viewModel.isVerified) pinPrompt = { viewModel.navigate("/$path") } 
                                    else viewModel.navigate("/$path")
                                }
                            }
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
                                else if (f.isLocked && !viewModel.isVerified) pinPrompt = { 
                                    if (isAudioFile(f.path)) viewModel.currentPlayingFile = f else previewFile = f 
                                } else {
                                    if (isAudioFile(f.path)) viewModel.currentPlayingFile = f else previewFile = f
                                }
                            }
                        )
                    }
                }
            }
        }
        
        if (pinPrompt != null) PinPromptModal(viewModel.t("locked_area"), { val a = pinPrompt; pinPrompt = null; a?.invoke() }, { pinPrompt = null }, viewModel)
        
        if (showFolderPickerForUnlock) {
            FolderSelectionDialog(
                allFiles = viewModel.allFiles,
                viewModel = viewModel,
                onFolderSelected = { dest ->
                    viewModel.unlockTo(viewModel.selectionSet, dest)
                    showFolderPickerForUnlock = false
                },
                onDismiss = { showFolderPickerForUnlock = false }
            )
        }
    }
    
    if (showCreateFolderDialog) {
        AlertDialog(onDismissRequest = { showCreateFolderDialog = false }, title = { Text(viewModel.t("new_folder")) }, text = { OutlinedTextField(folderName, { folderName = it }, label = { Text(viewModel.t("folder_name_placeholder")) }) },
            confirmButton = { Button(onClick = { if (folderName.isNotBlank()) { viewModel.createFolder(folderName); folderName = ""; showCreateFolderDialog = false } }) { Text(viewModel.t("create")) } },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text(viewModel.t("cancel")) } })
    }
    if (showDeleteConfirm != null) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = null }, title = { Text(viewModel.t("delete")) }, text = { Text(viewModel.t("hapus_item", mapOf("count" to showDeleteConfirm!!.size.toString()))) },
            confirmButton = { Button(onClick = { viewModel.deletePaths(showDeleteConfirm!!); showDeleteConfirm = null }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text(viewModel.t("confirm")) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text(viewModel.t("cancel")) } })
    }
    if (showRenameDialog && itemToRename != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(viewModel.t("rename")) },
            text = { OutlinedTextField(newName, { newName = it }, label = { Text(viewModel.t("folder_name_placeholder")) }) },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank() && newName != itemToRename!!.first) {
                        val target = itemToRename!!.second!!
                        val isId = target.contains("-") && target.length > 30
                        viewModel.renamePath(target, newName, if (isId) target else null)
                    }
                    showRenameDialog = false
                }) { Text(viewModel.t("save")) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text(viewModel.t("cancel")) } }
        )
    }
    if (showShareDialog != null) {
        ShareDialog(
            filePath = showShareDialog!!.first,
            fileId = showShareDialog!!.second,
            viewModel = viewModel,
            onClose = { showShareDialog = null }
        )
    }
    if (previewFile != null) {
        FilePreviewScreen(
            file = previewFile!!,
            allFiles = currentFiles,
            viewModel = viewModel,
            onFileChange = { previewFile = it },
            onClose = { previewFile = null }
        )
    }
}
