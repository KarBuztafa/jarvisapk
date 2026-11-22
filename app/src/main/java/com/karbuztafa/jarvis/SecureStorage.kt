package com.karbuztafa.jarvis

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "jarvis_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(key: String) {
        prefs.edit().putString("GEMINI_API_KEY", key.trim()).apply()
    }

    fun getApiKey(): String? = prefs.getString("GEMINI_API_KEY", null)

    fun saveProxyUrl(url: String) {
        prefs.edit().putString("PROXY_URL", url.trim()).apply()
    }

    fun getProxyUrl(): String? = prefs.getString("PROXY_URL", null)

    fun setSleepMode(isSleeping: Boolean) {
        prefs.edit().putBoolean("SLEEP_MODE", isSleeping).apply()
    }

    fun isSleepMode(): Boolean = prefs.getBoolean("SLEEP_MODE", false)
}