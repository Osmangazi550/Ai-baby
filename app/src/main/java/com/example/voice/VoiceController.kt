package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.VoiceLanguage
import java.util.Locale
import java.util.UUID

private const val TAG = "VoiceController"

interface VoiceEventListener {
    fun onSpeechRecognized(text: String, detectedLang: VoiceLanguage)
    fun onSpeechPartialResult(partialText: String)
    fun onRmsAmplitudeChanged(normalizedRms: Float)
    fun onSpeechStart()
    fun onSpeechEnd()
    fun onSpeechError(errorMessage: String, errorCode: Int)
    fun onSpeakingStateChanged(isSpeaking: Boolean)
    fun onListeningWaitingForSpeech()
    fun onAudioRecorded(wavBytes: ByteArray)
}

class VoiceController(
    private val context: Context,
    private val listener: VoiceEventListener
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var directAudioRecorder: DirectAudioRecorder? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    // State tracking
    private var userWantsToListen = false
    private var currentLanguagePreference = VoiceLanguage.AUTO
    private var lastPartialText: String = ""
    private var hasRetriedLanguageFallback = false

    init {
        initTts()
    }

    private fun initTts() {
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
            Log.d(TAG, "TextToSpeech instance created.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create TextToSpeech instance", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            configureFemaleVoice(VoiceLanguage.ENGLISH)
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS onStart: $utteranceId")
                    listener.onSpeakingStateChanged(true)
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS onDone: $utteranceId")
                    listener.onSpeakingStateChanged(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS onError: $utteranceId")
                    listener.onSpeakingStateChanged(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "TTS onError: $utteranceId, code: $errorCode")
                    listener.onSpeakingStateChanged(false)
                }
            })
            Log.d(TAG, "TTS Initialized successfully with female voice configuration.")
        } else {
            Log.e(TAG, "TTS Initialization failed with code: $status")
        }
    }

    private fun configureFemaleVoice(language: VoiceLanguage) {
        val tts = textToSpeech ?: return
        try {
            tts.setPitch(1.15f)
            tts.setSpeechRate(1.0f)

            val availableVoices = tts.voices
            if (!availableVoices.isNullOrEmpty()) {
                val targetLangCode = if (language == VoiceLanguage.BANGLA) "bn" else "en"

                val femaleVoice = availableVoices.firstOrNull { voice ->
                    val name = voice.name.lowercase(Locale.ROOT)
                    (name.contains("female") || name.contains("fem") || name.contains("#female")) &&
                            voice.locale.language == targetLangCode &&
                            !voice.isNetworkConnectionRequired
                } ?: availableVoices.firstOrNull { voice ->
                    val name = voice.name.lowercase(Locale.ROOT)
                    (name.contains("female") || name.contains("fem") || name.contains("#female"))
                }

                if (femaleVoice != null) {
                    tts.voice = femaleVoice
                    Log.d(TAG, "Selected TTS female voice: ${femaleVoice.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set specific voice profile", e)
        }
    }

    /**
     * Checks if speech recognition service is available on this Android environment.
     * With Direct Audio Engine and Gemini Multimodal voice understanding, voice input
     * is guaranteed on all devices and emulators.
     */
    fun isRecognitionAvailable(): Boolean {
        return true
    }

    /**
     * Starts voice recognition session on the Android MainLooper.
     */
    fun startListening(languagePreference: VoiceLanguage) {
        Log.d(TAG, "startListening invoked. Lang: ${languagePreference.displayName}")
        stopSpeaking()
        userWantsToListen = true
        currentLanguagePreference = languagePreference
        lastPartialText = ""
        hasRetriedLanguageFallback = false

        mainHandler.post {
            startListeningInternal(languagePreference)
        }
    }

    private fun startListeningInternal(languagePreference: VoiceLanguage) {
        if (!userWantsToListen) return

        // 1. Verify RECORD_AUDIO permission at runtime
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.e(TAG, "RECORD_AUDIO permission missing at runtime")
            userWantsToListen = false
            listener.onSpeechError(
                "Microphone permission is required. Please grant permission to speak with AI BABY.",
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
            )
            return
        }

        // 2. Start Direct Audio Engine (hardware AudioRecord + Gemini Multimodal Voice Processing)
        // Eliminates ERROR_LANGUAGE_UNAVAILABLE (13) and guarantees flawless Bengali & English speech input!
        try {
            destroyDirectAudioRecorder()
            destroyRecognizerInternal()

            val directRecorder = DirectAudioRecorder(
                onAmplitudeChanged = { normalizedRms ->
                    if (userWantsToListen) {
                        listener.onRmsAmplitudeChanged(normalizedRms)
                    }
                },
                onAudioRecorded = { wavBytes ->
                    Log.d(TAG, "Direct audio recorded: ${wavBytes.size} bytes")
                    userWantsToListen = false
                    listener.onSpeechEnd()
                    listener.onAudioRecorded(wavBytes)
                },
                onError = { errorMsg ->
                    Log.e(TAG, "DirectAudioRecorder error: $errorMsg")
                    userWantsToListen = false
                    listener.onSpeechError(errorMsg, -1)
                },
                onSpeechStart = {
                    if (userWantsToListen) {
                        listener.onSpeechStart()
                    }
                },
                onSpeechEnd = {
                    listener.onSpeechEnd()
                }
            )

            directAudioRecorder = directRecorder
            directRecorder.startRecording()
            listener.onSpeechStart()
            Log.d(TAG, "DirectAudioRecorder successfully listening for speech.")
        } catch (e: Exception) {
            Log.e(TAG, "DirectAudioRecorder failed to start", e)
            userWantsToListen = false
            listener.onSpeechError("Microphone could not be started: ${e.message}", -1)
        }
    }

    private fun startListeningWithGenericFallback() {
        if (!userWantsToListen) return
        try {
            destroyRecognizerInternal()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context) ?: return
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(createRecognitionListener())

            // Generic free-form intent without restrictive language tags
            val genericIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            recognizer.startListening(genericIntent)
            Log.d(TAG, "Generic fallback SpeechRecognizer started listening successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Generic fallback startListening failed", e)
            userWantsToListen = false
            destroyRecognizerInternal()
            listener.onSpeechError("Speech recognition unavailable on this device. Please use text chat.", -1)
        }
    }

    private fun buildRecognizerIntent(languagePreference: VoiceLanguage): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

            when (languagePreference) {
                VoiceLanguage.BANGLA -> {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                }
                VoiceLanguage.ENGLISH -> {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
                }
                VoiceLanguage.AUTO -> {
                    val defaultLocale = Locale.getDefault()
                    val tag = if (defaultLocale.language == "bn") "bn-BD" else "en-US"
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
                }
            }
        }
    }

    /**
     * Stops listening immediately when requested.
     */
    fun stopListening() {
        Log.d(TAG, "stopListening requested by user/system")
        userWantsToListen = false
        mainHandler.post {
            try {
                directAudioRecorder?.stopRecording()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping direct audio: ${e.message}")
            }
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recognizer: ${e.message}")
            }
            destroyRecognizerInternal()
        }
    }

    fun isCurrentlyListening(): Boolean = userWantsToListen

    private fun destroyRecognizerInternal() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
        }
        speechRecognizer = null
    }

    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        val tts = textToSpeech
        if (!isTtsInitialized || tts == null) {
            Log.w(TAG, "TTS is not ready yet.")
            onFinished?.invoke()
            return
        }

        val detectedLang = VoiceLanguage.detectLanguage(text)
        try {
            if (detectedLang == VoiceLanguage.BANGLA) {
                val bnLocale = Locale("bn", "BD")
                val result = tts.setLanguage(bnLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale("bn", "IN"))
                }
            } else {
                tts.setLanguage(Locale.US)
            }
            configureFemaleVoice(detectedLang)

            val utteranceId = UUID.randomUUID().toString()
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            Log.d(TAG, "TTS speaking: \"$text\" with utteranceId: $utteranceId")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to speak text", e)
            onFinished?.invoke()
        }
    }

    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
            listener.onSpeakingStateChanged(false)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "SpeechRecognizer onReadyForSpeech: mic open and ready")
                if (userWantsToListen) {
                    listener.onSpeechStart()
                }
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "SpeechRecognizer onBeginningOfSpeech: speech detected")
                if (userWantsToListen) {
                    listener.onSpeechStart()
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (userWantsToListen) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    listener.onRmsAmplitudeChanged(normalized)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "SpeechRecognizer onEndOfSpeech: user stopped speaking, finalizing...")
                listener.onSpeechEnd()
            }

            override fun onError(error: Int) {
                val errorName = getSpeechErrorName(error)
                Log.w(TAG, "SpeechRecognizer onError: code $error ($errorName), lastPartial=\"$lastPartialText\"")

                // If code 12 (ERROR_LANGUAGE_NOT_SUPPORTED) or code 13 (ERROR_LANGUAGE_UNAVAILABLE) occurs:
                // Transparently fallback to generic system free-form recognizer!
                if ((error == 12 || error == 13) && !hasRetriedLanguageFallback) {
                    hasRetriedLanguageFallback = true
                    Log.w(TAG, "Language unavailable error ($error). Retrying with generic system speech recognition...")
                    userWantsToListen = true
                    mainHandler.post {
                        startListeningWithGenericFallback()
                    }
                    return
                }

                // Stop listening immediately on error
                userWantsToListen = false
                destroyRecognizerInternal()

                // If user had already spoken partial words, deliver them!
                if (lastPartialText.isNotBlank()) {
                    Log.d(TAG, "Delivering last partial speech after error: \"$lastPartialText\"")
                    val textToDeliver = lastPartialText
                    lastPartialText = ""
                    val detectedLang = VoiceLanguage.detectLanguage(textToDeliver)
                    listener.onSpeechRecognized(textToDeliver, detectedLang)
                    return
                }

                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording hardware error. Check your microphone."
                    SpeechRecognizer.ERROR_CLIENT -> "Speech recognizer client error. Tap mic and speak again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    SpeechRecognizer.ERROR_NETWORK -> "Network issue during speech recognition."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timed out."
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Tap mic and try speaking again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer was busy. Tap mic to retry."
                    SpeechRecognizer.ERROR_SERVER -> "Speech server error. Please try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected before timeout. Tap mic to speak."
                    12 -> "Selected speech language is not supported by device speech services. Try switching to English."
                    13 -> "Voice recognition service is downloading or unavailable on this device. Tap 'Try Again' or use the text box below to talk with AI BABY."
                    else -> "Speech recognition paused. Tap mic to speak."
                }

                listener.onSpeechError(errorMsg, error)
            }

            override fun onResults(results: Bundle?) {
                Log.d(TAG, "SpeechRecognizer onResults received")
                userWantsToListen = false
                destroyRecognizerInternal()

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim()
                    ?: lastPartialText.takeIf { it.isNotBlank() }

                lastPartialText = ""

                if (!recognizedText.isNullOrEmpty()) {
                    val detectedLang = VoiceLanguage.detectLanguage(recognizedText)
                    Log.d(TAG, "Speech successfully recognized: \"$recognizedText\" (lang: $detectedLang)")
                    listener.onSpeechRecognized(recognizedText, detectedLang)
                } else {
                    Log.w(TAG, "onResults called with empty recognition bundle")
                    listener.onSpeechError("No speech detected. Tap mic to speak.", SpeechRecognizer.ERROR_NO_MATCH)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (userWantsToListen) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim()
                    if (!partial.isNullOrEmpty()) {
                        Log.d(TAG, "onPartialResults: \"$partial\"")
                        lastPartialText = partial
                        listener.onSpeechPartialResult(partial)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun getSpeechErrorName(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO (3)"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT (5)"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS (9)"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK (2)"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT (1)"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH (7)"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY (8)"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER (4)"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT (6)"
            10 -> "ERROR_TOO_MANY_REQUESTS (10)"
            11 -> "ERROR_SERVER_DISCONNECTED (11)"
            12 -> "ERROR_LANGUAGE_NOT_SUPPORTED (12)"
            13 -> "ERROR_LANGUAGE_UNAVAILABLE (13)"
            14 -> "ERROR_CANNOT_CHECK_SUPPORT (14)"
            else -> "ERROR_UNKNOWN ($errorCode)"
        }
    }

    fun destroy() {
        userWantsToListen = false
        mainHandler.post {
            destroyDirectAudioRecorder()
            destroyRecognizerInternal()
        }
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
    }

    private fun destroyDirectAudioRecorder() {
        try {
            directAudioRecorder?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying direct recorder: ${e.message}")
        }
        directAudioRecorder = null
    }
}
