package com.disbox.mobile

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class DisboxFile(
    val id: String = UUID.randomUUID().toString(),
    var path: String,
    val messageIds: List<String>,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis()
)

class DisboxApi(private val context: Context, var webhookUrl: String) {
    private val client = OkHttpClient()
    private val gson = Gson()
    var hashedWebhook: String? = null
    var lastSyncedId: String? = null
    var chunkSize = 8 * 1024 * 1024 // 8MB
    var onStatusChange: ((String) -> Unit)? = null
    
    private val metadataDir: File by lazy {
        val dir = File(Environment.getExternalStorageDirectory(), "disbox")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    suspend fun init(forceSyncId: String? = null): String = withContext(Dispatchers.IO) {
        hashedWebhook = hashWebhook(webhookUrl)
        syncMetadata(forceSyncId)
        hashedWebhook!!
    }

    private fun hashWebhook(url: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun getMsgIdFromDiscovery(): String? = withContext(Dispatchers.IO) {
        val localFile = File(metadataDir, "$hashedWebhook.json")
        var localMsgId: String? = null
        if (localFile.exists()) {
            try {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(localFile.readText(), type)
                if (map["isDirty"] == true) return@withContext "pending"
                localMsgId = map["lastMsgId"] as? String
            } catch (e: Exception) { e.printStackTrace() }
        }

        var webhookMsgId: String? = null
        try {
            val request = Request.Builder().url(webhookUrl).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val map: Map<String, Any> = gson.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)
                    val name = map["name"] as? String
                    val match = Regex("dbx[:\\s]+(\\d+)").find(name ?: "")
                    webhookMsgId = match?.groupValues?.get(1)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        val candidates = listOfNotNull(localMsgId, webhookMsgId)
        if (candidates.isEmpty()) return@withContext null
        candidates.maxByOrNull { it.toLongOrNull() ?: 0L }
    }

    private suspend fun downloadMetadataFromMsg(msgId: String): List<DisboxFile> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$webhookUrl/messages/$msgId").build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Message $msgId not accessible")
        
        val body = response.body?.string() ?: throw Exception("Empty body")
        val map: Map<String, Any> = gson.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)
        @Suppress("UNCHECKED_CAST")
        val attachments = map["attachments"] as? List<Map<String, Any>>
        val url = attachments?.firstOrNull { (it["filename"] as? String)?.contains("metadata.json") == true }?.get("url") as? String
            ?: attachments?.firstOrNull()?.get("url") as? String
            ?: throw Exception("No attachment")

        val attReq = Request.Builder().url(url).build()
        val attRes = client.newCall(attReq).execute()
        if (!attRes.isSuccessful) throw Exception("Failed to download metadata file")
        
