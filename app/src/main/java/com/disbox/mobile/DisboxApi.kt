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

data class ShareLink(
    val id: String,
    val hash: String,
    val file_path: String,
    val file_id: String?,
    val token: String,
    val permission: String,
    val expires_at: Long?,
    val created_at: Long
)

data class ShareSettings(
    val hash: String,
    val mode: String = "public",
    val cf_worker_url: String?,
    val cf_api_token: String?,
    val webhook_url: String?,
    val enabled: Boolean = false
)

data class MetadataContainer(
    val files: List<DisboxFile>,
    val pinHash: String? = null,
    val shareLinks: List<ShareLink>? = null,
    val lastMsgId: String? = null,
    val isDirty: Boolean? = null,
    val updatedAt: Long? = null,
    val snapshotHistory: List<String>? = null
)

class DisboxApi(private val context: Context, var webhookUrl: String) {
    companion object {
        const val PUBLIC_WORKER_URL = "https://disbox-shared-link.naufal-backup.workers.dev"
        val PUBLIC_API_KEYS = mapOf(
            "https://disbox-shared-link.alamsyahnaufal453.workers.dev" to "disbox-shared-link-0002",
            "https://disbox-shared-link.naufal-backup.workers.dev" to "disbox-shared-link-0001",
            "https://disbox-worker-2.naufal-backup.workers.dev" to "disbox-shared-link-0001",
            "https://disbox-worker-3.naufal-backup.workers.dev" to "disbox-shared-link-0001"
        )
        const val DEFAULT_PUBLIC_API_KEY = "disbox-shared-link-0001"
    }

    private val client = OkHttpClient()
    private val gson = Gson()
    var hashedWebhook: String? = null
    private var encryptionKey: ByteArray? = null
    
    private val baseUrl: String get() {
        var url = webhookUrl.split("?")[0].trim()
        if (!url.startsWith("http")) url = "https://$url"
        url = url.replace("discordapp.com", "discord.com")
        return url
    }

    var lastSyncedId: String? = null
    var chunkSize = 8 * 1024 * 1024
    var onStatusChange: ((String) -> Unit)? = null

    private val db: DisboxDatabase by lazy { DisboxDatabase.getDatabase(context) }
    private val fileDao by lazy { db.fileDao() }
    private val metaDao by lazy { db.metadataSyncDao() }
    private val settingsDao by lazy { db.settingsDao() }
    private val shareSettingsDao by lazy { db.shareSettingsDao() }
    private val shareLinkDao by lazy { db.shareLinkDao() }

