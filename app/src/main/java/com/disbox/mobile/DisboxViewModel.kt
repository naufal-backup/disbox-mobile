package com.disbox.mobile

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.disbox.mobile.data.repository.DisboxRepository
import com.disbox.mobile.data.service.DisboxApiService
import com.disbox.mobile.domain.usecase.SyncMetadataUseCase
import com.disbox.mobile.domain.usecase.UploadFileUseCase
import com.disbox.mobile.domain.usecase.DownloadFileUseCase
import com.disbox.mobile.domain.usecase.FileOperationsUseCase
import com.disbox.mobile.model.*
import com.disbox.mobile.utils.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class DisboxViewModel(val app: Application) : AndroidViewModel(app) {
    private val apiService = DisboxApiService(app)
    private val db = DisboxDatabase.getDatabase(app)
    val repository = DisboxRepository(app, apiService, db)

    // UseCases
    private val syncMetadataUseCase = SyncMetadataUseCase(repository)
    private val uploadFileUseCase = UploadFileUseCase(repository)
    private val downloadFileUseCase = DownloadFileUseCase(repository)
    private val fileOpsUseCase = FileOperationsUseCase(repository)

    // UI State
    var webhookUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var isConnected by mutableStateOf(false)
    var isLoggedIn by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var allFiles = mutableStateListOf<DisboxFile>()
    var currentPath by mutableStateOf("/")
    var activePage by mutableStateOf("drive")
    var metadataStatus by mutableStateOf("synced")
    
    // Selection and Transfers
    var selectionSet = mutableStateListOf<String>()
    var transferProgress = mutableStateMapOf<String, Float>()
    var moveCopyMode by mutableStateOf<String?>(null)
    var moveCopyItems by mutableStateOf<Set<String>>(emptySet())
    
    // Player
    var currentPlayingFile by mutableStateOf<DisboxFile?>(null)
    var isPlaying by mutableStateOf(false)
    var playbackProgress by mutableStateOf(0f)
    var playbackPosition by mutableStateOf(0L)
    var playbackDuration by mutableStateOf(0L)
    var repeatMode by mutableStateOf(0)

    // Settings
    var theme by mutableStateOf("dark")
    var language by mutableStateOf("en")
    var accentColor by mutableStateOf("#5865F2")
    var showPreviews by mutableStateOf(true)
    var showImagePreviews by mutableStateOf(true)
    var showVideoPreviews by mutableStateOf(true)
    var showMusicPreviews by mutableStateOf(true)
    var showRecent by mutableStateOf(true)
    var cloudSaveEnabled by mutableStateOf(false)
    var shareEnabled by mutableStateOf(false)
    var zoomLevel by mutableStateOf(1.0f)
    var viewMode by mutableStateOf("grid")
    var sortMode by mutableStateOf("name")
    
    // Sharing State
    var shareLinks by mutableStateOf<List<ShareLink>>(emptyList())
    var cfWorkerUrl by mutableStateOf("")
    
    var savedWebhooks by mutableStateOf<List<String>>(emptyList())
    var isVerified by mutableStateOf(false)

    private val prefs = app.getSharedPreferences("disbox_prefs", Context.MODE_PRIVATE)

    init {
        loadSettings()
        // Cek login session
        val savedUser = prefs.getString("username", null)
        val savedWebhook = prefs.getString("webhook", null)
        apiService.authToken = prefs.getString("auth_token", null)

        if (savedUser != null && savedWebhook != null) {
            isLoggedIn = true
            username = savedUser
            connect(savedWebhook, user = savedUser)
        } else if (savedWebhook != null) {
            connect(savedWebhook)
        }
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

    fun t(key: String, args: Map<String, String> = emptyMap()) = I18n.t(language, key, args)

    fun login(user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            try {
                val res = apiService.login(mapOf("username" to user, "password" to pass))
                val isOk = res?.get("ok")?.toString() == "true"
                if (isOk) {
                    val wurl = res?.get("webhook_url")?.toString() ?: ""
                    username = res?.get("username")?.toString() ?: ""
                    val token = res?.get("token")?.toString()
                    
                    apiService.authToken = token
                    prefs.edit().putString("auth_token", token).apply()

                    isLoggedIn = true
                    prefs.edit().putString("username", username).apply()
                    webhookUrl = wurl
                    repository.init(wurl, user = username)
                    isConnected = true
                    prefs.edit().putString("webhook", wurl).apply()
                    refresh()
                    loadShareLinks()
                    onResult(true, null)
                } else {
                    val errorMsg = res?.get("error")?.toString() ?: res?.get("message")?.toString() ?: "Login failed"
                    onResult(false, errorMsg)
                }
            } catch (e: Exception) { 
                e.printStackTrace()
                onResult(false, "Network error: ${e.message}") 
            }
            finally { isLoading = false }
        }
    }

    fun register(user: String, pass: String, wurl: String, metaUrl: String?, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            try {
                val body = mutableMapOf("username" to user, "password" to pass, "webhook_url" to wurl)
                if (!metaUrl.isNullOrBlank()) body["metadata_url"] = metaUrl
                val res = apiService.register(body)
                val isOk = res?.get("ok")?.toString() == "true"
                if (isOk) {
                    onResult(true, null)
                } else {
                    val errorMsg = res?.get("error")?.toString() ?: res?.get("message")?.toString() ?: "Registration failed"
                    onResult(false, errorMsg)
                }
            } catch (e: Exception) { 
                e.printStackTrace()
                onResult(false, "Network error: ${e.message}") 
            }
            finally { isLoading = false }
        }
    }

    fun connectWithVerify(url: String, onResult: (Boolean, String?) -> Unit) {
        if (url.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            try {
                val res = apiService.verifyWebhook(url)
                val isOk = res?.get("ok")?.toString() == "true"
                if (isOk) {
                    val token = res?.get("token")?.toString()
                    apiService.authToken = token
                    prefs.edit().putString("auth_token", token).apply()
                    
                    connect(url)
                    onResult(true, null)
                } else {
                    val errorMsg = res?.get("error")?.toString() ?: res?.get("message")?.toString() ?: "Verification failed"
                    onResult(false, errorMsg)
                }
            } catch (e: Exception) { 
                e.printStackTrace()
                onResult(false, "Network error: ${e.message}") 
            }
            finally { isLoading = false }
        }
    }

    fun connect(url: String, forceId: String? = null, metadataUrl: String? = null, user: String? = null) {
        if (url.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            try {
                webhookUrl = url
                username = user ?: ""
                repository.init(url, forceId, metadataUrl, user)
                isConnected = true
                prefs.edit().putString("webhook", url).apply()
                if (user != null) prefs.edit().putString("username", user).apply()
                refresh()
                loadShareLinks()
            } catch (e: Exception) { 
                e.printStackTrace()
                isConnected = false
            } finally { isLoading = false }
        }
    }

    fun disconnect() {
        isConnected = false
        isLoggedIn = false
        username = ""
        webhookUrl = ""
        allFiles.clear()
        apiService.logout()
        prefs.edit().remove("username").remove("webhook").remove("auth_token").apply()
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            try {
                syncMetadataUseCase()
                val files = repository.getFileSystem()
                allFiles.clear()
                allFiles.addAll(files)
            } catch (e: Exception) { e.printStackTrace() } finally { isLoading = false }
        }
    }

    fun createFolder(name: String) {
        val folderPath = (if (currentPath == "/") name else "${currentPath.trim('/')}/$name") + "/.keep"
        val tempId = "temp-${System.currentTimeMillis()}"
        val optFolder = DisboxFile(tempId, folderPath, emptyList(), 0, isOptimistic = true)
        allFiles.add(optFolder)
        viewModelScope.launch {
            try {
                repository.createFolder(name, currentPath)
                val idx = allFiles.indexOfFirst { it.id == tempId }
                if (idx >= 0) allFiles[idx] = allFiles[idx].copy(isOptimistic = false)
                repository.persistCloud(allFiles.toList())
            } catch (e: Exception) {
                allFiles.removeIf { it.id == tempId }
            }
        }
    }

    fun deletePaths(idsOrPaths: List<String>) {
        val backup = allFiles.toList()
        allFiles.removeIf { f -> idsOrPaths.any { it == f.id || it == f.path || f.path.startsWith("$it/") } }
        clearSelection()
        viewModelScope.launch {
            try {
                repository.deletePaths(idsOrPaths)
                repository.persistCloud(allFiles.toList())
            } catch (e: Exception) {
                allFiles.clear()
                allFiles.addAll(backup)
            }
        }
    }

    fun uploadFiles(uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                val fileName = FileUtils.getFileName(app, uri)
                val fileSize = FileUtils.getFileSize(app, uri)
                // Deterministic fileId for resume
                val b64Name = android.util.Base64.encodeToString(fileName.toByteArray(), android.util.Base64.NO_WRAP).replace("=", "")
                val fileId = "res_${b64Name}_${fileSize}"
                
                val path = if (currentPath == "/") fileName else "${currentPath.trim('/')}/$fileName"
                val optFile = DisboxFile(fileId, path, emptyList(), fileSize, isOptimistic = true, progress = 0f)
                allFiles.add(optFile)
                
                try {
                    uploadFileUseCase(uri, path, 7 * 1024 * 1024, fileId) { p ->
                        val idx = allFiles.indexOfFirst { it.id == fileId }
                        if (idx >= 0) allFiles[idx] = allFiles[idx].copy(progress = p)
                    }
                    val idx = allFiles.indexOfFirst { it.id == fileId }
                    if (idx >= 0) allFiles[idx] = allFiles[idx].copy(isOptimistic = false)
                    repository.persistCloud(allFiles.toList())
                } catch (e: Exception) {
                    allFiles.removeIf { it.id == fileId }
                }
            }
        }
    }

    fun downloadFile(file: DisboxFile) {
        viewModelScope.launch {
            val dest = File(app.getExternalFilesDir(null), file.path.split("/").last())
            downloadFileUseCase(file, dest) { }
        }
    }

    fun downloadFileToCache(file: DisboxFile, dest: File, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                downloadFileUseCase(file, dest) { }
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun downloadFilePartial(file: DisboxFile, dest: File, startChunk: Int, endChunk: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val out = java.io.FileOutputStream(dest)
                out.use { stream ->
                    val actualEnd = endChunk.coerceAtMost(file.messageIds.size - 1)
                    for (i in startChunk..actualEnd) {
                        val data = repository.downloadFileChunk(file.messageIds[i].msgId, file.messageIds[i].index) ?: throw Exception("Chunk failed")
                        stream.write(data)
                    }
                }
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    // --- SHARING ---
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
        // Mock revoke
        viewModelScope.launch { loadShareLinks() }
    }

    fun revokeAllLinks() {
        // Mock revoke all
        viewModelScope.launch { loadShareLinks() }
    }

    fun checkHasPin(onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.hasPin()) }
    }

    fun setPin(pin: String?) {
        viewModelScope.launch { repository.setPin(pin) }
    }

    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = repository.verifyPin(pin)
            if (res) isVerified = true
            onResult(res)
        }
    }

    fun renamePath(oldPath: String, newName: String, id: String? = null) {
        viewModelScope.launch { repository.renamePath(oldPath, newName, id); refresh() }
    }

    fun toggleBulkStatus(idsOrPaths: Collection<String>, isLocked: Boolean? = null, isStarred: Boolean? = null) {
        viewModelScope.launch { repository.bulkSetStatus(idsOrPaths.toSet(), isLocked, isStarred); refresh() }
    }

    fun setPage(page: String) { activePage = page }
    fun navigate(path: String) { currentPath = path }
    fun setView(mode: String) { viewMode = mode; prefs.edit().putString("view_mode", mode).apply() }
    fun setZoom(level: Float) { zoomLevel = level; prefs.edit().putFloat("zoom_level", level).apply() }
    fun updateSortMode(mode: String) { sortMode = mode; prefs.edit().putString("sort_mode", mode).apply() }
    fun toggleSelection(idOrPath: String) {
        if (selectionSet.contains(idOrPath)) selectionSet.remove(idOrPath)
        else selectionSet.add(idOrPath)
    }
    fun clearSelection() { selectionSet.clear() }
    fun updateLanguage(code: String) { language = code; prefs.edit().putString("language", code).apply() }
    fun updateAccentColor(hex: String) { accentColor = hex; prefs.edit().putString("accent_color", hex).apply() }
    fun toggleTheme() { theme = if (theme == "dark") "light" else "dark"; prefs.edit().putString("theme", theme).apply() }
    fun updatePreviews(v: Boolean) { showPreviews = v; prefs.edit().putBoolean("show_previews", v).apply() }
    fun updateRecent(v: Boolean) { showRecent = v; prefs.edit().putBoolean("show_recent", v).apply() }
    fun updateCloudSaveEnabled(v: Boolean) { cloudSaveEnabled = v; prefs.edit().putBoolean("cloud_save_enabled", v).apply() }
    fun updateRepeatMode(m: Int) { repeatMode = m }
    fun startMove(items: Collection<String>) { moveCopyMode = "move"; moveCopyItems = items.toSet() }
    fun startCopy(items: Collection<String>) { moveCopyMode = "copy"; moveCopyItems = items.toSet() }
    fun paste(dest: String) { 
        viewModelScope.launch {
            moveCopyItems.forEach { item -> if (moveCopyMode == "move") repository.renamePath(item, dest) }
            moveCopyMode = null
            moveCopyItems = emptySet()
            refresh()
        }
    }
    fun unlockTo(items: Collection<String>, dest: String) {
        viewModelScope.launch { repository.bulkSetStatus(items.toSet(), isLocked = false); refresh() }
    }
    fun exportCloudSaveAsZip(name: String, onResult: (File?) -> Unit) { }
}
