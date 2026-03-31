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

class DisboxViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = DisboxApiService()
    private val db = DisboxDatabase.getDatabase(application)
    val repository = DisboxRepository(application, apiService, db)

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
    
    // Selection and Transfers
    var selectionSet = mutableStateListOf<String>()
    var moveCopyMode by mutableStateOf<String?>(null)
    var moveCopyItems by mutableStateOf<Set<String>>(emptySet())
    
    // Settings
    var theme by mutableStateOf("dark")
    var language by mutableStateOf("en")
    var zoomLevel by mutableStateOf(1.0f)
    var viewMode by mutableStateOf("grid")
    
    private val prefs = application.getSharedPreferences("disbox_prefs", Context.MODE_PRIVATE)

    init {
        loadSettings()
        // Cek login session
        val savedUser = prefs.getString("username", null)
        val savedWebhook = prefs.getString("webhook", null)
        if (savedUser != null && savedWebhook != null) {
            connect(savedWebhook, user = savedUser)
        } else if (savedWebhook != null) {
            connect(savedWebhook)
        }
    }

    private fun loadSettings() {
        theme = prefs.getString("theme", "dark") ?: "dark"
        language = prefs.getString("language", "en") ?: "en"
        zoomLevel = prefs.getFloat("zoom_level", 1.0f)
        viewMode = prefs.getString("view_mode", "grid") ?: "grid"
    }

    fun t(key: String, args: Map<String, String> = emptyMap()) = I18n.t(language, key, args)

    // --- AUTH ACTIONS ---

    fun login(user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            try {
                val res = apiService.login(mapOf("username" to user, "password" to pass))
                if (res != null && res["ok"] == true) {
                    val wurl = res["webhook_url"] as String
                    username = res["username"] as String
                    isLoggedIn = true
                    prefs.edit().putString("username", username).apply()
                    connect(wurl, user = username)
                    onResult(true, null)
                } else {
                    onResult(false, res?.get("error") as? String ?: "Login failed")
                }
            } catch (e: Exception) { onResult(false, e.message) }
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
                if (res != null && res["ok"] == true) {
                    onResult(true, null)
                } else {
                    onResult(false, res?.get("error") as? String ?: "Registration failed")
                }
            } catch (e: Exception) { onResult(false, e.message) }
            finally { isLoading = false }
        }
    }

    fun connect(url: String, forceId: String? = null, metadataUrl: String? = null, user: String? = null) {
        if (url.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            try {
                webhookUrl = url
                repository.init(url, forceId, metadataUrl, user)
                isConnected = true
                prefs.edit().putString("webhook", url).apply()
                refresh()
            } catch (e: Exception) { e.printStackTrace() } finally { isLoading = false }
        }
    }

    fun disconnect() {
        isConnected = false
        isLoggedIn = false
        username = ""
        webhookUrl = ""
        allFiles.clear()
        prefs.edit().remove("username").remove("webhook").apply()
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

    // --- OPTIMISTIC FILE OPERATIONS ---

    fun createFolder(name: String) {
        val folderPath = (if (currentPath == "/") name else "${currentPath.trim('/')}/$name") + "/.keep"
        val tempId = "temp-${System.currentTimeMillis()}"
        val optFolder = DisboxFile(tempId, folderPath, emptyList(), 0, isOptimistic = true)
        
        allFiles.add(optFolder)
        
        viewModelScope.launch {
            try {
                repository.createFolder(name, currentPath)
                // Update item optimistic jadi normal
                val idx = allFiles.indexOfFirst { it.id == tempId }
                if (idx >= 0) allFiles[idx] = allFiles[idx].copy(isOptimistic = false)
                // Jalankan sinkronisasi awan di latar belakang
                repository.persistCloud(allFiles.toList())
            } catch (e: Exception) {
                allFiles.removeIf { it.id == tempId } // Rollback
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
                allFiles.addAll(backup) // Rollback
            }
        }
    }

    fun uploadFiles(uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                val tempId = UUID.randomUUID().toString()
                val fileName = FileUtils.getFileName(application, uri)
                val path = if (currentPath == "/") fileName else "${currentPath.trim('/')}/$fileName"
                
                val optFile = DisboxFile(tempId, path, emptyList(), 0, isOptimistic = true, progress = 0f)
                allFiles.add(optFile)

                try {
                    repository.uploadFile(uri, path, 7 * 1024 * 1024) { p ->
                        val idx = allFiles.indexOfFirst { it.id == tempId }
                        if (idx >= 0) allFiles[idx] = allFiles[idx].copy(progress = p)
                    }
                    val idx = allFiles.indexOfFirst { it.id == tempId }
                    if (idx >= 0) allFiles[idx] = allFiles[idx].copy(isOptimistic = false)
                    repository.persistCloud(allFiles.toList())
                } catch (e: Exception) {
                    allFiles.removeIf { it.id == tempId }
                }
            }
        }
    }

    fun navigate(path: String) { currentPath = path }
    fun setView(mode: String) { viewMode = mode; prefs.edit().putString("view_mode", mode).apply() }
    fun toggleSelection(idOrPath: String) {
        if (selectionSet.contains(idOrPath)) selectionSet.remove(idOrPath)
        else selectionSet.add(idOrPath)
    }
    fun clearSelection() { selectionSet.clear() }
}
