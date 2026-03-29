package com.disbox.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel
import com.disbox.mobile.model.DisboxFile

@Composable
fun FolderSelectionDialog(
    allFiles: List<DisboxFile>,
    viewModel: DisboxViewModel,
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
        title = { Text(viewModel.t("pilih_tujuan")) },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(viewModel.t("cancel")) } }
    )
}
