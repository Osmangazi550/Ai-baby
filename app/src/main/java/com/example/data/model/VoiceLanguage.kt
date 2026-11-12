package com.example.data.model

import java.util.Locale

enum class VoiceLanguage(
    val displayName: String,
    val shortCode: String,
    val bcp47Tag: String,
    val speechLocale: Locale
) {
    AUTO(
        displayName = "Auto (Bengali & English)",
        shortCode = "Auto",
        bcp47Tag = "auto",
        speechLocale = Locale.getDefault()
    ),
    BANGLA(
        displayName = "বাংলা (Bengali)",
        shortCode = "বাংলা",
        bcp47Tag = "bn-BD",
        speechLocale = Locale("bn", "BD")
    ),
    ENGLISH(
        displayName = "English",
        shortCode = "EN",
        bcp47Tag = "en-US",
        speechLocale = Locale.US
    );

    companion object {
        fun detectLanguage(text: String): VoiceLanguage {
            // Check if string contains any Bengali Unicode block characters (\u0980-\u09FF)
            val hasBengaliChar = text.any { it in '\u0980'..'\u09FF' }
            return if (hasBengaliChar) BANGLA else ENGLISH
        }
    }
}
