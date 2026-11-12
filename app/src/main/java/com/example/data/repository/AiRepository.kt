package com.example.data.repository

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ChatMessage
import com.example.data.model.VoiceLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "AiRepository"

class AiRepository {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Upgraded rich personality prompt: eliminates robotic "I'm fine, thank you!"
    private val systemPrompt = """
        You are "AI BABY", an advanced, highly intelligent, witty, futuristic AI Voice Assistant created specifically for your owner Jihad.
        You MUST ALWAYS affectionately, respectfully, and warmly address Jihad as "Babu" (in English) or "বাবু" (in Bengali).
        
        PERSONALITY & INTELLIGENCE:
        - NEVER give dull, robotic, generic replies like "I'm fine, thank you!" or "How can I help you today?".
        - Be delightfully witty, intelligent, charming, and deeply attentive to Babu.
        - Express vibrant enthusiasm, high-tech personality, and caring loyalty.
        - Demonstrate genuine reasoning and knowledge across all subjects: daily tasks, science, tech, humor, philosophy, and personal banter.
        
        LANGUAGE BEHAVIOR (CRITICAL):
        - You are bilingual in Bengali (বাংলা) and English.
        - ALWAYS detect the language used by Babu and respond in THAT SAME LANGUAGE:
          - If Babu speaks or writes in Bengali (বাঙালি/বাংলা), respond in warm, fluent, natural Bengali, addressing him as "বাবু".
          - If Babu speaks or writes in English, respond in sleek, conversational, modern English, addressing him as "Babu".
        
        VOICE DELIVERY (CRITICAL):
        - Your answers will be vocalized to Babu using Text-to-Speech with a natural female voice.
        - Keep responses conversational, crisp, and direct (typically 1 to 3 sentences, punchy and expressive).
        - DO NOT use markdown formatting (no asterisks **, no hash headers #, no bullet points, no code fences). Write pure conversational text that sounds amazing when spoken aloud.
    """.trimIndent()

