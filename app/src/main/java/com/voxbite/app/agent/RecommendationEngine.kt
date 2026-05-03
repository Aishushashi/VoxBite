package com.voxbite.app.agent

import java.util.Calendar

class RecommendationEngine {

    // ─── Recent command history (in-memory, max 10) ───────────────────────────
    private val recentCommands = mutableListOf<String>()

    fun recordCommand(item: String) {
        if (item.isBlank()) return
        recentCommands.remove(item) // avoid duplicates
        recentCommands.add(0, item) // most recent first
        if (recentCommands.size > 10) recentCommands.removeAt(recentCommands.size - 1)
    }

    // ─── Time-based defaults ──────────────────────────────────────────────────
    private fun timeBasedSuggestions(): List<String> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 6..10  -> listOf("idli", "dosa", "poha", "upma", "vada")       // breakfast
            hour in 11..14 -> listOf("biryani", "thali", "roti", "rice", "dal")    // lunch
            hour in 15..17 -> listOf("samosa", "chai", "sandwich", "pakoda")       // snacks
            hour in 18..22 -> listOf("biryani", "pizza", "burger", "noodles", "paratha") // dinner
            else           -> listOf("biryani", "dosa", "roti")                    // late night
        }
    }

    // ─── Build final recommendation list ─────────────────────────────────────
    // Merges history (priority) + time-based, deduped, max 3
    fun getRecommendations(): List<String> {
        val timeBased = timeBasedSuggestions()
        val merged    = mutableListOf<String>()

        // Add recent history first (max 2 from history)
        merged.addAll(recentCommands.take(2))

        // Fill remaining slots from time-based suggestions
        for (item in timeBased) {
            if (merged.size >= 3) break
            if (!merged.contains(item)) merged.add(item)
        }

        return merged.take(3)
    }

    // ─── Spoken recommendation string per language ────────────────────────────
    fun getSpokenRecommendation(lang: String): String {
        val items = getRecommendations()
        if (items.isEmpty()) return ""

        val list = items.joinToString(", ")
        return when (lang) {
            "hi-IN" -> "आप ऑर्डर कर सकते हैं: $list"
            "kn-IN" -> "ನೀವು ಆರ್ಡರ್ ಮಾಡಬಹುದು: $list"
            else    -> "You might want to order: $list"
        }
    }

    // ─── Time greeting spoken on app open ────────────────────────────────────
    fun getGreeting(lang: String): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeHint = when {
            hour in 6..10  -> when (lang) {
                "hi-IN" -> "सुप्रभात! नाश्ते में क्या चाहिए?"
                "kn-IN" -> "ಶುಭೋದಯ! ತಿಂಡಿಗೆ ಏನು ಬೇಕು?"
                else    -> "Good morning! What's for breakfast?"
            }
            hour in 11..14 -> when (lang) {
                "hi-IN" -> "दोपहर का खाना क्या ऑर्डर करें?"
                "kn-IN" -> "ಮಧ್ಯಾಹ್ನದ ಊಟಕ್ಕೆ ಏನು ಆರ್ಡರ್ ಮಾಡಲಿ?"
                else    -> "Lunchtime! What would you like to order?"
            }
            hour in 15..17 -> when (lang) {
                "hi-IN" -> "शाम की चाय या नाश्ता?"
                "kn-IN" -> "ಸಂಜೆಯ ತಿಂಡಿ ಅಥವಾ ಚಹಾ?"
                else    -> "Evening snack time! What do you feel like?"
            }
            else -> when (lang) {
                "hi-IN" -> "रात के खाने में क्या ऑर्डर करें?"
                "kn-IN" -> "ರಾತ್ರಿಯ ಊಟಕ್ಕೆ ಏನು ಆರ್ಡರ್ ಮಾಡಲಿ?"
                else    -> "Dinner time! What are you craving?"
            }
        }
        return timeHint
    }
}