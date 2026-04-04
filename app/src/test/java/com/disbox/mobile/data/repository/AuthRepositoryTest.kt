package com.disbox.mobile.data.repository

import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    private val authRepository = AuthRepository(mockk(), mockk())

    @Test
    fun testWebhookRegex() {
        val validUrl = "https://discord.com/api/webhooks/1234567890/abc_def-GHI"
        val canaryUrl = "https://canary.discord.com/api/webhooks/1234567890/abc_def"
        val ptbUrl = "https://ptb.discord.com/api/webhooks/1234567890/abc_def"
        val invalidUrl = "https://example.com/api/webhooks/123/abc"
        val invalidFormat = "https://discord.com/api/webhooks/abc/123"

        assertTrue(authRepository.validateWebhook(validUrl))
        assertTrue(authRepository.validateWebhook(canaryUrl))
        assertTrue(authRepository.validateWebhook(ptbUrl))
        assertFalse(authRepository.validateWebhook(invalidUrl))
        assertFalse(authRepository.validateWebhook(invalidFormat))
    }
}
