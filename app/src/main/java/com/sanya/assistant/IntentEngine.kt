package com.sanya.assistant

/**
 * Sanya Natural Language Intent Engine.
 *
 * Converts natural spoken sentences into the simpler command
 * vocabulary understood by CommandParser.
 *
 * Examples:
 *
 * "Hey Sanya, can you please open YouTube for me?"
 *      -> "open youtube"
 *
 * "Sanya, could you call Ravi?"
 *      -> "call ravi"
 *
 * "Please search for Tamil songs"
 *      -> "search tamil songs"
 *
 * "Can you turn the flashlight on?"
 *      -> "turn on flashlight"
 */
object IntentEngine {

    fun process(input: String): String {

        var text = input
            .lowercase()
            .trim()

        if (text.isEmpty()) return ""

        // ---------------------------------------------------------
        // WAKE WORDS
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """\b(?:hey\s+)?(?:sanya|sania|saniya|sonya|sonia|sunya)\b"""
            ),
            " "
        )

        // ---------------------------------------------------------
        // PUNCTUATION
        // ---------------------------------------------------------

        text = text.replace(
            Regex("""[,.!?;:]+"""),
            " "
        )

        // ---------------------------------------------------------
        // POLITE / CONVERSATIONAL PREFIXES
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:can|could|would|will)\s+you\s+(?:please\s+)?"""
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

        text = text.replace(
            Regex(
                """^(?:i\s+want\s+you\s+to|i\s+want\s+to|i'd\s+like\s+you\s+to|i'd\s+like\s+to|i\s+would\s+like\s+you\s+to|i\s+would\s+like\s+to)\s+"""
            ),
            ""
        )

        // ---------------------------------------------------------
        // COMMON CONTRACTIONS
        // ---------------------------------------------------------

        text = text.replace("what's", "what is")
        text = text.replace("whats", "what is")
        text = text.replace("i'm", "i am")
        text = text.replace("you're", "you are")
        text = text.replace("can't", "cannot")
        text = text.replace("don't", "do not")
        text = text.replace("it's", "it is")

        // ---------------------------------------------------------
        // OPEN APPLICATIONS
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:open|launch|start|run|load)\s+"""
            ),
            "open "
        )

        text = text.replace(
            Regex(
                """^(?:take\s+me\s+to|go\s+to)\s+(youtube|whatsapp|instagram|facebook|google|chrome|gmail|spotify|camera|settings)\s*$"""
            ),
            "open $1"
        )

        // ---------------------------------------------------------
        // CALL
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:please\s+)?(?:make|give|place)\s+(?:a\s+)?(?:phone\s+)?call\s+(?:to\s+)?"""
            ),
            "call "
        )

        text = text.replace(
            Regex(
                """^(?:phone|ring|dial)\s+(?:to\s+)?"""
            ),
            "call "
        )

        text = text.replace(
            Regex(
                """^(?:call)\s+(?:up\s+)?"""
            ),
            "call "
        )

        // ---------------------------------------------------------
        // WHATSAPP CALL
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:make|give|place)\s+(?:a\s+)?(?:whatsapp\s+)?(?:video\s+)?call\s+(?:to\s+)?"""
            ),
            "whatsapp call "
        )

        text = text.replace(
            Regex(
                """^(?:whatsapp)\s+(?:video\s+)?call\s+(?:to\s+)?"""
            ),
            "whatsapp call "
        )

        // ---------------------------------------------------------
        // MESSAGE / SMS
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:send|write)\s+(?:a\s+)?(?:text|message|sms|msg)\s+(?:to\s+)?"""
            ),
            "message "
        )

        text = text.replace(
            Regex(
                """^(?:text|message|sms|msg)\s+(?:to\s+)?"""
            ),
            "message "
        )

        // ---------------------------------------------------------
        // SEARCH
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:look\s+up|look\s+for|find)\s+"""
            ),
            "search "
        )

        text = text.replace(
            Regex(
                """^(?:search)\s+(?:for)\s+"""
            ),
            "search "
        )

        text = text.replace(
            Regex(
                """^(?:google)\s+(?:for)?\s*"""
            ),
            "search "
        )

        // ---------------------------------------------------------
        // YOUTUBE
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:watch|find)\s+(?:a\s+)?(?:video|videos)\s+(?:of|about)\s+"""
            ),
            "youtube "
        )

        text = text.replace(
            Regex(
                """^(?:watch)\s+"""
            ),
            "play "
        )

        // ---------------------------------------------------------
        // PLAY MUSIC / VIDEO
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:put\s+on)\s+"""
            ),
            "play "
        )

        text = text.replace(
            Regex(
                """^(?:play)\s+"""
            ),
            "play "
        )

        // ---------------------------------------------------------
        // TIMER
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:set\s+)?(?:a\s+)?timer\s+(?:for\s+)?"""
            ),
            "set timer "
        )

        // "set a timer for 5 minutes"
        // becomes "set timer 5 minutes"
        text = text.replace(
            Regex(
                """^timer\s+(?:for\s+)?"""
            ),
            "set timer "
        )

        // ---------------------------------------------------------
        // REMINDER
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:please\s+)?remind\s+me\s+(?:to\s+)?"""
            ),
            "remind me "
        )

        // ---------------------------------------------------------
        // ALARM
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:set\s+)?(?:an?\s+)?alarm\s+(?:for|at)\s+"""
            ),
            "alarm "
        )

        // ---------------------------------------------------------
        // NOTES
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:please\s+)?(?:write|make|create|take)\s+(?:a\s+)?note\s+(?:saying\s+|that\s+says\s+|that\s+)?"""
            ),
            "note "
        )

        // ---------------------------------------------------------
        // TODO
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:please\s+)?(?:add|put)\s+(.+?)\s+(?:to|in|on)\s+(?:my\s+)?(?:todo|to-do|to do)\s+list"""
            )
        ) {
            "add ${it.groupValues[1]} to my todo list"
        }

        // ---------------------------------------------------------
        // VOLUME
        // ---------------------------------------------------------

        if (
            Regex(
                """^(?:make\s+the\s+volume\s+)?(?:turn\s+the\s+volume\s+)?(?:volume\s+)?(?:louder|higher|up)$"""
            ).matches(text)
        ) {
            text = "volume up"
        }

        if (
            Regex(
                """^(?:make\s+the\s+volume\s+)?(?:turn\s+the\s+volume\s+)?(?:volume\s+)?(?:quieter|lower|down)$"""
            ).matches(text)
        ) {
            text = "volume down"
        }

        // ---------------------------------------------------------
        // FLASHLIGHT
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:switch|turn|turning)\s+(?:the\s+)?flashlight\s+on$"""
            ),
            "turn on flashlight"
        )

        text = text.replace(
            Regex(
                """^(?:switch|turn|turning)\s+(?:the\s+)?flashlight\s+off$"""
            ),
            "turn off flashlight"
        )

        text = text.replace(
            Regex(
                """^(?:switch|turn)\s+(?:the\s+)?torch\s+on$"""
            ),
            "turn on torch"
        )

        text = text.replace(
            Regex(
                """^(?:switch|turn)\s+(?:the\s+)?torch\s+off$"""
            ),
            "turn off torch"
        )

        // ---------------------------------------------------------
        // WIFI
        // ---------------------------------------------------------

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

        // ---------------------------------------------------------
        // BLUETOOTH
        // ---------------------------------------------------------

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

        // ---------------------------------------------------------
        // MOBILE DATA
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:switch|turn)\s+(?:the\s+)?(?:mobile\s+)?data\s+on$"""
            ),
            "turn on data"
        )

        text = text.replace(
            Regex(
                """^(?:switch|turn)\s+(?:the\s+)?(?:mobile\s+)?data\s+off$"""
            ),
            "turn off data"
        )

        // ---------------------------------------------------------
        // NAVIGATION
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:please\s+)?(?:take|navigate)\s+me\s+to\s+"""
            ),
            "navigate to "
        )

        text = text.replace(
            Regex(
                """^(?:how\s+do\s+i\s+get\s+to)\s+"""
            ),
            "navigate to "
        )

        text = text.replace(
            Regex(
                """^(?:give\s+me\s+directions\s+to)\s+"""
            ),
            "navigate to "
        )

        text = text.replace(
            Regex(
                """^(?:directions\s+to)\s+"""
            ),
            "navigate to "
        )

        // ---------------------------------------------------------
        // INFORMATION
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:tell\s+me)\s+(?:the\s+)?time$"""
            ),
            "what time is it"
        )

        text = text.replace(
            Regex(
                """^(?:what\s+time\s+is\s+it)$"""
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
                """^(?:what\s+is\s+today'?s\s+date)$"""
            ),
            "what is the date"
        )

        text = text.replace(
            Regex(
                """^(?:how much\s+battery\s+do\s+i\s+have|what\s+is\s+my\s+battery|how much\s+battery)$"""
            ),
            "battery"
        )

        // ---------------------------------------------------------
        // MEDIA CONTROLS
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """^(?:skip|go)\s+(?:to\s+)?(?:the\s+)?next\s+(?:song|track|video)$"""
            ),
            "next"
        )

        text = text.replace(
            Regex(
                """^(?:go\s+)?(?:back\s+to\s+)?(?:the\s+)?previous\s+(?:song|track|video)$"""
            ),
            "previous"
        )

        text = text.replace(
            Regex(
                """^(?:stop|pause)\s+(?:the\s+)?(?:music|song|video|playing)$"""
            ),
            "pause"
        )

        text = text.replace(
            Regex(
                """^(?:continue|resume)\s+(?:the\s+)?(?:music|song|video|playing)$"""
            ),
            "resume"
        )

        // ---------------------------------------------------------
        // CONVERSATIONAL FILLERS
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """\b(?:actually|basically|simply|really|maybe|just|kindly|now)\b"""
            ),
            " "
        )

        // ---------------------------------------------------------
        // SPOKEN FORMS
        // ---------------------------------------------------------

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

        // ---------------------------------------------------------
        // REMOVE POLITE ENDINGS
        // ---------------------------------------------------------

        text = text.replace(
            Regex(
                """\s+(?:please|pls|plz|for\s+me|for\s+me\s+please)$"""
            ),
            ""
        )

        // ---------------------------------------------------------
        // CLEAN SPACES
        // ---------------------------------------------------------

        text = text
            .replace(Regex("""\s+"""), " ")
            .trim()

        return text
    }

    /**
     * Returns a question string suitable for local knowledge matching.
     * Existing command transformations are reused first so wake words and
     * polite wrappers are removed consistently with the rest of Sanya.
     */
    fun normalizeQuestion(input: String): String {

        var text = process(input)

        if (text.isEmpty()) return ""

        text = text
            .replace(Regex("""^(?:tell\s+me|give\s+me)\s+"""), "")
            .replace(Regex("""^(?:do\s+you\s+know)\s+"""), "")
            .replace(Regex("""^(?:could\s+you|would\s+you|can\s+you)\s+"""), "")
            .replace(Regex("""\s+(?:please|pls|plz)$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return text
    }

    /**
     * True only for an unresolved natural-language question.
     * Normal Sanya commands are checked by CommandParser first, so commands
     * such as "what is the time" or "how do I get to Chennai" still follow
     * their existing command paths.
     */
    /**
     * Returns true when the sentence is a real-world/general knowledge
     * question that should go to Sanya's knowledge pipeline instead of the
     * normal command parser.
     *
     * Important: questions about Sanya's own tools/memory (time, battery,
     * notes, todos, personal facts, calculator, etc.) remain protected so
     * the existing command system keeps handling them.
     */
    fun isGeneralQuestion(input: String): Boolean {

        val question =
            normalizeQuestion(input)

        if (question.isEmpty()) return false

        if (isProtectedCommandQuestion(question)) {
            return false
        }

        val directQuestion = Regex(
            """^(?:what|who|whom|when|where|why|how|which|whose|is|are|was|were|am|can|could|will|would|should|shall|has|have|had|do|does|did)\b"""
        ).containsMatchIn(question)

        if (directQuestion) {
            return true
        }

        // Natural spoken forms such as:
        // "tell me who the president of India is"
        // "give me information about photosynthesis"
        return Regex(
            """^(?:tell\s+me|give\s+me)\s+(?:what|who|whom|when|where|why|how|which|whose|information\s+about|details\s+about)\b"""
        ).containsMatchIn(question)
    }

    /**
     * Questions that intentionally belong to Sanya's existing command
     * system. These must not be sent to web search.
     */
    fun isProtectedCommandQuestion(input: String): Boolean {

        val question =
            normalizeQuestionForClassification(input)

        if (question.isEmpty()) return false

        if (Regex(
                """^(?:what\s+(?:is|are)|what's|what)\s+(?:the\s+)?(?:time|date|day)|^(?:what\s+time|time\s+now|current\s+time|today's\s+date|today\s+date)\b"""
            ).containsMatchIn(question)) {
            return true
        }

        if (Regex(
                """^(?:how\s+much\s+battery|what\s+(?:is|are)\s+my\s+battery|battery|battery\s+level)\b"""
            ).containsMatchIn(question)) {
            return true
        }

        if (Regex(
                """^(?:am\s+i\s+(?:online|offline)|do\s+i\s+have\s+internet|is\s+(?:the\s+)?internet\s+(?:on|connected|working)|network\s+status)\b"""
            ).containsMatchIn(question)) {
            return true
        }

        if (Regex(
                """^(?:what\s+(?:did|have|do)\s+you\s+(?:learn|know))\b"""
            ).containsMatchIn(question)) {
            return true
        }

        // Personal memory / Brain recall.
        if (Regex(
                """^(?:what\s+(?:is|are)|what's|tell\s+me|show\s+me|read)\s+my\s+.+"""
            ).matches(question)) {
            return true
        }

        // Notes and todo list.
        if (Regex(
                """^(?:(?:read|show|list|what\s+(?:is|are)|what's)\s+)?(?:my\s+)?notes\b"""
            ).containsMatchIn(question) ||
            Regex(
                """^(?:(?:read|show|list|what\s+(?:is|are)|what's)\s+)?(?:my\s+)?(?:to-?do|todo)\s+list\b"""
            ).containsMatchIn(question)) {
            return true
        }

        // Calculator expressions should remain offline and local.
        if (Regex(
                """^(?:calculate|calc|compute|what\s+is|what's|how\s+much\s+is)?\s*-?\d+(?:\.\d+)?\s*(?:plus|minus|times|multiplied\s+by|x|into|divided\s+by|over|\+|-|\*|×|/|÷)\s*-?\d+(?:\.\d+)?(?:\s*)$"""
            ).matches(question) ||
            Regex(
                """^(?:calculate|what\s+is|what's)?\s*-?\d+(?:\.\d+)?\s*(?:percent|%)\s+of\s+-?\d+(?:\.\d+)?$"""
            ).matches(question)) {
            return true
        }

        return false
    }

    /**
     * Classification-only normalization. Unlike process(), this deliberately
     * does not convert commands such as "search for ..." or "watch ...".
     */
    private fun normalizeQuestionForClassification(input: String): String {

        var text = input
            .lowercase()
            .trim()

        if (text.isEmpty()) return ""

        text = text.replace(
            Regex(
                """\b(?:hey\s+)?(?:sanya|sania|saniya|sonya|sonia|sunya)\b"""
            ),
            " "
        )

        text = text
            .replace("what's", "what is")
            .replace("whats", "what is")
            .replace("today's", "today is")
            .replace("todays", "today is")
            .replace("i'm", "i am")
            .replace("you're", "you are")
            .replace("can't", "cannot")
            .replace("don't", "do not")
            .replace("it's", "it is")
            .replace(Regex("""[,.!?;:]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        text = text.replace(
            Regex(
                """^(?:can|could|would|will)\s+you\s+(?:please\s+)?"""
            ),
            ""
        )

        text = text.replace(
            Regex("""^(?:please|pls|plz|kindly)\s+"""),
            ""
        )

        return text
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Returns possible interpretations.
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

        // ---------------------------------------------------------
        // PLAY / WATCH
        // ---------------------------------------------------------

        if (primary.startsWith("watch ")) {
            results.add(
                "play " + primary.removePrefix("watch ").trim()
            )
        }

        // ---------------------------------------------------------
        // FIND
        // ---------------------------------------------------------

        if (primary.startsWith("find ")) {
            results.add(
                "search " + primary.removePrefix("find ").trim()
            )
        }

        // ---------------------------------------------------------
        // LOOK UP
        // ---------------------------------------------------------

        if (primary.startsWith("look up ")) {
            results.add(
                "search " + primary.removePrefix("look up ").trim()
            )
        }

        return results
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
