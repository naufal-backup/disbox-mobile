package com.disbox.mobile.data.service

import com.disbox.mobile.*
import com.disbox.mobile.data.repository.SessionManager
import com.disbox.mobile.model.DisboxFile
import com.disbox.mobile.model.MetadataContainer
import com.disbox.mobile.utils.CryptoUtils
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataSyncService @Inject constructor(
    private val client: HttpClient,
    private val sessionManager: SessionManager,
    private val fileDao: FileDao,
    private val metadataSyncDao: MetadataSyncDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun syncMetadata(): Result<Unit> = withContext(Dispatchers.IO) {
        val webhookUrl = sessionManager.getWebhookUrl() ?: return@withContext Result.failure(Exception("No webhook URL"))
        val hash = CryptoUtils.sha256Hex(CryptoUtils.normalizeUrl(webhookUrl))
        
        // 1. Try to fetch from Supabase if Cloud Auth
        // 2. Or fallback to Discord Auto-discovery (Scanning last 50 messages)
        
        return@withContext try {
            val autoDiscoveryResult = autoDiscoverMetadata(webhookUrl)
            if (autoDiscoveryResult.isSuccess) {
                val container = autoDiscoveryResult.getOrThrow()
                saveToLocalDb(container, hash)
                Result.success(Unit)
            } else {
                autoDiscoveryResult.map { Unit }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun autoDiscoverMetadata(webhookUrl: String): Result<MetadataContainer> {
        val parts = webhookUrl.split("/")
        if (parts.size < 7) return Result.failure(Exception("Invalid webhook URL"))
        val webhookId = parts[parts.size - 2]
        val webhookToken = parts[parts.size - 1]

        // Discord API: GET /webhooks/{id}/{token}/messages?limit=50
        // NOTE: This usually only works for messages created by the webhook itself.
        return try {
            val response = client.get("https://discord.com/api/webhooks/$webhookId/$webhookToken/messages?limit=50")
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val messages = json.parseToJsonElement(body).jsonArray
                
                // Scan messages for metadata chunks (usually pinned or tagged with magic header in content)
                // In Disbox, metadata is usually stored as a JSON file in a message.
                // We'll search for the latest message containing "disbox_metadata.json" or similar.
                
                for (message in messages) {
                    val attachments = message.jsonObject["attachments"]?.jsonArray ?: continue
                    for (attachment in attachments) {
                        val filename = attachment.jsonObject["filename"]?.jsonPrimitive?.content ?: ""
                        if (filename.contains("metadata")) {
                            val url = attachment.jsonObject["url"]?.jsonPrimitive?.content ?: continue
                            val metadataResponse = client.get(url)
                            if (metadataResponse.status == HttpStatusCode.OK) {
                                val metadataJson = metadataResponse.bodyAsText()
                                // If metadata is encrypted, decrypt it here.
                                // Metadata is typically encrypted with AES-GCM using the webhook-derived key.
                                val key = CryptoUtils.deriveKey(webhookUrl)
                                val decrypted = CryptoUtils.decryptGCM(metadataResponse.readBytes(), key)
                                val container = json.decodeFromString<MetadataContainer>(String(decrypted))
                                return Result.success(container)
                            }
                        }
                    }
                }
                Result.failure(Exception("No metadata found in Discord messages"))
            } else {
                Result.failure(Exception("Failed to fetch messages from Discord: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveToLocalDb(container: MetadataContainer, hash: String) {
        val fileEntities = container.files.map { file ->
            val pathParts = file.path.split("/")
            val name = pathParts.last()
            val parentPath = if (pathParts.size > 1) {
                pathParts.dropLast(1).joinToString("/")
            } else {
                ""
            }
            
            FileEntity(
                id = file.id,
                hash = hash,
                path = file.path,
                parentPath = parentPath,
                name = name,
                size = file.size,
                createdAt = file.createdAt,
                messageIds = json.encodeToString(file.messageIds),
                isLocked = if (file.isLocked) 1 else 0,
                isStarred = if (file.isStarred) 1 else 0
            )
        }
        
        fileDao.deleteAllByHash(hash)
        fileDao.insertAll(fileEntities)
        
        metadataSyncDao.insertOrReplace(MetadataSyncEntity(
            hash = hash,
            lastMsgId = container.lastMsgId,
            snapshotHistory = json.encodeToString(container.snapshotHistory ?: emptyList<String>()),
            isDirty = 0,
            updatedAt = container.updatedAt ?: System.currentTimeMillis()
        ))
    }
}
