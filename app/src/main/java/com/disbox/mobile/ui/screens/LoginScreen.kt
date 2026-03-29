package com.disbox.mobile.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(viewModel: DisboxViewModel) {
    var url by remember { mutableStateOf(viewModel.webhookUrl) }
    var msgId by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary), Alignment.Center) {
            Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Disbox", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        Text(viewModel.t("subtitle"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        
        if (viewModel.savedWebhooks.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Text(viewModel.t("saved_webhooks_count", mapOf("count" to viewModel.savedWebhooks.size.toString())), 
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, 
                modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            
            viewModel.savedWebhooks.forEach { savedUrl ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
                        onClick = { url = savedUrl; viewModel.connect(savedUrl) },
                        onLongClick = { viewModel.removeWebhook(savedUrl) }
                    ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            savedUrl.take(40) + if(savedUrl.length > 40) "..." else "", 
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if(url == savedUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(viewModel.t("webhook_url")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
        
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Hide Advanced" else "Advanced Options (Manual Sync)", fontSize = 12.sp)
            }
        }
        
        if (showAdvanced) {
            OutlinedTextField(
                value = msgId, 
                onValueChange = { msgId = it }, 
                label = { Text("Metadata Message ID (Optional)") }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("e.g. 123456789012345678") }
            )
            Text("Use this if your file list is empty or doesn't sync automatically.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(16.dp))
        }

        Button(onClick = { viewModel.connect(url, msgId.ifBlank { null }) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(10.dp), enabled = !viewModel.isLoading) {
            if (viewModel.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text(viewModel.t("connect_drive"), fontWeight = FontWeight.Bold)
        }
    }
}