        val attBody = attRes.body?.string() ?: throw Exception("Empty metadata file")
        gson.fromJson(attBody, object : TypeToken<List<DisboxFile>>() {}.type)
    }

    suspend fun syncMetadata(forceId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val msgId = forceId ?: getMsgIdFromDiscovery() ?: return@withContext false
            if (msgId == "pending") return@withContext true

            val files = downloadMetadataFromMsg(msgId)
            saveMetadataToLocal(files, msgId)
            lastSyncedId = msgId
            onStatusChange?.invoke("synced")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            onStatusChange?.invoke("error")
            false
        }
    }

    private suspend fun saveMetadataToLocal(files: List<DisboxFile>, msgId: String? = null, isDirty: Boolean = false) = withContext(Dispatchers.IO) {
        val file = File(metadataDir, "$hashedWebhook.json")
        val map = mapOf(
            "lastMsgId" to (msgId ?: lastSyncedId),
            "files" to files,
            "isDirty" to isDirty,
            "updatedAt" to System.currentTimeMillis()
        )
        file.writeText(gson.toJson(map))
        if (isDirty) onStatusChange?.invoke("dirty")
    }

    suspend fun getFileSystem(): List<DisboxFile> = withContext(Dispatchers.IO) {
        val file = File(metadataDir, "$hashedWebhook.json")
        if (!file.exists()) return@withContext emptyList()
        try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(file.readText(), type)
            val filesListJson = gson.toJson(map["files"])
            gson.fromJson(filesListJson, object : TypeToken<List<DisboxFile>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun uploadMetadataToDiscord(explicitFiles: List<DisboxFile>? = null) = withContext(Dispatchers.IO) {
        val files = explicitFiles ?: getFileSystem()
        val json = gson.toJson(files)
        
        onStatusChange?.invoke("uploading")
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "disbox_metadata.json", json.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
            
        val request = Request.Builder()
            .url("$webhookUrl?wait=true")
            .post(body)
            .build()
            
        try {
            val res = client.newCall(request).execute()
            if (res.isSuccessful) {
                val resBody = res.body?.string()
                val map: Map<String, Any> = gson.fromJson(resBody, object : TypeToken<Map<String, Any>>() {}.type)
                val newMsgId = map["id"] as? String
                if (newMsgId != null) {
                    saveMetadataToLocal(files, newMsgId, false)
                    lastSyncedId = newMsgId
                    onStatusChange?.invoke("synced")
                    
                    // Update webhook name
                    val patchBody = gson.toJson(mapOf("name" to "dbx: $newMsgId")).toRequestBody("application/json".toMediaTypeOrNull())
                    val patchReq = Request.Builder().url(webhookUrl).patch(patchBody).build()
                    try { client.newCall(patchReq).execute() } catch (e: Exception) {}
                }
            } else {
                onStatusChange?.invoke("error")
            }
        } catch (e: Exception) { 
            e.printStackTrace()
            onStatusChange?.invoke("error")
        }
    }

    suspend fun createFile(path: String, msgIds: List<String>, size: Long, id: String? = null) {
        val files = getFileSystem().toMutableList()
        val fileId = id ?: UUID.randomUUID().toString()
        val entry = DisboxFile(fileId, path, msgIds, size)
        val idx = files.indexOfFirst { it.id == fileId }
        if (idx >= 0) files[idx] = entry else files.add(entry)
        saveMetadataToLocal(files, isDirty = true)
        uploadMetadataToDiscord(files)
    }

    suspend fun createFolder(folderName: String, currentPath: String) {
        val dirPath = if (currentPath == "/") "" else currentPath.trimStart('/')
        val folderPath = if (dirPath.isEmpty()) "$folderName/.keep" else "$dirPath/$folderName/.keep"
        createFile(folderPath, emptyList(), 0)
    }

    suspend fun deletePath(targetPath: String, id: String? = null) {
        val files = getFileSystem().filterNot { 
            (id != null && it.id == id) || (id == null && it.path == targetPath) || it.path.startsWith("$targetPath/")
        }
        saveMetadataToLocal(files, isDirty = true)
        uploadMetadataToDiscord(files)
    }

    suspend fun bulkDelete(items: List<String>) {
        val files = getFileSystem()
        val filtered = files.filterNot { f ->
            items.any { item ->
                f.id == item || f.path == item || f.path.startsWith("$item/")
            }
        }
        saveMetadataToLocal(filtered, isDirty = true)
        uploadMetadataToDiscord(filtered)
    }

    suspend fun renamePath(oldPath: String, newPath: String, id: String? = null) {
        var found = false
        val files = getFileSystem().map {
            when {
                (id != null && it.id == id) || (id == null && it.path == oldPath) -> { found = true; it.copy(path = newPath) }
                it.path.startsWith("$oldPath/") -> { found = true; it.copy(path = it.path.replaceFirst("$oldPath/", "$newPath/")) }
                else -> it
            }
        }
        if (found) {
            saveMetadataToLocal(files, isDirty = true)
            uploadMetadataToDiscord(files)
        }
    }

    suspend fun movePath(sourcePath: String, destDir: String, id: String? = null) {
        val name = sourcePath.split("/").last()
        val newPath = if (destDir.isEmpty()) name else "$destDir/$name"
        renamePath(sourcePath, newPath, id)
    }

    suspend fun copyPath(sourcePath: String, destDir: String, id: String? = null) {
        val files = getFileSystem().toMutableList()
        val name = sourcePath.split("/").last()
        val newPath = if (destDir.isEmpty()) name else "$destDir/$name"
        
        val toAdd = mutableListOf<DisboxFile>()
        files.forEach { f ->
            if ((id != null && f.id == id) || (id == null && f.path == sourcePath)) {
                toAdd.add(f.copy(id = UUID.randomUUID().toString(), path = newPath, createdAt = System.currentTimeMillis()))
            } else if (f.path.startsWith("$sourcePath/")) {
                toAdd.add(f.copy(id = UUID.randomUUID().toString(), path = f.path.replaceFirst("$sourcePath/", "$newPath/"), createdAt = System.currentTimeMillis()))
            }
        }
        if (toAdd.isNotEmpty()) {
            files.addAll(toAdd)
            saveMetadataToLocal(files, isDirty = true)
            uploadMetadataToDiscord(files)
        }
    }

    suspend fun uploadFile(uri: Uri, uploadPath: String, onProgress: (Float) -> Unit): List<String> = withContext(Dispatchers.IO) {
        val fileId = UUID.randomUUID().toString()
        val resolver = context.contentResolver
        val cursor = resolver.query(uri, null, null, null, null)
        var size = 0L
        var name = "file"
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx != -1) size = it.getLong(sizeIdx)
                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1) name = it.getString(nameIdx)
            }
        }

        val numChunks = Math.ceil(size.toDouble() / chunkSize).toInt().coerceAtLeast(1)
        val messageIds = mutableListOf<String>()
        val inputStream = resolver.openInputStream(uri) ?: throw Exception("Failed to open file")
        
        inputStream.use { stream ->
            val buffer = ByteArray(chunkSize)
            for (i in 0 until numChunks) {
                var bytesRead = 0
                while (bytesRead < chunkSize) {
                    val read = stream.read(buffer, bytesRead, chunkSize - bytesRead)
                    if (read == -1) break
                    bytesRead += read
                }
                if (bytesRead == 0 && i > 0) break
                
                val chunkData = buffer.copyOf(bytesRead)
                val chunkName = "${fileId}_${name}.part$i"
                
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", chunkName, chunkData.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    .build()
                
                val request = Request.Builder().url("$webhookUrl?wait=true").post(body).build()
                var success = false
                var retries = 0
                while (!success && retries < 3) {
                    try {
                        val response = client.newCall(request).execute()
                        if (response.code == 429) {
                            val rBody = response.body?.string()
                            val map: Map<String, Any>? = rBody?.let { gson.fromJson(it, object : TypeToken<Map<String, Any>>() {}.type) }
                            val retryAfter = ((map?.get("retry_after") as? Double) ?: 5.0) + 1.0
                            Thread.sleep((retryAfter * 1000).toLong())
                            continue
                        }
                        if (!response.isSuccessful) throw Exception("Chunk $i upload failed")
                        val rBody = response.body?.string() ?: ""
                        val map: Map<String, Any> = gson.fromJson(rBody, object : TypeToken<Map<String, Any>>() {}.type)
                        messageIds.add(map["id"] as String)
                        success = true
                    } catch (e: Exception) {
                        retries++
                        if (retries >= 3) throw e
                        Thread.sleep(2000)
                    }
                }
                onProgress((i + 1).toFloat() / numChunks)
            }
        }
        
        val updatedFiles = getFileSystem().toMutableList()
        val entry = DisboxFile(fileId, uploadPath, messageIds, size)
        val idx = updatedFiles.indexOfFirst { it.id == fileId }
        if (idx >= 0) updatedFiles[idx] = entry else updatedFiles.add(entry)
        saveMetadataToLocal(updatedFiles, isDirty = true)
        uploadMetadataToDiscord(updatedFiles)
        messageIds
    }

    suspend fun downloadFile(file: DisboxFile, destFile: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val messageIds = file.messageIds
        FileOutputStream(destFile).use { out ->
            for (i in messageIds.indices) {
                val msgUrl = "$webhookUrl/messages/${messageIds[i]}"
                val msgReq = Request.Builder().url(msgUrl).build()
                val msgRes = client.newCall(msgReq).execute()
                if (!msgRes.isSuccessful) throw Exception("Failed to fetch msg ${messageIds[i]}")
                val body = msgRes.body?.string() ?: ""
                val map: Map<String, Any> = gson.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)
                @Suppress("UNCHECKED_CAST")
                val attachments = map["attachments"] as? List<Map<String, Any>>
                val url = attachments?.firstOrNull()?.get("url") as? String ?: throw Exception("No attachment URL")
                
                val chunkReq = Request.Builder().url(url).build()
                val chunkRes = client.newCall(chunkReq).execute()
                if (!chunkRes.isSuccessful) throw Exception("Failed to download chunk")
                
                chunkRes.body?.byteStream()?.use { it.copyTo(out) }
                onProgress((i + 1).toFloat() / messageIds.size)
            }
        }
    }
}
