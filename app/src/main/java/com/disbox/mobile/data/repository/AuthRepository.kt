package com.disbox.mobile.data.repository

import com.disbox.mobile.utils.CryptoUtils
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val client: HttpClient,
    private val sessionManager: SessionManager
) {
    private val webhookRegex = Regex("^https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+$")

    fun validateWebhook(url: String): Boolean {
        return webhookRegex.matches(url)
    }

    suspend fun loginManual(webhookUrl: String): Result<Unit> {
        if (!validateWebhook(webhookUrl)) {
            return Result.failure(Exception("Invalid Webhook URL"))
        }
        
        // Test the webhook
        try {
            val response = client.get(webhookUrl)
            if (response.status == HttpStatusCode.OK) {
                sessionManager.setManualAuth(webhookUrl)
                return Result.success(Unit)
            } else {
                return Result.failure(Exception("Webhook validation failed: ${response.status}"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // Supabase Auth implementation will be here
    // For now, let's implement the skeleton as requested in Phase 1
    suspend fun loginCloud(email: String, password: String): Result<Unit> {
        // Implement Supabase auth logic
        // 1. Hash password with SHA-256
        val hashedPassword = CryptoUtils.sha256Hex(password)
        
        // 2. Call Supabase API
        // This is a placeholder for actual Supabase SDK or Ktor calls
        return try {
            // Mock success for now, or implement actual Ktor call to Supabase
            // sessionManager.setCloudAuth("mock_token", email)
            Result.failure(Exception("Supabase Auth not yet implemented"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
