package com.disbox.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.disbox.mobile.DisboxViewModel
import com.disbox.mobile.ui.components.PinPromptModal

@Composable
fun SettingsScreen(viewModel: DisboxViewModel) {
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) { 
        viewModel.checkHasPin { hasPin = it } 
    }
    
    val totalSize = remember(viewModel.allFiles.toList()) { viewModel.allFiles.sumOf { it.size } }
    val formattedSize = remember(totalSize) { 
        val gb = totalSize.toDouble() / (1024 * 1024 * 1024)
        if (gb < 1.0) "${totalSize / (1024 * 1024)} MB"
        else "%.2f GB".format(gb)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Pengaturan", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text("Penyimpanan", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                Text(formattedSize, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Discord Unlimited ∞", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text("Bahasa", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("id" to "ID", "en" to "EN", "zh" to "ZH").forEach { (code, label) ->
                        Button(
                            onClick = { viewModel.updateLanguage(code) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.language == code) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text(label) }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text("Warna Aksen", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("#5865F2", "#00D4AA", "#F0A500", "#ED4245", "#EB459E", "#9B59B6").forEach { colorHex ->
                        val color = Color(android.graphics.Color.parseColor(colorHex))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(2.dp, if(viewModel.accentColor == colorHex) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .clickable { viewModel.updateAccentColor(colorHex) }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text("Perilaku Aplikasi", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text("Mode Gelap", fontWeight = FontWeight.Bold); Text("Tema aplikasi", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.theme == "dark", { viewModel.toggleTheme() })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text("Pratinjau", fontWeight = FontWeight.Bold); Text("Tampilkan thumbnail file", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.showPreviews, { viewModel.updatePreviews(it) })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text("Tab Terbaru", fontWeight = FontWeight.Bold); Text("Tampilkan file yang baru diunggah", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.showRecent, { viewModel.updateRecent(it) })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text("Cloud Save", fontWeight = FontWeight.Bold); Text("Simpan data game/aplikasi ke cloud", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.cloudSaveEnabled, { viewModel.updateCloudSaveEnabled(it) })
                }
            }
        }
        
        Button(
            onClick = { showDisconnectConfirm = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Putuskan Koneksi Drive", fontWeight = FontWeight.Bold)
        }

        if (showDisconnectConfirm) {
            AlertDialog(
                onDismissRequest = { showDisconnectConfirm = false },
                title = { Text("Putuskan Drive?") },
                text = { Text("Anda harus memasukkan kembali kredensial untuk terhubung.") },
                confirmButton = { Button(onClick = { viewModel.disconnect(); showDisconnectConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Ya, Putuskan") } },
                dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text("Batal") } }
            )
        }
    }
}
