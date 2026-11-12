package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.StateError
import com.example.ui.theme.StateIdle
import com.example.ui.theme.StateListening
import com.example.ui.theme.StateSpeaking
import com.example.ui.theme.StateThinking

sealed interface AssistantState {
    val statusText: String
    val glowColor: Color

    data object Idle : AssistantState {
        override val statusText: String = "AI BABY Ready • Tap mic to speak"
        override val glowColor: Color = StateIdle
    }

    data class Listening(val liveTranscription: String = "") : AssistantState {
        override val statusText: String = "Listening to Babu..."
        override val glowColor: Color = StateListening
    }

    data object Thinking : AssistantState {
        override val statusText: String = "AI BABY is thinking..."
        override val glowColor: Color = StateThinking
    }

    data class Speaking(val spokenText: String) : AssistantState {
        override val statusText: String = "Speaking with Babu..."
        override val glowColor: Color = StateSpeaking
    }

    data class Error(val message: String) : AssistantState {
        override val statusText: String = message
        override val glowColor: Color = StateError
    }
}
