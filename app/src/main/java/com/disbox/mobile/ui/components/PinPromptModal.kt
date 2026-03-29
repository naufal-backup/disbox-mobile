package com.disbox.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel

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
                    value = pin, onValueChange = { pin = it }, label = { Text(viewModel.t("pin_current_placeholder")) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = { 
            Button(onClick = { 
                viewModel.verifyPin(pin) { 
                    if (it) onVerified() 
                    else { 
                        error = viewModel.t("pin_error_wrong")
                        pin = "" 
                    } 
                } 
            }) { Text(viewModel.t("confirm")) } 
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(viewModel.t("cancel")) } }
    )
}
