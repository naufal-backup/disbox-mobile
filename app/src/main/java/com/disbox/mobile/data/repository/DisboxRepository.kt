package com.disbox.mobile.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.disbox.mobile.utils.CryptoUtils
import com.disbox.mobile.data.service.DisboxApiService
import com.disbox.mobile.model.ShareLink
import com.disbox.mobile.model.ShareSettings
import com.disbox.mobile.model.DisboxFile
import com.disbox.mobile.model.MessageId
import com.disbox.mobile.model.MetadataContainer
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
    private var encryptionKey: ByteArray? = null
    private val baseUrl: String get() = CryptoUtils.normalizeUrl(webhookUrl)
    
    private val fileDao = db.fileDao()
    private val metaDao = db.metadataSyncDao()
    private val settingsDao = db.settingsDao()
    private val shareSettingsDao = db.shareSettingsDao()
    private val shareLinkDao = db.shareLinkDao()
    private val gson = apiService.getGson()

    private val metadataDir: File by lazy {
        val dir = context.getExternalFilesDir("metadata") ?: File(context.filesDir, "metadata")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    suspend fun init(url: String, forceId: String? = null): String = withContext(Dispatchers.IO) {
        webhookUrl = url
        hashedWebhook = hashWebhook(url)
        encryptionKey = CryptoUtils.deriveKey(url)
        migrateJsonToSqlite()
        syncMetadata(forceId = forceId, forceSync = true)
        hashedWebhook!!
    }

    private fun hashWebhook(url: String): String {
        val normalized = CryptoUtils.normalizeUrl(url)
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
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
                saveMetadataToLocal(container.files, container.lastMsgId, container.isDirty == true, container.snapshotHistory ?: emptyList())
                container.pinHash?.let { setPin(it, isAlreadyHashed = true) }
                localFile.renameTo(File(localFile.parent, "${localFile.name}.bak"))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    suspend fun syncMetadata(forceId: String? = null, forceSync: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val discovery = if (forceId != null) mapOf("best" to forceId, "history" to emptyList<String>())
            else getMsgIdFromDiscovery(forceSync)

            if (discovery == null || discovery == "pending") return@withContext true

            @Suppress("UNCHECKED_CAST")
            val discMap = discovery as Map<String, Any>
            val msgId = discMap["best"] as String
            val history = discMap["history"] as List<String>

            val currentHash = hashedWebhook ?: return@withContext false
            val meta = metaDao.getMetadata(currentHash)
            if (!forceSync && forceId == null && msgId == meta?.lastMsgId) return@withContext true

            var resolvedMsgId = msgId
            var container: MetadataContainer? = null

            try {
                container = downloadMetadataFromMsg(msgId)
            } catch (e: Exception) {
                for (fid in history.reversed().filter { it != msgId }) {
                    try { container = downloadMetadataFromMsg(fid); resolvedMsgId = fid; break } catch (err: Exception) {}
                }
            }

            if (container != null) {
                saveMetadataToLocal(container.files, resolvedMsgId, shareLinks = container.shareLinks ?: emptyList())
                container.pinHash?.let { setPin(it, isAlreadyHashed = true) }
                true
            } else false
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private suspend fun getMsgIdFromDiscovery(force: Boolean): Any? {
        val currentHash = hashedWebhook ?: return null
        val meta = metaDao.getMetadata(currentHash)
        if (meta?.isDirty == 1 && !force) return "pending"

        var webhookMsgId: String? = null
        apiService.getWebhookInfo(baseUrl)?.let { info ->
            val name = info["name"] as? String
            webhookMsgId = Regex("(?:dbx|disbox|db)[:\\s]+(\\d+)", RegexOption.IGNORE_CASE).find(name ?: "")?.groupValues?.get(1)
        }

        if (webhookMsgId == null) {
            apiService.listMessages(baseUrl)?.forEach { msg ->
                val attachments = msg["attachments"] as? List<Map<String, Any>>
                if (attachments?.any { (it["filename"] as? String)?.contains("metadata.json") == true } == true) {
                    webhookMsgId = msg["id"] as? String
                    return@forEach
                }
            }
        }

        val best = listOfNotNull(meta?.lastMsgId, webhookMsgId).mapNotNull { it.toLongOrNull() }.maxOrNull()?.toString() ?: return null
        val history = try { gson.fromJson<List<String>>(meta?.snapshotHistory ?: "[]", object : TypeToken<List<String>>() {}.type) } catch (e: Exception) { emptyList() }
        return mapOf("best" to best, "history" to history)
    }

    private suspend fun downloadMetadataFromMsg(msgId: String): MetadataContainer {
        val msg = apiService.getMessage(baseUrl, msgId) ?: throw Exception("Message not found")
        @Suppress("UNCHECKED_CAST")
        val attachments = msg["attachments"] as? List<Map<String, Any>>
        val url = attachments?.firstOrNull { (it["filename"] as? String)?.contains("metadata.json") == true }?.get("url") as? String
            ?: attachments?.firstOrNull()?.get("url") as? String ?: throw Exception("No attachment")

        val encryptedBytes = apiService.downloadAttachment(url) ?: throw Exception("Download failed")
        val decryptedBytes = encryptionKey?.let { CryptoUtils.decrypt(encryptedBytes, it) } ?: encryptedBytes
        val body = String(decryptedBytes)

        return try {
            val element = gson.fromJson(body, com.google.gson.JsonElement::class.java)
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                MetadataContainer(
                    files = gson.fromJson(obj.get("files"), object : TypeToken<List<DisboxFile>>() {}.type) ?: emptyList(),
                    shareLinks = gson.fromJson(obj.get("shareLinks"), object : TypeToken<List<ShareLink>>() {}.type) ?: emptyList(),
                    pinHash = if (obj.has("pinHash") && !obj.get("pinHash").isJsonNull) obj.get("pinHash").asString else null
                )
            } else {
                MetadataContainer(files = gson.fromJson(body, object : TypeToken<List<DisboxFile>>() {}.type) ?: emptyList())
            }
        } catch (e: Exception) {
            MetadataContainer(files = gson.fromJson(body, object : TypeToken<List<DisboxFile>>() {}.type) ?: emptyList())
        }
    }

    suspend fun saveMetadataToLocal(files: List<DisboxFile>, msgId: String? = null, isDirty: Boolean = false, explicitHistory: List<String>? = null, shareLinks: List<ShareLink>? = null) = withContext(Dispatchers.IO) {
        val hash = hashedWebhook ?: return@withContext
        db.withTransaction {
            fileDao.deleteAllByHash(hash)
            fileDao.insertAll(files.map { f ->
                val norm = f.path.trim('/'); val parts = norm.split("/"); val name = parts.last(); val parent = parts.dropLast(1).joinToString("/").ifEmpty { "/" }
                FileEntity(f.id, hash, norm, parent, name, f.size, f.createdAt, gson.toJson(f.messageIds), if (f.isLocked) 1 else 0, if (f.isStarred) 1 else 0)
            })
            shareLinks?.let { links ->
                shareLinkDao.deleteAllByHash(hash)
                links.forEach { shareLinkDao.insertOrReplace(ShareLinkEntity(it.id, hash, it.file_path, it.file_id, it.token, it.permission, it.expires_at, it.created_at)) }
            }
            val meta = metaDao.getMetadata(hash)
            val history = explicitHistory?.toMutableList() ?: gson.fromJson<MutableList<String>>(meta?.snapshotHistory ?: "[]", object : TypeToken<MutableList<String>>() {}.type)
            if (msgId != null && !history.contains(msgId)) { history.add(msgId); if (history.size > 3) history.removeAt(0) }
            metaDao.insertOrReplace(MetadataSyncEntity(hash, msgId ?: meta?.lastMsgId, gson.toJson(history), if (isDirty) 1 else 0, System.currentTimeMillis()))
        }
    }

    suspend fun uploadMetadataToDiscord(explicitFiles: List<DisboxFile>? = null) = withContext(Dispatchers.IO) {
        val hash = hashedWebhook ?: return@withContext
        val files = explicitFiles ?: getFileSystem(false)
        val pin = settingsDao.getSetting(hash, "pin_hash")?.value
        val links = getShareLinks()
        val container = MetadataContainer(files = files, pinHash = pin, shareLinks = links)
        val json = gson.toJson(container)
        val encrypted = encryptionKey?.let { CryptoUtils.encrypt(json.toByteArray(), it) } ?: json.toByteArray()

        apiService.uploadFile(baseUrl, "metadata.json", encrypted)?.let { newMsgId ->
            saveMetadataToLocal(files, newMsgId, false, shareLinks = links)
            apiService.patchWebhookName(baseUrl, "dbx: $newMsgId")
        }
    }

    suspend fun getFileSystem(filterCloudSave: Boolean = true): List<DisboxFile> = withContext(Dispatchers.IO) {
        val hash = hashedWebhook ?: return@withContext emptyList()
        fileDao.getAllFilesByHash(hash).mapNotNull {
            if (filterCloudSave && it.path.startsWith("cloudsave/")) null
            else DisboxFile(it.id, it.path, gson.fromJson(it.messageIds, object : TypeToken<List<MessageId>>() {}.type), it.size, it.createdAt, it.isLocked == 1, it.isStarred == 1)
        }
    }

    suspend fun getShareLinks(): List<ShareLink> = withContext(Dispatchers.IO) {
        val hash = hashedWebhook ?: return@withContext emptyList()
        shareLinkDao.getAllLinksByHash(hash).map { ShareLink(it.id, it.hash, it.filePath, it.fileId, it.token, it.permission, it.expiresAt, it.createdAt) }
    }

    suspend fun setPin(pin: String, isAlreadyHashed: Boolean = false) {
        val hash = hashedWebhook ?: return
        val hashedPin = if (isAlreadyHashed) pin else MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
        settingsDao.insertOrReplace(SettingsEntity(hash, "pin_hash", hashedPin))
        if (!isAlreadyHashed) uploadMetadataToDiscord()
    }

    suspend fun verifyPin(pin: String): Boolean {
        val hash = hashedWebhook ?: return false
        val stored = settingsDao.getSetting(hash, "pin_hash")?.value ?: return false
        val hashed = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
        return stored == hashed
    }

    suspend fun createFolder(name: String, parentPath: String): Boolean {
        val normParent = parentPath.trim('/')
        val fullPath = (if (normParent.isEmpty()) name else "$normParent/$name") + "/.keep"
        if (fileDao.getFileByPath(fullPath, hashedWebhook!!) != null) return false
        val files = getFileSystem(false).toMutableList()
        val newFile = DisboxFile(UUID.randomUUID().toString(), fullPath, emptyList(), 0)
        files.add(newFile)
        saveMetadataToLocal(files, isDirty = true)
        uploadMetadataToDiscord(files)
        return true
    }

    suspend fun deletePaths(idsOrPaths: List<String>) {
        val files = getFileSystem(false).filterNot { f ->
            idsOrPaths.any { target -> f.id == target || f.path == target.trim('/') || f.path.startsWith("${target.trim('/')}/") }
        }
        saveMetadataToLocal(files, isDirty = true)
        uploadMetadataToDiscord(files)
    }

    suspend fun renamePath(oldPath: String, newName: String, id: String? = null): Boolean {
        val normOld = oldPath.trim('/')
        val parts = normOld.split("/"); val parent = parts.dropLast(1).joinToString("/")
        val normNew = if (parent.isEmpty()) newName else "$parent/$newName"
        val files = getFileSystem(false).map {
            when {
                (id != null && it.id == id) || it.path == normOld -> it.copy(path = normNew)
                it.path.startsWith("$normOld/") -> it.copy(path = it.path.replaceFirst("$normOld/", "$normNew/"))
                else -> it
            }
        }
        saveMetadataToLocal(files, isDirty = true)
        uploadMetadataToDiscord(files)
        return true
    }

    suspend fun bulkSetStatus(idsOrPaths: Set<String>, isLocked: Boolean? = null, isStarred: Boolean? = null) {
        val files = getFileSystem(false).map { f ->
            var l = f.isLocked; var s = f.isStarred
            val isTarget = idsOrPaths.any { t -> f.id == t || f.path == t.trim('/') || f.path.startsWith("${t.trim('/')}/") }
            if (isTarget) { if (isLocked != null) l = isLocked; if (isStarred != null) s = isStarred }
            f.copy(isLocked = l, isStarred = s)
        }
        saveMetadataToLocal(files, isDirty = true)
        uploadMetadataToDiscord(files)
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
                val data = buffer.copyOf(read)
                val encrypted = encryptionKey?.let { CryptoUtils.encrypt(data, it) } ?: data
                apiService.uploadFile(baseUrl, "${fileId}_${name}.part$i", encrypted)?.let { msgIds.add(MessageId(it, 0)) }
                onProgress((i + 1).toFloat() / numChunks)
            }
        }
        val files = getFileSystem(false).toMutableList()
        files.add(DisboxFile(fileId, path, msgIds, size))
        saveMetadataToLocal(files, isDirty = true)
        uploadMetadataToDiscord(files)
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

    suspend fun getShareSettings(): ShareSettings {
        val hash = hashedWebhook ?: return ShareSettings("")
        return shareSettingsDao.getSettings(hash)?.let { ShareSettings(it.hash, it.mode, it.cf_worker_url, it.cf_api_token, it.webhook_url, it.enabled == 1) }
            ?: ShareSettings(hash, "public", "https://disbox-shared-link.naufal-backup.workers.dev", null, webhookUrl, false)
    }

    suspend fun saveShareSettings(s: ShareSettings) {
        val hash = hashedWebhook ?: return
        shareSettingsDao.insertOrReplace(ShareSettingsEntity(hash, s.mode, s.cf_worker_url, s.cf_api_token, s.webhook_url, if (s.enabled) 1 else 0))
    }

    suspend fun createShareLink(filePath: String, fileId: String?, permission: String, expiresAt: Long?): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val hash = hashedWebhook ?: return@withContext mapOf("ok" to false)
            val settings = getShareSettings()
            val workerUrl = (settings.cf_worker_url ?: "https://disbox-shared-link.naufal-backup.workers.dev").trimEnd('/')
            val token = UUID.randomUUID().toString().replace("-", "")
            
            val file = if (fileId != null) fileDao.getFileById(fileId, hash) else fileDao.getFileByPath(filePath.trim('/'), hash)
            val msgIdsRaw = gson.fromJson<List<MessageId>>(file?.messageIds ?: "[]", object : TypeToken<List<MessageId>>() {}.type)
            val msgIds: List<Map<String, Any>> = msgIdsRaw.map { 
                mapOf<String, Any>(
                    "msgId" to it.msgId,
                    "index" to it.index
                )
            }
            val encKey = android.util.Base64.encodeToString(encryptionKey, android.util.Base64.NO_WRAP)

            val body = mapOf<String, Any>(
                "token" to token,
                "fileId" to (fileId ?: ""),
                "filePath" to filePath,
                "permission" to permission,
                "expiresAt" to (expiresAt ?: 0L),
                "webhookHash" to hash,
                "messageIds" to msgIds,
                "encryptionKeyB64" to encKey,
                "webhookUrl" to baseUrl
            )

            apiService.createShareLink(workerUrl, "disbox-shared-link-0001", body)?.let {

                val id = UUID.randomUUID().toString()
                shareLinkDao.insertOrReplace(ShareLinkEntity(id, hash, filePath, fileId, token, permission, expiresAt, System.currentTimeMillis()))
                uploadMetadataToDiscord()
                return@withContext mapOf("ok" to true, "link" to "$workerUrl/share/$token", "token" to token, "id" to id)
            }
            mapOf("ok" to false)
        } catch (e: Exception) { mapOf("ok" to false, "reason" to e.message) }
    }
}
