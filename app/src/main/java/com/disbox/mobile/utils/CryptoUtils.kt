package com.disbox.mobile.utils

import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val TAG = "CryptoUtils"
    private const val MAGIC_HEADER = "DBX_ENC:"
    private const val IV_LENGTH_GCM = 12
    private const val IV_LENGTH_CBC = 16
    private const val TAG_LENGTH_GCM = 16 // bytes (128 bits)

    fun normalizeUrl(url: String): String {
        var normalized = url.split("?")[0].trim().trimEnd('/')
        // Normalize domain names
        normalized = normalized.replace("://discordapp.com/", "://discord.com/")
        normalized = normalized.replace("://canary.discord.com/", "://discord.com/")
        normalized = normalized.replace("://ptb.discord.com/", "://discord.com/")
        return normalized
    }

    fun sha256(text: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8))
    }

    fun sha256Hex(text: String): String {
        return sha256(text).joinToString("") { "%02x".format(it) }
    }

    fun deriveKey(webhookUrl: String): ByteArray {
        // Normalize the URL to be consistent with Desktop and DisboxApi
        val normalized = normalizeUrl(webhookUrl)
        return sha256(normalized)
    }

    // --- AES-GCM (Default for file chunks) ---

    fun encryptGCM(data: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH_GCM)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_LENGTH_GCM * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)

        val encrypted = cipher.doFinal(data)

        val magicBytes = MAGIC_HEADER.toByteArray(Charsets.UTF_8)
        val result = ByteArray(magicBytes.size + iv.size + encrypted.size)
        System.arraycopy(magicBytes, 0, result, 0, magicBytes.size)
        System.arraycopy(iv, 0, result, magicBytes.size, iv.size)
        System.arraycopy(encrypted, 0, result, magicBytes.size + iv.size, encrypted.size)

        return result
    }

    fun decryptGCM(data: ByteArray, key: ByteArray): ByteArray {
        val magicBytes = MAGIC_HEADER.toByteArray(Charsets.UTF_8)
        if (data.size < magicBytes.size + IV_LENGTH_GCM + TAG_LENGTH_GCM) return data

        // Check magic header for compatibility
        for (i in magicBytes.indices) {
            if (data[i] != magicBytes[i]) {
                // If no magic header, return raw data (backward compatibility)
                return data
            }
        }

        try {
            val iv = data.sliceArray(magicBytes.size until magicBytes.size + IV_LENGTH_GCM)
            val ciphertext = data.sliceArray(magicBytes.size + IV_LENGTH_GCM until data.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(TAG_LENGTH_GCM * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)

            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "GCM Decryption failed", e)
            return data
        }
    }

    // --- AES-CBC (Used for PIN or local data) ---

    fun encryptCBC(data: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH_CBC)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    fun decryptCBC(data: ByteArray, key: ByteArray): ByteArray {
        if (data.size < IV_LENGTH_CBC) return data
        try {
            val iv = data.sliceArray(0 until IV_LENGTH_CBC)
            val ciphertext = data.sliceArray(IV_LENGTH_CBC until data.size)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "CBC Decryption failed", e)
            return data
        }
    }
}
