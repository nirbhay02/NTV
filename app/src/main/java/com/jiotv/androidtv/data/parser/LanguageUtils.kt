package com.jiotv.androidtv.data.parser

import java.util.Locale

/**
 * Utility for normalizing, canonicalizing, and filtering channel languages across the app.
 */
object LanguageUtils {

    /**
     * Standard order for displaying languages in UI (prioritizing popular Indian broadcast languages).
     */
    val POPULAR_LANGUAGES = listOf(
        "Hindi",
        "English",
        "Marathi",
        "Bengali",
        "Tamil",
        "Telugu",
        "Kannada",
        "Malayalam",
        "Punjabi",
        "Gujarati",
        "Bhojpuri",
        "Odia",
        "Urdu",
        "Assamese"
    )

    /**
     * Returns canonical title-cased language name.
     */
    fun canonical(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return "Hindi"

        return when {
            t.equals("hindi", ignoreCase = true) -> "Hindi"
            t.equals("english", ignoreCase = true) -> "English"
            t.equals("marathi", ignoreCase = true) -> "Marathi"
            t.equals("punjabi", ignoreCase = true) -> "Punjabi"
            t.equals("urdu", ignoreCase = true) -> "Urdu"
            t.equals("bengali", ignoreCase = true) || t.equals("bangla", ignoreCase = true) -> "Bengali"
            t.equals("malayalam", ignoreCase = true) -> "Malayalam"
            t.equals("tamil", ignoreCase = true) -> "Tamil"
            t.equals("gujarati", ignoreCase = true) -> "Gujarati"
            t.equals("odia", ignoreCase = true) || t.equals("oriya", ignoreCase = true) -> "Odia"
            t.equals("telugu", ignoreCase = true) -> "Telugu"
            t.equals("bhojpuri", ignoreCase = true) -> "Bhojpuri"
            t.equals("kannada", ignoreCase = true) -> "Kannada"
            t.equals("assamese", ignoreCase = true) -> "Assamese"
            else -> t.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    /**
     * Returns true if channel's language is permitted by enabledLanguages set.
     * If enabledLanguages is empty, all languages are permitted by default.
     */
    fun isAllowed(channelLanguage: String?, enabledLanguages: Set<String>): Boolean {
        if (enabledLanguages.isEmpty()) return true
        val canon = canonical(channelLanguage)
        return enabledLanguages.any { it.equals(canon, ignoreCase = true) }
    }
}
