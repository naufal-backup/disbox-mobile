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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.disbox.mobile.ui.theme.DisboxMobileTheme
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val viewModel: DisboxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DisboxApp(viewModel, onFinish = { finish() })
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

fun isImageFile(name: String): Boolean {
    val ext = name.split(".").last().lowercase()
    return ext in listOf("jpg", "jpeg", "png", "gif", "webp")
}

fun isPdfFile(name: String): Boolean {
    return name.split(".").last().lowercase() == "pdf"
}

@Composable
fun FileThumbnail(file: DisboxFile, viewModel: DisboxViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val name = file.path.split("/").last()
    val isImage = isImageFile(name)
    var thumbFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val cacheKey = "thumb_${file.id}"
    val targetFile = File(context.cacheDir, cacheKey)

    LaunchedEffect(file.id, viewModel.showPreviews) {
        if (!viewModel.showPreviews || !isImage) {
            thumbFile = null
            return@LaunchedEffect
        }
        if (targetFile.exists()) {
            thumbFile = targetFile
            return@LaunchedEffect
        }
        isLoading = true
        try {
            viewModel.api?.downloadFile(file, targetFile) { }
            if (targetFile.exists()) {
                thumbFile = targetFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (thumbFile != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbFile).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Text(getFileIcon(name), fontSize = 24.sp)
        }
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
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisboxApp(viewModel: DisboxViewModel, onFinish: () -> Unit) {
    val allTabIds = listOf("drive", "recent", "starred", "locked", "settings")
    val allTabs = listOf("Drive", "Recent", "Starred", "Locked", "Settings")
    val allIcons = listOf(Icons.Default.Storage, Icons.Default.History, Icons.Default.Star, Icons.Default.Lock, Icons.Default.Settings)
    
    val filteredIndices = allTabIds.indices.filter { i -> allTabIds[i] != "recent" || viewModel.showRecent }
    val tabIds = filteredIndices.map { allTabIds[it] }
    val tabs = filteredIndices.map { allTabs[it] }
    val icons = filteredIndices.map { allIcons[it] }

    BackHandler {
        when {
            viewModel.activePage != "drive" -> viewModel.setPage("drive")
            viewModel.currentPath != "/" -> {
                val p = viewModel.currentPath.split("/").filter { it.isNotEmpty() }.dropLast(1).joinToString("/")
                viewModel.navigate(if (p.isEmpty()) "/" else "/$p")
            }
            else -> onFinish()
        }
    }
    DisboxMobileTheme(darkTheme = viewModel.theme == "dark") {
        if (!viewModel.isConnected && !viewModel.isLoading) LoginScreen(viewModel)
        else if (viewModel.isLoading && !viewModel.isConnected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Connecting...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        tabIds.forEachIndexed { index, id ->
                            NavigationBarItem(
                                icon = { Icon(icons[index], contentDescription = tabs[index]) },
                                label = { Text(tabs[index], fontSize = 10.sp) },
                                selected = viewModel.activePage == id,
                                onClick = { viewModel.setPage(id) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (viewModel.activePage) {
                        "drive" -> DriveScreen(viewModel)
                        "recent" -> DriveScreen(viewModel, isRecentView = true)
                        "starred" -> DriveScreen(viewModel, isStarredView = true)
                        "locked" -> if (viewModel.isVerified) DriveScreen(viewModel, isLockedView = true) else LockedGateway(viewModel)
                        "settings" -> SettingsScreen(viewModel)
                        else -> PlaceholderScreen(viewModel.activePage)
                    }
                    if (viewModel.progressMap.isNotEmpty()) TransferPanel(viewModel.progressMap)
                }
            }
        }
    }
}

@Composable
fun LockedGateway(viewModel: DisboxViewModel) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var hasPin by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { viewModel.checkHasPin { hasPin = it; checking = false } }
    if (checking) return
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        if (!hasPin) {
            Text("PIN Belum Diset", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Silakan buat PIN di Settings.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(Modifier.height(24.dp))
            Button(onClick = { viewModel.setPage("settings") }) { Text("Buka Settings") }
        } else {
            Text("Area Terkunci", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Masukkan PIN Anda untuk melihat konten yang dilindungi", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = pin, onValueChange = { pin = it },
                label = { Text("PIN") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.width(200.dp), textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 24.sp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
            )
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(24.dp))
            Button(onClick = { viewModel.verifyPin(pin) { if (!it) { error = "PIN salah"; pin = "" } } }, enabled = pin.length >= 4, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Buka Akses") }
        }
    }
}

@Composable
fun PinPromptModal(title: String, onVerified: () -> Unit, onCancel: () -> Unit, viewModel: DisboxViewModel) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it }, label = { Text("Masukkan PIN") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                )
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = { viewModel.verifyPin(pin) { if (it) onVerified() else { error = "PIN salah"; pin = "" } } }) { Text("Verifikasi") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Batal") } }
    )
}

@Composable
fun LoginScreen(viewModel: DisboxViewModel) {
    var url by remember { mutableStateOf(viewModel.webhookUrl) }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary), Alignment.Center) {
            Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(24.dp)); Text("Disbox", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        Text("Discord Cloud Storage", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Webhook URL") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
        Spacer(Modifier.height(24.dp))
        Button(onClick = { viewModel.connect(url) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(10.dp), enabled = !viewModel.isLoading) {
            if (viewModel.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("Connect Drive", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FolderSelectionDialog(
    allFiles: List<DisboxFile>,
    onFolderSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val folders = remember(allFiles) {
        val set = mutableSetOf("/")
        allFiles.forEach { f ->
            val parts = f.path.split("/").filter { it.isNotEmpty() }
            var current = ""
            parts.dropLast(1).forEach { p ->
                current = if (current.isEmpty()) p else "$current/$p"
                set.add("/$current")
            }
            if (f.path.endsWith(".keep")) {
                set.add("/" + f.path.removeSuffix("/.keep"))
            }
        }
        set.toList().sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Folder Tujuan") },
        text = {
            Box(Modifier.heightIn(max = 300.dp)) {
                LazyColumn {
                    items(folders) { folder ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onFolderSelected(folder) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, null, tint = Color(0xFFF0A500), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(folder, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DriveScreen(viewModel: DisboxViewModel, isLockedView: Boolean = false, isStarredView: Boolean = false, isRecentView: Boolean = false) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { viewModel.uploadFile(it) } }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var previewFile by remember { mutableStateOf<DisboxFile?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<List<String>?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var itemToRename by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var pinPrompt by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showFolderPickerForUnlock by remember { mutableStateOf(false) }

    val processed = remember(viewModel.allFiles, viewModel.currentPath, isLockedView, isStarredView, isRecentView) {
        val fileList = mutableListOf<DisboxFile>(); val folderList = mutableListOf<Pair<String, String>>()
        val dirPath = if (viewModel.currentPath == "/") "" else viewModel.currentPath.trim('/')
        val folderLockStatus = mutableMapOf<String, Pair<Int, Int>>()
        viewModel.allFiles.forEach { f ->
            val parts = f.path.split("/").filter { it.isNotEmpty() }; var temp = ""
            parts.dropLast(1).forEach { p ->
                temp = if (temp.isEmpty()) p else "$temp/$p"
                val s = folderLockStatus.getOrPut(temp) { 0 to 0 }
                folderLockStatus[temp] = (s.first + 1) to (s.second + (if (f.isLocked) 1 else 0))
            }
        }
        viewModel.allFiles.forEach { f ->
            val parts = f.path.split("/").filter { it.isNotEmpty() }; val name = parts.last()
            var shouldIncludeFile = false
            when {
                isStarredView -> if (f.isStarred && !f.isLocked && name != ".keep") shouldIncludeFile = true
                isRecentView -> if ((System.currentTimeMillis() - f.createdAt) < 7*24*3600*1000 && !f.isLocked && name != ".keep") shouldIncludeFile = true
                isLockedView -> if (f.isLocked && name != ".keep") shouldIncludeFile = true
                else -> if (!f.isLocked && name != ".keep") shouldIncludeFile = true
            }
            if (shouldIncludeFile) {
                val fDir = parts.dropLast(1).joinToString("/"); if (isStarredView || isRecentView || fDir == dirPath) fileList.add(f)
            }
            var currentAcc = ""
            parts.dropLast(1).forEach { dirName ->
                val parentPath = currentAcc; currentAcc = if (currentAcc.isEmpty()) dirName else "$currentAcc/$dirName"
                val status = folderLockStatus[currentAcc]; val folderIsLocked = status != null && status.first > 0 && status.first == status.second
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
        if (isRecentView) fileList.sortByDescending { it.createdAt }
        folderList.distinctBy { it.second }.sortedBy { it.first } to fileList.filter { !it.path.endsWith(".keep") }.sortedBy { it.path.split("/").last() }
    }
    val folders = processed.first; val currentFiles = processed.second

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (viewModel.selectionSet.isNotEmpty()) {
                            Text("${viewModel.selectionSet.size} terpilih", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        } else if (isStarredView) {
                            Text("Starred", Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                        } else if (isRecentView) {
                            Text("Recent", Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                        } else {
                            BreadcrumbBar(viewModel.currentPath) { viewModel.navigate(it) }
                        }
                    }
                    
                    if (viewModel.selectionSet.isNotEmpty()) {
                        val selectedItems = viewModel.allFiles.filter { f ->
                            viewModel.selectionSet.contains(f.id) || 
                            viewModel.selectionSet.contains(f.path) || 
                            (f.path.endsWith("/.keep") && viewModel.selectionSet.contains(f.path.removeSuffix("/.keep"))) ||
                            (f.path == ".keep" && viewModel.selectionSet.contains(""))
                        }
                        val allStarred = selectedItems.isNotEmpty() && selectedItems.all { it.isStarred }
                        val allLocked = selectedItems.isNotEmpty() && selectedItems.all { it.isLocked }

                        if (!isLockedView) {
                            IconButton(onClick = { viewModel.toggleBulkStatus(viewModel.selectionSet, isStarred = !allStarred) }) {
                                Icon(if (allStarred) Icons.Default.StarBorder else Icons.Default.Star, "Toggle Star massal")
                            }
                        }

                        if (isLockedView) {
                            IconButton(onClick = { showFolderPickerForUnlock = true }) {
                                Icon(Icons.Default.LockOpen, "Unlock massal ke folder...")
                            }
                        } else {
                            IconButton(onClick = { viewModel.toggleBulkStatus(viewModel.selectionSet, isLocked = !allLocked) }) {
                                Icon(if (allLocked) Icons.Default.LockOpen else Icons.Default.Lock, "Toggle Lock massal")
                            }
                        }

                        IconButton(onClick = { viewModel.startMove(viewModel.selectionSet) }) { 
                            Icon(Icons.Default.DriveFileMove, "Move massal") 
                        }
                        IconButton(onClick = { viewModel.startCopy(viewModel.selectionSet) }) { 
                            Icon(Icons.Default.ContentCopy, "Copy massal") 
                        }
                        IconButton(onClick = { showDeleteConfirm = viewModel.selectionSet.toList() }) { 
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) 
                        }
                        IconButton(onClick = { viewModel.clearSelection() }) { 
                            Icon(Icons.Default.Close, contentDescription = null) 
                        }
                    } else {
                        MetadataStatusIndicator(viewModel.metadataStatus)
                        IconButton(onClick = { viewModel.setView(if (viewModel.viewMode == "grid") "list" else "grid") }) { 
                            Icon(if (viewModel.viewMode == "grid") Icons.Default.List else Icons.Default.GridView, contentDescription = null) 
                        }
                        IconButton(onClick = { viewModel.refresh() }) { 
                            Icon(Icons.Default.Refresh, contentDescription = null) 
                        }
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ZoomIn, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = viewModel.zoomLevel,
                        onValueChange = { viewModel.setZoom(it) },
                        valueRange = 0.6f..1.5f,
                        modifier = Modifier.width(100.dp).height(32.dp)
                    )
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            }
        },
        floatingActionButton = {
            if (!isStarredView && !isRecentView && !isLockedView) {
                Column(horizontalAlignment = Alignment.End) {
                    if (viewModel.moveCopyMode != null) {
                        ExtendedFloatingActionButton(onClick = { viewModel.paste(viewModel.currentPath) }, icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) }, text = { Text("Paste here") })
                    } else {
                        SmallFloatingActionButton(onClick = { showCreateFolderDialog = true }, modifier = Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        FloatingActionButton(onClick = { filePicker.launch("*/*") }) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (folders.isEmpty() && currentFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Folder kosong", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) }
            } else if (viewModel.viewMode == "grid") {
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = (100.dp * viewModel.zoomLevel)), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folders) { (name, path) -> FolderItemGrid(name, path, viewModel) {
                        val status = viewModel.allFiles.filter { it.path.startsWith("$path/") }; val isLocked = status.isNotEmpty() && status.all { it.isLocked }
                        if (isLocked && !viewModel.isVerified) pinPrompt = { viewModel.navigate("/$path") } else viewModel.navigate("/$path")
                    } }
                    items(currentFiles) { f -> FileItemGrid(f, viewModel) { if (f.isLocked && !viewModel.isVerified) pinPrompt = { previewFile = f } else previewFile = f } }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(folders) { (name, path) -> FolderItemList(name, path, viewModel) {
                        val status = viewModel.allFiles.filter { it.path.startsWith("$path/") }; val isLocked = status.isNotEmpty() && status.all { it.isLocked }
                        if (isLocked && !viewModel.isVerified) pinPrompt = { viewModel.navigate("/$path") } else viewModel.navigate("/$path")
                    } }
                    items(currentFiles) { f -> FileItemList(f, viewModel) { if (f.isLocked && !viewModel.isVerified) pinPrompt = { previewFile = f } else previewFile = f } }
                }
            }
            if (pinPrompt != null) PinPromptModal("Buka Area Terkunci", { val a = pinPrompt; pinPrompt = null; a?.invoke() }, { pinPrompt = null }, viewModel)
            
            if (showFolderPickerForUnlock) {
                FolderSelectionDialog(
                    allFiles = viewModel.allFiles,
                    onFolderSelected = { dest ->
                        viewModel.unlockTo(viewModel.selectionSet, dest)
                        showFolderPickerForUnlock = false
                    },
                    onDismiss = { showFolderPickerForUnlock = false }
                )
            }
        }
    }
    if (showCreateFolderDialog) {
        AlertDialog(onDismissRequest = { showCreateFolderDialog = false }, title = { Text("New Folder") }, text = { OutlinedTextField(folderName, { folderName = it }, label = { Text("Name") }) },
            confirmButton = { Button(onClick = { if (folderName.isNotBlank()) { viewModel.createFolder(folderName); folderName = ""; showCreateFolderDialog = false } }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } })
    }
    if (showDeleteConfirm != null) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = null }, title = { Text("Delete Items") }, text = { Text("Are you sure you want to delete ${showDeleteConfirm!!.size} items?") },
            confirmButton = { Button(onClick = { viewModel.deletePaths(showDeleteConfirm!!); showDeleteConfirm = null }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") } })
    }
    if (previewFile != null) FilePreviewScreen(previewFile!!, viewModel) { previewFile = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(file: DisboxFile, viewModel: DisboxViewModel, onClose: () -> Unit) {
    val name = file.path.split("/").last(); val context = LocalContext.current; var previewText by remember { mutableStateOf<String?>(null) }
    var previewImageFile by remember { mutableStateOf<File?>(null) }; var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var isDownloadingPreview by remember { mutableStateOf(false) }; var errorMsg by remember { mutableStateOf<String?>(null) }
    val textExts = listOf("txt", "md", "json", "js", "py", "rs", "html", "css", "xml", "yml", "yaml", "sql", "sh", "env")
    val ext = name.split(".").last().lowercase()
    LaunchedEffect(file) {
        isDownloadingPreview = true; errorMsg = null
        try {
            val tempFile = File(context.cacheDir, "preview_$name")
            when {
                isImageFile(name) -> { viewModel.api?.downloadFile(file, tempFile) { }; previewImageFile = tempFile }
                isPdfFile(name) -> { viewModel.api?.downloadFile(file, tempFile) { }; previewPdfFile = tempFile }
                textExts.contains(ext) -> { viewModel.api?.downloadFile(file, tempFile) { }; previewText = tempFile.readText() }
            }
        } catch (e: Exception) { errorMsg = "Gagal memuat: ${e.message}" } finally { isDownloadingPreview = false }
    }
    LaunchedEffect(previewPdfFile) {
        previewPdfFile?.let {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", it)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/pdf"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) })
            onClose()
        }
    }
    ModalBottomSheet(onDismissRequest = onClose, dragHandle = null) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = null) }
                Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${file.size / 1024} KB", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                Button(onClick = { viewModel.downloadFile(file); onClose() }) { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Download") }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background), Alignment.Center) {
                if (isDownloadingPreview) CircularProgressIndicator()
                else if (errorMsg != null) Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                else if (previewImageFile != null) AsyncImage(ImageRequest.Builder(context).data(previewImageFile).build(), contentDescription = null, modifier = Modifier.fillMaxSize())
                else if (previewText != null) Box(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { Text(previewText!!, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                else Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(getFileIcon(name), fontSize = 64.sp); Text("No preview", color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FolderItemGrid(name: String, path: String, viewModel: DisboxViewModel, onClick: () -> Unit) {
    val isSelected = viewModel.selectionSet.contains(path); val status = viewModel.allFiles.filter { it.path.startsWith("$path/") }
    val isLocked = status.isNotEmpty() && status.all { it.isLocked }; val isStarred = viewModel.allFiles.any { it.path == "$path/.keep" && it.isStarred }
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf(name) }

    Box(Modifier.fillMaxWidth()) {
        GridFileItem(null, name, true, 0, isSelected, viewModel.selectionSet.isNotEmpty(), isLocked, isStarred, viewModel.zoomLevel, viewModel, { viewModel.toggleSelection(path) }, { if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(path) else onClick() })
        var showMenu by remember { mutableStateOf(false) }
        Box(Modifier.align(Alignment.TopStart).combinedClickable(onClick={}, onLongClick={showMenu=true}).size(40.dp))
        DropdownMenu(expanded = showMenu, onDismissRequest = {showMenu=false}) {
            val targets = if (viewModel.selectionSet.contains(path)) viewModel.selectionSet else setOf(path)
            val isBulk = targets.size > 1

            DropdownMenuItem(
                text = { Text(if (isBulk) "Rename (tidak tersedia massal)" else "Rename") },
                enabled = !isBulk,
                onClick = { showRenameDialog = true; showMenu = false },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (isBulk) "Move ${targets.size} item" else "Move") },
                onClick = { viewModel.startMove(targets); showMenu = false },
                leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (isBulk) "Copy ${targets.size} item" else "Copy") },
                onClick = { viewModel.startCopy(targets); showMenu = false },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (isBulk) "Kunci/Buka Massal" else if(isLocked) "Buka Kunci" else "Kunci Folder") },
                onClick = { 
                    viewModel.toggleBulkStatus(targets, isLocked = !isLocked)
                    showMenu = false 
                },
                leadingIcon = { Icon(if(isLocked) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (isBulk) "Beri/Hapus Star Massal" else if(isStarred) "Hapus Star" else "Beri Star") },
                onClick = { 
                    viewModel.toggleBulkStatus(targets, isStarred = !isStarred)
                    showMenu = false 
                },
                leadingIcon = { Icon(if(isStarred) Icons.Default.StarBorder else Icons.Default.Star, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (isBulk) "Hapus ${targets.size} item" else "Hapus", color=MaterialTheme.colorScheme.error) },
                onClick = { 
                    viewModel.deletePaths(targets.toList())
                    showMenu = false 
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint=MaterialTheme.colorScheme.error) }
            )
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Folder") },
            text = { OutlinedTextField(newFolderName, { newFolderName = it }, label = { Text("New Name") }) },
            confirmButton = {
                Button(onClick = {
                    if (newFolderName.isNotBlank() && newFolderName != name) {
                        viewModel.renamePath(path, newFolderName, null)
                    }
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileItemGrid(file: DisboxFile, viewModel: DisboxViewModel, onClick: () -> Unit) {
    val isSelected = viewModel.selectionSet.contains(file.id); val name = file.path.split("/").last()
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(name) }

    Box(Modifier.fillMaxWidth()) {
        GridFileItem(file, name, false, file.size, isSelected, viewModel.selectionSet.isNotEmpty(), file.isLocked, file.isStarred, viewModel.zoomLevel, viewModel, {viewModel.toggleSelection(file.id)}, { if(viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(file.id) else onClick() })
        var showMenu by remember { mutableStateOf(false) }
        Box(Modifier.align(Alignment.TopStart).combinedClickable(onClick={}, onLongClick={showMenu=true}).size(40.dp))
        DropdownMenu(expanded = showMenu, onDismissRequest = {showMenu=false}) {
            val targets = if (viewModel.selectionSet.contains(file.id)) viewModel.selectionSet else setOf(file.id)
            val isBulk = targets.size > 1

            DropdownMenuItem(
                text = { Text("Download") },
                onClick = { viewModel.downloadFile(file); showMenu = false },
                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (isBulk) "Rename (tidak tersedia massal)" else "Rename") },
                enabled = !isBulk,
                onClick = { 
                    showRenameDialog = true
                    showMenu = false 
                },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (isBulk) "Move ${targets.size} item" else "Move") },
                onClick = { viewModel.startMove(targets); showMenu = false },
                leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (isBulk) "Copy ${targets.size} item" else "Copy") },
                onClick = { viewModel.startCopy(targets); showMenu = false },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (isBulk) "Kunci/Buka Massal" else if(file.isLocked) "Buka Kunci" else "Kunci File") },
                onClick = { 
                    viewModel.toggleBulkStatus(targets, isLocked = !file.isLocked)
                    showMenu = false 
                },
                leadingIcon = { Icon(if(file.isLocked) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (isBulk) "Beri/Hapus Star Massal" else if(file.isStarred) "Hapus Star" else "Beri Star") },
                onClick = { 
                    viewModel.toggleBulkStatus(targets, isStarred = !file.isStarred)
                    showMenu = false 
                },
                leadingIcon = { Icon(if(file.isStarred) Icons.Default.StarBorder else Icons.Default.Star, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (isBulk) "Hapus ${targets.size} item" else "Hapus", color=MaterialTheme.colorScheme.error) },
                onClick = { 
                    viewModel.deletePaths(targets.toList())
                    showMenu = false 
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint=MaterialTheme.colorScheme.error) }
            )
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = { OutlinedTextField(newName, { newName = it }, label = { Text("New Name") }) },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank() && newName != name) {
                        viewModel.renamePath(file.path, newName, file.id)
                    }
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun FolderItemList(name: String, path: String, viewModel: DisboxViewModel, onClick: () -> Unit) {
    val isSelected = viewModel.selectionSet.contains(path); val status = viewModel.allFiles.filter { it.path.startsWith("$path/") }
    val isLocked = status.isNotEmpty() && status.all { it.isLocked }; val isStarred = viewModel.allFiles.any { it.path == "$path/.keep" && it.isStarred }
    ListFileItem(null, name, true, 0, isSelected, viewModel.selectionSet.isNotEmpty(), isLocked, isStarred, viewModel.zoomLevel, viewModel, onClick, { viewModel.toggleSelection(path) })
}

@Composable
fun FileItemList(file: DisboxFile, viewModel: DisboxViewModel, onClick: () -> Unit) {
    ListFileItem(file, file.path.split("/").last(), false, file.size, viewModel.selectionSet.contains(file.id), viewModel.selectionSet.isNotEmpty(), file.isLocked, file.isStarred, viewModel.zoomLevel, viewModel, onClick, { viewModel.toggleSelection(file.id) })
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ListFileItem(file: DisboxFile?, name: String, isFolder: Boolean, size: Long = 0, isSelected: Boolean, isSelectionMode: Boolean, isLocked: Boolean = false, isStarred: Boolean = false, zoom: Float, viewModel: DisboxViewModel, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    Row(modifier = Modifier.fillMaxWidth().height(64.dp * zoom).padding(horizontal = 12.dp, vertical = 2.dp).clip(RoundedCornerShape(10.dp)).then(if(isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(10.dp)) else Modifier).combinedClickable(onClick = onClick, onLongClick = onLongClick).background(bgColor).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isSelectionMode) { Box(modifier = Modifier.size(18.dp * zoom).clip(RoundedCornerShape(4.dp)).border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent), contentAlignment = Alignment.Center) { if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp * zoom)) }; Spacer(Modifier.width(12.dp)) }
        Box(modifier = Modifier.size(36.dp * zoom).clip(RoundedCornerShape(8.dp)).background(if (isFolder) Color(0xFFF0A500).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            if (isFolder || file == null) Icon(if (isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile, contentDescription = null, tint = if (isFolder) Color(0xFFF0A500) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp * zoom))
            else FileThumbnail(file, viewModel, Modifier.fillMaxSize())
            if (isLocked) Box(modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp)) { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp * zoom)) }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp * zoom, modifier = Modifier.weight(1f, false))
                if (isStarred) { Spacer(modifier = Modifier.width(4.dp)); Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF0A500), modifier = Modifier.size(12.dp * zoom)) }
            }
            Text(if (isFolder) "Folder" else "${size / 1024} KB", fontSize = 11.sp * zoom, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GridFileItem(file: DisboxFile?, name: String, isFolder: Boolean, size: Long = 0, isSelected: Boolean, isSelectionMode: Boolean, isLocked: Boolean = false, isStarred: Boolean = false, zoom: Float, viewModel: DisboxViewModel, onLongClick: () -> Unit, onClick: () -> Unit) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if(isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp)) else Modifier).combinedClickable(onClick = onClick, onLongClick = onLongClick).background(bgColor).padding(12.dp)) {
        if (isSelectionMode) Box(modifier = Modifier.align(Alignment.TopEnd).size(18.dp * zoom).clip(RoundedCornerShape(4.dp)).border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent), contentAlignment = Alignment.Center) { if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp * zoom)) }
        if (isStarred && !isSelectionMode) Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF0A500), modifier = Modifier.align(Alignment.TopEnd).size(14.dp * zoom))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.size(44.dp * zoom).clip(RoundedCornerShape(10.dp)).background(if (isFolder) Color(0xFFF0A500).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                if (isFolder || file == null) Icon(if (isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile, contentDescription = null, tint = if (isFolder) Color(0xFFF0A500) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp * zoom))
                else FileThumbnail(file, viewModel, Modifier.fillMaxSize())
                if (isLocked) Box(modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp)) { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp * zoom)) }
            }
            Spacer(Modifier.height(6.dp))
            Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 11.sp * zoom, textAlign = TextAlign.Center)
            Text(if (isFolder) "Folder" else "${size / 1024} KB", fontSize = 9.sp * zoom, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun TransferPanel(progressMap: Map<String, Float>) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 200.dp), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Transfers", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                progressMap.forEach { (name, p) -> item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(name, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (p >= 1f) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00D4AA), modifier = Modifier.size(14.dp)); Text("Done", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00D4AA)) }
                        else { LinearProgressIndicator(progress = {p}, modifier = Modifier.width(80.dp).height(4.dp).clip(CircleShape)); Text("${(p * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    }
                } }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: DisboxViewModel) {
    val CHUNK_OPTIONS = listOf(Triple("Free (10MB)", 10 * 1024 * 1024, "Standard limit"), Triple("Nitro (25MB)", 25 * 1024 * 1024, "Nitro Basic limit"), Triple("Premium (500MB)", 500 * 1024 * 1024, "Nitro Premium limit"))
    val currentIndex = CHUNK_OPTIONS.indexOfFirst { it.second == viewModel.chunkSize }.coerceAtLeast(0)
    var showDisconnectConfirm by remember { mutableStateOf(false) }; var hasPin by remember { mutableStateOf(false) }; var showPinDialog by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.checkHasPin { hasPin = it } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(32.dp))
        
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text("App Behavior", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text("Dark Mode", fontWeight = FontWeight.Bold); Text("Toggle color theme", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.theme == "dark", { viewModel.toggleTheme() })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text("Live Previews", fontWeight = FontWeight.Bold); Text("Show images in grid", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.showPreviews, { viewModel.updatePreviews(it) })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text("Show Recent Tab", fontWeight = FontWeight.Bold); Text("Display Recent in navigation", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.showRecent, { viewModel.updateRecent(it) })
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text("Security", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Master PIN", fontWeight = FontWeight.Bold); Text(if (hasPin) "PIN aktif. Item aman." else "PIN belum diset.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    if (!hasPin) Button(onClick = { showPinDialog = "set" }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Set PIN", fontSize = 12.sp) }
                    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
                        OutlinedButton(onClick = { showPinDialog = "change" }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Ubah", fontSize = 12.sp) }
                        OutlinedButton(onClick = { showPinDialog = "remove" }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Hapus", fontSize = 12.sp) }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text("Interface Zoom", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(viewModel.zoomLevel, { viewModel.setZoom(it) }, valueRange = 0.6f..1.5f, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text("${(viewModel.zoomLevel * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text("Chunk Size", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Text("${CHUNK_OPTIONS[currentIndex].first} - ${CHUNK_OPTIONS[currentIndex].third}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(currentIndex.toFloat(), { viewModel.setChunk(CHUNK_OPTIONS[it.toInt()].second) }, valueRange = 0f..2f, steps = 1)
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text("Account", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Text("Webhook: ${viewModel.webhookUrl.take(20)}...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showDisconnectConfirm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Disconnect Session") }
            }
        }
        
        Text("Disbox Mobile v3.0.0", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
    }
    if (showPinDialog != null) PinSettingsDialog(showPinDialog!!, { showPinDialog = null; viewModel.checkHasPin { hasPin = it } }, viewModel)
    if (showDisconnectConfirm) AlertDialog(onDismissRequest = { showDisconnectConfirm = false }, title = { Text("Disconnect") }, text = { Text("Are you sure?") }, confirmButton = { Button(onClick = { viewModel.disconnect(); showDisconnectConfirm = false }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text("Disconnect") } }, dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text("Batal") } })
}

@Composable
fun PinSettingsDialog(mode: String, onClose: () -> Unit, viewModel: DisboxViewModel) {
    var step by remember { mutableStateOf(if (mode == "set") "new" else "verify") }; var currentPin by remember { mutableStateOf("") }; var newPin by remember { mutableStateOf("") }; var confirmPin by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text(when(mode) { "set" -> "Set PIN"; "change" -> "Ubah PIN"; else -> "Hapus PIN" }) },
        text = { Column {
            if (step == "verify") OutlinedTextField(currentPin, { currentPin = it }, label = { Text("PIN Saat Ini") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword))
            else { OutlinedTextField(newPin, { newPin = it }, label = { Text("PIN Baru") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)); OutlinedTextField(confirmPin, { confirmPin = it }, label = { Text("Konfirmasi PIN") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)) }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { Button(onClick = { if (step == "verify") viewModel.verifyPin(currentPin) { if (it) { if (mode == "remove") viewModel.removePin { onClose() } else { step = "new"; error = "" } } else error = "PIN salah" } else { if (newPin.length < 4) error = "Min 4 angka"; else if (newPin != confirmPin) error = "Tidak cocok"; else viewModel.setPin(newPin) { onClose() } } }) { Text(if (step == "verify" && mode != "remove") "Lanjut" else "Simpan") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } })
}

@Composable
fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(name, fontWeight = FontWeight.Bold); Text("Coming Soon", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) } }
}
