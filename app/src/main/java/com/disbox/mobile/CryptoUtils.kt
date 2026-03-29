package com.disbox.mobile

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val MAGIC_HEADER = "DBX_ENC:"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 16 // bytes (128 bits)

    fun normalizeUrl(url: String): String {
        var normalized = url.split("?")[0].trim().trimEnd('/')
        // Normalize domain names
        normalized = normalized.replace("://discordapp.com/", "://discord.com/")
        normalized = normalized.replace("://canary.discord.com/", "://discord.com/")
        normalized = normalized.replace("://ptb.discord.com/", "://discord.com/")
        return normalized
    }

    fun deriveKey(webhookUrl: String): ByteArray {
        // Normalize the URL to be consistent with Desktop and DisboxApi
        val normalized = normalizeUrl(webhookUrl)
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(normalized.toByteArray(Charsets.UTF_8))
    }

    fun encrypt(data: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_LENGTH * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)

        val encrypted = cipher.doFinal(data)

        val magicBytes = MAGIC_HEADER.toByteArray(Charsets.UTF_8)
        val result = ByteArray(magicBytes.size + iv.size + encrypted.size)
        System.arraycopy(magicBytes, 0, result, 0, magicBytes.size)
        System.arraycopy(iv, 0, result, magicBytes.size, iv.size)
        System.arraycopy(encrypted, 0, result, magicBytes.size + iv.size, encrypted.size)

        return result
    }

    fun decrypt(data: ByteArray, key: ByteArray): ByteArray {
        val magicBytes = MAGIC_HEADER.toByteArray(Charsets.UTF_8)
        if (data.size < magicBytes.size + IV_LENGTH + TAG_LENGTH) return data

        // Check magic header for compatibility
        for (i in magicBytes.indices) {
            if (data[i] != magicBytes[i]) {
                // If no magic header, return raw data (backward compatibility)
                return data
            }
        }

        try {
            val iv = data.sliceArray(magicBytes.size until magicBytes.size + IV_LENGTH)
            val ciphertext = data.sliceArray(magicBytes.size + IV_LENGTH until data.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)

            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            e.printStackTrace()
            return data
        }
    }
}
