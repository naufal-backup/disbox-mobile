package com.disbox.mobile.data.service

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class SessionCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("disbox_cookies", Context.MODE_PRIVATE)
    private val cookiesMap = mutableMapOf<String, MutableList<Cookie>>()

    init {
        // Load cookies from SharedPreferences on init
        prefs.all.forEach { (host, cookiesJson) ->
            if (cookiesJson is String) {
                try {
                    val cookiesList = cookiesJson.split("|").mapNotNull { 
                        Cookie.parse("https://$host".toHttpUrl(), it) 
                    }
                    cookiesMap[host] = cookiesList.toMutableList()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val existingCookies = cookiesMap[host] ?: mutableListOf()
        
        cookies.forEach { newCookie ->
            existingCookies.removeAll { it.name == newCookie.name }
            existingCookies.add(newCookie)
        }
        
        cookiesMap[host] = existingCookies
        
        // Persist to SharedPreferences
        val cookiesJson = existingCookies.joinToString("|") { it.toString() }
        prefs.edit().putString(host, cookiesJson).apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookiesMap[host] ?: return emptyList()
        
        // Filter out expired cookies
        val now = System.currentTimeMillis()
        val validCookies = cookies.filter { it.expiresAt > now }
        
        if (validCookies.size != cookies.size) {
            cookiesMap[host] = validCookies.toMutableList()
            val cookiesJson = validCookies.joinToString("|") { it.toString() }
            prefs.edit().putString(host, cookiesJson).apply()
        }
        
        return validCookies
    }

    fun clear() {
        cookiesMap.clear()
        prefs.edit().clear().apply()
    }
}