    private val metadataDir: File by lazy {
        val dir = File(Environment.getExternalStorageDirectory(), "disbox")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    suspend fun init(forceSyncId: String? = null): String = withContext(Dispatchers.IO) {
        hashedWebhook = hashWebhook(webhookUrl)
        encryptionKey = CryptoUtils.deriveKey(webhookUrl)
        migrateJsonToSqlite()
        val success = syncMetadata(forceSyncId)
        if (!success && forceSyncId != null) {
            throw Exception("Failed to load metadata from Message ID: $forceSyncId")
        }
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
        if (!response.isSuccessful) throw Exception("Message $msgId not accessible: ${response.code}")

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
            // Manual parsing to avoid Proguard/Kapt issues
            val jsonObject = gson.fromJson(attBody, com.google.gson.JsonObject::class.java)

            val files: List<DisboxFile> = if (jsonObject.has("files")) {
                gson.fromJson(jsonObject.get("files"), object : TypeToken<List<DisboxFile>>() {}.type) ?: emptyList()
            } else {
                // Legacy support for metadata being just a list of files
                gson.fromJson(attBody, object : TypeToken<List<DisboxFile>>() {}.type) ?: emptyList()
            }

            val shareLinks: List<ShareLink> = if (jsonObject.has("shareLinks")) {
                gson.fromJson(jsonObject.get("shareLinks"), object : TypeToken<List<ShareLink>>() {}.type) ?: emptyList()
            } else {
                emptyList()
            }

            val pinHash = if (jsonObject.has("pinHash")) jsonObject.get("pinHash").asString else null
            
            MetadataContainer(
                files = files,
                shareLinks = shareLinks,
                pinHash = pinHash
            )
        } catch (e: Exception) {
            // Fallback for very old metadata that might not even be a JSON object
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
            val localFiles = getFileSystem(filterCloudSave = false)
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
                saveMetadataToLocal(container.files, resolvedMsgId, shareLinks = container.shareLinks ?: emptyList())
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
        explicitHistory: List<String>? = null,
        shareLinks: List<ShareLink>? = null
    ) = withContext(Dispatchers.IO) {
        val currentHash = hashedWebhook ?: return@withContext

        db.withTransaction {
            fileDao.deleteAllByHash(currentHash)
            fileDao.insertAll(files.map { f ->
                val normalizedPath = f.path.trim('/')
                val parts = normalizedPath.split("/")
                val name = parts.last()
                val parent = parts.dropLast(1).joinToString("/").ifEmpty { "/" }
                FileEntity(
                    f.id, currentHash, normalizedPath, parent, name, f.size, f.createdAt, 
                    gson.toJson(f.messageIds), 
                    if (f.isLocked) 1 else 0, 
                    if (f.isStarred) 1 else 0
                )
            })

            shareLinks?.let { links ->
                shareLinkDao.deleteAllByHash(currentHash)
                links.forEach { link ->
                    shareLinkDao.insertOrReplace(ShareLinkEntity(
                        link.id, currentHash, link.file_path, link.file_id, 
                        link.token, link.permission, link.expires_at, link.created_at
                    ))
                }
            }

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

    suspend fun getFileSystem(filterCloudSave: Boolean = true): List<DisboxFile> = withContext(Dispatchers.IO) {
        val currentHash = hashedWebhook ?: return@withContext emptyList()
        fileDao.getAllFilesByHash(currentHash).mapNotNull {
            if (filterCloudSave && it.path.startsWith("cloudsave/")) return@mapNotNull null
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
        val files = explicitFiles ?: getFileSystem(filterCloudSave = false)
        
        val pinSetting = settingsDao.getSetting(currentHash, "pin_hash")
        val shareLinks = getShareLinks()
        
        if (files.isEmpty() && pinSetting == null && shareLinks.isEmpty()) return@withContext

        val container = MetadataContainer(
            files = files,
            pinHash = pinSetting?.value,
            shareLinks = shareLinks
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
                    saveMetadataToLocal(files, newMsgId, false, shareLinks = shareLinks)
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

    // --- Sharing Methods ---

    suspend fun getShareSettings(): ShareSettings = withContext(Dispatchers.IO) {
        val currentHash = hashedWebhook ?: return@withContext ShareSettings("", enabled = false, cf_worker_url = null, cf_api_token = null, webhook_url = null)
        val row = shareSettingsDao.getSettings(currentHash)
        if (row != null) {
            ShareSettings(row.hash, row.mode, row.cf_worker_url, row.cf_api_token, row.webhook_url, row.enabled == 1)
        } else {
            ShareSettings(currentHash, "public", PUBLIC_WORKER_URL, null, webhookUrl, false)
        }
    }

    suspend fun saveShareSettings(settings: ShareSettings) = withContext(Dispatchers.IO) {
        val currentHash = hashedWebhook ?: return@withContext
        shareSettingsDao.insertOrReplace(ShareSettingsEntity(
            hash = currentHash,
            mode = settings.mode,
            cf_worker_url = settings.cf_worker_url ?: "", // Provide non-null fallback
            cf_api_token = settings.cf_api_token ?: "",  // Provide non-null fallback
            webhook_url = settings.webhook_url ?: webhookUrl ?: "", // Ensure fallback chain is non-null
            enabled = if (settings.enabled) 1 else 0
        ))
    }
    suspend fun getShareLinks(): List<ShareLink> = withContext(Dispatchers.IO) {
        val currentHash = hashedWebhook ?: return@withContext emptyList()
        shareLinkDao.getAllLinksByHash(currentHash).map {
            ShareLink(it.id, it.hash, it.filePath, it.fileId, it.token, it.permission, it.expiresAt, it.createdAt)
        }
    }

    private fun getApiKey(settings: ShareSettings, cfWorkerUrl: String): String {
        if (settings.mode == "private" && !settings.cf_api_token.isNullOrBlank()) {
            return settings.cf_api_token.trim()
        }

        val normalize = { u: String -> u.lowercase().replace(Regex("^https?://"), "").trimEnd('/') }
        val target = normalize(cfWorkerUrl)

        for ((url, key) in PUBLIC_API_KEYS) {
            if (normalize(url) == target) return key.trim()
        }

        return DEFAULT_PUBLIC_API_KEY.trim()
    }

    suspend fun createShareLink(filePath: String, fileId: String?, permission: String, expiresAt: Long?): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val currentHash = hashedWebhook ?: return@withContext mapOf("ok" to false, "reason" to "no_hash")
            val token = UUID.randomUUID().toString().replace("-", "")
            val settings = getShareSettings()

            var cfWorkerUrl = settings.cf_worker_url ?: PUBLIC_WORKER_URL
            if (!cfWorkerUrl.startsWith("http")) {
                return@withContext mapOf("ok" to false, "reason" to "invalid_worker_url")
            }
            cfWorkerUrl = cfWorkerUrl.trimEnd('/')

            val apiKey = getApiKey(settings, cfWorkerUrl)

            // Get messageIds for chunks
            val file = if (fileId != null) {
                fileDao.getFileById(fileId, currentHash)
            } else {
                fileDao.getFileByPath(filePath.trim('/'), currentHash)
            } ?: return@withContext mapOf("ok" to false, "reason" to "file_not_found")

            val messageIdsRaw = gson.fromJson<List<String>>(file.messageIds, object : TypeToken<List<String>>() {}.type)
            val messageIds = messageIdsRaw.map { mapOf("msgId" to it, "attachmentUrl" to null) }

            // Derive encryption key for the worker to help decrypt
            val encryptionKeyB64 = android.util.Base64.encodeToString(CryptoUtils.deriveKey(webhookUrl), android.util.Base64.NO_WRAP)

            val bodyMap = mapOf(
                "token" to token,
                "fileId" to fileId,
                "filePath" to filePath,
                "permission" to permission,
                "expiresAt" to expiresAt,
                "webhookHash" to currentHash,
                "messageIds" to messageIds,
                "encryptionKeyB64" to encryptionKeyB64,
                "webhookUrl" to baseUrl
            )

            val body = gson.toJson(bodyMap).toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$cfWorkerUrl/share/create")
                .post(body)
                .addHeader("X-Disbox-Key", apiKey)
                .build()

            val res = client.newCall(request).execute()
            if (!res.isSuccessful) {
                return@withContext mapOf("ok" to false, "reason" to "worker_error", "status" to res.code)
            }

            val id = UUID.randomUUID().toString()
            shareLinkDao.insertOrReplace(ShareLinkEntity(
                id, currentHash, filePath, fileId, token, permission, expiresAt, System.currentTimeMillis()
            ))

            uploadMetadataToDiscord()

            mapOf("ok" to true, "link" to "$cfWorkerUrl/share/$token", "token" to token, "id" to id)
        } catch (e: Exception) {
            e.printStackTrace()
            mapOf("ok" to false, "reason" to (e.message ?: "unknown"))
        }
    }

    suspend fun revokeShareLink(id: String, token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentHash = hashedWebhook ?: return@withContext false
            val settings = getShareSettings()
            val cfWorkerUrl = (settings.cf_worker_url ?: PUBLIC_WORKER_URL).trimEnd('/')
            val apiKey = getApiKey(settings, cfWorkerUrl)

            val request = Request.Builder()
                .url("$cfWorkerUrl/share/revoke/$token")
                .delete()
                .addHeader("X-Disbox-Key", apiKey)
                .build()

            client.newCall(request).execute() // Best effort
            shareLinkDao.deleteById(id, currentHash)
            uploadMetadataToDiscord()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun revokeAllLinks(): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentHash = hashedWebhook ?: return@withContext false
            val settings = getShareSettings()
            val cfWorkerUrl = (settings.cf_worker_url ?: PUBLIC_WORKER_URL).trimEnd('/')
            val apiKey = getApiKey(settings, cfWorkerUrl)

            val request = Request.Builder()
                .url("$cfWorkerUrl/share/revoke-all/$currentHash")
                .delete()
                .addHeader("X-Disbox-Key", apiKey)
                .build()

            client.newCall(request).execute() // Best effort
            shareLinkDao.deleteAllByHash(currentHash)
            uploadMetadataToDiscord()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun createFile(path: String, msgIds: List<String>, size: Long, id: String? = null) {
        val files = getFileSystem(filterCloudSave = false).toMutableList()
        val normalizedPath = path.trim('/')
        val fileId = id ?: UUID.randomUUID().toString()
        val entry = DisboxFile(fileId, normalizedPath, msgIds, size)
        val idx = files.indexOfFirst { it.id == fileId }
        if (idx >= 0) files[idx] = entry else files.add(entry)
        saveMetadataToLocal(files, isDirty = true)
        uploadMetadataToDiscord(files)
    }

    suspend fun checkDuplicate(name: String, parentPath: String): Boolean = withContext(Dispatchers.IO) {
        val currentHash = hashedWebhook ?: return@withContext false
        val normalizedParent = if (parentPath == "/") "" else parentPath.trim('/')
        
        val fullPath = if (normalizedParent.isEmpty()) name else "$normalizedParent/$name"
        
        val existingFile = fileDao.getFileByPath(fullPath, currentHash)
        if (existingFile != null) return@withContext true
        
        val keepPath = "$fullPath/.keep"
        val existingFolder = fileDao.getFileByPath(keepPath, currentHash)
        if (existingFolder != null) return@withContext true
        
        return@withContext false
    }

    suspend fun createFolder(folderName: String, currentPath: String): Boolean {
        if (checkDuplicate(folderName, currentPath)) return false
        val dirPath = currentPath.trim('/')
        val folderPath = if (dirPath.isEmpty()) "$folderName/.keep" else "$dirPath/$folderName/.keep"
        createFile(folderPath, emptyList(), 0)
        return true
    }

    suspend fun deletePath(targetPath: String, id: String? = null) {
        val normalizedTarget = targetPath.trim('/')
        val files = getFileSystem(filterCloudSave = false).filterNot {
            (id != null && it.id == id) || (id == null && it.path == normalizedTarget) || it.path.startsWith("$normalizedTarget/")
        }
        saveMetadataToLocal(files, isDirty = true)
        uploadMetadataToDiscord(files)
    }

    suspend fun bulkDelete(items: List<String>) {
        val files = getFileSystem(filterCloudSave = false)
        val filtered = files.filterNot { f ->
            items.any { item -> 
                val normalizedItem = item.trim('/')
                f.id == item || f.path == normalizedItem || f.path.startsWith("$normalizedItem/") 
            }
        }
        saveMetadataToLocal(filtered, isDirty = true)
        uploadMetadataToDiscord(filtered)
    }

    suspend fun renamePath(oldPath: String, newPath: String, id: String? = null): Boolean {
        val normalizedOld = oldPath.trim('/')
        val normalizedNew = newPath.trim('/')

        // Extract new name and parent path from the *new* path
        val newParts = normalizedNew.split("/")
        val newName = newParts.last()
        val newParentPath = newParts.dropLast(1).joinToString("/").ifEmpty { null } // null for root

        if (checkDuplicate(newName, newPath)) return false

        var found = false
        val files = getFileSystem(filterCloudSave = false).map {
            when {
                (id != null && it.id == id) || (id == null && it.path == normalizedOld) -> {
                    found = true; it.copy(path = normalizedNew)
                }
                it.path.startsWith("$normalizedOld/") -> {
                    found = true; it.copy(path = it.path.replaceFirst("$normalizedOld/", "$normalizedNew/"))
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
        val normalizedSource = sourcePath.trim('/')
        val name = normalizedSource.split("/").last()
        val normalizedDest = destDir.trim('/')
        val newPath = if (normalizedDest.isEmpty()) name else "$normalizedDest/$name"
        renamePath(normalizedSource, newPath, id)
    }

    suspend fun copyPath(sourcePath: String, destDir: String, id: String? = null) {
        val normalizedSource = sourcePath.trim('/')
        val normalizedDest = destDir.trim('/')
        val files = getFileSystem(filterCloudSave = false).toMutableList()
        val name = normalizedSource.split("/").last()
        val newPath = if (normalizedDest.isEmpty()) name else "$normalizedDest/$name"
        val toAdd = mutableListOf<DisboxFile>()
        files.forEach { f ->
            if ((id != null && f.id == id) || (id == null && f.path == normalizedSource)) {
                toAdd.add(f.copy(id = UUID.randomUUID().toString(), path = newPath, createdAt = System.currentTimeMillis()))
            } else if (f.path.startsWith("$normalizedSource/")) {
                toAdd.add(f.copy(id = UUID.randomUUID().toString(), path = f.path.replaceFirst("$normalizedSource/", "$newPath/"), createdAt = System.currentTimeMillis()))
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
        val files = getFileSystem(filterCloudSave = false).toMutableList()
        var changed = false
        files.forEachIndexed { i, f ->
            var newLocked = f.isLocked
            var newStarred = f.isStarred
            var itemChanged = false

            if (isLocked != null) {
                // Lock bersifat rekursif: isi folder ikut terkunci
                val isLockTarget = idsOrPaths.any { target ->
                    val normalizedTarget = target.trim('/')
                    f.id == target || f.path == normalizedTarget || f.path == "$normalizedTarget/.keep" || f.path.startsWith("$normalizedTarget/")
                }
                if (isLockTarget) {
                    newLocked = isLocked
                    if (newLocked != f.isLocked) itemChanged = true
                }
            }

            if (isStarred != null) {
                // Star bersifat non-rekursif: hanya folder/file itu saja
                val isStarTarget = idsOrPaths.any { target ->
                    val normalizedTarget = target.trim('/')
                    f.id == target || f.path == normalizedTarget || f.path == "$normalizedTarget/.keep"
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

    suspend fun downloadFile(file: DisboxFile, destFile: File, onProgress: (Float) -> Unit) {
        downloadFilePartial(file, destFile, 0, file.messageIds.size, onProgress)
    }

    suspend fun downloadFilePartial(
        file: DisboxFile, 
        destFile: File, 
        startIndex: Int, 
        endIndex: Int, 
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val messageIds = file.messageIds
        val actualEndIndex = endIndex.coerceAtMost(messageIds.size)
        
        // Use append mode if we're not starting from 0
        val out = FileOutputStream(destFile, startIndex > 0)
        out.use { stream ->
            for (i in startIndex until actualEndIndex) {
                kotlinx.coroutines.yield()
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
                stream.write(chunkData)
                onProgress((i + 1).toFloat() / messageIds.size)
            }
        }
    }

    suspend fun downloadSingleChunk(msgId: String): ByteArray = withContext(Dispatchers.IO) {
        val msgUrl = "$baseUrl/messages/$msgId"
        val msgRes = client.newCall(Request.Builder().url(msgUrl).build()).execute()
        if (!msgRes.isSuccessful) throw Exception("Failed to fetch msg $msgId")
        val map: Map<String, Any> = gson.fromJson(msgRes.body?.string() ?: "", object : TypeToken<Map<String, Any>>() {}.type)
        @Suppress("UNCHECKED_CAST")
        val attachments = map["attachments"] as? List<Map<String, Any>>
        val url = attachments?.firstOrNull()?.get("url") as? String ?: throw Exception("No attachment URL")
        val chunkRes = client.newCall(Request.Builder().url(url).build()).execute()
        if (!chunkRes.isSuccessful) throw Exception("Failed to download chunk")
        var chunkData = chunkRes.body?.bytes() ?: throw Exception("Empty chunk body")
        chunkData = encryptionKey?.let { CryptoUtils.decrypt(chunkData, it) } ?: chunkData
        chunkData
    }
}
