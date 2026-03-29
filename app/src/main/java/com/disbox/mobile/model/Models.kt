package com.disbox.mobile.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class MessageId(
    @SerializedName("msgId") val msgId: String,
    @SerializedName("index") val index: Int = 0
)

data class DisboxFile(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("path") var path: String,
    @SerializedName("messageIds") val messageIds: List<MessageId>,
    @SerializedName("size") val size: Long,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("isLocked") val isLocked: Boolean = false,
    @SerializedName("isStarred") val isStarred: Boolean = false
)

data class ShareLink(
    @SerializedName("id") val id: String,
    @SerializedName("hash") val hash: String,
    @SerializedName("file_path") val file_path: String,
    @SerializedName("file_id") val file_id: String?,
    @SerializedName("token") val token: String,
    @SerializedName("permission") val permission: String,
    @SerializedName("expires_at") val expires_at: Long?,
    @SerializedName("created_at") val created_at: Long
)

data class ShareSettings(
    @SerializedName("hash") val hash: String,
    @SerializedName("mode") val mode: String = "public",
    @SerializedName("cf_worker_url") val cf_worker_url: String?,
    @SerializedName("cf_api_token") val cf_api_token: String?,
    @SerializedName("webhook_url") val webhook_url: String?,
    @SerializedName("enabled") val enabled: Boolean = false
)

data class MetadataContainer(
    @SerializedName("files") val files: List<DisboxFile>,
    @SerializedName("pinHash") val pinHash: String? = null,
    @SerializedName("shareLinks") val shareLinks: List<ShareLink>? = null,
    @SerializedName("lastMsgId") val lastMsgId: String? = null,
    @SerializedName("isDirty") val isDirty: Boolean? = null,
    @SerializedName("updatedAt") val updatedAt: Long? = null,
    @SerializedName("snapshotHistory") val snapshotHistory: List<String>? = null
)
