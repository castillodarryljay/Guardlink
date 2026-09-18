package com.example.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

object AudioStreamManager {
    private const val TAG = "AudioStreamManager"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val recordedBytesStream = ByteArrayOutputStream()
    private val recordingLock = Any()

    // Hardware Audio Effects
    private var aec: AcousticEchoCanceler? = null
    private var agc: AutomaticGainControl? = null
    private var ns: NoiseSuppressor? = null

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var lastPlayedSegmentBase64: String? = null

    // Thread-safe Playback Queue and Playback Thread to eliminate stuttering/jitter
    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
    private var playbackThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun startRecording(context: Context) {
        synchronized(recordingLock) {
            if (isRecording) return
            
            // Stop playback immediately to avoid acoustic feedback/echo loop
            stopPlayback()

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Cannot start recording: RECORD_AUDIO permission not granted")
                return
            }

            try {
                val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
                val bufferSize = if (minBufferSize > 2048) minBufferSize else 2048
                
                // VOICE_COMMUNICATION is specifically designed for 2-way VoIP/Intercom 
                // and triggers hardware-level Acoustic Echo Cancellation and Noise Suppression.
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    CHANNEL_IN,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "Failed to initialize AudioRecord")
                    audioRecord = null
                    return
                }

                // Explicitly enable hardware voice filters on the capture session if not automatically enabled
                try {
                    val sessionId = audioRecord?.audioSessionId ?: 0
                    if (sessionId != 0) {
                        if (AcousticEchoCanceler.isAvailable()) {
                            aec = AcousticEchoCanceler.create(sessionId)?.apply {
                                enabled = true
                            }
                            Log.d(TAG, "Hardware AcousticEchoCanceler successfully enabled on session: $sessionId")
                        }
                        if (AutomaticGainControl.isAvailable()) {
                            agc = AutomaticGainControl.create(sessionId)?.apply {
                                enabled = true
                            }
                            Log.d(TAG, "Hardware AutomaticGainControl successfully enabled on session: $sessionId")
                        }
                        if (NoiseSuppressor.isAvailable()) {
                            ns = NoiseSuppressor.create(sessionId)?.apply {
                                enabled = true
                            }
                            Log.d(TAG, "Hardware NoiseSuppressor successfully enabled on session: $sessionId")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing hardware audio enhancements", e)
                }

                audioRecord?.startRecording()
                isRecording = true
                recordedBytesStream.reset()

                thread(name = "AudioRecordThread") {
                    val buffer = ByteArray(1024)
                    while (isRecording) {
                        val record = audioRecord
                        if (record != null && isRecording) {
                            val readBytes = record.read(buffer, 0, buffer.size)
                            if (readBytes > 0) {
                                synchronized(recordingLock) {
                                    if (com.example.network.FirebaseManager.isCurrentAdminSpeaking) {
                                        recordedBytesStream.reset()
                                    } else {
                                        recordedBytesStream.write(buffer, 0, readBytes)
                                    }
                                }
                            }
                        } else {
                            break
                        }
                    }
                }
                Log.d(TAG, "Live voice recording started successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting live audio recording", e)
                stopRecording()
            }
        }
    }

    fun getLatestAudioSegmentBase64(): String {
        synchronized(recordingLock) {
            if (com.example.network.FirebaseManager.isCurrentAdminSpeaking) {
                recordedBytesStream.reset()
                return ""
            }
            if (recordedBytesStream.size() == 0) return ""
            val bytes = recordedBytesStream.toByteArray()
            recordedBytesStream.reset()
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    fun stopRecording() {
        synchronized(recordingLock) {
            isRecording = false
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: java.lang.IllegalStateException) {
                // Ignore illegal state if already stopped/released
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio recorder", e)
            } finally {
                audioRecord = null
                
                // Release hardware filters
                try {
                    aec?.release()
                    agc?.release()
                    ns?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing audio effects", e)
                } finally {
                    aec = null
                    agc = null
                    ns = null
                }

                recordedBytesStream.reset()
                Log.d(TAG, "Live audio recording stopped.")
            }
        }
    }

    fun startPlayback() {
        if (isRecording) {
            Log.d(TAG, "Skip startPlayback: recording is currently active to avoid feedback")
            return
        }
        if (isPlaying) return
        try {
            playbackQueue.clear()

            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
            val bufferSize = if (minBufferSize > 2048) minBufferSize else 2048

            // USAGE_MEDIA with CONTENT_TYPE_MUSIC guarantees loud speakerphone audio output
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT)
                .setEncoding(AUDIO_FORMAT)
                .build()

            audioTrack = AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioTrack")
                audioTrack = null
                return
            }

            audioTrack?.play()
            isPlaying = true

            // Dedicated non-blocking playback thread
            playbackThread = thread(name = "AudioPlaybackThread") {
                while (isPlaying) {
                    val data = playbackQueue.poll()
                    if (data != null && data.isNotEmpty()) {
                        try {
                            audioTrack?.write(data, 0, data.size)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error writing to AudioTrack", e)
                        }
                    } else {
                        try {
                            Thread.sleep(10)
                        } catch (e: InterruptedException) {
                            break
                        }
                    }
                }
            }
            Log.d(TAG, "Live voice playback started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting live audio playback", e)
            stopPlayback()
        }
    }

    fun playAudioSegment(base64Audio: String) {
        if (base64Audio.isEmpty()) return
        // Do not queue or play audio segments if local microphone is active to prevent echo and loopback
        if (isRecording) {
            return
        }
        // Skip playing duplicate segments to prevent server/network update loops and echo
        if (base64Audio == lastPlayedSegmentBase64) {
            return
        }
        lastPlayedSegmentBase64 = base64Audio

        if (!isPlaying || audioTrack == null) {
            startPlayback()
        }
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            if (audioBytes.isNotEmpty()) {
                playbackQueue.add(audioBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding audio segment to queue", e)
        }
    }

    fun stopPlayback() {
        isPlaying = false
        playbackThread?.interrupt()
        playbackThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: java.lang.IllegalStateException) {
            // Ignore illegal state
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio playback", e)
        } finally {
            audioTrack = null
            playbackQueue.clear()
            lastPlayedSegmentBase64 = null
            Log.d(TAG, "Live audio playback stopped.")
        }
    }
}
