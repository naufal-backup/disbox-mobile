package com.disbox.mobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel

@Composable
fun LoginScreen(viewModel: DisboxViewModel) {
    var mode by remember { mutableStateOf<String?>(null) } // "login", "register", "guest"
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var metaUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(70.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary), Alignment.Center) {
            Icon(Icons.Default.Cloud, null, tint = Color.White, modifier = Modifier.size(35.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Disbox", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Discord Cloud Storage", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        
        Spacer(Modifier.height(32.dp))

        AnimatedContent(targetState = mode, label = "mode") { currentMode ->
            if (currentMode == null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeButton("Masuk dengan Akun", Icons.Default.Person, MaterialTheme.colorScheme.primary) { mode = "login" }
                    ModeButton("Daftar Akun Baru", Icons.Default.PersonAdd, MaterialTheme.colorScheme.secondary) { mode = "register" }
                    ModeButton("Setup Baru (Guest)", Icons.Default.FlashOn, MaterialTheme.colorScheme.tertiary) { mode = "guest" }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { mode = null; error = null }) { Icon(Icons.Default.ArrowBack, null) }
                        Text(if(currentMode == "login") "Login Akun" else if(currentMode == "register") "Daftar Akun" else "Guest Access", fontWeight = FontWeight.Bold)
                    }

                    if (currentMode == "login" || currentMode == "register") {
                        OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    }

                    if (currentMode == "register" || currentMode == "guest") {
                        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Webhook URL") }, modifier = Modifier.fillMaxWidth())
                    }

                    if (currentMode == "register") {
                        OutlinedTextField(value = metaUrl, onValueChange = { metaUrl = it }, label = { Text("Link CDN Metadata (Opsional)") }, modifier = Modifier.fillMaxWidth())
                    }

                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            error = null
                            when(currentMode) {
                                "login" -> viewModel.login(user, pass) { ok, err -> if(!ok) error = err }
                                "register" -> viewModel.register(user, pass, url, metaUrl) { ok, err -> 
                                    if(ok) mode = "login" else error = err 
                                }
                                "guest" -> viewModel.connect(url)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !viewModel.isLoading
                    ) {
                        if (viewModel.isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                        else Text(if(currentMode == "login") "Masuk" else if(currentMode == "register") "Daftar & Simpan" else "Hubungkan")
                    }
                }
            }
        }
    }
}

@Composable
fun ModeButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.1f), contentColor = color),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, null)
            Spacer(Modifier.width(16.dp))
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}
