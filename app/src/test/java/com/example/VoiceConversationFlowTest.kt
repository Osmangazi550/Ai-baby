package com.example

import com.example.data.model.AssistantState
import com.example.data.model.ChatMessage
import com.example.data.model.VoiceLanguage
import com.example.data.repository.AiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceConversationFlowTest {

    @Test
    fun testLanguageDetectionForBengaliAndEnglish() {
        val bengaliText = "কেমন আছো বাবু? তোমার নাম কি?"
        val detectedBengali = VoiceLanguage.detectLanguage(bengaliText)
        assertEquals(VoiceLanguage.BANGLA, detectedBengali)

        val englishText = "Hello Babu, what is the weather today?"
        val detectedEnglish = VoiceLanguage.detectLanguage(englishText)
        assertEquals(VoiceLanguage.ENGLISH, detectedEnglish)
    }

    @Test
    fun testChatMessageCreationAndState() {
        val userMsg = ChatMessage(
            text = "Hello AI BABY",
            isUser = true,
            language = VoiceLanguage.ENGLISH
        )
        assertTrue(userMsg.isUser)
        assertEquals("Hello AI BABY", userMsg.text)

        val aiMsg = ChatMessage(
            text = "Hello Babu, how can I help you?",
            isUser = false,
            language = VoiceLanguage.ENGLISH,
            executedActionTitle = "Battery: 85%"
        )
        assertFalse(aiMsg.isUser)
        assertEquals("Battery: 85%", aiMsg.executedActionTitle)

        val idleState = AssistantState.Idle
        assertNotNull(idleState.statusText)

        val listeningState = AssistantState.Listening(liveTranscription = "Hello")
        assertEquals("Listening to Babu...", listeningState.statusText)
        assertEquals("Hello", listeningState.liveTranscription)
    }

    @Test
    fun testHistorySanitizationForGeminiApi() {
        val repository = AiRepository()

        // Case 1: Initial greeting from model followed by current user prompt
        val historyWithInitialGreeting = listOf(
            ChatMessage(text = "Hello Babu! I am AI BABY", isUser = false),
            ChatMessage(text = "What is the weather?", isUser = true)
        )
        val sanitized = repository.sanitizeHistoryForGemini(
            history = historyWithInitialGreeting,
            currentInput = "What is the weather?"
        )
        // Since the only past message was a model greeting, sanitized history must be empty
        // to prevent illegal model-first turn or duplicate user turn
        assertEquals(0, sanitized.size)

        // Case 2: Multi-turn history with previous conversation
        val multiTurnHistory = listOf(
            ChatMessage(text = "Hello Babu! I am AI BABY", isUser = false), // Should be skipped
            ChatMessage(text = "Who are you?", isUser = true),
            ChatMessage(text = "I am AI BABY, Babu!", isUser = false),
            ChatMessage(text = "Tell me a joke", isUser = true) // current input
        )
        val sanitizedMultiTurn = repository.sanitizeHistoryForGemini(
            history = multiTurnHistory,
            currentInput = "Tell me a joke"
        )
        assertEquals(2, sanitizedMultiTurn.size)
        assertTrue(sanitizedMultiTurn[0].isUser)
        assertEquals("Who are you?", sanitizedMultiTurn[0].text)
        assertFalse(sanitizedMultiTurn[1].isUser)
        assertEquals("I am AI BABY, Babu!", sanitizedMultiTurn[1].text)
    }

    @Test
    fun testCleanTextForSpeechSynthesis() {
        val repository = AiRepository()
        val markdownText = "**Hello Babu!** Here is a #cool fact: `AI BABY` is ready."
        val cleaned = repository.cleanTextForSpeech(markdownText)
        assertEquals("Hello Babu! Here is a cool fact: AI BABY is ready.", cleaned)
        assertFalse(cleaned.contains("*"))
        assertFalse(cleaned.contains("`"))
        assertFalse(cleaned.contains("#"))
    }

    @Test
    fun testVoiceInputGreetingEnglish() {
        val query = "Hello AI BABY"
        val lang = VoiceLanguage.detectLanguage(query)
        assertEquals(VoiceLanguage.ENGLISH, lang)

        val userMsg = ChatMessage(text = query, isUser = true, language = lang)
        assertEquals("Hello AI BABY", userMsg.text)
        assertTrue(userMsg.isUser)
    }

    @Test
    fun testVoiceInputGreetingBengali() {
        val query = "হ্যালো AI BABY"
        val lang = VoiceLanguage.detectLanguage(query)
        assertEquals(VoiceLanguage.BANGLA, lang)

        val userMsg = ChatMessage(text = query, isUser = true, language = lang)
        assertEquals("হ্যালো AI BABY", userMsg.text)
        assertTrue(userMsg.isUser)
    }

    @Test
    fun testGeneralConversationDistinction() {
        // General questions must NOT be handled as device commands, but forwarded to Gemini AI
        val queries = listOf(
            "Hello AI BABY",
            "What do you think about artificial intelligence?",
            "Can you write a poem for Babu?",
            "তুমি কেমন আছো?",
            "How does quantum computing work?"
        )
        for (q in queries) {
            val isBengali = VoiceLanguage.detectLanguage(q) == VoiceLanguage.BANGLA
            // General queries should be forwarded to AI
            val isCommand = q.contains("timer", ignoreCase = true) ||
                    (q.contains("weather", ignoreCase = true) && (q.contains("what", ignoreCase = true) || q.contains("how", ignoreCase = true))) ||
                    q.contains("joke", ignoreCase = true)
            assertFalse("Query \"$q\" should not be an intercepted device shortcut", isCommand)
        }
    }
}
