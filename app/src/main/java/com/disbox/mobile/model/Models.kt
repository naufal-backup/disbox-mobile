package com.disbox.mobile.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

@Serializable
data class MessageId(
    @SerialName("msgId") val msgId: String,
    @SerialName("index") val index: Int = 0
)

@Serializable
data class DisboxFile(
    @SerialName("id") val id: String = UUID.randomUUID().toString(),
    @SerialName("path") var path: String,
    @SerialName("messageIds") val messageIds: List<MessageId>,
    @SerialName("size") val size: Long,
    @SerialName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("isLocked") val isLocked: Boolean = false,
    @SerialName("isStarred") val isStarred: Boolean = false,
    @SerialName("thumbnailMsgId") val thumbnailMsgId: String? = null,
    
    // UI Only (Not stored in JSON structure usually, but used for Optimistic UI)
    @Transient var isOptimistic: Boolean = false,
    @Transient var progress: Float = 0f
)

@Serializable
data class ShareLink(
    @SerialName("id") val id: String,
    @SerialName("hash") val hash: String,
    @SerialName("file_path") val file_path: String,
    @SerialName("file_id") val file_id: String? = null,
    @SerialName("token") val token: String,
    @SerialName("permission") val permission: String,
    @SerialName("expires_at") val expires_at: Long? = null,
    @SerialName("created_at") val created_at: Long
)

@Serializable
data class ShareSettings(
    @SerialName("hash") val hash: String,
    @SerialName("mode") val mode: String = "public",
    @SerialName("cf_worker_url") val cf_worker_url: String? = null,
    @SerialName("cf_api_token") val cf_api_token: String? = null,
    @SerialName("webhook_url") val webhook_url: String? = null,
    @SerialName("enabled") val enabled: Boolean = false
)

@Serializable
data class MetadataContainer(
    @SerialName("files") val files: List<DisboxFile>,
    @SerialName("pinHash") val pinHash: String? = null,
    @SerialName("shareLinks") val shareLinks: List<ShareLink>? = null,
    @SerialName("lastMsgId") val lastMsgId: String? = null,
    @SerialName("isDirty") val isDirty: Boolean? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null,
    @SerialName("snapshotHistory") val snapshotHistory: List<String>? = null
)

@Serializable
data class MessageIdRequest(
    @SerialName("msgId") val msgId: String,
    @SerialName("index") val index: Int
)

@Serializable
data class ShareLinkRequest(
    @SerialName("token") val token: String,
    @SerialName("fileId") val fileId: String?,
    @SerialName("filePath") val filePath: String,
    @SerialName("permission") val permission: String,
    @SerialName("expiresAt") val expiresAt: Long?,
    @SerialName("webhookHash") val webhookHash: String,
    @SerialName("messageIds") val messageIds: List<MessageIdRequest>,
    @SerialName("encryptionKeyB64") val encryptionKeyB64: String,
    @SerialName("webhookUrl") val webhookUrl: String
)
