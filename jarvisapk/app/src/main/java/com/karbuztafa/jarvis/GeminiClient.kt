package com.karbuztafa.jarvis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiClient(private val storage: SecureStorage) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun askJarvis(userPrompt: String): String = withContext(Dispatchers.IO) {
        if (storage.isSleepMode()) return@withContext "UYKU_MODU_AKTIF"

        val apiKey = storage.getApiKey()
        val proxyUrl = storage.getProxyUrl()

        if (apiKey.isNullOrEmpty() && proxyUrl.isNullOrEmpty()) {
            return@withContext "Lütfen önce API anahtarını veya sunucu adresini kaydedin."
        }

        val endpoint = if (!proxyUrl.isNullOrEmpty()) proxyUrl else "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val systemInstruction = """
            Sen Jarvis adlı Türkçe yapay zeka asistanısın. Lenovo Tab M10 tablet üzerinde çalışıyorsun.
            Yanıtların kısa, net, doğal ve sesli okunmaya uygun Türkçe olmalı. Markdown sembolleri kullanma.
            Uygulama açma isteği geldiğinde yanıtın başına sadece şu etiketi koy: [AC:UygulamaAdi] ve kısa onay cümlesi ekle.
            Örnek: [AC:YouTube] YouTube açılıyor.
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
            }))
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (!proxyUrl.isNullOrEmpty() && !apiKey.isNullOrEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string() ?: return@withContext "Sunucudan boş yanıt döndü."
                if (!response.isSuccessful) return@withContext "Bağlantı hatası: ${response.code}"
                val json = JSONObject(bodyStr)
                json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text").trim()
            }
        } catch (e: Exception) {
            "İnternet bağlantısı sağlanamadı."
        }
    }
}