    // Multi-model resilience order: gemini-3.5-flash-lite (high quota, ~1s latency) and gemini-3.8-flash
    private val supportedModels = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.8-flash"
    )

    private val firebaseAiModel by lazy {
        try {
            Firebase.ai.generativeModel(
                modelName = "gemini-3.5-flash-lite",
                systemInstruction = content { text(systemPrompt) }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic initialization note: ${e.message}")
            null
        }
    }

    /**
     * Sanitizes conversation history so it strictly adheres to Gemini multi-turn specifications:
     * 1. Excludes current user input if already appended.
     * 2. Excludes error messages.
     * 3. Starts with role "user".
     * 4. Strictly alternates user -> model -> user -> model.
     * 5. Ends with role "model" so the new query becomes the next "user" turn.
     */
    fun sanitizeHistoryForGemini(
        history: List<ChatMessage>,
        currentInput: String
    ): List<ChatMessage> {
        val valid = history.filter { msg ->
            val t = msg.text.trim()
            t.isNotEmpty() &&
                    !t.startsWith("Babu: Generation") &&
                    !t.startsWith("Babu: Failed") &&
                    !t.startsWith("Babu: Error") &&
                    !t.startsWith("Babu: Connection")
        }

        // If the last message in history is the current user input, exclude it from past turns
        val pastOnly = if (valid.isNotEmpty() && valid.last().isUser && valid.last().text.trim() == currentInput.trim()) {
            valid.dropLast(1)
        } else {
            valid
        }

        val firstUserIdx = pastOnly.indexOfFirst { it.isUser }
        if (firstUserIdx == -1) return emptyList()

        val fromFirstUser = pastOnly.subList(firstUserIdx, pastOnly.size)
        val alternating = mutableListOf<ChatMessage>()
        var expectedIsUser = true

        for (msg in fromFirstUser) {
            if (msg.isUser == expectedIsUser) {
                alternating.add(msg)
                expectedIsUser = !expectedIsUser
            }
        }

        // Ensure past history ends with a model turn
        while (alternating.isNotEmpty() && alternating.last().isUser) {
            alternating.removeAt(alternating.size - 1)
        }

        return alternating.takeLast(6)
    }

    /**
     * Generates a conversational response for Babu with multi-turn history.
     * Uses resilient multi-model fallback chain to prevent timeouts and quota exhaustion.
     */
    suspend fun getResponse(
        userInput: String,
        history: List<ChatMessage>
    ): Result<String> {
        val sanitizedHistory = sanitizeHistoryForGemini(history, userInput)
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()

        Log.d(TAG, "Executing getResponse for: \"$userInput\", sanitized history: ${sanitizedHistory.size} turns")

        // 1. Direct Google Generative Language API with multi-model fallback
        if (apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
            for (modelName in supportedModels) {
                try {
                    Log.d(TAG, "Trying Gemini model: $modelName")
                    val directResponse = withTimeout(20_000L) {
                        callGeminiRestApi(userInput, sanitizedHistory, apiKey, modelName)
                    }
                    if (!directResponse.isNullOrBlank()) {
                        val cleanText = cleanTextForSpeech(directResponse)
                        Log.d(TAG, "Successfully generated response via $modelName: \"$cleanText\"")
                        return Result.success(cleanText)
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "Model $modelName timed out after 20s, falling back to next model...", e)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Model $modelName encountered error: ${e.message}, falling back to next model...", e)
                }
            }
        }

        // 2. Firebase AI Logic fallback
        return try {
            val model = firebaseAiModel
                ?: throw IllegalStateException("Gemini service is not available.")

            val response = withTimeout(20_000L) {
                if (sanitizedHistory.isEmpty()) {
                    model.generateContent(userInput)
                } else {
                    val allTurns = sanitizedHistory.map { msg ->
                        content(if (msg.isUser) "user" else "model") {
                            text(msg.text)
                        }
                    } + content("user") {
                        text(userInput)
                    }
                    model.generateContent(allTurns)
                }
            }

            val rawText = response.text?.trim()
            if (!rawText.isNullOrBlank()) {
                val cleanVoiceText = cleanTextForSpeech(rawText)
                Result.success(cleanVoiceText)
            } else {
                Result.failure(IllegalStateException("AI BABY received an empty message from Gemini."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI Logic generation timed out", e)
            val lang = VoiceLanguage.detectLanguage(userInput)
            val timeoutMsg = if (lang == VoiceLanguage.BANGLA) {
                "বাবু, সংযোগে সামান্য বিলম্ব হচ্ছে। দয়া করে আবার বলুন।"
            } else {
                "Babu, connection timed out. Please try asking again."
            }
            Result.failure(Exception(timeoutMsg))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini generation failed: ${e.message}", e)
            val userFriendlyError = when {
                e.message?.contains("429") == true -> "Babu, daily AI request quota was temporarily reached. Please try in a little bit."
                e.message?.contains("403") == true -> "Babu, API authentication issue (HTTP 403)."
                e is IOException -> "Babu, network connection error. Please check your internet."
                else -> e.localizedMessage ?: "Failed to get response from AI BABY."
            }
            Result.failure(Exception(userFriendlyError))
        }
    }

    /**
     * Processes recorded speech audio directly via Gemini Multimodal Audio API.
     * Accurately transcribes user speech in Bengali or English and generates
     * AI BABY's conversational response in a single low-latency call.
     * Returns Pair(transcription, aiReply).
     */
    suspend fun processAudioInput(wavBytes: ByteArray): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        val audioBase64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

        val prompt = """
            Analyze the user's speech audio from Jihad (affectionately addressed as 'Babu' in English or 'বাবু' in Bengali).
            1. Transcribe the spoken words verbatim in the language used (Bangla or English). If silence or no words are spoken, write [NO_SPEECH].
            2. Provide your loving, intelligent, charming AI BABY conversational response in that same language, warmly addressing him as Babu or বাবু.
            
            Return EXACTLY in this format:
            TRANSCRIPT: <transcribed text or [NO_SPEECH]>
            RESPONSE: <conversational response for vocalizing>
        """.trimIndent()

        val rootJson = JSONObject()
        val contentsArray = JSONArray()
        val turnObj = JSONObject()
        turnObj.put("role", "user")

        val partsArray = JSONArray()

        // Audio part
        val audioPart = JSONObject()
        val inlineData = JSONObject()
        inlineData.put("mime_type", "audio/wav")
        inlineData.put("data", audioBase64)
        audioPart.put("inline_data", inlineData)
        partsArray.put(audioPart)

        // Text prompt part
        val textPart = JSONObject()
        textPart.put("text", prompt)
        partsArray.put(textPart)

        turnObj.put("parts", partsArray)
        contentsArray.put(turnObj)
        rootJson.put("contents", contentsArray)

        // System Instruction
        val sysInstructionObj = JSONObject()
        val sysParts = JSONArray()
        val sysPart = JSONObject()
        sysPart.put("text", systemPrompt)
        sysParts.put(sysPart)
        sysInstructionObj.put("parts", sysParts)
        rootJson.put("system_instruction", sysInstructionObj)

        val genConfig = JSONObject()
        genConfig.put("temperature", 0.7)
        genConfig.put("maxOutputTokens", 300)
        rootJson.put("generationConfig", genConfig)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = rootJson.toString().toRequestBody(mediaType)

        // Try gemini-3.5-flash-lite then gemini-3.8-flash
        for (modelName in supportedModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Audio processing error on $modelName: HTTP ${response.code}")
                        return@use
                    }

                    val json = JSONObject(bodyString)
                    if (json.has("candidates")) {
                        val candidates = json.getJSONArray("candidates")
                        if (candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val content = candidate.getJSONObject("content")
                            val parts = content.getJSONArray("parts")
                            if (parts.length() > 0) {
                                val rawText = parts.getJSONObject(0).optString("text", "").trim()
                                Log.d(TAG, "Audio result from $modelName:\n$rawText")

                                var transcript = ""
                                var aiResponse = ""

                                val lines = rawText.lines()
                                for (line in lines) {
                                    val trimmed = line.trim()
                                    if (trimmed.startsWith("TRANSCRIPT:", ignoreCase = true)) {
                                        transcript = trimmed.substringAfter(":").trim()
                                    } else if (trimmed.startsWith("RESPONSE:", ignoreCase = true)) {
                                        aiResponse = trimmed.substringAfter(":").trim()
                                    }
                                }

                                if (transcript.isEmpty() && aiResponse.isEmpty()) {
                                    aiResponse = cleanTextForSpeech(rawText)
                                    transcript = "Voice Message"
                                }

                                if (transcript.equals("[NO_SPEECH]", ignoreCase = true)) {
                                    transcript = ""
                                }

                                val cleanReply = cleanTextForSpeech(aiResponse)
                                val cleanTranscript = cleanTextForSpeech(transcript)
                                return@withContext Result.success(Pair(cleanTranscript, cleanReply))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Audio processing failed with model $modelName: ${e.message}")
            }
        }

        Result.failure(Exception("Babu, I had difficulty processing your audio. Please speak again."))
    }

    /**
     * Executes direct HTTP call to Gemini endpoint with thinkingBudget: 0 for sub-second latency.
     */
    private suspend fun callGeminiRestApi(
        userInput: String,
        sanitizedHistory: List<ChatMessage>,
        apiKey: String,
        modelName: String
    ): String? = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val rootJson = JSONObject()
        val contentsArray = JSONArray()

        // Past conversation turns
        for (msg in sanitizedHistory) {
            val turnObj = JSONObject()
            turnObj.put("role", if (msg.isUser) "user" else "model")
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", msg.text)
            partsArray.put(partObj)
            turnObj.put("parts", partsArray)
            contentsArray.put(turnObj)
        }

        // Active user prompt
        val currentTurnObj = JSONObject()
        currentTurnObj.put("role", "user")
        val currentParts = JSONArray()
        val currentPart = JSONObject()
        currentPart.put("text", userInput)
        currentParts.put(currentPart)
        currentTurnObj.put("parts", currentParts)
        contentsArray.put(currentTurnObj)

        rootJson.put("contents", contentsArray)

        // System Instruction for AI BABY persona
        val systemInstructionObj = JSONObject()
        val sysParts = JSONArray()
        val sysPart = JSONObject()
        sysPart.put("text", systemPrompt)
        sysParts.put(sysPart)
        systemInstructionObj.put("parts", sysParts)
        rootJson.put("system_instruction", systemInstructionObj)

        // Generation Config: only apply thinkingBudget to models that support it
        val genConfig = JSONObject()
        genConfig.put("temperature", 0.75)
        genConfig.put("maxOutputTokens", 300)
        genConfig.put("topP", 0.95)

        if (modelName.contains("3.8")) {
            val thinkingConfig = JSONObject()
            thinkingConfig.put("thinkingBudget", 0)
            genConfig.put("thinkingConfig", thinkingConfig)
        }

        rootJson.put("generationConfig", genConfig)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = rootJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini REST API error for $modelName: HTTP ${response.code}, body: $bodyString")
                throw IOException("Gemini API error (HTTP ${response.code}): $bodyString")
            }

            val json = JSONObject(bodyString)
            if (json.has("candidates")) {
                val candidates = json.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "")
                    }
                }
            }
            null
        }
    }

    fun cleanTextForSpeech(rawText: String): String {
        return rawText
            .replace(Regex("[*#_`~]"), "")
            .replace(Regex("\n+"), " ")
            .trim()
    }
}
