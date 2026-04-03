package com.disbox.mobile.data.service

import android.content.Context
import com.disbox.mobile.model.*
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

class DisboxApiService(context: Context) {
    var authToken: String? = null
    private val cookieJar = SessionCookieJar(context)

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            
            authToken?.let {
                builder.header("Authorization", "Bearer $it")
            }
            
            val request = builder.build()
            var response = chain.proceed(request)
            
            // Manual redirect handling (max 3 levels) for Vercel/Edge functions
            var redirectCount = 0
            while ((response.code == 301 || response.code == 302 || response.code == 307 || response.code == 308) && redirectCount < 3) {
                val location = response.header("Location") ?: break
                response.close()
                val newRequest = request.newBuilder().url(location).build()
                response = chain.proceed(newRequest)
                redirectCount++
            }
            response
        }
        .build()

    private val gson = GsonBuilder()
        .registerTypeAdapter(MessageId::class.java, MessageIdAdapter())
        .create()

    private val BASE_API_URL = "https://disbox-web-weld.vercel.app/api"

    // --- AUTH API ---
    suspend fun verifyWebhook(url: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        val body = mapOf("webhook_url" to url)
        val json = gson.toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_API_URL/auth/webhook").post(json).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    return@withContext gson.fromJson(bodyStr, object : TypeToken<Map<String, Any>>() {}.type)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }

    suspend fun login(body: Map<String, Any>): Map<String, Any>? = withContext(Dispatchers.IO) {
        val json = gson.toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_API_URL/auth/login").post(json).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    return@withContext gson.fromJson(bodyStr, object : TypeToken<Map<String, Any>>() {}.type)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }

    suspend fun register(body: Map<String, Any>): Map<String, Any>? = withContext(Dispatchers.IO) {
        val json = gson.toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_API_URL/auth/register").post(json).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    return@withContext gson.fromJson(bodyStr, object : TypeToken<Map<String, Any>>() {}.type)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }

    // --- FILES API (DATABASE-FIRST) ---
    suspend fun listFiles(identifier: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE_API_URL/files/list?identifier=$identifier").build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    return@withContext gson.fromJson(bodyStr, object : TypeToken<Map<String, Any>>() {}.type)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }

    suspend fun syncAllFiles(identifier: String, files: List<DisboxFile>): Boolean = withContext(Dispatchers.IO) {
        val body = mapOf("identifier" to identifier, "files" to files)
        val json = gson.toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_API_URL/files/sync-all").post(json).build()
        try {
            client.newCall(request).execute().use { response -> return@withContext response.isSuccessful }
        } catch (e: Exception) { false }
    }

    suspend fun upsertFile(identifier: String, file: DisboxFile): Boolean = withContext(Dispatchers.IO) {
        val body = mapOf("identifier" to identifier, "file" to file)
        val json = gson.toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_API_URL/files/upsert").post(json).build()
        try {
            client.newCall(request).execute().use { response -> return@withContext response.isSuccessful }
        } catch (e: Exception) { false }
    }

    suspend fun deleteFile(identifier: String, path: String): Boolean = withContext(Dispatchers.IO) {
        val body = mapOf("identifier" to identifier, "path" to path)
        val json = gson.toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_API_URL/files/delete").post(json).build()
        try {
            client.newCall(request).execute().use { response -> return@withContext response.isSuccessful }
        } catch (e: Exception) { false }
    }

    // --- CLOUD CONFIG ---
    suspend fun getCloudConfig(identifier: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE_API_URL/cloud/config?identifier=$identifier").build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    return@withContext gson.fromJson(bodyStr, object : TypeToken<Map<String, Any>>() {}.type)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }

    // --- LEGACY DISCORD / WEBHOOK API ---
    suspend fun getWebhookInfo(baseUrl: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    return@withContext gson.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }

    suspend fun getMessage(baseUrl: String, msgId: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/messages/$msgId").build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    return@withContext gson.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }

    suspend fun downloadAttachment(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val isDiscord = url.contains("discord.com") || url.contains("discordapp.com")
        val finalUrl = if (isDiscord) {
            "$BASE_API_URL/proxy?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        } else url

        val request = Request.Builder().url(finalUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext response.body?.bytes()
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }

    fun logout() {
        authToken = null
        cookieJar.clear()
    }

    suspend fun uploadFile(baseUrl: String, filename: String, data: ByteArray): String? = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", filename,
                data.toRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder().url("$baseUrl?wait=true").post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@withContext null
                    val map: Map<String, Any> = gson.fromJson(bodyStr, object : TypeToken<Map<String, Any>>() {}.type)
                    return@withContext map["id"] as? String
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }

    suspend fun patchWebhookName(baseUrl: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val patchBody = gson.toJson(mapOf("name" to name))
            .toRequestBody("application/json".toMediaTypeOrNull())
        try {
            client.newCall(Request.Builder().url(baseUrl).patch(patchBody).build()).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) { false }
    }

    // --- SHARE API ---
    suspend fun createShareLink(workerUrl: String, apiKey: String, bodyJson: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        val body = bodyJson.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$workerUrl/share/create")
            .post(body)
            .addHeader("X-Disbox-Key", apiKey)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@withContext null
                    return@withContext gson.fromJson(bodyStr, object : TypeToken<Map<String, Any>>() {}.type)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }

    fun getGson(): Gson = gson
}
