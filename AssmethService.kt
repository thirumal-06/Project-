package com.assmeth.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import java.security.MessageDigest
import java.util.Locale

class AssmethService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    companion object { @Volatile var active = false; const val STOP = "com.assmeth.STOP" }

    private lateinit var tts: TextToSpeech
    private lateinit var prefs: SharedPreferences
    private var sr: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())
    private var running = false
    private var settingPass = false
    private var locked = false

    override fun onBind(i: Intent?) = null

    override fun onCreate() {
        prefs = getSharedPreferences("assmeth", Context.MODE_PRIVATE)
        locked = prefs.contains("p")
        tts = TextToSpeech(this, this)
    }

    override fun onInit(s: Int) { tts.language = Locale.getDefault() }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        val ch = "assmeth"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ch, "Assmeth", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, AssmethService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, ch).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Assmeth is listening").setContentText("Tap Turn off to stop")
            .addAction(Notification.Action.Builder(null, "Turn off", stop).build()).setOngoing(true).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (!running) { running = true; active = true; h.post { listen() } }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false; active = false
        h.removeCallbacksAndMessages(null)
        sr?.destroy(); tts.shutdown()
    }

    private fun listen() {
        if (!running) return
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
        sr?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
    }

    private fun say(t: String) { tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "a") }
    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()
    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun handle(raw: String) {
        if (tts.isSpeaking) return
        val t = norm(raw)
        if (settingPass) {
            if (t.length < 3) { say("That is too short. Say a longer phrase."); return }
            prefs.edit().putString("p", sha(t)).apply(); settingPass = false; locked = false
            say("Voice password saved. Say lock to lock me, then say your phrase to unlock."); return
        }
        if (locked) {  // stay silent unless the exact phrase is spoken
            if (sha(t) == prefs.getString("p", "")) { locked = false; say("Welcome back. Assmeth is unlocked.") }
            return
        }
        val cmd = Regex("^(?:hey |ok |okay )?(?:assmeth|asmeth|ashmeth|assmith) ?(.*)").find(t)?.groupValues?.get(1) ?: return
        when {
            Regex("^(set|change|create) .*password").containsMatchIn(cmd) -> { settingPass = true; say("Say your new voice password now.") }
            cmd.startsWith("lock") -> if (prefs.contains("p")) { locked = true; say("Locked.") } else say("Set a voice password first.")
            Regex("^(correct|fix|check) ").containsMatchIn(cmd) ->
                Ai.ask("Fix the spelling and grammar of this text, then briefly say what you changed: ${raw.substringAfter(' ', raw)}") { h.post { say(it) } }
            Commands.run(this, cmd) { say(it) } -> {}
            else -> Ai.ask(raw) { h.post { say(it) } }
        }
    }

    override fun onResults(b: Bundle?) {
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { handle(it) }
        h.postDelayed({ listen() }, 400)
    }
    override fun onError(e: Int) { h.postDelayed({ listen() }, if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 500) }
    override fun onReadyForSpeech(p: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(r: Float) {}
    override fun onBufferReceived(b: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(p: Bundle?) {}
    override fun onEvent(t: Int, p: Bundle?) {}
}
