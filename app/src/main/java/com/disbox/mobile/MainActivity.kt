package com.disbox.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.disbox.mobile.ui.theme.DisboxMobileTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: DisboxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DisboxApp(viewModel)
        }
        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}

fun getFileIcon(name: String): String {
    val ext = name.split(".").last().lowercase()
    return when (ext) {
        "pdf" -> "📄"
        "mp4", "mov", "avi", "mkv" -> "🎬"
        "mp3", "wav", "flac", "ogg" -> "🎵"
        "jpg", "jpeg", "png", "gif", "webp", "svg" -> "🖼"
        "zip", "rar", "tar", "gz", "7z" -> "📦"
        "js", "ts", "jsx", "tsx", "py", "rs" -> "⚙️"
        "html" -> "🌐"
        "css" -> "🎨"
        "json" -> "📋"
        "doc", "docx", "txt", "md" -> "📝"
        "xls", "xlsx", "csv" -> "📊"
        else -> "📄"
    }
}

@Composable
fun MetadataStatusIndicator(status: String) {
    val (color, label, icon) = when (status) {
        "synced" -> Triple(Color(0xFF00D4AA), "Synced", Icons.Default.CheckCircle)
        "uploading" -> Triple(Color(0xFF5865F2), "Uploading...", Icons.Default.Refresh)
        "dirty" -> Triple(Color(0xFFF0A500), "Pending", Icons.Default.History)
        "error" -> Triple(Color(0xFFED4245), "Sync Error", Icons.Default.Error)
        else -> Triple(Color.Gray, "", Icons.Default.Info)
    }

    if (label.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisboxApp(viewModel: DisboxViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Drive", "Recent", "Starred", "Trash", "Settings")
    val icons = listOf(Icons.Default.Storage, Icons.Default.History, Icons.Default.Star, Icons.Default.Delete, Icons.Default.Settings)

    DisboxMobileTheme(darkTheme = viewModel.theme == "dark") {
        if (!viewModel.isConnected && !viewModel.isLoading) {
            LoginScreen(viewModel)
        } else if (viewModel.isLoading && !viewModel.isConnected) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Reconnecting to drive...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            tabs.forEachIndexed { index, title ->
                                NavigationBarItem(
                                    icon = { Icon(icons[index], contentDescription = title) },
                                    label = { Text(title, fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when (selectedTab) {
                        0 -> DriveScreen(viewModel)
                        4 -> SettingsScreen(viewModel)
                        else -> PlaceholderScreen(tabs[selectedTab])
                    }
                    
                    if (viewModel.progressMap.isNotEmpty()) {
                        TransferPanel(viewModel.progressMap)
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: DisboxViewModel) {
    var url by remember { mutableStateOf(viewModel.webhookUrl) }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Disbox", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.SansSerif, color = MaterialTheme.colorScheme.onBackground)
        Text("Discord Cloud Storage", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        
        Spacer(modifier = Modifier.height(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Created by naufal-backup", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Email: naufalalamsyah453@gmail.com", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text("LinkedIn: linkedin.com/in/naufal-gastiadirrijal-fawwaz-alamsyah-a34b43363", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Webhook URL") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.connect(url) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(10.dp),
            enabled = !viewModel.isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (viewModel.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("Connect Drive", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(viewModel: DisboxViewModel) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadFile(it) }
    }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var previewFile by remember { mutableStateOf<DisboxFile?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<List<String>?>(null) }
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var itemToRename by remember { mutableStateOf<Pair<String, String?>?>(null) } // Path, Id

    val currentFiles = viewModel.allFiles.filter { f ->
        val parts = f.path.split("/").filter { it.isNotEmpty() }
        val dirPath = if (viewModel.currentPath == "/") "" else viewModel.currentPath.trim('/')
        if (f.path.endsWith(".keep")) {
            val folderPath = f.path.removeSuffix("/.keep")
            val fParts = folderPath.split("/").filter { it.isNotEmpty() }
            val dParts = dirPath.split("/").filter { it.isNotEmpty() }
            if (dirPath.isEmpty()) fParts.size == 1 else fParts.size == dParts.size + 1 && folderPath.startsWith(dirPath)
        } else {
            val fDir = parts.dropLast(1).joinToString("/")
            fDir == dirPath
        }
    }

    val subDirs = viewModel.allFiles.filter { it.path.contains("/") || it.path.endsWith(".keep") }.mapNotNull { f ->
        val parts = f.path.split("/").filter { it.isNotEmpty() }
        val dirPath = if (viewModel.currentPath == "/") "" else viewModel.currentPath.trim('/')
        val dParts = dirPath.split("/").filter { it.isNotEmpty() }
        
        if (f.path.endsWith(".keep")) {
            val folderPath = f.path.removeSuffix("/.keep")
            val fParts = folderPath.split("/").filter { it.isNotEmpty() }
            if (dirPath.isEmpty()) {
                if (fParts.size == 1) fParts[0] to folderPath else null
            } else {
                if (fParts.size == dParts.size + 1 && folderPath.startsWith("$dirPath/")) fParts.last() to folderPath else null
            }
        } else {
            if (parts.size > dParts.size + 1) {
                if (dirPath.isEmpty()) parts[0] to parts[0]
                else if (f.path.startsWith("$dirPath/")) parts[dParts.size] to dParts.plus(parts[dParts.size]).joinToString("/")
                else null
            } else null
        }
    }.distinctBy { it.second }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                // Address Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            viewModel.currentPath, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    title = { 
                        Text("My Drive", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        MetadataStatusIndicator(status = viewModel.metadataStatus)
                        
                        IconButton(onClick = { viewModel.setView(if (viewModel.viewMode == "grid") "list" else "grid") }) {
                            Icon(if (viewModel.viewMode == "grid") Icons.Default.List else Icons.Default.GridView, null)
                        }

                        if (viewModel.selectionSet.isNotEmpty()) {
                            if (viewModel.selectionSet.size == 1) {
                                IconButton(onClick = {
                                    val idOrPath = viewModel.selectionSet.first()
                                    val file = viewModel.allFiles.find { it.id == idOrPath || it.path == idOrPath }
                                    if (file != null) {
                                        val name = file.path.split("/").last()
                                        newName = name
                                        itemToRename = file.path to file.id
                                        showRenameDialog = true
                                    } else {
                                        // Folder
                                        newName = idOrPath.split("/").last()
                                        itemToRename = idOrPath to null
                                        showRenameDialog = true
                                    }
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            }
                            
                            IconButton(onClick = { viewModel.startMove(viewModel.selectionSet) }) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = null)
                            }
                            
                            IconButton(onClick = { viewModel.startCopy(viewModel.selectionSet) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                            }

                            IconButton(onClick = { showDeleteConfirm = viewModel.selectionSet.toList() }) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        } else {
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                            }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (viewModel.moveCopyMode != null) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.paste(viewModel.currentPath) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                        icon = { Icon(Icons.Default.ContentPaste, null) },
                        text = { Text("Paste here") }
                    )
                    SmallFloatingActionButton(
                        onClick = { viewModel.cancelMoveCopy() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.Close, null)
                    }
                } else {
                    SmallFloatingActionButton(
                        onClick = { showCreateFolderDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    }
                    FloatingActionButton(onClick = { filePicker.launch("*/*") }, containerColor = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (viewModel.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            
            // Tool Area
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (viewModel.currentPath != "/") {
                    Row(modifier = Modifier.clickable {
                        val p = viewModel.currentPath.split("/").filter { it.isNotEmpty() }.dropLast(1).joinToString("/")
                        viewModel.navigate(if (p.isEmpty()) "/" else "/$p")
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.ZoomIn, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = viewModel.zoomLevel,
                    onValueChange = { viewModel.setZoom(it) },
                    valueRange = 0.6f..1.5f,
                    modifier = Modifier.width(100.dp)
                )
            }

            if (currentFiles.isEmpty() && subDirs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Folder is empty", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            } else {
                if (viewModel.viewMode == "grid") {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = (100.dp * viewModel.zoomLevel)),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(subDirs) { (name, fullPath) ->
                            GridFileItem(name = name, isFolder = true, isSelected = viewModel.selectionSet.contains(fullPath), 
                                isSelectionMode = viewModel.selectionSet.isNotEmpty(), zoom = viewModel.zoomLevel,
                                onLongClick = { viewModel.toggleSelection(fullPath) },
                                onClick = {
                                    if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(fullPath)
                                    else viewModel.navigate("/$fullPath")
                                }
                            )
                        }
                        items(currentFiles.filter { !it.path.endsWith(".keep") }) { file ->
                            val name = file.path.split("/").last()
                            GridFileItem(name = name, isFolder = false, size = file.size, isSelected = viewModel.selectionSet.contains(file.id), 
                                isSelectionMode = viewModel.selectionSet.isNotEmpty(), zoom = viewModel.zoomLevel,
                                onLongClick = { viewModel.toggleSelection(file.id) },
                                onClick = {
                                    if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(file.id)
                                    else previewFile = file
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(subDirs) { (name, fullPath) ->
                            ListFileItem(name = name, isFolder = true, isSelected = viewModel.selectionSet.contains(fullPath), 
                                isSelectionMode = viewModel.selectionSet.isNotEmpty(), zoom = viewModel.zoomLevel,
                                onLongClick = { viewModel.toggleSelection(fullPath) },
                                onClick = {
                                    if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(fullPath)
                                    else viewModel.navigate("/$fullPath")
                                }
                            )
                        }
                        items(currentFiles.filter { !it.path.endsWith(".keep") }) { file ->
                            val name = file.path.split("/").last()
                            ListFileItem(name = name, isFolder = false, size = file.size, isSelected = viewModel.selectionSet.contains(file.id), 
                                isSelectionMode = viewModel.selectionSet.isNotEmpty(), zoom = viewModel.zoomLevel,
                                onLongClick = { viewModel.toggleSelection(file.id) },
                                onClick = {
                                    if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(file.id)
                                    else previewFile = file
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("New Folder", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = folderName, 
                    onValueChange = { folderName = it }, 
                    label = { Text("Folder Name") },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (folderName.isNotBlank()) {
                        viewModel.createFolder(folderName)
                        folderName = ""
                        showCreateFolderDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRenameDialog && itemToRename != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Rename Item", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.renamePath(itemToRename!!.first, newName, itemToRename!!.second)
                        showRenameDialog = false
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Hapus Item", fontWeight = FontWeight.Bold) },
            text = { Text("Apakah Anda yakin ingin menghapus ${showDeleteConfirm!!.size} item terpilih? Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePaths(showDeleteConfirm!!)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Batal") }
            }
        )
    }

    if (previewFile != null) {
        FilePreviewScreen(file = previewFile!!, viewModel = viewModel, onClose = { previewFile = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(file: DisboxFile, viewModel: DisboxViewModel, onClose: () -> Unit) {
    val name = file.path.split("/").last()
    val context = LocalContext.current
    var previewText by remember { mutableStateOf<String?>(null) }
    var isDownloadingPreview by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val textExts = listOf("txt", "md", "json", "js", "py", "rs", "html", "css", "xml", "yml", "yaml", "sql", "sh", "env")

    LaunchedEffect(file) {
        val ext = name.split(".").last().lowercase()
        if (textExts.contains(ext)) {
            isDownloadingPreview = true
            errorMsg = null
            try {
                val tempFile = File(context.cacheDir, "preview_$name")
                viewModel.api?.downloadFile(file, tempFile) { }
                previewText = tempFile.readText()
            } catch (e: Exception) {
                errorMsg = "Gagal memuat pratinjau: ${e.message}"
            } finally {
                isDownloadingPreview = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, dragHandle = null, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    Text("${file.size / 1024} KB", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Button(onClick = { viewModel.downloadFile(file); onClose() }, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                if (isDownloadingPreview) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Downloading preview...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                } else if (errorMsg != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(errorMsg!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                    }
                } else if (previewText != null) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text(previewText!!, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(getFileIcon(name), fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No preview available for this file type", fontSize = 14.sp, 
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ListFileItem(name: String, isFolder: Boolean, size: Long = 0, isSelected: Boolean, isSelectionMode: Boolean, zoom: Float, onClick: () -> Unit, onLongClick: () -> Unit) {
    val baseHeight = 72.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(baseHeight * zoom)
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Desktop-like checkbox for list
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(18.dp * zoom)
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        1.5.dp, 
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp * zoom))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        Box(
            modifier = Modifier.size(40.dp * zoom).clip(CircleShape)
                .background(if (isFolder) Color(0xFFF0A500).copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (isFolder) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFF0A500), modifier = Modifier.size(24.dp * zoom))
            } else {
                Text(getFileIcon(name), fontSize = 24.sp * zoom)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp * zoom)
            if (!isFolder) {
                Text("${size / 1024} KB", fontSize = 12.sp * zoom, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            } else {
                Text("Folder", fontSize = 12.sp * zoom, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GridFileItem(name: String, isFolder: Boolean, size: Long = 0, isSelected: Boolean, isSelectionMode: Boolean, zoom: Float, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        // Desktop-like checkbox for grid (top-right)
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp * zoom)
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        1.5.dp, 
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp * zoom))
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.size(48.dp * zoom).clip(CircleShape)
                    .background(if (isFolder) Color(0xFFF0A500).copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (isFolder) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFF0A500), modifier = Modifier.size(28.dp * zoom))
                } else {
                    Text(getFileIcon(name), fontSize = 28.sp * zoom)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                name, 
                fontWeight = FontWeight.Medium, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis, 
                color = MaterialTheme.colorScheme.onSurface, 
                fontSize = 11.sp * zoom,
                textAlign = TextAlign.Center
            )
            Text(
                if (isFolder) "Folder" else "${size / 1024} KB", 
                fontSize = 9.sp * zoom, 
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun TransferPanel(progressMap: Map<String, Float>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 200.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Transfers", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn {
                progressMap.forEach { (name, p) ->
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(name, modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.width(80.dp).height(4.dp).clip(CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${(p * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: DisboxViewModel) {
    val CHUNK_OPTIONS = listOf(
        Triple("Free (10MB)", 10 * 1024 * 1024, "Standard limit"),
        Triple("Nitro (25MB)", 25 * 1024 * 1024, "Nitro Basic limit"),
        Triple("Premium (500MB)", 500 * 1024 * 1024, "Nitro Premium limit")
    )
    val currentIndex = CHUNK_OPTIONS.indexOfFirst { it.second == viewModel.chunkSize }.coerceAtLeast(0)
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.SansSerif, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Appearance", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Dark Mode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Toggle app color theme", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(checked = viewModel.theme == "dark", onCheckedChange = { viewModel.toggleTheme() })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Transfers", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Chunk Size", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Current: ${CHUNK_OPTIONS[currentIndex].first}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { viewModel.setChunk(CHUNK_OPTIONS[it.toInt()].second) },
                    valueRange = 0f..2f,
                    steps = 1,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                    Text(CHUNK_OPTIONS[currentIndex].third, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Account", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Webhook: ${viewModel.webhookUrl.take(20)}...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showDisconnectConfirm = true }, 
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Disconnect Session")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Developer Credits", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Naufal Gastiadirrijal Fawwaz Alamsyah", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("GitHub: naufal-backup", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("Email: naufalalamsyah453@gmail.com", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("LinkedIn: linkedin.com/in/naufal-gastiadirrijal-fawwaz-alamsyah-a34b43363", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Disbox Mobile v2.0.0", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Disconnect Session", fontWeight = FontWeight.Bold) },
            text = { Text("Apakah Anda yakin ingin memutus sesi? Webhook akan dihapus dari penyimpanan lokal.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.disconnect()
                        showDisconnectConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text("Coming Soon", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}
