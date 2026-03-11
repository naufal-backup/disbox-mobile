package com.disbox.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.disbox.mobile.ui.theme.DisboxMobileTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DisboxMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DisboxApp()
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
fun DisboxApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isConnected by remember { mutableStateOf(false) }
    var webhookUrl by remember { mutableStateOf("") }
    var api by remember { mutableStateOf<DisboxApi?>(null) }
    var files by remember { mutableStateOf<List<DisboxFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var progressMap by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                api?.let { disbox ->
                    val fileName = "uploaded_file_${System.currentTimeMillis()}"
                    try {
                        disbox.uploadFile(it, fileName) { p ->
                            progressMap = progressMap.toMutableMap().apply { put(fileName, p) }
                        }
                        Toast.makeText(context, "Upload success", Toast.LENGTH_SHORT).show()
                        files = disbox.getFileSystem()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        progressMap = progressMap.toMutableMap().apply { remove(fileName) }
                    }
                }
            }
        }
    }

    if (!isConnected) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Disbox Mobile", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Discord Cloud Storage", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = webhookUrl,
                onValueChange = { webhookUrl = it },
                label = { Text("Webhook URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (webhookUrl.isNotBlank()) {
                        isLoading = true
                        api = DisboxApi(context, webhookUrl)
                        coroutineScope.launch {
                            try {
                                api!!.init()
                                files = api!!.getFileSystem()
                                isConnected = true
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to connect", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Text("Connect Drive")
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("My Drive") },
                    actions = {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                api?.syncMetadata()
                                files = api?.getFileSystem() ?: emptyList()
                                isLoading = false
                            }
                        }) {
                            Text("Sync")
                        }
                        IconButton(onClick = { isConnected = false }) {
                            Text("Out")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { filePickerLauncher.launch("*/*") }) {
                    Text("+")
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (progressMap.isNotEmpty()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Active Transfers:")
                        progressMap.forEach { (name, prog) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, modifier = Modifier.weight(1f), maxLines = 1)
                                Spacer(modifier = Modifier.width(8.dp))
                                LinearProgressIndicator(progress = prog, modifier = Modifier.width(100.dp))
                            }
                        }
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files) { file ->
                        val name = file.path.split("/").lastOrNull() ?: "Unknown"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable {
                                    coroutineScope.launch {
                                        val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "disbox_downloads")
                                        if (!destDir.exists()) destDir.mkdirs()
                                        val destFile = File(destDir, name)
                                        try {
                                            Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
                                            api?.downloadFile(file, destFile) { p ->
                                                progressMap = progressMap.toMutableMap().apply { put(name, p) }
                                            }
                                            Toast.makeText(context, "Downloaded to ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            progressMap = progressMap.toMutableMap().apply { remove(name) }
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(name, fontWeight = FontWeight.Bold)
                                Text("${file.size / 1024} KB", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}
