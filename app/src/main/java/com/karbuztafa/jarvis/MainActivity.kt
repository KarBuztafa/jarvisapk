package com.karbuztafa.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var storage: SecureStorage
    private lateinit var statusText: TextView
    private lateinit var sleepToggleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = SecureStorage(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Jarvis Kontrol Paneli"
            textSize = 22f
            setPadding(0, 0, 0, 32)
        }

        statusText = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, 24) }

        val apiKeyInput = EditText(this).apply {
            hint = "Gemini API Anahtarı"
            setText(storage.getApiKey() ?: "")
        }

        val proxyUrlInput = EditText(this).apply {
            hint = "Opsiyonel Sunucu URL"
            setText(storage.getProxyUrl() ?: "")
        }

        val saveButton = Button(this).apply {
            text = "Güvenli Kaydet ve Başlat"
            setOnClickListener {
                storage.saveApiKey(apiKeyInput.text.toString())
                storage.saveProxyUrl(proxyUrlInput.text.toString())
                Toast.makeText(this@MainActivity, "Kaydedildi.", Toast.LENGTH_SHORT).show()
                startJarvisService()
            }
        }

        sleepToggleButton = Button(this).apply {
            setOnClickListener {
                val newState = !storage.isSleepMode()
                storage.setSleepMode(newState)
                updateSleepUI()
                startService(Intent(this@MainActivity, JarvisService::class.java).apply {
                    action = JarvisService.ACTION_UPDATE_STATE
                })
            }
        }

        val batteryButton = Button(this).apply {
            text = "Kilitli Ekran/Pil İzni Ver"
            setOnClickListener { requestBatteryOptimizationExemption() }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(apiKeyInput)
        layout.addView(proxyUrlInput)
        layout.addView(saveButton)
        layout.addView(sleepToggleButton)
        layout.addView(batteryButton)

        setContentView(layout)
        updateSleepUI()
        checkAudioPermission()
    }

    private fun updateSleepUI() {
        if (storage.isSleepMode()) {
            statusText.text = "Durum: UYKU MODU (Gemini Tamamen Kapalı)"
            sleepToggleButton.text = "Uyku Modunu Kapat"
        } else {
            statusText.text = "Durum: AKTİF (Hey Jarvis Bekleniyor)"
            sleepToggleButton.text = "Uyku Modunu Aç"
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        } else if (!storage.getApiKey().isNullOrEmpty()) {
            startJarvisService()
        }
    }

    private fun startJarvisService() {
        val intent = Intent(this, JarvisService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } else {
            Toast.makeText(this, "İzin zaten verilmiş.", Toast.LENGTH_SHORT).show()
        }
    }
}