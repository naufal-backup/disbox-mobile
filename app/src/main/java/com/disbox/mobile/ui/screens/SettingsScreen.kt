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
    val CHUNK_OPTIONS = listOf(
        Triple("Free (10MB)", 10 * 1024 * 1024, viewModel.t("chunk_free_desc")), 
        Triple("Nitro (25MB)", 25 * 1024 * 1024, viewModel.t("chunk_nitro_desc")), 
        Triple("Premium (500MB)", 500 * 1024 * 1024, viewModel.t("chunk_premium_desc"))
    )
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.checkHasPin { hasPin = it } }
    
    val totalSize = remember(viewModel.allFiles) { viewModel.allFiles.sumOf { it.size } }
    val formattedSize = remember(totalSize) { 
        val gb = totalSize.toDouble() / (1024 * 1024 * 1024)
        if (gb < 1.0) "${totalSize / (1024 * 1024)} MB"
        else "%.2f GB".format(gb)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(viewModel.t("settings"), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text(viewModel.t("storage"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                Text(formattedSize, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Discord Unlimited ∞", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text(viewModel.t("language"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
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
                Text(viewModel.t("accent_color"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
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
                Text(viewModel.t("app_behavior"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text(viewModel.t("dark"), fontWeight = FontWeight.Bold); Text(viewModel.t("theme"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.theme == "dark", { viewModel.toggleTheme() })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text(viewModel.t("previews"), fontWeight = FontWeight.Bold); Text(viewModel.t("previews_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.showPreviews, { viewModel.updatePreviews(it) })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text(viewModel.t("recent_tab"), fontWeight = FontWeight.Bold); Text(viewModel.t("recent_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.showRecent, { viewModel.updateRecent(it) })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text(viewModel.t("cloud_save"), fontWeight = FontWeight.Bold); Text(viewModel.t("cloud_save_desc"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                    Switch(viewModel.cloudSaveEnabled, { viewModel.updateCloudSaveEnabled(it) })
                }
            }
        }
        
        // Disconnect button
        Button(
            onClick = { showDisconnectConfirm = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(viewModel.t("disconnect_drive"), fontWeight = FontWeight.Bold)
        }

        if (showDisconnectConfirm) {
            AlertDialog(
                onDismissRequest = { showDisconnectConfirm = false },
                title = { Text(viewModel.t("disconnect_drive")) },
                text = { Text(viewModel.t("disconnect_desc")) },
                confirmButton = { Button(onClick = { viewModel.disconnect(); showDisconnectConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(viewModel.t("confirm")) } },
                dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text(viewModel.t("cancel")) } }
            )
        }
    }
}
