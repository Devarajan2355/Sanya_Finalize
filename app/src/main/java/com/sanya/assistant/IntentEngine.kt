package com.sanya.assistant

/**
 * Sanya Intent Engine
 *
 * Converts natural spoken phrases into a cleaner command phrase
 * that the existing CommandParser can understand.
 *
 * This is intentionally offline and does not require an API key.
 */
object IntentEngine {

    /**
     * Main entry point.
     *
     * Example:
     * "Sanya, can you please open YouTube for me?"
     *      ↓
     * "open youtube"
     */
    fun process(input: String): String {
        var text = input.lowercase().trim()

        if (text.isEmpty()) return ""

        // Remove common wake words.
        text = text.replace(
            Regex(
                """\b(?:hey\s+)?(?:sanya|sania|saniya|sonya|sunya)\b"""
            ),
            " "
        )

        // Remove punctuation.
        text = text.replace(
            Regex("""[,.!?;:]+"""),
            " "
        )

        // Conversational phrases.
        text = text.replace(
            Regex(
                """^(?:can|could|would|will)\s+you\s+"""
            ),
            ""
        )

        text = text.replace(
            Regex(
                """^(?:can|could|would|will)\s+"""
            ),
            ""
        )

        text = text.replace(
            Regex(
                """^(?:please|pls|plz|kindly)\s+"""
            ),
            ""
        )

        // Remove polite endings.
        text = text.replace(
            Regex(
                """\s+(?:please|for\s+me|for\s+me\s+please)$"""
            ),
            ""
        )

        // Natural language → command language.

        // OPEN APP
        text = text.replace(
            Regex(
                """^(?:i\s+want\s+to\s+|i'd\s+like\s+to\s+|i\s+would\s+like\s+to\s+)?(?:open|launch|start)\s+"""
            ),
            "open "
        )

        text = text.replace(
            Regex(
                """^(?:take\s+me\s+to|go\s+to)\s+"""
            ),
            "open "
        )

        // CALL
        text = text.replace(
            Regex(
                """^(?:please\s+)?(?:give|make)\s+(?:a\s+)?(?:phone\s+)?call\s+to\s+"""
            ),
            "call "
        )

        text = text.replace(
            Regex(
                """^(?:i\s+want\s+to\s+)?(?:phone|ring|dial)\s+"""
            ),
            "call "
        )

        // MESSAGE
        text = text.replace(
            Regex(
                """^(?:send|write)\s+(?:a\s+)?(?:text|message)\s+to\s+"""
            ),
            "message "
        )

        text = text.replace(
            Regex(
                """^(?:i\s+want\s+to\s+)?(?:text|message)\s+"""
            ),
            "message "
        )

        // SEARCH
        text = text.replace(
            Regex(
                """^(?:i\s+want\s+to\s+)?(?:look\s+up|look\s+for|find)\s+"""
            ),
            "search "
        )

        text = text.replace(
            Regex(
                """^(?:search\s+for)\s+"""
            ),
            "search "
        )

        // YOUTUBE SEARCH
        text = text.replace(
            Regex(
                """^(?:i\s+want\s+to\s+)?(?:watch|find)\s+(?:a\s+video\s+of\s+|videos\s+of\s+)"""
            ),
            "youtube "
        )

        // PLAY MEDIA
        text = text.replace(
            Regex(
                """^(?:i\s+want\s+to\s+)?(?:play|put\s+on)\s+"""
            ),
            "play "
        )

        // TIMER
        text = text.replace(
            Regex(
                """^(?:set\s+)?(?:a\s+)?timer\s+(?:for\s+)?"""
            ),
            "set timer "
        )

        // REMINDER
        text = text.replace(
            Regex(
                """^(?:please\s+)?remind\s+me\s+(?:to\s+)?"""
            ),
            "remind "
        )

        // NOTE
        text = text.replace(
            Regex(
                """^(?:please\s+)?(?:write|make|create|take)\s+(?:a\s+)?note\s+(?:saying\s+|that\s+)?"""
            ),
            "note "
        )

        // TODO
        text = text.replace(
            Regex(
                """^(?:please\s+)?(?:add|put)\s+"""
            ),
            "add "
        )

        // VOLUME
        text = text.replace(
            Regex(
                """^(?:make\s+the\s+volume\s+)?(?:turn\s+the\s+volume\s+)?(?:louder|higher)$"""
            ),
            "volume up"
        )

        text = text.replace(
            Regex(
                """^(?:make\s+the\s+volume\s+)?(?:turn\s+the\s+volume\s+)?(?:quieter|lower)$"""
            ),
            "volume down"
        )

        // FLASHLIGHT
        text = text.replace(
            Regex(
                """^(?:switch|turn)\s+(?:the\s+)?flashlight\s+on$"""
            ),
            "turn on flashlight"
        )

        text = text.replace(
            Regex(
                """^(?:switch|turn)\s+(?:the\s+)?flashlight\s+off$"""
            ),
            "turn off flashlight"
        )

        // WIFI
        text = text.replace(
            Regex(
                """^(?:switch|turn)\s+(?:the\s+)?wifi\s+on$"""
            ),
            "turn on wifi"
        )

        text = text.replace(
            Regex(
                """^(?:switch|turn)\s+(?:the\s+)?wifi\s+off$"""
            ),
            "turn off wifi"
        )

        // BLUETOOTH
        text = text.replace(
            Regex(
                """^(?:switch|turn)\s+(?:the\s+)?bluetooth\s+on$"""
            ),
            "turn on bluetooth"
        )

        text = text.replace(
            Regex(
                """^(?:switch|turn)\s+(?:the\s+)?bluetooth\s+off$"""
            ),
            "turn off bluetooth"
        )

        // NAVIGATION
        text = text.replace(
            Regex(
                """^(?:please\s+)?(?:take|navigate)\s+me\s+to\s+"""
            ),
            "navigate "
        )

        text = text.replace(
            Regex(
                """^(?:how\s+do\s+i\s+get\s+to)\s+"""
            ),
            "navigate "
        )

        // INFORMATION
        text = text.replace(
            Regex(
                """^(?:tell\s+me)\s+(?:the\s+)?time$"""
            ),
            "what time is it"
        )

        text = text.replace(
            Regex(
                """^(?:tell\s+me)\s+(?:today'?s\s+)?date$"""
            ),
            "what is the date"
        )

        text = text.replace(
            Regex(
                """^(?:how much\s+battery\s+do\s+i\s+have|what\s+is\s+my\s+battery)$"""
            ),
            "battery"
        )

        // Common spoken contractions.
        text = text.replace("what's", "what is")
        text = text.replace("whats", "what is")
        text = text.replace("i'm", "i am")
        text = text.replace("you're", "you are")
        text = text.replace("can't", "cannot")
        text = text.replace("don't", "do not")

        // Remove unnecessary conversational words.
        text = text.replace(
            Regex(
                """\b(?:actually|basically|just|simply|really|maybe|kindly|now)\b"""
            ),
            " "
        )

        // Handle common spoken forms.
        text = text.replace(
            Regex("""\bopen\s+up\s+"""),
            "open "
        )

        text = text.replace(
            Regex("""\bturn\s+on\s+the\s+"""),
            "turn on "
        )

        text = text.replace(
            Regex("""\bturn\s+off\s+the\s+"""),
            "turn off "
        )

        // Collapse multiple spaces.
        text = text.replace(
            Regex("""\s+"""),
            " "
        ).trim()

        return text
    }

    /**
     * Returns several possible interpretations.
     *
     * The first result is always the primary interpretation.
     */
    fun alternatives(input: String): List<String> {

        val primary = process(input)

        if (primary.isEmpty()) {
            return emptyList()
        }

        val results = mutableListOf<String>()
        results.add(primary)

        // "watch X" can also mean YouTube search.
        if (primary.startsWith("watch ")) {
            results.add(
                "play " + primary.removePrefix("watch ").trim()
    )
}

        if (primary.startsWith("play ")) {
            results.add(
                "play " + primary.removePrefix("play ").trim()
    )
}

        // "find X" can be interpreted as search X.
        if (primary.startsWith("find ")) {
            results.add(
                "search " + primary.removePrefix("find ").trim()
            )
        }

        return results.distinct()
    }
}
