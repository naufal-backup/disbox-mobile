package com.disbox.mobile.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.disbox.mobile.utils.CryptoUtils
import com.disbox.mobile.data.service.DisboxApiService
import com.disbox.mobile.model.*
import com.disbox.mobile.FileEntity
import com.disbox.mobile.MetadataSyncEntity
import com.disbox.mobile.SettingsEntity
import com.disbox.mobile.ShareSettingsEntity
import com.disbox.mobile.ShareLinkEntity
import com.disbox.mobile.DisboxDatabase
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.*

class DisboxRepository(
    private val context: Context,
    private val apiService: DisboxApiService,
    private val db: DisboxDatabase
) {
    var webhookUrl: String = ""
    var hashedWebhook: String? = null
    var username: String? = null
    private var encryptionKey: ByteArray? = null
    private val baseUrl: String get() = CryptoUtils.normalizeUrl(webhookUrl)
    
    private val fileDao = db.fileDao()
    private val metaDao = db.metadataSyncDao()
    private val settingsDao = db.settingsDao()
    private val shareSettingsDao = db.shareSettingsDao()
    private val shareLinkDao = db.shareLinkDao()
    private val gson = apiService.getGson()

    private val identifier: String get() = username ?: hashedWebhook ?: ""

    suspend fun init(url: String, forceId: String? = null, metadataUrl: String? = null, user: String? = null): String = withContext(Dispatchers.IO) {
        webhookUrl = url
        username = user
        hashedWebhook = hashWebhook(url)
        encryptionKey = CryptoUtils.deriveKey(url)
        syncMetadata(forceId = forceId, metadataUrl = metadataUrl, forceSync = true)
        hashedWebhook!!
    }

    private fun hashWebhook(url: String): String {
        val normalized = CryptoUtils.normalizeUrl(url)
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun syncMetadata(forceId: String? = null, metadataUrl: String? = null, forceSync: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val id = identifier.ifEmpty { return@withContext false }
            val cloudFilesRes = apiService.listFiles(id)
            if (cloudFilesRes != null && cloudFilesRes["ok"] == true) {
                val files = gson.fromJson<List<DisboxFile>>(gson.toJson(cloudFilesRes["files"]), object : TypeToken<List<DisboxFile>>() {}.type)
                if (files.isNotEmpty()) {
                    saveMetadataToLocal(files)
                    return@withContext true
                }
            }

            var legacyContainer: MetadataContainer? = null
            apiService.getCloudConfig(id)?.let { cfg ->
                val b64 = cfg["metadata_b64"] as? String
                if (b64 != null) {
                    val encryptedBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    val decryptedBytes = encryptionKey?.let { CryptoUtils.decrypt(encryptedBytes, it) } ?: encryptedBytes
                    legacyContainer = gson.fromJson(String(decryptedBytes), MetadataContainer::class.java)
                }
            }

            if (legacyContainer == null && metadataUrl?.startsWith("http") == true) {
                legacyContainer = downloadMetadataFromUrl(metadataUrl)
            }

            if (legacyContainer == null) {
                val webhookMsgId = getWebhookMsgId()
                val bestId = forceId ?: webhookMsgId
                if (bestId != null) {
                    legacyContainer = downloadMetadataFromMsg(bestId)
                }
            }

            if (legacyContainer != null) {
                val files = legacyContainer!!.files
                files.forEach { apiService.upsertFile(id, it) }
                saveMetadataToLocal(files)
                return@withContext true
            }

            if (forceSync) saveMetadataToLocal(emptyList())
            false
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private suspend fun getWebhookMsgId(): String? {
        apiService.getWebhookInfo(baseUrl)?.let { info ->
            val name = info["name"] as? String
            return Regex("(?:dbx|disbox|db)[:\\s]+(\\d+)", RegexOption.IGNORE_CASE).find(name ?: "")?.groupValues?.get(1)
        }
        return null
    }

    private suspend fun downloadMetadataFromUrl(url: String): MetadataContainer {
        val encryptedBytes = apiService.downloadAttachment(url) ?: throw Exception("Download failed")
        val decryptedBytes = encryptionKey?.let { CryptoUtils.decrypt(encryptedBytes, it) } ?: encryptedBytes
        return gson.fromJson(String(decryptedBytes), MetadataContainer::class.java)
    }

    private suspend fun downloadMetadataFromMsg(msgId: String): MetadataContainer {
        val msg = apiService.getMessage(baseUrl, msgId) ?: throw Exception("Message not found")
        @Suppress("UNCHECKED_CAST")
        val attachments = msg["attachments"] as? List<Map<String, Any>>
        val url = attachments?.firstOrNull { (it["filename"] as? String)?.contains("metadata.json") == true }?.get("url") as? String
            ?: attachments?.firstOrNull()?.get("url") as? String ?: throw Exception("No attachment")
        return downloadMetadataFromUrl(url)
    }

    suspend fun saveMetadataToLocal(files: List<DisboxFile>, msgId: String? = null) = withContext(Dispatchers.IO) {
        val hash = hashedWebhook ?: return@withContext
        db.withTransaction {
            fileDao.deleteAllByHash(hash)
            fileDao.insertAll(files.map { f ->
                val norm = f.path.trim('/'); val parts = norm.split("/"); val name = parts.last(); val parent = parts.dropLast(1).joinToString("/").ifEmpty { "/" }
                FileEntity(f.id, hash, norm, parent, name, f.size, f.createdAt, gson.toJson(f.messageIds), if (f.isLocked) 1 else 0, if (f.isStarred) 1 else 0)
            })
            metaDao.insertOrReplace(MetadataSyncEntity(hash, msgId, "[]", 0, System.currentTimeMillis()))
        }
    }

    suspend fun persistCloud(files: List<DisboxFile>) {
        val id = identifier.ifEmpty { return }
        withContext(Dispatchers.IO) {
            apiService.syncAllFiles(id, files)
            val pin = settingsDao.getSetting(hashedWebhook!!, "pin_hash")?.value
            val container = MetadataContainer(files = files, pinHash = pin)
            val json = gson.toJson(container)
            val encrypted = encryptionKey?.let { CryptoUtils.encrypt(json.toByteArray(), it) } ?: json.toByteArray()
            apiService.uploadFile(baseUrl, "metadata.json", encrypted)?.let { apiService.patchWebhookName(baseUrl, "dbx: $it") }
        }
    }

    suspend fun getFileSystem(filterCloudSave: Boolean = true): List<DisboxFile> = withContext(Dispatchers.IO) {
        val hash = hashedWebhook ?: return@withContext emptyList()
        fileDao.getAllFilesByHash(hash).mapNotNull {
            if (filterCloudSave && it.path.startsWith("cloudsave/")) null
            else DisboxFile(it.id, it.path, gson.fromJson(it.messageIds, object : TypeToken<List<MessageId>>() {}.type), it.size, it.createdAt, it.isLocked == 1, it.isStarred == 1)
        }
    }

    suspend fun createFolder(name: String, parentPath: String): Boolean = withContext(Dispatchers.IO) {
        val normParent = parentPath.trim('/')
        val folderPath = (if (normParent.isEmpty()) name else "$normParent/$name") + "/.keep"
        val newFile = DisboxFile(UUID.randomUUID().toString(), folderPath, emptyList(), 0)
        apiService.upsertFile(identifier, newFile)
        val files = getFileSystem(false).toMutableList()
        files.add(newFile)
        saveMetadataToLocal(files)
        true
    }

    suspend fun deletePaths(idsOrPaths: List<String>) = withContext(Dispatchers.IO) {
        val id = identifier
        idsOrPaths.forEach { target -> apiService.deleteFile(id, target.trim('/')) }
        val files = getFileSystem(false).filterNot { f ->
            idsOrPaths.any { target -> f.id == target || f.path == target.trim('/') || f.path.startsWith("${target.trim('/')}/") }
        }
        saveMetadataToLocal(files)
    }

    suspend fun renamePath(oldPath: String, newName: String, id: String? = null): Boolean = withContext(Dispatchers.IO) {
        val normOld = oldPath.trim('/')
        val parts = normOld.split("/"); val parent = parts.dropLast(1).joinToString("/")
        val normNew = if (parent.isEmpty()) newName else "$parent/$newName"
        
        apiService.deleteFile(identifier, normOld)
        
        val files = getFileSystem(false).map {
            val updated = when {
                (id != null && it.id == id) || it.path == normOld -> it.copy(path = normNew)
                it.path.startsWith("$normOld/") -> it.copy(path = it.path.replaceFirst("$normOld/", "$normNew/"))
                else -> it
            }
            if (updated.path != it.path) apiService.upsertFile(identifier, updated)
            updated
        }
        saveMetadataToLocal(files)
        true
    }

    suspend fun bulkSetStatus(idsOrPaths: Collection<String>, isLocked: Boolean? = null, isStarred: Boolean? = null) = withContext(Dispatchers.IO) {
        val files = getFileSystem(false).map { f ->
            var l = f.isLocked; var s = f.isStarred
            val isTarget = idsOrPaths.any { t -> f.id == t || f.path == t.trim('/') || f.path.startsWith("${t.trim('/')}/") }
            if (isTarget) { if (isLocked != null) l = isLocked; if (isStarred != null) s = isStarred }
            val updated = f.copy(isLocked = l, isStarred = s)
            if (updated != f) apiService.upsertFile(identifier, updated)
            updated
        }
        saveMetadataToLocal(files)
    }

    suspend fun uploadFile(uri: android.net.Uri, path: String, chunkSize: Int, onProgress: (Float) -> Unit): List<String> = withContext(Dispatchers.IO) {
        val fileId = UUID.randomUUID().toString()
        val resolver = context.contentResolver
        var size = 0L; var name = "file"
        resolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                size = it.getLong(it.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE))
                name = it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
            }
        }
        val numChunks = Math.ceil(size.toDouble() / chunkSize).toInt().coerceAtLeast(1)
        val msgIds = mutableListOf<MessageId>()
        resolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(chunkSize)
            for (i in 0 until numChunks) {
                val read = stream.read(buffer)
                if (read <= 0) break
                val encrypted = encryptionKey?.let { CryptoUtils.encrypt(buffer.copyOf(read), it) } ?: buffer.copyOf(read)
                apiService.uploadFile(baseUrl, "${fileId}_${name}.part$i", encrypted)?.let { msgIds.add(MessageId(it, 0)) }
                onProgress((i + 1).toFloat() / numChunks)
            }
        }
        val newFile = DisboxFile(fileId, path, msgIds, size)
        apiService.upsertFile(identifier, newFile)
        val files = getFileSystem(false).toMutableList()
        files.add(newFile)
        saveMetadataToLocal(files)
        msgIds.map { it.msgId }
    }

    suspend fun downloadFileChunk(msgId: String, index: Int): ByteArray? {
        val msg = apiService.getMessage(baseUrl, msgId) ?: return null
        @Suppress("UNCHECKED_CAST")
        val attachments = msg["attachments"] as? List<Map<String, Any>>
        val url = if (attachments != null && index < attachments.size) attachments[index]["url"] as? String else attachments?.firstOrNull()?.get("url") as? String
        val data = apiService.downloadAttachment(url ?: return null) ?: return null
        return encryptionKey?.let { CryptoUtils.decrypt(data, it) } ?: data
    }

    suspend fun verifyPin(pin: String): Boolean {
        val hash = hashedWebhook ?: return false
        val stored = settingsDao.getSetting(hash, "pin_hash")?.value ?: return false
        val hashed = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
        return stored == hashed
    }

    suspend fun getShareSettings(): ShareSettings {
        val hash = hashedWebhook ?: return ShareSettings("")
        return shareSettingsDao.getSettings(hash)?.let { ShareSettings(it.hash, it.mode, it.cf_worker_url, it.cf_api_token, it.webhook_url, it.enabled == 1) }
            ?: ShareSettings(hash, "public", "https://disbox-shared-link.naufal-backup.workers.dev", null, webhookUrl, false)
    }

    suspend fun getShareLinks(): List<ShareLink> {
        val hash = hashedWebhook ?: return emptyList()
        return shareLinkDao.getAllLinksByHash(hash).map { ShareLink(it.id, it.hash, it.filePath, it.fileId, it.token, it.permission, it.expiresAt, it.createdAt) }
    }

    suspend fun createShareLink(filePath: String, fileId: String?, permission: String, expiresAt: Long?): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val hash = hashedWebhook ?: return@withContext mapOf("ok" to false)
            val settings = getShareSettings()
            val workerUrl = (settings.cf_worker_url ?: "https://disbox-shared-link.naufal-backup.workers.dev").trimEnd('/')
            val token = UUID.randomUUID().toString().replace("-", "")
            val file = if (fileId != null) fileDao.getFileById(fileId, hash) else fileDao.getFileByPath(filePath.trim('/'), hash)
            val msgIdsRaw = gson.fromJson<List<MessageId>>(file?.messageIds ?: "[]", object : TypeToken<List<MessageId>>() {}.type)
            val msgIds = msgIdsRaw.map { MessageIdRequest(it.msgId, it.index) }
            val encKey = android.util.Base64.encodeToString(encryptionKey, android.util.Base64.NO_WRAP)
            val request = ShareLinkRequest(token, fileId, filePath, permission, expiresAt, hash, msgIds, encKey, baseUrl)
            val res = apiService.createShareLink(workerUrl, "disbox-shared-link-0001", gson.toJson(request))
            if (res != null && res["ok"] == true) {
                shareLinkDao.insertOrReplace(ShareLinkEntity(UUID.randomUUID().toString(), hash, filePath, fileId, token, permission, expiresAt, System.currentTimeMillis()))
                return@withContext mapOf("ok" to true, "link" to "$workerUrl/share/$token")
            }
            mapOf("ok" to false)
        } catch (e: Exception) { mapOf("ok" to false, "reason" to e.message) }
    }
}
