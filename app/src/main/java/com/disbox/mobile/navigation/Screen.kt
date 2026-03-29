package com.disbox.mobile.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Drive : Screen("drive")
    object Recent : Screen("recent")
    object Starred : Screen("starred")
    object Locked : Screen("locked")
    object CloudSave : Screen("cloud-save")
    object Shared : Screen("shared")
    object Settings : Screen("settings")
}
