package com.karbuztafa.jarvis

import android.app.*
import android.content.Intent
import android.os.*
import android.speech.*
import android.speech.tts.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Locale

class JarvisService : Service(), TextToSpeech.OnInitListener {

    private lateinit var storage: SecureStorage
    private lateinit var geminiClient: GeminiClient
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isAwakeForCommand = false
    private var isSpeaking = false

    companion object {
        const val CHANNEL_ID = "JarvisForegroundChannel"
        const val ACTION_UPDATE_STATE = "com.karbuztafa.jarvis.UPDATE_STATE"
    }

    override fun onCreate() {
        super.onCreate()
        storage = SecureStorage(this)
        geminiClient = GeminiClient(storage)
        createNotificationChannel()
        startForeground(1, buildNotification("Jarvis hazır. Hey Jarvis deyin"))

        tts = TextToSpeech(this, this)
        initSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE_STATE) {
            if (storage.isSleepMode()) {
                stopListening()
                updateNotification("Jarvis Uyku Modunda")
            } else {
                updateNotification("Jarvis aktif. Hey Jarvis bekleniyor")
                scheduleNextListen(300)
            }
        }
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("tr", "TR")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { isSpeaking = true }
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    isAwakeForCommand = false
                    if (!storage.isSleepMode()) scheduleNextListen(400)
                }
                @Deprecated("Deprecated")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    isAwakeForCommand = false
                    scheduleNextListen(500)
                }
            })
            if (!storage.isSleepMode()) scheduleNextListen(500)
        }
    }

    private fun initSpeechRecognizer() {
        mainHandler.post {
            speechRecognizer?.destroy()
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) {
                            if (!storage.isSleepMode() && !isSpeaking) scheduleNextListen(1000)
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            handleSpokenInput(matches?.firstOrNull() ?: "")
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            }
        }
    }

    private fun handleSpokenInput(text: String) {
        if (storage.isSleepMode() || text.isBlank()) {
            scheduleNextListen(600)
            return
        }

        val lower = text.lowercase(Locale("tr", "TR"))
        if (!isAwakeForCommand) {
            if (lower.contains("jarvis") || lower.contains("carvis")) {
                val cmd = lower.replace("hey jarvis", "").replace("jarvis", "").replace("carvis", "").trim()
                if (cmd.length > 2) processCommandWithGemini(cmd)
                else {
                    isAwakeForCommand = true
                    speak("Dinliyorum.")
                }
            } else {
                scheduleNextListen(400)
            }
        } else {
            processCommandWithGemini(text)
        }
    }

    private fun processCommandWithGemini(command: String) {
        if (storage.isSleepMode()) return
        serviceScope.launch {
            val reply = geminiClient.askJarvis(command)
            if (reply == "UYKU_MODU_AKTIF") return@launch

            var clean = reply
            if (reply.startsWith("[AC:")) {
                val idx = reply.indexOf("]")
                if (idx > 4) {
                    val app = reply.substring(4, idx)
                    clean = reply.substring(idx + 1).trim()
                    if (!AppLauncher.openAppByName(this@JarvisService, app)) {
                        clean = "$app bulunamadı."
                    }
                }
            }
            speak(clean)
        }
    }

    private fun speak(text: String) {
        stopListening()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTT")
    }

    private fun scheduleNextListen(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (!storage.isSleepMode() && !isSpeaking) startListening()
        }, delayMs)
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !isAwakeForCommand)
        }
        try { speechRecognizer?.startListening(intent) } catch (_: Exception) { initSpeechRecognizer() }
    }

    private fun stopListening() {
        mainHandler.post { try { speechRecognizer?.stopListening() } catch (_: Exception) {} }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Jarvis Arka Plan", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis Asistan")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification(text))
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}