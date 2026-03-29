package com.disbox.mobile.data.service

import com.disbox.mobile.model.*
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.reflect.Type
import java.util.UUID

class MessageIdAdapter : JsonDeserializer<MessageId>, JsonSerializer<MessageId> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): MessageId {
        return if (json.isJsonPrimitive) {
            MessageId(json.asString, 0)
        } else {
            val obj = json.asJsonObject
            MessageId(
                obj.get("msgId")?.asString ?: obj.get("id")?.asString ?: "",
                obj.get("index")?.asInt ?: 0
            )
        }
    }

    override fun serialize(src: MessageId, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        obj.addProperty("msgId", src.msgId)
        obj.addProperty("index", src.index)
        return obj
    }
}

class DisboxApiService {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build()
            chain.proceed(request)
        }
        .build()

    private val gson = GsonBuilder()
        .registerTypeAdapter(MessageId::class.java, MessageIdAdapter())
        .create()

    suspend fun getWebhookInfo(baseUrl: String): Map<String, Any>? {
        val request = Request.Builder().url(baseUrl).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                return gson.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)
            }
        }
        return null
    }

    suspend fun listMessages(baseUrl: String, limit: Int = 100): List<Map<String, Any>>? {
        val url = if (baseUrl.endsWith("/")) "${baseUrl}messages?limit=$limit" else "$baseUrl/messages?limit=$limit"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                return gson.fromJson(body, object : TypeToken<List<Map<String, Any>>>() {}.type)
            }
        }
        return null
    }

    suspend fun getMessage(baseUrl: String, msgId: String): Map<String, Any>? {
        val request = Request.Builder().url("$baseUrl/messages/$msgId").build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                return gson.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)
            }
        }
        return null
    }

    suspend fun downloadAttachment(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return response.body?.bytes()
            }
        }
        return null
    }

    suspend fun uploadFile(baseUrl: String, filename: String, data: ByteArray): String? {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", filename,
                data.toRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder().url("$baseUrl?wait=true").post(body).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return null
                val map: Map<String, Any> = gson.fromJson(bodyStr, object : TypeToken<Map<String, Any>>() {}.type)
                return map["id"] as? String
            }
        }
        return null
    }

    suspend fun patchWebhookName(baseUrl: String, name: String): Boolean {
        val patchBody = gson.toJson(mapOf("name" to name))
            .toRequestBody("application/json".toMediaTypeOrNull())
        client.newCall(Request.Builder().url(baseUrl).patch(patchBody).build()).execute().use { response ->
            return response.isSuccessful
        }
    }

    suspend fun createShareLink(workerUrl: String, apiKey: String, bodyJson: String): Map<String, Any>? {
        val body = bodyJson.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$workerUrl/share/create")
            .post(body)
            .addHeader("X-Disbox-Key", apiKey)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return null
                return gson.fromJson(bodyStr, object : TypeToken<Map<String, Any>>() {}.type)
            }
        }
        return null
    }

    suspend fun deleteShareLink(workerUrl: String, apiKey: String, token: String): Boolean {
        val request = Request.Builder()
            .url("$workerUrl/share/revoke/$token")
            .delete()
            .addHeader("X-Disbox-Key", apiKey)
            .build()

        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }

    suspend fun deleteAllShareLinks(workerUrl: String, apiKey: String, hash: String): Boolean {
        val request = Request.Builder()
            .url("$workerUrl/share/revoke-all/$hash")
            .delete()
            .addHeader("X-Disbox-Key", apiKey)
            .build()

        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }

    fun getGson(): Gson = gson
}
