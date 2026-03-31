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

    private val BASE_API_URL = "https://disbox-web-weld.vercel.app/api"

    // --- AUTH API ---
    suspend fun login(body: Map<String, Any>): Map<String, Any>? {
        val json = gson.toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_API_URL/auth/login").post(json).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return gson.fromJson(response.body?.string(), object : TypeToken<Map<String, Any>>() {}.type)
            }
        }
        return null
    }

    suspend fun register(body: Map<String, Any>): Map<String, Any>? {
        val json = gson.toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_API_URL/auth/register").post(json).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return gson.fromJson(response.body?.string(), object : TypeToken<Map<String, Any>>() {}.type)
            }
        }
        return null
    }

    // --- FILES API (DATABASE-FIRST) ---
    suspend fun listFiles(identifier: String): Map<String, Any>? {
        val request = Request.Builder().url("$BASE_API_URL/files/list?identifier=$identifier").build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return gson.fromJson(response.body?.string(), object : TypeToken<Map<String, Any>>() {}.type)
            }
        }
        return null
    }

    suspend fun syncAllFiles(identifier: String, files: List<FileItem>): Boolean {
        val body = mapOf("identifier" to identifier, "files" to files)
        val json = gson.toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_API_URL/files/sync-all").post(json).build()
        client.newCall(request).execute().use { response -> return response.isSuccessful }
    }

    suspend fun upsertFile(identifier: String, file: FileItem): Boolean {
        val body = mapOf("identifier" to identifier, "file" to file)
        val json = gson.toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_API_URL/files/upsert").post(json).build()
        client.newCall(request).execute().use { response -> return response.isSuccessful }
    }

    suspend fun deleteFile(identifier: String, path: String): Boolean {
        val body = mapOf("identifier" to identifier, "path" to path)
        val json = gson.toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_API_URL/files/delete").post(json).build()
        client.newCall(request).execute().use { response -> return response.isSuccessful }
    }

    // --- CLOUD CONFIG ---
    suspend fun getCloudConfig(identifier: String): Map<String, Any>? {
        val request = Request.Builder().url("$BASE_API_URL/cloud/config?identifier=$identifier").build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return gson.fromJson(response.body?.string(), object : TypeToken<Map<String, Any>>() {}.type)
            }
        }
        return null
    }

    // --- LEGACY DISCORD / WEBHOOK API ---
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

    // --- SHARE API ---
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

    fun getGson(): Gson = gson
}
