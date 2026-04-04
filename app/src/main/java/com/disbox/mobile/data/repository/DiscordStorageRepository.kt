package com.disbox.mobile.data.repository

import android.content.Context
import android.net.Uri
import com.disbox.mobile.model.MessageId
import com.disbox.mobile.utils.CryptoUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscordStorageRepository @Inject constructor(
    private val client: HttpClient,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) {
    private val CHUNK_SIZE = 7 * 1024 * 1024 + 512 * 1024 // 7.5 MB
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun uploadFile(uri: Uri, fileName: String, onProgress: (Float) -> Unit): Result<List<MessageId>> = withContext(Dispatchers.IO) {
        val webhookUrl = sessionManager.getWebhookUrl() ?: return@withContext Result.failure(Exception("No webhook URL configured"))
        val key = CryptoUtils.deriveKey(webhookUrl)
        
        val messageIds = mutableListOf<MessageId>()
        
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext Result.failure(Exception("Could not open input stream"))
            val totalSize = inputStream.available().toLong()
            var uploadedSize = 0L
            
            val buffer = ByteArray(CHUNK_SIZE)
            var bytesRead: Int
            var index = 0
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                val chunkData = if (bytesRead == CHUNK_SIZE) buffer else buffer.copyOf(bytesRead)
                val encryptedData = CryptoUtils.encryptGCM(chunkData, key)
                
                val response = client.submitFormWithBinaryData(
                    url = webhookUrl,
                    formData = formData {
                        append("file", encryptedData, Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"chunk_$index.dbx\"")
                        })
                    }
                )
                
                if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
                    val body = response.bodyAsText()
                    val jsonResponse = json.parseToJsonElement(body).jsonObject
                    val msgId = jsonResponse["id"]?.jsonPrimitive?.content ?: throw Exception("Failed to get message ID from Discord")
                    messageIds.add(MessageId(msgId, index))
                    
                    uploadedSize += bytesRead
                    onProgress(uploadedSize.toFloat() / totalSize)
                    index++
                } else {
                    return@withContext Result.failure(Exception("Discord upload failed with status ${response.status}"))
                }
            }
            inputStream.close()
            Result.success(messageIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(messageIds: List<MessageId>, fileName: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        val webhookUrl = sessionManager.getWebhookUrl() ?: return@withContext Result.failure(Exception("No webhook URL configured"))
        val key = CryptoUtils.deriveKey(webhookUrl)
        
        // Extract channel ID and token from webhook URL
        // https://discord.com/api/webhooks/{id}/{token}
        val parts = webhookUrl.split("/")
        if (parts.size < 7) return@withContext Result.failure(Exception("Invalid webhook URL"))
        val webhookId = parts[parts.size - 2]
        val webhookToken = parts[parts.size - 1]
        
        try {
            val sortedIds = messageIds.sortedBy { it.index }
            val combinedData = mutableListOf<Byte>()
            
            for (msgId in sortedIds) {
                // Discord message API for webhooks: GET /webhooks/{id}/{token}/messages/{message_id}
                val response = client.get("https://discord.com/api/webhooks/$webhookId/$webhookToken/messages/${msgId.msgId}")
                if (response.status == HttpStatusCode.OK) {
                    val body = response.bodyAsText()
                    val jsonResponse = json.parseToJsonElement(body).jsonObject
                    val attachments = jsonResponse["attachments"]?.jsonArray ?: throw Exception("No attachments found")
                    if (attachments.isEmpty()) throw Exception("Empty attachments")
                    
                    val downloadUrl = attachments[0].jsonObject["url"]?.jsonPrimitive?.content ?: throw Exception("No download URL")
                    
                    val fileResponse = client.get(downloadUrl)
                    if (fileResponse.status == HttpStatusCode.OK) {
                        val encryptedChunk = fileResponse.readBytes()
                        val decryptedChunk = CryptoUtils.decryptGCM(encryptedChunk, key)
                        combinedData.addAll(decryptedChunk.toList())
                    } else {
                        return@withContext Result.failure(Exception("Failed to download chunk ${msgId.index}"))
                    }
                } else {
                    return@withContext Result.failure(Exception("Failed to fetch message ${msgId.msgId} from Discord"))
                }
            }
            Result.success(combinedData.toByteArray())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
