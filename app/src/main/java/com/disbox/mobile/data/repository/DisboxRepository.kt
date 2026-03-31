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
            
            // 1. Prioritas Utama: Supabase (Row-level files)
            val cloudFilesRes = apiService.listFiles(id)
            if (cloudFilesRes != null && cloudFilesRes["ok"] == true) {
                val files = gson.fromJson<List<DisboxFile>>(gson.toJson(cloudFilesRes["files"]), object : TypeToken<List<DisboxFile>>() {}.type)
                if (files.isNotEmpty()) {
                    saveMetadataToLocal(files)
                    return@withContext true
                }
            }

            // 2. Fallback: Migrasi Legacy (CDN/Discord)
            var legacyContainer: MetadataContainer? = null
            
            // A. Cek Legacy Blob di Cloud Config
            apiService.getCloudConfig(id)?.let { cfg ->
                val b64 = cfg["metadata_b64"] as? String
                if (b64 != null) {
                    val encryptedBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    val decryptedBytes = encryptionKey?.let { CryptoUtils.decrypt(encryptedBytes, it) } ?: encryptedBytes
                    legacyContainer = gson.fromJson(String(decryptedBytes), MetadataContainer::class.java)
                }
            }

            // B. Cek Metadata URL (CDN)
            if (legacyContainer == null && metadataUrl?.startsWith("http") == true) {
                legacyContainer = downloadMetadataFromUrl(metadataUrl)
            }

            // C. Cek Discord Discovery
            if (legacyContainer == null) {
                val disc = getMsgIdFromDiscovery(forceSync) as? Map<String, Any>
                val bestId = forceId ?: disc?.get("best") as? String
                if (bestId != null) {
                    legacyContainer = downloadMetadataFromMsg(bestId)
                }
            }

            if (legacyContainer != null) {
                val files = legacyContainer!!.files
                // Migrasi ke Supabase Rows
                files.forEach { apiService.upsertFile(id, it) }
                saveMetadataToLocal(files)
                return@withContext true
            }

            // Jika benar-benar baru, init array kosong agar tidak looping
            if (forceSync) saveMetadataToLocal(emptyList())
            
            false
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private suspend fun getMsgIdFromDiscovery(force: Boolean): Any? {
        var webhookMsgId: String? = null
        apiService.getWebhookInfo(baseUrl)?.let { info ->
            val name = info["name"] as? String
            webhookMsgId = Regex("(?:dbx|disbox|db)[:\\s]+(\\d+)", RegexOption.IGNORE_CASE).find(name ?: "")?.groupValues?.get(1)
        }
        return mapOf("best" to webhookMsgId)
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
        // Background task: Sync All to Supabase
        withContext(Dispatchers.IO) {
            apiService.syncAllFiles(id, files)
            
            // Backup as Blob also
            val pin = settingsDao.getSetting(hashedWebhook!!, "pin_hash")?.value
            val container = MetadataContainer(files = files, pinHash = pin)
            val json = gson.toJson(container)
            val encrypted = encryptionKey?.let { CryptoUtils.encrypt(json.toByteArray(), it) } ?: json.toByteArray()
            
            apiService.uploadFile(baseUrl, "metadata.json", encrypted)?.let {
                apiService.patchWebhookName(baseUrl, "dbx: $it")
            }
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
        
        // Save to Supabase Row
        apiService.upsertFile(identifier, newFile)
        
        // Update Local
        val files = getFileSystem(false).toMutableList()
        files.add(newFile)
        saveMetadataToLocal(files)
        true
    }

    suspend fun deletePaths(idsOrPaths: List<String>) = withContext(Dispatchers.IO) {
        val id = identifier
        idsOrPaths.forEach { target ->
            apiService.deleteFile(id, target.trim('/'))
        }
        
        val files = getFileSystem(false).filterNot { f ->
            idsOrPaths.any { target -> f.id == target || f.path == target.trim('/') || f.path.startsWith("${target.trim('/')}/") }
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

    // --- OTHER HELPERS ---
    suspend fun verifyPin(pin: String): Boolean {
        val hash = hashedWebhook ?: return false
        val stored = settingsDao.getSetting(hash, "pin_hash")?.value ?: return false
        val hashed = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
        return stored == hashed
    }
}
