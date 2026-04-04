package com.disbox.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disbox.mobile.DisboxViewModel
import com.disbox.mobile.navigation.Screen

@Composable
fun Sidebar(
    viewModel: DisboxViewModel,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp, top = 8.dp)
            ) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Disbox",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Navigation Items
            SidebarItem(
                label = "Semua File",
                icon = Icons.Default.Folder,
                selected = currentRoute == Screen.Drive.route,
                onClick = { onNavigate(Screen.Drive.route); onClose() }
            )
            SidebarItem(
                label = "Favorit",
                icon = Icons.Default.Star,
                selected = currentRoute == Screen.Starred.route,
                onClick = { onNavigate(Screen.Starred.route); onClose() }
            )
            SidebarItem(
                label = "Terbaru",
                icon = Icons.Default.History,
                selected = currentRoute == Screen.Recent.route,
                onClick = { onNavigate(Screen.Recent.route); onClose() }
            )
            SidebarItem(
                label = "Terkunci",
                icon = Icons.Default.Lock,
                selected = currentRoute == Screen.Locked.route,
                onClick = { onNavigate(Screen.Locked.route); onClose() }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            if (viewModel.cloudSaveEnabled) {
                SidebarItem(
                    label = "Cloud Save",
                    icon = Icons.Default.Backup,
                    selected = currentRoute == Screen.CloudSave.route,
                    onClick = { onNavigate(Screen.CloudSave.route); onClose() }
                )
            }

            SidebarItem(
                label = "Dibagikan",
                icon = Icons.Default.Link,
                selected = currentRoute == Screen.Shared.route,
                onClick = { onNavigate(Screen.Shared.route); onClose() }
            )

            Spacer(Modifier.weight(1f))

            SidebarItem(
                label = "Pengaturan",
                icon = Icons.Default.Settings,
                selected = currentRoute == Screen.Settings.route,
                onClick = { onNavigate(Screen.Settings.route); onClose() }
            )
            
            SidebarItem(
                label = "Keluar",
                icon = Icons.Default.Logout,
                selected = false,
                onClick = { viewModel.disconnect(); onClose() }
            )
        }
    }
}

@Composable
fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        modifier = Modifier.padding(vertical = 4.dp),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary
        )
    )
}
