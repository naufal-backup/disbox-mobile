package com.disbox.mobile

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class DisboxViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("disbox_prefs", Context.MODE_PRIVATE)

    var isConnected by mutableStateOf(false)
    var webhookUrl by mutableStateOf(prefs.getString("webhook_url", "") ?: "")
    var api by mutableStateOf<DisboxApi?>(null)
    var allFiles by mutableStateOf<List<DisboxFile>>(emptyList())
    var currentPath by mutableStateOf("/")
    var activePage by mutableStateOf("drive")
    var isVerified by mutableStateOf(false)
    
    var isLoading by mutableStateOf(false)
    var progressMap by mutableStateOf<Map<String, Float>>(emptyMap())
    var selectionSet by mutableStateOf<Set<String>>(emptySet())

    var theme by mutableStateOf(prefs.getString("theme", "dark") ?: "dark")
    var chunkSize by mutableStateOf(prefs.getInt("chunk_size", 10 * 1024 * 1024))
    var metadataStatus by mutableStateOf("synced")

    var viewMode by mutableStateOf(prefs.getString("view_mode", "grid") ?: "grid")
    var zoomLevel by mutableStateOf(prefs.getFloat("zoom_level", 1f))
    var showPreviews by mutableStateOf(prefs.getBoolean("show_previews", true))
    var showRecent by mutableStateOf(prefs.getBoolean("show_recent", true))

    var moveCopyMode by mutableStateOf<String?>(null)
    var moveCopyItems by mutableStateOf<Set<String>>(emptySet())

    private val notificationHelper = NotificationHelper(application)
    private var pollJob: Job? = null

    init {
        if (webhookUrl.isNotEmpty()) {
            connect(webhookUrl)
        }
    }

    fun setPage(page: String) {
        if (activePage == "locked" && page != "locked") {
            isVerified = false
        }
        activePage = page
        if (page == "drive" || page == "locked") {
            currentPath = "/"
        }
    }

    fun connect(url: String) {
        pollJob?.cancel()
        isConnected = false
        allFiles = emptyList()
        currentPath = "/"
        selectionSet = emptySet()
        moveCopyMode = null
        moveCopyItems = emptySet()
        metadataStatus = "synced"
        activePage = "drive"
        isVerified = false

        webhookUrl = url
        prefs.edit().putString("webhook_url", url).apply()

        val newApi = DisboxApi(getApplication(), url)
        newApi.chunkSize = chunkSize
        newApi.onStatusChange = { metadataStatus = it }
        api = newApi

        viewModelScope.launch {
            isLoading = true
            try {
                newApi.init()
                allFiles = newApi.getFileSystem()
                isConnected = true
                startPolling()
            } catch (e: Exception) {
                e.printStackTrace()
                isConnected = false
            } finally {
                isLoading = false
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isConnected) {
                delay(30000)
                if (metadataStatus == "synced" || metadataStatus == "error") {
                    refresh(silent = true)
                }
            }
        }
    }

    fun setView(mode: String) {
        viewMode = mode
        prefs.edit().putString("view_mode", mode).apply()
    }

    fun setZoom(level: Float) {
        zoomLevel = level
        prefs.edit().putFloat("zoom_level", level).apply()
    }

    fun updatePreviews(enabled: Boolean) {
        showPreviews = enabled
        prefs.edit().putBoolean("show_previews", enabled).apply()
    }

    fun updateRecent(enabled: Boolean) {
        showRecent = enabled
        prefs.edit().putBoolean("show_recent", enabled).apply()
    }

    fun setChunk(size: Int) {
        chunkSize = size
        api?.chunkSize = size
        prefs.edit().putInt("chunk_size", size).apply()
    }

    fun toggleTheme() {
        val newTheme = if (theme == "dark") "light" else "dark"
        theme = newTheme
        prefs.edit().putString("theme", newTheme).apply()
    }

    fun disconnect() {
        pollJob?.cancel()
        prefs.edit().remove("webhook_url").apply()
        isConnected = false
        api = null
        allFiles = emptyList()
        currentPath = "/"
        selectionSet = emptySet()
        webhookUrl = ""
        moveCopyMode = null
        moveCopyItems = emptySet()
        activePage = "drive"
        isVerified = false
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) isLoading = true
            api?.syncMetadata()
            allFiles = api?.getFileSystem() ?: emptyList()
            if (!silent) isLoading = false
        }
    }

    fun navigate(path: String) {
        currentPath = path
        selectionSet = emptySet()
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            isLoading = true
            api?.createFolder(name, currentPath)
            allFiles = api?.getFileSystem() ?: emptyList()
            isLoading = false
        }
    }

    fun deletePaths(pathsOrIds: List<String>) {
        allFiles = allFiles.filterNot { f ->
            pathsOrIds.contains(f.id) ||
            pathsOrIds.contains(f.path) ||
            pathsOrIds.any { p -> f.path.startsWith("$p/") }
        }
        selectionSet = emptySet()

        viewModelScope.launch {
            try {
                api?.bulkDelete(pathsOrIds)
                allFiles = api?.getFileSystem() ?: emptyList()
            } catch (e: Exception) {
                refresh()
            }
        }
    }

    fun renamePath(oldPath: String, newName: String, id: String?) {
        viewModelScope.launch {
            isLoading = true
            val parts = oldPath.split("/")
            val newPath = if (parts.size > 1) {
                parts.dropLast(1).joinToString("/") + "/$newName"
            } else newName
            api?.renamePath(oldPath, newPath, id)
            allFiles = api?.getFileSystem() ?: emptyList()
            isLoading = false
        }
    }

    fun toggleLock(idOrPath: String, isLocked: Boolean) {
        toggleBulkStatus(setOf(idOrPath), isLocked = isLocked)
    }

    fun toggleStar(idOrPath: String, isStarred: Boolean) {
        toggleBulkStatus(setOf(idOrPath), isStarred = isStarred)
    }

    fun toggleBulkStatus(idsOrPaths: Set<String>, isLocked: Boolean? = null, isStarred: Boolean? = null) {
        viewModelScope.launch {
            isLoading = true
            api?.bulkSetStatus(idsOrPaths, isLocked, isStarred)
            allFiles = api?.getFileSystem() ?: emptyList()
            isLoading = false
        }
    }

    fun setPin(pin: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = api?.setPin(pin) ?: false
            callback(ok)
        }
    }

    fun verifyPin(pin: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = api?.verifyPin(pin) ?: false
            if (ok) isVerified = true
            callback(ok)
        }
    }

    fun checkHasPin(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = api?.hasPin() ?: false
            callback(ok)
        }
    }

    fun removePin(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            api?.removePin()
            isVerified = false
            callback(true)
        }
    }

    fun uploadFile(uri: Uri) {
        viewModelScope.launch {
            api?.let { disbox ->
                val fileName = getFileName(uri)
                val path = if (currentPath == "/") fileName
                           else "${currentPath.trimStart('/')}/$fileName"
                val notificationId = fileName.hashCode()
                try {
                    disbox.uploadFile(uri, path) { p ->
                        progressMap = progressMap.toMutableMap().apply { put(fileName, p) }
                        notificationHelper.showProgressNotification(notificationId, "Uploading $fileName", p, true)
                    }
                    allFiles = disbox.getFileSystem()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    progressMap = progressMap.toMutableMap().apply { remove(fileName) }
                }
            }
        }
    }

    fun downloadFile(file: DisboxFile) {
        viewModelScope.launch {
            val name = file.path.split("/").last()
            val destDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "disbox_downloads"
            )
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, name)
            val notificationId = name.hashCode()
            try {
                api?.downloadFile(file, destFile) { p ->
                    progressMap = progressMap.toMutableMap().apply { put(name, p) }
                    notificationHelper.showProgressNotification(notificationId, "Downloading $name", p, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                progressMap = progressMap.toMutableMap().apply { remove(name) }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1) name = it.getString(idx)
            }
        }
        return name
    }

    fun toggleSelection(id: String) {
        selectionSet = if (selectionSet.contains(id)) {
            selectionSet - id
        } else {
            selectionSet + id
        }
    }

    fun clearSelection() {
        selectionSet = emptySet()
    }

    fun startMove(items: Set<String>) {
        moveCopyMode = "move"
        moveCopyItems = items
        selectionSet = emptySet()
    }

    fun startCopy(items: Set<String>) {
        moveCopyMode = "copy"
        moveCopyItems = items
        selectionSet = emptySet()
    }

    fun cancelMoveCopy() {
        moveCopyMode = null
        moveCopyItems = emptySet()
    }

    fun unlockTo(idsOrPaths: Set<String>, destDir: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                // 1. Move items first
                val targetPath = if (destDir == "/") "" else destDir.trimStart('/')
                idsOrPaths.forEach { idOrPath ->
                    val file = allFiles.find { it.id == idOrPath || it.path == idOrPath }
                    if (file != null) api?.movePath(file.path, targetPath, file.id)
                    else api?.movePath(idOrPath, targetPath, null)
                }
                // 2. Then unlock them
                api?.bulkSetStatus(idsOrPaths, isLocked = false)
                allFiles = api?.getFileSystem() ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                selectionSet = emptySet()
                isLoading = false
            }
        }
    }

    fun paste(destDir: String) {
        val mode = moveCopyMode ?: return
        val items = moveCopyItems
        val targetPath = if (destDir == "/") "" else destDir.trimStart('/')

        viewModelScope.launch {
            isLoading = true
            try {
                items.forEach { idOrPath ->
                    val file = allFiles.find { it.id == idOrPath || it.path == idOrPath }
                    if (mode == "move") {
                        if (file != null) api?.movePath(file.path, targetPath, file.id)
                        else api?.movePath(idOrPath, targetPath, null)
                    } else {
                        if (file != null) api?.copyPath(file.path, targetPath, file.id)
                        else api?.copyPath(idOrPath, targetPath, null)
                    }
                }
                allFiles = api?.getFileSystem() ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                moveCopyMode = null
                moveCopyItems = emptySet()
                isLoading = false
            }
        }
    }
}
