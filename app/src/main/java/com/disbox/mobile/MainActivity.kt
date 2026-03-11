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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
            DisboxMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DisboxApp(viewModel)
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisboxApp(viewModel: DisboxViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Drive", "Recent", "Starred", "Trash", "Settings")
    val icons = listOf(Icons.Default.Home, Icons.Default.DateRange, Icons.Default.Star, Icons.Default.Delete, Icons.Default.Settings)

    if (!viewModel.isConnected) {
        LoginScreen(viewModel)
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            icon = { Icon(icons[index], contentDescription = title) },
                            label = { Text(title, fontSize = 10.sp) },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index }
                        )
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
                
                // Progress Overlay for background tasks
                if (viewModel.progressMap.isNotEmpty()) {
                    TransferPanel(viewModel.progressMap)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: DisboxViewModel) {
    var url by remember { mutableStateOf(viewModel.webhookUrl) }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
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
        Text("Disbox", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text("Discord Cloud Storage", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(48.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Webhook URL") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.connect(url) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !viewModel.isLoading
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

    val subDirs = viewModel.allFiles.filter { it.path.contains("/") }.mapNotNull { f ->
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
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("My Drive", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(viewModel.currentPath, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                },
                actions = {
                    if (viewModel.selectionSet.isNotEmpty()) {
                        IconButton(onClick = { viewModel.deleteSelected() }) {
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
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = { showCreateFolderDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                }
                FloatingActionButton(onClick = { filePicker.launch("*/*") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (viewModel.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            
            // Breadcrumbs equivalent (Simple back button if not at root)
            if (viewModel.currentPath != "/") {
                Row(modifier = Modifier.fillMaxWidth().clickable {
                    val p = viewModel.currentPath.split("/").filter { it.isNotEmpty() }.dropLast(1).joinToString("/")
                    viewModel.navigate(if (p.isEmpty()) "/" else "/$p")
                }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back", fontSize = 14.sp)
                }
            }

            if (currentFiles.isEmpty() && subDirs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Folder is empty", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(subDirs) { (name, fullPath) ->
                        FileItem(name = name, isFolder = true, isSelected = viewModel.selectionSet.contains(fullPath),
                            onLongClick = { viewModel.toggleSelection(fullPath) },
                            onClick = {
                                if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(fullPath)
                                else viewModel.navigate("/$fullPath")
                            }
                        )
                    }
                    items(currentFiles.filter { !it.path.endsWith(".keep") }) { file ->
                        val name = file.path.split("/").last()
                        FileItem(name = name, isFolder = false, size = file.size, isSelected = viewModel.selectionSet.contains(file.id),
                            onLongClick = { viewModel.toggleSelection(file.id) },
                            onClick = {
                                if (viewModel.selectionSet.isNotEmpty()) viewModel.toggleSelection(file.id)
                                else viewModel.downloadFile(file)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(value = folderName, onValueChange = { folderName = it }, label = { Text("Name") })
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
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileItem(name: String, isFolder: Boolean, size: Long = 0, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(if (isFolder) Color(0xFFF0A500).copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (isFolder) Color(0xFFF0A500) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!isFolder) {
                Text("${size / 1024} KB", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            } else {
                Text("Folder", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
        if (isSelected) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun TransferPanel(progressMap: Map<String, Float>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 200.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Transfers", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn {
                progressMap.forEach { (name, p) ->
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(name, modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.width(8.dp))
                            LinearProgressIndicator(progress = p, modifier = Modifier.width(80.dp).height(4.dp).clip(CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${(p * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: DisboxViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Webhook: ${viewModel.webhookUrl.take(30)}...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.disconnect() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Disconnect")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("About", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Disbox Mobile v2.0.0", fontSize = 12.sp)
                Text("Based on Disbox Linux Engine", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Coming Soon", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}
