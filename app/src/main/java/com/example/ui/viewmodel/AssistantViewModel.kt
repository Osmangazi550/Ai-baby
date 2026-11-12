package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.actions.PhoneActionController
import com.example.data.model.AssistantState
import com.example.data.model.ChatMessage
import com.example.data.model.VoiceLanguage
import com.example.data.repository.AiRepository
import com.example.voice.VoiceController
import com.example.voice.VoiceEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AssistantViewModel"

class AssistantViewModel(application: Application) : AndroidViewModel(application), VoiceEventListener {

    private val aiRepository = AiRepository()
    private val phoneActionController = PhoneActionController(application)

    private val _assistantState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "Hello Babu! I am AI BABY, your personal AI voice assistant. Speak or type in English or বাংলা, and I will assist you!",
                isUser = false,
                language = VoiceLanguage.ENGLISH
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(VoiceLanguage.AUTO)
    val selectedLanguage: StateFlow<VoiceLanguage> = _selectedLanguage.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _liveTranscription = MutableStateFlow("")
    val liveTranscription: StateFlow<String> = _liveTranscription.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val voiceController by lazy {
        VoiceController(application, this)
    }

    private var speechPulseJob: Job? = null
    private var lastUserQuery: String? = null

    fun setLanguage(lang: VoiceLanguage) {
        _selectedLanguage.value = lang
        // If currently listening, update listening with new language preference
        if (_assistantState.value is AssistantState.Listening) {
            voiceController.startListening(lang)
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean {
        return voiceController.isRecognitionAvailable()
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun toggleListening() {
        if (_assistantState.value is AssistantState.Listening) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        Log.d(TAG, "startListening invoked. Permission: ${hasRecordAudioPermission()}, Available: ${isSpeechRecognitionAvailable()}")
        if (!hasRecordAudioPermission()) {
            _assistantState.value = AssistantState.Error(
                "Microphone permission is required for voice input. Please allow microphone access."
            )
            return
        }

        stopSpeaking()
        _liveTranscription.value = "Listening... Speak now to AI BABY"
        _assistantState.value = AssistantState.Listening(liveTranscription = "Listening... Speak now to AI BABY")
        voiceController.startListening(_selectedLanguage.value)
    }

    fun stopListening() {
        val currentSpeech = _liveTranscription.value.trim()
        voiceController.stopListening()
        _amplitude.value = 0f

        if (currentSpeech.isNotEmpty() && !currentSpeech.startsWith("Listening...")) {
            Log.d(TAG, "stopListening invoked with active transcription: \"$currentSpeech\", submitting query...")
            processUserQuery(currentSpeech)
            _liveTranscription.value = ""
        }
    }

    fun stopSpeaking() {
        speechPulseJob?.cancel()
        voiceController.stopSpeaking()
        _isSpeaking.value = false
        _amplitude.value = 0f
        if (_assistantState.value is AssistantState.Speaking) {
            _assistantState.value = AssistantState.Idle
        }
    }

    fun dismissError() {
        if (_assistantState.value is AssistantState.Error) {
            _assistantState.value = AssistantState.Idle
        }
    }

    fun replayMessage(message: ChatMessage) {
        stopSpeaking()
        _assistantState.value = AssistantState.Speaking(message.text)
        voiceController.speak(message.text)
        startSpeakingWavePulse()
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
        stopSpeaking()
        _assistantState.value = AssistantState.Idle
    }

    /**
     * Processes text input (from voice recognition or manual typed query).
     */
    fun processUserQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        stopListening()
        stopSpeaking()
        lastUserQuery = trimmed

        val detectedLang = VoiceLanguage.detectLanguage(trimmed)
        val isBengali = detectedLang == VoiceLanguage.BANGLA || _selectedLanguage.value == VoiceLanguage.BANGLA

        // 1. Add User Message immediately to conversation stream
        val userMsg = ChatMessage(
            text = trimmed,
            isUser = true,
            language = detectedLang
        )
        _chatMessages.value = _chatMessages.value + userMsg

        // 2. Check for modular phone action first (e.g., flashlight, battery, settings)
        val actionResult = phoneActionController.detectAndExecuteAction(trimmed, isBengali)
        if (actionResult.handled) {
            val aiMsg = ChatMessage(
                text = actionResult.spokenFeedback,
                isUser = false,
                language = detectedLang,
                executedActionTitle = actionResult.actionName
            )
            _chatMessages.value = _chatMessages.value + aiMsg
            _assistantState.value = AssistantState.Speaking(actionResult.spokenFeedback)
            voiceController.speak(actionResult.spokenFeedback)
            startSpeakingWavePulse()
            return
        }

        // 3. Consult Gemini 3.8 Flash AI
        _assistantState.value = AssistantState.Thinking
        viewModelScope.launch {
            try {
                val result = aiRepository.getResponse(trimmed, _chatMessages.value)
                result.onSuccess { responseText ->
                    val aiMsg = ChatMessage(
                        text = responseText,
                        isUser = false,
                        language = VoiceLanguage.detectLanguage(responseText)
                    )
                    _chatMessages.value = _chatMessages.value + aiMsg
                    _assistantState.value = AssistantState.Speaking(responseText)
                    voiceController.speak(responseText)
                    startSpeakingWavePulse()
                }.onFailure { error ->
                    val errorText = error.localizedMessage ?: "Failed to get response from AI BABY."
                    Log.e(TAG, "Gemini inference failed: $errorText", error)
                    _assistantState.value = AssistantState.Error(errorText)
                    voiceController.speak(errorText)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unhandled error during query processing", e)
                _assistantState.value = AssistantState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun retryLastQuery() {
        val query = lastUserQuery
        if (!query.isNullOrBlank()) {
            processUserQuery(query)
        }
    }

    private fun startSpeakingWavePulse() {
        speechPulseJob?.cancel()
        speechPulseJob = viewModelScope.launch {
            // Emulate dynamic vocal wave pulses for the visualizer while AI is speaking
            var phase = 0f
            while (_isSpeaking.value || _assistantState.value is AssistantState.Speaking) {
                phase += 0.35f
                val base = kotlin.math.sin(phase.toDouble()).toFloat()
                val pulse = (base * 0.45f + 0.55f).coerceIn(0.2f, 0.95f)
                _amplitude.value = pulse
                delay(80)
            }
            _amplitude.value = 0f
        }
    }

    // VoiceEventListener callbacks
    override fun onSpeechStart() {
        _assistantState.value = AssistantState.Listening(liveTranscription = _liveTranscription.value)
    }

    override fun onSpeechPartialResult(partialText: String) {
        _liveTranscription.value = partialText
        _assistantState.value = AssistantState.Listening(partialText)
    }

    override fun onSpeechRecognized(text: String, detectedLang: VoiceLanguage) {
        Log.d(TAG, "onSpeechRecognized: \"$text\" with detected language: $detectedLang")
        _liveTranscription.value = text
        processUserQuery(text)
    }

    override fun onRmsAmplitudeChanged(normalizedRms: Float) {
        _amplitude.value = normalizedRms
    }

    override fun onSpeechEnd() {
        Log.d(TAG, "onSpeechEnd: speech input ended, processing utterance...")
        if (_liveTranscription.value.isNotBlank()) {
            _assistantState.value = AssistantState.Listening(_liveTranscription.value)
        }
    }

    override fun onListeningWaitingForSpeech() {
        // Keep microphone active and waiting for speech without showing an error
        _assistantState.value = AssistantState.Listening(liveTranscription = "")
    }

    override fun onSpeechError(errorMessage: String, errorCode: Int) {
        _amplitude.value = 0f
        _assistantState.value = AssistantState.Error(errorMessage)
    }

    override fun onAudioRecorded(wavBytes: ByteArray) {
        Log.d(TAG, "onAudioRecorded received ${wavBytes.size} bytes of WAV audio")
        _assistantState.value = AssistantState.Thinking
        viewModelScope.launch {
            try {
                val result = aiRepository.processAudioInput(wavBytes)
                result.onSuccess { (transcribedText, aiReply) ->
                    Log.d(TAG, "Audio successfully processed: transcript=\"$transcribedText\", reply=\"$aiReply\"")

                    if (transcribedText.isBlank() || transcribedText.equals("[NO_SPEECH]", ignoreCase = true)) {
                        // User was silent or no words were recognized
                        val spokenPrompt = if (aiReply.isNotBlank()) aiReply else "Babu, I am listening! Please speak to me."
                        _assistantState.value = AssistantState.Speaking(spokenPrompt)
                        voiceController.speak(spokenPrompt)
                        startSpeakingWavePulse()
                        return@launch
                    }

                    _liveTranscription.value = transcribedText

                    // 1. Add User Message immediately to conversation stream
                    val detectedLang = VoiceLanguage.detectLanguage(transcribedText)
                    val userMsg = ChatMessage(
                        text = transcribedText,
                        isUser = true,
                        language = detectedLang
                    )
                    _chatMessages.value = _chatMessages.value + userMsg

                    // 2. Check for voice command shortcut (Weather, Timer, Joke, Flashlight, Battery, Time)
                    val isBengali = detectedLang == VoiceLanguage.BANGLA || _selectedLanguage.value == VoiceLanguage.BANGLA
                    val actionResult = phoneActionController.detectAndExecuteAction(transcribedText, isBengali)
                    if (actionResult.handled) {
                        val aiMsg = ChatMessage(
                            text = actionResult.spokenFeedback,
                            isUser = false,
                            language = detectedLang,
                            executedActionTitle = actionResult.actionName
                        )
                        _chatMessages.value = _chatMessages.value + aiMsg
                        _assistantState.value = AssistantState.Speaking(actionResult.spokenFeedback)
                        voiceController.speak(actionResult.spokenFeedback)
                        startSpeakingWavePulse()
                        return@launch
                    }

                    // 3. Display AI response in the conversation stream
                    val finalReply = if (aiReply.isNotBlank()) {
                        aiReply
                    } else {
                        val genResult = aiRepository.getResponse(transcribedText, _chatMessages.value)
                        genResult.getOrDefault("Babu, I am here for you!")
                    }

                    val aiMsg = ChatMessage(
                        text = finalReply,
                        isUser = false,
                        language = VoiceLanguage.detectLanguage(finalReply)
                    )
                    _chatMessages.value = _chatMessages.value + aiMsg
                    _assistantState.value = AssistantState.Speaking(finalReply)
                    voiceController.speak(finalReply)
                    startSpeakingWavePulse()
                }.onFailure { error ->
                    Log.e(TAG, "Audio processing failed: ${error.message}", error)
                    val errorMsg = error.localizedMessage ?: "Voice processing error"
                    _assistantState.value = AssistantState.Error(errorMsg)
                    voiceController.speak("Babu, I had difficulty processing your audio. Please speak again.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in onAudioRecorded", e)
                _assistantState.value = AssistantState.Error(e.localizedMessage ?: "Voice error")
            }
        }
    }

    override fun onSpeakingStateChanged(isSpeaking: Boolean) {
        _isSpeaking.value = isSpeaking
        if (!isSpeaking && _assistantState.value is AssistantState.Speaking) {
            _assistantState.value = AssistantState.Idle
            speechPulseJob?.cancel()
            _amplitude.value = 0f
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechPulseJob?.cancel()
        voiceController.destroy()
    }
}
