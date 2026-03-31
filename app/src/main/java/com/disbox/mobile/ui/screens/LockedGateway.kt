package com.disbox.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel

@Composable
fun LockedGateway(viewModel: DisboxViewModel) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var hasPin by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        viewModel.checkHasPin { 
            hasPin = it
            checking = false 
        }
    }
    
    if (checking) return
    
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Lock, 
                    contentDescription = null, 
                    modifier = Modifier.size(64.dp), 
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                
                if (!hasPin) {
                    Text("PIN Belum Diatur", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Silakan atur PIN master di pengaturan untuk mengakses area terkunci.", 
                        textAlign = TextAlign.Center, 
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.setPage("settings") },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { 
                        Text("Buka Pengaturan", fontWeight = FontWeight.Bold) 
                    }
                } else {
                    Text("Area Terkunci", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Masukkan PIN master Anda untuk membuka akses.", 
                        textAlign = TextAlign.Center, 
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(32.dp))
                    
                    OutlinedTextField(
                        value = pin, 
                        onValueChange = { if (it.length <= 8) pin = it },
                        label = { Text("Master PIN") }, 
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), 
                        shape = RoundedCornerShape(16.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            textAlign = TextAlign.Center, 
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        singleLine = true
                    )
                    
                    if (error.isNotEmpty()) {
                        Text(
                            error, 
                            color = MaterialTheme.colorScheme.error, 
                            fontSize = 12.sp, 
                            modifier = Modifier.padding(top = 12.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    
                    Button(
                        onClick = { 
                            viewModel.verifyPin(pin) { success ->
                                if (!success) { 
                                    error = "PIN salah!"
                                    pin = "" 
                                } 
                            } 
                        }, 
                        enabled = pin.length >= 4, 
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) { 
                        Icon(Icons.Default.Key, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Buka Akses", fontWeight = FontWeight.Bold) 
                    }
                }
            }
        }
    }
}
