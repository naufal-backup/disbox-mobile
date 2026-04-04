package com.disbox.mobile.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthType {
    NONE, MANUAL, CLOUD
}

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "disbox_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _authType = MutableStateFlow(getAuthTypeFromPrefs())
    val authType: StateFlow<AuthType> = _authType

    private fun getAuthTypeFromPrefs(): AuthType {
        val type = sharedPreferences.getString("auth_type", AuthType.NONE.name)
        return try {
            AuthType.valueOf(type ?: AuthType.NONE.name)
        } catch (e: Exception) {
            AuthType.NONE
        }
    }

    fun setManualAuth(webhookUrl: String) {
        sharedPreferences.edit().apply {
            putString("auth_type", AuthType.MANUAL.name)
            putString("webhook_url", webhookUrl)
            apply()
        }
        _authType.value = AuthType.MANUAL
    }

    fun setCloudAuth(token: String, email: String) {
        sharedPreferences.edit().apply {
            putString("auth_type", AuthType.CLOUD.name)
            putString("supabase_token", token)
            putString("user_email", email)
            apply()
        }
        _authType.value = AuthType.CLOUD
    }

    fun getWebhookUrl(): String? = sharedPreferences.getString("webhook_url", null)
    fun getSupabaseToken(): String? = sharedPreferences.getString("supabase_token", null)
    fun getUserEmail(): String? = sharedPreferences.getString("user_email", null)

    fun logout() {
        sharedPreferences.edit().clear().apply()
        _authType.value = AuthType.NONE
    }

    fun isLoggedIn(): Boolean = authType.value != AuthType.NONE
}
