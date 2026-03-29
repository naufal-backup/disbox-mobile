package com.disbox.mobile

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.disbox.mobile.data.repository.DisboxRepository
import com.disbox.mobile.data.service.DisboxApiService
import com.disbox.mobile.DisboxDatabase
import com.disbox.mobile.domain.usecase.*
import com.disbox.mobile.utils.I18n
import com.disbox.mobile.utils.FileUtils
import com.disbox.mobile.model.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class DisboxViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = DisboxApiService()
    private val db = DisboxDatabase.getDatabase(application)
    val repository = DisboxRepository(application, apiService, db)
    val api get() = repository

    // UseCases
    private val syncMetadataUseCase = SyncMetadataUseCase(repository)
    private val uploadFileUseCase = UploadFileUseCase(repository)
    private val downloadFileUseCase = DownloadFileUseCase(repository)
    private val fileOpsUseCase = FileOperationsUseCase(repository)

    // UI State
    var webhookUrl by mutableStateOf("")
    var isConnected by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var allFiles by mutableStateOf<List<DisboxFile>>(emptyList())
    var currentPath by mutableStateOf("/")
    var activePage by mutableStateOf("drive")
    var metadataStatus by mutableStateOf("synced")
    
    // Settings
    var theme by mutableStateOf("dark")
    var language by mutableStateOf("en")
    var accentColor by mutableStateOf("#5865F2")
    var showPreviews by mutableStateOf(true)
    var showRecent by mutableStateOf(true)
    var cloudSaveEnabled by mutableStateOf(false)
    var showImagePreviews by mutableStateOf(true)
    var showVideoPreviews by mutableStateOf(true)
    var showMusicPreviews by mutableStateOf(true)
    var zoomLevel by mutableStateOf(1.0f)
    var viewMode by mutableStateOf("grid")
    var sortMode by mutableStateOf("name")
    
    // Selection and Transfers
    var selectionSet = mutableStateListOf<String>()
    var transferProgress = mutableStateMapOf<String, Float>()
    var moveCopyMode by mutableStateOf<String?>(null) // "move" or "copy"
    var moveCopyItems by mutableStateOf<Set<String>>(emptySet())
    
    // Sharing
    var shareEnabled by mutableStateOf(false)
    var shareLinks by mutableStateOf<List<ShareLink>>(emptyList())
    var cfWorkerUrl by mutableStateOf("")
    
    // Player
    var currentPlayingFile by mutableStateOf<DisboxFile?>(null)
    var isPlaying by mutableStateOf(false)
    var playbackProgress by mutableStateOf(0f)
    var playbackPosition by mutableStateOf(0L)
    var playbackDuration by mutableStateOf(0L)
    var repeatMode by mutableStateOf(0) // 0: none, 1: one, 2: all

    // Auth
    var savedWebhooks by mutableStateOf<List<String>>(emptyList())
    var isVerified by mutableStateOf(false)

    private val prefs = application.getSharedPreferences("disbox_prefs", Context.MODE_PRIVATE)

    init {
        loadSettings()
        loadSavedWebhooks()
    }

    private fun loadSettings() {
        theme = prefs.getString("theme", "dark") ?: "dark"
        language = prefs.getString("language", "en") ?: "en"
        accentColor = prefs.getString("accent_color", "#5865F2") ?: "#5865F2"
        showPreviews = prefs.getBoolean("show_previews", true)
        showRecent = prefs.getBoolean("show_recent", true)
        cloudSaveEnabled = prefs.getBoolean("cloud_save_enabled", false)
        showImagePreviews = prefs.getBoolean("show_image_previews", true)
        showVideoPreviews = prefs.getBoolean("show_video_previews", true)
        showMusicPreviews = prefs.getBoolean("show_music_previews", true)
        zoomLevel = prefs.getFloat("zoom_level", 1.0f)
        viewMode = prefs.getString("view_mode", "grid") ?: "grid"
        sortMode = prefs.getString("sort_mode", "name") ?: "name"
    }

    private fun loadSavedWebhooks() {
        savedWebhooks = prefs.getStringSet("webhooks", emptySet())?.toList() ?: emptyList()
    }

    fun t(key: String, args: Map<String, String> = emptyMap()) = I18n.t(language, key, args)

    fun connect(url: String, forceId: String? = null) {
        if (url.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            try {
                webhookUrl = url
                repository.init(url, forceId)
                isConnected = true
                saveWebhook(url)
                refresh()
                loadShareLinks()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    private fun saveWebhook(url: String) {
        val set = prefs.getStringSet("webhooks", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(url)
        prefs.edit().putStringSet("webhooks", set).apply()
        loadSavedWebhooks()
    }

    fun removeWebhook(url: String) {
        val set = prefs.getStringSet("webhooks", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(url)
        prefs.edit().putStringSet("webhooks", set).apply()
        loadSavedWebhooks()
    }

    fun disconnect() {
        isConnected = false
        webhookUrl = ""
        allFiles = emptyList()
        isVerified = false
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            try {
                syncMetadataUseCase()
                allFiles = repository.getFileSystem()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun navigate(path: String) {
        currentPath = path
    }

    fun setPage(page: String) {
        activePage = page
    }

    fun setView(mode: String) {
        viewMode = mode
        prefs.edit().putString("view_mode", mode).apply()
    }

    fun setZoom(level: Float) {
        zoomLevel = level
        prefs.edit().putFloat("zoom_level", level).apply()
    }

    fun updateSortMode(mode: String) {
        sortMode = mode
        prefs.edit().putString("sort_mode", mode).apply()
    }

    // File Operations
    fun createFolder(name: String) {
        viewModelScope.launch {
            fileOpsUseCase.createFolder(name, currentPath)
            refresh()
        }
    }

    fun deletePaths(idsOrPaths: List<String>) {
        viewModelScope.launch {
            fileOpsUseCase.deletePaths(idsOrPaths)
            clearSelection()
            refresh()
        }
    }

    fun renamePath(oldPath: String, newName: String, id: String? = null) {
        viewModelScope.launch {
            fileOpsUseCase.renamePath(oldPath, newName, id)
            refresh()
        }
    }

    fun toggleSelection(idOrPath: String) {
        if (selectionSet.contains(idOrPath)) selectionSet.remove(idOrPath)
        else selectionSet.add(idOrPath)
    }

    fun clearSelection() {
        selectionSet.clear()
    }

    fun toggleBulkStatus(idsOrPaths: Collection<String>, isLocked: Boolean? = null, isStarred: Boolean? = null) {
        viewModelScope.launch {
            fileOpsUseCase.toggleStatus(idsOrPaths.toSet(), isLocked, isStarred)
            refresh()
        }
    }

    fun uploadFiles(uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                val name = UUID.randomUUID().toString() 
                transferProgress[name] = 0f
                uploadFileUseCase(uri, currentPath.trim('/') + "/$name", 8 * 1024 * 1024) { 
                    transferProgress[name] = it
                }
            }
            refresh()
        }
    }

    fun downloadFile(file: DisboxFile) {
        viewModelScope.launch {
            val dest = File(getApplication<Application>().getExternalFilesDir(null), file.path.split("/").last())
            downloadFileUseCase(file, dest) { }
        }
    }

    // Sharing
    fun loadShareLinks() {
        viewModelScope.launch {
            val settings = repository.getShareSettings()
            shareEnabled = settings.enabled
            cfWorkerUrl = settings.cf_worker_url ?: ""
            shareLinks = repository.getShareLinks()
        }
    }

    fun createShareLink(path: String, id: String?, permission: String, expiresAt: Long?, onResult: (Map<String, Any>) -> Unit) {
        viewModelScope.launch {
            val res = repository.createShareLink(path, id, permission, expiresAt)
            loadShareLinks()
            onResult(res)
        }
    }

    fun revokeShareLink(id: String, token: String) {
        viewModelScope.launch {
            // repository.revokeShareLink(id, token)
            loadShareLinks()
        }
    }

    fun revokeAllLinks() {
        viewModelScope.launch {
            // repository.revokeAllLinks()
            loadShareLinks()
        }
    }

    // Security
    fun checkHasPin(onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.verifyPin("")) } // Dummy check
    }

    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = repository.verifyPin(pin)
            if (res) isVerified = true
            onResult(res)
        }
    }

    // Move/Copy
    fun startMove(items: Collection<String>) { moveCopyMode = "move"; moveCopyItems = items.toSet() }
    fun startCopy(items: Collection<String>) { moveCopyMode = "copy"; moveCopyItems = items.toSet() }
    fun paste(dest: String) { 
        viewModelScope.launch {
            moveCopyItems.forEach { item ->
                if (moveCopyMode == "move") repository.renamePath(item, dest)
            }
            moveCopyMode = null
            moveCopyItems = emptySet()
            refresh()
        }
    }
    fun unlockTo(items: Collection<String>, dest: String) {
        viewModelScope.launch {
            repository.bulkSetStatus(items.toSet(), isLocked = false)
            refresh()
        }
    }
    fun exportCloudSaveAsZip(name: String, onResult: (File?) -> Unit) { }
    fun updateLanguage(code: String) { language = code; prefs.edit().putString("language", code).apply() }
    fun updateAccentColor(hex: String) { accentColor = hex; prefs.edit().putString("accent_color", hex).apply() }
    fun toggleTheme() { theme = if (theme == "dark") "light" else "dark"; prefs.edit().putString("theme", theme).apply() }
    fun updatePreviews(v: Boolean) { showPreviews = v; prefs.edit().putBoolean("show_previews", v).apply() }
    fun updateRecent(v: Boolean) { showRecent = v; prefs.edit().putBoolean("show_recent", v).apply() }
    fun updateCloudSaveEnabled(v: Boolean) { cloudSaveEnabled = v; prefs.edit().putBoolean("cloud_save_enabled", v).apply() }
    fun updateRepeatMode(m: Int) { repeatMode = m }
}
