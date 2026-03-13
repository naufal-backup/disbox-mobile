package com.disbox.mobile

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.*
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

data class DisboxFile(
    val id: String = UUID.randomUUID().toString(),
    var path: String,
    val messageIds: List<String>,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val isLocked: Boolean = false,
    val isStarred: Boolean = false
)

data class MetadataContainer(
    val files: List<DisboxFile>,
    val pinHash: String? = null,
    val lastMsgId: String? = null,
    val isDirty: Boolean? = null,
    val updatedAt: Long? = null,
    val snapshotHistory: List<String>? = null
)

class DisboxApi(private val context: Context, var webhookUrl: String) {
    private val client = OkHttpClient()
    private val gson = Gson()
    var hashedWebhook: String? = null
    private var encryptionKey: ByteArray? = null
    
    private val baseUrl: String get() = webhookUrl.split("?")[0]

    var lastSyncedId: String? = null
    var chunkSize = 8 * 1024 * 1024
    var onStatusChange: ((String) -> Unit)? = null

    private val db: DisboxDatabase by lazy { DisboxDatabase.getDatabase(context) }
    private val fileDao by lazy { db.fileDao() }
    private val metaDao by lazy { db.metadataSyncDao() }
    private val settingsDao by lazy { db.settingsDao() }

    private val metadataDir: File by lazy {
        val dir = File(Environment.getExternalStorageDirectory(), "disbox")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    suspend fun init(forceSyncId: String? = null): String = withContext(Dispatchers.IO) {
        hashedWebhook = hashWebhook(webhookUrl)
        encryptionKey = CryptoUtils.deriveKey(webhookUrl)
        migrateJsonToSqlite()
        syncMetadata(forceSyncId)
        hashedWebhook!!
    }

    private fun hashWebhook(url: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(baseUrl.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun migrateJsonToSqlite() = withContext(Dispatchers.IO) {
        val localFile = File(metadataDir, "$hashedWebhook.json")
        if (localFile.exists()) {
            try {
                val content = localFile.readText()
                val container = try {
                    gson.fromJson(content, MetadataContainer::class.java)
                } catch (e: Exception) {
                    val files = gson.fromJson<List<DisboxFile>>(content, object : TypeToken<List<DisboxFile>>() {}.type)
                    MetadataContainer(files = files)
                }

                saveMetadataToLocal(
                    container.files,
                    container.lastMsgId,
                    container.isDirty == true,
                    container.snapshotHistory ?: emptyList()
                )
                
                container.pinHash?.let { setPin(it, isAlreadyHashed = true) }

                localFile.renameTo(File(metadataDir, "$hashedWebhook.json.bak"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun getMsgIdFromDiscovery(): Any? = withContext(Dispatchers.IO) {
        val currentHash = hashedWebhook ?: return@withContext null
        val meta = metaDao.getMetadata(currentHash)

        var localMsgId: String? = null
        var snapshotHistory = emptyList<String>()
        if (meta != null) {
            if (meta.isDirty == 1) return@withContext "pending"
            localMsgId = meta.lastMsgId
            snapshotHistory = gson.fromJson(
                meta.snapshotHistory,
                object : TypeToken<List<String>>() {}.type
            )
        }

        var webhookMsgId: String? = null
        try {
            val request = Request.Builder().url(baseUrl).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val map: Map<String, Any> = gson.fromJson(
                        body,
                        object : TypeToken<Map<String, Any>>() {}.type
                    )
                    val name = map["name"] as? String
                    val match = Regex("(?:dbx|disbox|db)[:\\s]+(\\d+)").find(name ?: "")
                    webhookMsgId = match?.groupValues?.get(1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val best = listOfNotNull(localMsgId, webhookMsgId)
            .maxByOrNull { it.toLongOrNull() ?: 0L }
        if (best == null) return@withContext null

        mapOf("best" to best, "history" to snapshotHistory)
    }

    private suspend fun downloadMetadataFromMsg(msgId: String): MetadataContainer = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/messages/$msgId").build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Message $msgId not accessible")

        val body = response.body?.string() ?: throw Exception("Empty body")
        val map: Map<String, Any> = gson.fromJson(
            body,
            object : TypeToken<Map<String, Any>>() {}.type
        )
        @Suppress("UNCHECKED_CAST")
        val attachments = map["attachments"] as? List<Map<String, Any>>
        val url = attachments
            ?.firstOrNull { (it["filename"] as? String)?.contains("metadata.json") == true }
            ?.get("url") as? String
            ?: attachments?.firstOrNull()?.get("url") as? String
            ?: throw Exception("No attachment")

        val attRes = client.newCall(Request.Builder().url(url).build()).execute()
        if (!attRes.isSuccessful) throw Exception("Failed to download metadata file")

        val encryptedBytes = attRes.body?.bytes() ?: throw Exception("Empty metadata file")
        val decryptedBytes = encryptionKey?.let { CryptoUtils.decrypt(encryptedBytes, it) } ?: encryptedBytes
        val attBody = String(decryptedBytes, Charsets.UTF_8)

        try {
            gson.fromJson(attBody, MetadataContainer::class.java)
        } catch (e: Exception) {
            val files = gson.fromJson<List<DisboxFile>>(attBody, object : TypeToken<List<DisboxFile>>() {}.type)
            MetadataContainer(files = files)
        }
    }

    suspend fun syncMetadata(forceId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val discovery = if (forceId != null) {
                mapOf("best" to forceId, "history" to emptyList<String>())
            } else {
                getMsgIdFromDiscovery()
            }

            if (discovery == null) return@withContext false
            if (discovery == "pending") return@withContext true

            @Suppress("UNCHECKED_CAST")
            val discMap = discovery as Map<String, Any>
            val msgId = discMap["best"] as String
            val history = discMap["history"] as List<String>

            val currentHash = hashedWebhook ?: return@withContext false
            val localFiles = getFileSystem()
            val meta = metaDao.getMetadata(currentHash)
            val localMsgId = meta?.lastMsgId

            if (forceId == null && localFiles.isNotEmpty() && (msgId == lastSyncedId || msgId == localMsgId)) {
                if (lastSyncedId != msgId) {
                    lastSyncedId = msgId
                    onStatusChange?.invoke("synced")
                }
                return@withContext true
            }

            var resolvedMsgId = msgId
            var container: MetadataContainer? = null

            try {
                container = downloadMetadataFromMsg(msgId)
            } catch (e: Exception) {
                val fallbacks = history.reversed().filter { it != msgId }
                for (fid in fallbacks) {
                    try {
                        container = downloadMetadataFromMsg(fid)
                        resolvedMsgId = fid
                        break
                    } catch (err: Exception) {
                        err.printStackTrace()
                    }
                }
            }

            if (container != null) {
                saveMetadataToLocal(container.files, resolvedMsgId)
                container.pinHash?.let { setPin(it, isAlreadyHashed = true) }
                lastSyncedId = resolvedMsgId
                onStatusChange?.invoke("synced")
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            onStatusChange?.invoke("error")
            false
        }
    }

    private suspend fun saveMetadataToLocal(
        files: List<DisboxFile>,
        msgId: String? = null,
        isDirty: Boolean = false,
        explicitHistory: List<String>? = null
    ) = withContext(Dispatchers.IO) {
        val currentHash = hashedWebhook ?: return@withContext

        db.withTransaction {
            fileDao.deleteAllByHash(currentHash)
            fileDao.insertAll(files.map { f ->
                val parts = f.path.split("/")
                val name = parts.last()
                val parent = parts.dropLast(1).joinToString("/").ifEmpty { "/" }
                FileEntity(
                    f.id, currentHash, f.path, parent, name, f.size, f.createdAt, 
                    gson.toJson(f.messageIds), 
                    if (f.isLocked) 1 else 0, 
                    if (f.isStarred) 1 else 0
                )
            })

            val currentMeta = metaDao.getMetadata(currentHash)
            val history = if (explicitHistory != null) {
                explicitHistory.toMutableList()
            } else {
                gson.fromJson<MutableList<String>>(
                    currentMeta?.snapshotHistory ?: "[]",
                    object : TypeToken<MutableList<String>>() {}.type
                )
            }

            if (msgId != null && !history.contains(msgId)) {
                history.add(msgId)
                if (history.size > 3) history.removeAt(0)
            }

            metaDao.insertOrReplace(
                MetadataSyncEntity(
                    currentHash,
                    msgId ?: currentMeta?.lastMsgId,
                    gson.toJson(history),
                    if (isDirty) 1 else 0,
                    System.currentTimeMillis()
                )
            )
        }
        if (isDirty) onStatusChange?.invoke("dirty")
    }

    suspend fun getFileSystem(): List<DisboxFile> = withContext(Dispatchers.IO) {
        val currentHash = hashedWebhook ?: return@withContext emptyList()
        fileDao.getAllFilesByHash(currentHash).map {
            DisboxFile(
                it.id,
                it.path,
                gson.fromJson(it.messageIds, object : TypeToken<List<String>>() {}.type),
                it.size,
                it.createdAt,
                it.isLocked == 1,
                it.isStarred == 1
            )
        }
    }

    suspend fun uploadMetadataToDiscord(explicitFiles: List<DisboxFile>? = null) = withContext(Dispatchers.IO) {
        val currentHash = hashedWebhook ?: return@withContext
        val files = explicitFiles ?: getFileSystem()
        if (files.isEmpty()) return@withContext
        
        val pinSetting = settingsDao.getSetting(currentHash, "pin_hash")
        val container = MetadataContainer(
            files = files,
            pinHash = pinSetting?.value
        )
        
        val json = gson.toJson(container)

        val encryptedBytes = encryptionKey?.let { CryptoUtils.encrypt(json.toByteArray(Charsets.UTF_8), it) }
            ?: json.toByteArray(Charsets.UTF_8)

        onStatusChange?.invoke("uploading")
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "disbox_metadata.json",
                encryptedBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder().url("$baseUrl?wait=true").post(body).build()

        try {
            val res = client.newCall(request).execute()
            if (res.isSuccessful) {
                val resBody = res.body?.string()
                val map: Map<String, Any> = gson.fromJson(
                    resBody, object : TypeToken<Map<String, Any>>() {}.type
                )
                val newMsgId = map["id"] as? String
                if (newMsgId != null) {
                    saveMetadataToLocal(files, newMsgId, false)
                    lastSyncedId = newMsgId
                    onStatusChange?.invoke("synced")

                    val patchBody = gson.toJson(mapOf("name" to "dbx: $newMsgId"))
                        .toRequestBody("application/json".toMediaTypeOrNull())
                    try { client.newCall(Request.Builder().url(baseUrl).patch(patchBody).build()).execute() } catch (e: Exception) {}
                }
            } else onStatusChange?.invoke("error")
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

    suspend fun checkDuplicate(name: String, parentPath: String): Boolean = withContext(Dispatchers.IO) {
        val currentHash = hashedWebhook ?: return@withContext false
        val normalizedParent = if (parentPath == "/") "/" else parentPath.trim('/')
        
        val fullPath = if (normalizedParent == "/" || normalizedParent.isEmpty()) name else "$normalizedParent/$name"
        
        val existingFile = fileDao.getFileByPath(fullPath, currentHash)
        if (existingFile != null) return@withContext true
        
        val keepPath = "$fullPath/.keep"
        val existingFolder = fileDao.getFileByPath(keepPath, currentHash)
        if (existingFolder != null) return@withContext true
        
        return@withContext false
    }

    suspend fun createFolder(folderName: String, currentPath: String): Boolean {
        if (checkDuplicate(folderName, currentPath)) return false
        val dirPath = if (currentPath == "/") "" else currentPath.trimStart('/')
        val folderPath = if (dirPath.isEmpty()) "$folderName/.keep" else "$dirPath/$folderName/.keep"
        createFile(folderPath, emptyList(), 0)
        return true
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
            items.any { item -> f.id == item || f.path == item || f.path.startsWith("$item/") }
        }
        saveMetadataToLocal(filtered, isDirty = true)
        uploadMetadataToDiscord(filtered)
    }

    suspend fun renamePath(oldPath: String, newPath: String, id: String? = null): Boolean {
        val parts = newPath.split("/")
        val name = parts.last()
        val parentPath = parts.dropLast(1).joinToString("/").ifEmpty { "/" }
        
        if (checkDuplicate(name, parentPath)) return false

        var found = false
        val files = getFileSystem().map {
            when {
                (id != null && it.id == id) || (id == null && it.path == oldPath) -> {
                    found = true; it.copy(path = newPath)
                }
                it.path.startsWith("$oldPath/") -> {
                    found = true; it.copy(path = it.path.replaceFirst("$oldPath/", "$newPath/"))
                }
                else -> it
            }
        }
        if (found) {
            saveMetadataToLocal(files, isDirty = true)
            uploadMetadataToDiscord(files)
        }
        return found
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

    // PIN and Status methods
    suspend fun bulkSetStatus(idsOrPaths: Set<String>, isLocked: Boolean? = null, isStarred: Boolean? = null) = withContext(Dispatchers.IO) {
        val files = getFileSystem().toMutableList()
        var changed = false
        files.forEachIndexed { i, f ->
            var newLocked = f.isLocked
            var newStarred = f.isStarred
            var itemChanged = false

            if (isLocked != null) {
                // Lock bersifat rekursif: isi folder ikut terkunci
                val isLockTarget = idsOrPaths.any { target ->
                    f.id == target || f.path == target || f.path == "$target/.keep" || f.path.startsWith("$target/")
                }
                if (isLockTarget) {
                    newLocked = isLocked
                    if (newLocked != f.isLocked) itemChanged = true
                }
            }

            if (isStarred != null) {
                // Star bersifat non-rekursif: hanya folder/file itu saja
                val isStarTarget = idsOrPaths.any { target ->
                    f.id == target || f.path == target || f.path == "$target/.keep"
                }
                if (isStarTarget) {
                    newStarred = isStarred
                    if (newStarred != f.isStarred) itemChanged = true
                }
            }

            if (itemChanged) {
                files[i] = f.copy(isLocked = newLocked, isStarred = newStarred)
                changed = true
            }
        }
        if (changed) {
            saveMetadataToLocal(files, isDirty = true)
            uploadMetadataToDiscord(files)
        }
    }

    suspend fun setLocked(idOrPath: String, isLocked: Boolean) {
        bulkSetStatus(setOf(idOrPath), isLocked = isLocked)
    }

    suspend fun setStarred(idOrPath: String, isStarred: Boolean) {
        bulkSetStatus(setOf(idOrPath), isStarred = isStarred)
    }

    suspend fun setPin(pin: String, isAlreadyHashed: Boolean = false) = withContext(Dispatchers.IO) {
        val hash = hashedWebhook ?: return@withContext false
        val hashedPin = if (isAlreadyHashed) pin else MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
        settingsDao.insertOrReplace(SettingsEntity(hash, "pin_hash", hashedPin))
        if (!isAlreadyHashed) uploadMetadataToDiscord()
        true
    }

    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val hash = hashedWebhook ?: return@withContext false
        val setting = settingsDao.getSetting(hash, "pin_hash") ?: return@withContext false
        val hashedPin = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
        setting.value == hashedPin
    }

    suspend fun hasPin(): Boolean = withContext(Dispatchers.IO) {
        val hash = hashedWebhook ?: return@withContext false
        settingsDao.getSetting(hash, "pin_hash") != null
    }

    suspend fun removePin() = withContext(Dispatchers.IO) {
        val hash = hashedWebhook ?: return@withContext
        settingsDao.deleteSetting(hash, "pin_hash")
        uploadMetadataToDiscord()
    }

    suspend fun uploadFile(uri: Uri, uploadPath: String, onProgress: (Float) -> Unit): List<String> = withContext(Dispatchers.IO) {
        val fileId = UUID.randomUUID().toString()
        val resolver = context.contentResolver
        val cursor = resolver.query(uri, null, null, null, null)
        var size = 0L; var name = "file"
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
                var chunkData = buffer.copyOf(bytesRead)
                chunkData = encryptionKey?.let { CryptoUtils.encrypt(chunkData, it) } ?: chunkData
                val chunkName = "${fileId}_${name}.part$i"
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", chunkName, chunkData.toRequestBody("application/octet-stream".toMediaTypeOrNull())).build()
                val request = Request.Builder().url("$baseUrl?wait=true").post(body).build()
                var success = false; var retries = 0
                while (!success && retries < 3) {
                    try {
                        val response = client.newCall(request).execute()
                        if (response.code == 429) {
                            val rBody = response.body?.string(); val map: Map<String, Any>? = rBody?.let { gson.fromJson(it, object : TypeToken<Map<String, Any>>() {}.type) }
                            val retryAfter = ((map?.get("retry_after") as? Double) ?: 5.0) + 1.0
                            Thread.sleep((retryAfter * 1000).toLong()); continue
                        }
                        if (!response.isSuccessful) throw Exception("Chunk $i upload failed")
                        val rBody = response.body?.string() ?: ""; val map: Map<String, Any> = gson.fromJson(rBody, object : TypeToken<Map<String, Any>>() {}.type)
                        messageIds.add(map["id"] as String); success = true
                    } catch (e: Exception) { retries++; if (retries >= 3) throw e; Thread.sleep(2000) }
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
                val msgUrl = "$baseUrl/messages/${messageIds[i]}"
                val msgRes = client.newCall(Request.Builder().url(msgUrl).build()).execute()
                if (!msgRes.isSuccessful) throw Exception("Failed to fetch msg ${messageIds[i]}")
                val map: Map<String, Any> = gson.fromJson(msgRes.body?.string() ?: "", object : TypeToken<Map<String, Any>>() {}.type)
                @Suppress("UNCHECKED_CAST")
                val attachments = map["attachments"] as? List<Map<String, Any>>
                val url = attachments?.firstOrNull()?.get("url") as? String ?: throw Exception("No attachment URL")
                val chunkRes = client.newCall(Request.Builder().url(url).build()).execute()
                if (!chunkRes.isSuccessful) throw Exception("Failed to download chunk")
                var chunkData = chunkRes.body?.bytes() ?: throw Exception("Empty chunk body")
                chunkData = encryptionKey?.let { CryptoUtils.decrypt(chunkData, it) } ?: chunkData
                out.write(chunkData)
                onProgress((i + 1).toFloat() / messageIds.size)
            }
        }
    }
}
