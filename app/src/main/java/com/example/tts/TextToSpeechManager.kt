package com.example.tts

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

object TextToSpeechManager {
    private const val TAG = "TextToSpeechManager"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSpeech: String? = null
    private var appContext: Context? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    private val _latestBroadcast = MutableStateFlow<String?>(null)
    val latestBroadcast = _latestBroadcast.asStateFlow()

    fun init(context: Context) {
        if (tts != null && isInitialized) return
        appContext = context.applicationContext

        mainHandler.post {
            try {
                tts = TextToSpeech(appContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        isInitialized = true
                        val locale = Locale.getDefault()
                        val result = tts?.setLanguage(locale)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts?.language = Locale.US
                        }

                        tts?.setPitch(1.05f)
                        tts?.setSpeechRate(0.95f)

                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _isSpeaking.value = true
                            }

                            override fun onDone(utteranceId: String?) {
                                _isSpeaking.value = false
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                _isSpeaking.value = false
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                _isSpeaking.value = false
                                Log.e(TAG, "TTS Error code: $errorCode")
                            }
                        })

                        Log.i(TAG, "TextToSpeech initialized successfully")
                        pendingSpeech?.let { text ->
                            val queued = text
                            pendingSpeech = null
                            speakInternal(queued)
                        }
                    } else {
                        Log.e(TAG, "Failed to initialize TextToSpeech: status $status")
                        isInitialized = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception initializing TextToSpeech", e)
            }
        }
    }

    fun speak(context: Context, text: String, raiseVolume: Boolean = true) {
        if (text.isBlank()) return
        appContext = context.applicationContext
        _latestBroadcast.value = text

        // Play an attention-grabbing alert chime first so the listener knows an announcement is starting
        playBroadcastChime(context)

        // Ensure volume is adequate so speech isn't silent
        if (raiseVolume) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (currentVol < (maxVol * 0.65f).toInt()) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.75f).toInt(), 0)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not adjust audio volume for broadcast", e)
            }
        }

        if (!isInitialized || tts == null) {
            pendingSpeech = text
            init(context)
        } else {
            // Slight delay (450ms) after chime before speech begins
            mainHandler.postDelayed({
                speakInternal(text)
            }, 450L)
        }
    }

    private fun speakInternal(text: String) {
        try {
            val utteranceId = UUID.randomUUID().toString()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val params = Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } else {
                val params = HashMap<String, String>().apply {
                    put(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC.toString())
                    put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                @Suppress("DEPRECATION")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
            }
            _isSpeaking.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text", e)
            _isSpeaking.value = false
        }
    }

    private fun playBroadcastChime(context: Context) {
        try {
            val chimeUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, chimeUri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Could not play notification chime", e)
        }
    }

    fun stop() {
        try {
            tts?.stop()
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            tts?.shutdown()
            tts = null
            isInitialized = false
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
}
