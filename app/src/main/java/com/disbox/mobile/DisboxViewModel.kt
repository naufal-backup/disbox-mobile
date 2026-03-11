package com.disbox.mobile

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.io.File

class DisboxViewModel(application: Application) : AndroidViewModel(application) {
    var isConnected by mutableStateOf(false)
    var webhookUrl by mutableStateOf("")
    var api by mutableStateOf<DisboxApi?>(null)
    var allFiles by mutableStateOf<List<DisboxFile>>(emptyList())
    var currentPath by mutableStateOf("/")
    var isLoading by mutableStateOf(false)
    var progressMap by mutableStateOf<Map<String, Float>>(emptyMap())
    var selectionSet by mutableStateOf<Set<String>>(emptySet())
    var theme by mutableStateOf("dark")

    fun connect(url: String) {
        webhookUrl = url
        api = DisboxApi(getApplication(), url)
        viewModelScope.launch {
            isLoading = true
            try {
                api!!.init()
                allFiles = api!!.getFileSystem()
                isConnected = true
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun disconnect() {
        isConnected = false
        api = null
        allFiles = emptyList()
        currentPath = "/"
        selectionSet = emptySet()
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            api?.syncMetadata()
            allFiles = api?.getFileSystem() ?: emptyList()
            isLoading = false
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

    fun deleteSelected() {
        viewModelScope.launch {
            isLoading = true
            api?.bulkDelete(selectionSet.toList())
            selectionSet = emptySet()
            allFiles = api?.getFileSystem() ?: emptyList()
            isLoading = false
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

    fun uploadFile(uri: Uri) {
        viewModelScope.launch {
            api?.let { disbox ->
                val fileName = getFileName(uri)
                val path = if (currentPath == "/") fileName else "${currentPath.trimStart('/')}/$fileName"
                try {
                    disbox.uploadFile(uri, path) { p ->
                        progressMap = progressMap.toMutableMap().apply { put(fileName, p) }
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
            val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "disbox_downloads")
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, name)
            try {
                api?.downloadFile(file, destFile) { p ->
                    progressMap = progressMap.toMutableMap().apply { put(name, p) }
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
}
