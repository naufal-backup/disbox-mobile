package com.disbox.mobile.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CryptoUtilsTest {

    @Test
    fun testNormalizeUrl() {
        val url1 = "https://discord.com/api/webhooks/123/abc?param=1"
        val url2 = "https://canary.discord.com/api/webhooks/123/abc"
        val url3 = "https://ptb.discord.com/api/webhooks/123/abc/"
        
        assertEquals("https://discord.com/api/webhooks/123/abc", CryptoUtils.normalizeUrl(url1))
        assertEquals("https://discord.com/api/webhooks/123/abc", CryptoUtils.normalizeUrl(url2))
        assertEquals("https://discord.com/api/webhooks/123/abc", CryptoUtils.normalizeUrl(url3))
    }

    @Test
    fun testGCMEncryptionDecryption() {
        val data = "Hello World".toByteArray()
        val key = CryptoUtils.sha256("testkey")
        
        val encrypted = CryptoUtils.encryptGCM(data, key)
        val decrypted = CryptoUtils.decryptGCM(encrypted, key)
        
        assertArrayEquals(data, decrypted)
    }

    @Test
    fun testCBCEncryptionDecryption() {
        val data = "Hello World".toByteArray()
        val key = CryptoUtils.sha256("testkey")
        
        val encrypted = CryptoUtils.encryptCBC(data, key)
        val decrypted = CryptoUtils.decryptCBC(encrypted, key)
        
        assertArrayEquals(data, decrypted)
    }

    @Test
    fun testSha256Hex() {
        val text = "test"
        // echo -n "test" | sha256sum -> 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", CryptoUtils.sha256Hex(text))
    }
}
