package com.sanya.assistant

/**
 * SanyaResponseEngine
 *
 * Converts retrieved knowledge into a more natural, human-like response.
 *
 * This is intentionally separate from:
 * - IntentEngine.kt       -> understands the user's request
 * - SanyaCommands.kt      -> executes commands / retrieves knowledge
 * - AIBrain.kt            -> offline/basic knowledge
 *
 * It does NOT contain predefined names of presidents, prime ministers,
 * celebrities, or other people.
 */
object SanyaResponseEngine {

    /**
     * Main entry point.
     *
     * @param question Original user question
     * @param answer Raw answer retrieved from the knowledge system
     */
    fun createResponse(
        question: String,
        answer: String
    ): String {

        val q = clean(question)
        val a = clean(answer)

        if (a.isEmpty()) {
            return "I couldn't find a reliable answer to that."
        }

        // Reject obvious bad retrievals such as:
        // "What is the capital of Japan?"
        // -> "Capital punishment is a legal penalty in Japan."
        if (looksUnrelated(q, a)) {
            return "I couldn't find a reliable answer to that."
        }

        // Remove common API/search noise.
        val cleanedAnswer = removeNoise(a)

        if (cleanedAnswer.isEmpty()) {
            return "I couldn't find a reliable answer to that."
        }

        return makeNaturalResponse(q, cleanedAnswer)
    }

    /**
     * Makes the retrieved information sound conversational.
     */
    private fun makeNaturalResponse(
        question: String,
        answer: String
    ): String {

        val q = question.lowercase()

        return when {

            // -----------------------------------------------------
            // Definition / "What is..."
            // -----------------------------------------------------

            q.matches(
                Regex(
                    """^(?:what\s+is|what\s+are|define|explain)\b.*"""
                )
            ) -> {
                naturalDefinition(answer)
            }

            // -----------------------------------------------------
            // Person / "Who..."
            // -----------------------------------------------------

            q.matches(
                Regex(
                    """^(?:who|whom)\b.*"""
                )
            ) -> {
                naturalPersonAnswer(answer)
            }

            // -----------------------------------------------------
            // Time / date
            // -----------------------------------------------------

            q.matches(
                Regex(
                    """^when\b.*"""
                )
            ) -> {
                naturalTimeAnswer(answer)
            }

            // -----------------------------------------------------
            // Location
            // -----------------------------------------------------

            q.matches(
                Regex(
                    """^where\b.*"""
                )
            ) -> {
                naturalLocationAnswer(answer)
            }

            // -----------------------------------------------------
            // Why
            // -----------------------------------------------------

            q.matches(
                Regex(
                    """^why\b.*"""
                )
            ) -> {
                naturalExplanation(answer)
            }

            // -----------------------------------------------------
            // How
            // -----------------------------------------------------

            q.matches(
                Regex(
                    """^how\b.*"""
                )
            ) -> {
                naturalHowAnswer(answer)
            }

            // -----------------------------------------------------
            // Which
            // -----------------------------------------------------

            q.matches(
                Regex(
                    """^which\b.*"""
                )
            ) -> {
                naturalFactAnswer(answer)
            }

            // -----------------------------------------------------
            // General conversational request
            // -----------------------------------------------------

            else -> {
                answer
            }
        }
    }

    // -------------------------------------------------------------
    // Natural response helpers
    // -------------------------------------------------------------

    private fun naturalDefinition(
        answer: String
    ): String {

        val text = sentence(answer)

        return if (
            text.startsWith("Python is", ignoreCase = true) ||
            text.startsWith("Artificial intelligence is", ignoreCase = true) ||
            text.startsWith("AI is", ignoreCase = true)
        ) {
            text
        } else {
            "Sure. $text"
        }
    }

    private fun naturalPersonAnswer(
        answer: String
    ): String {

        val text = sentence(answer)

        return if (
            text.startsWith("He ", ignoreCase = true) ||
            text.startsWith("She ", ignoreCase = true) ||
            text.startsWith("They ", ignoreCase = true) ||
            text.contains(" is ", ignoreCase = true)
        ) {
            text
        } else {
            "The answer is $text"
        }
    }

    private fun naturalTimeAnswer(
        answer: String
    ): String {

        return sentence(answer)
    }

    private fun naturalLocationAnswer(
        answer: String
    ): String {

        return sentence(answer)
    }

    private fun naturalExplanation(
        answer: String
    ): String {

        return sentence(answer)
    }

    private fun naturalHowAnswer(
        answer: String
    ): String {

        return sentence(answer)
    }

    private fun naturalFactAnswer(
        answer: String
    ): String {

        return sentence(answer)
    }

    // -------------------------------------------------------------
    // Answer cleaning
    // -------------------------------------------------------------

    private fun removeNoise(
        input: String
    ): String {

        var text = input.trim()

        // Remove common search/API prefixes.
        text = text.replace(
            Regex(
                """^(?:answer|result|definition|description)\s*:\s*"""
            ),
            ""
        )

        // Remove repeated whitespace.
        text = text.replace(
            Regex("""\s+"""),
            " "
        )

        // Remove repeated punctuation.
        text = text.replace(
            Regex("""([.!?])\1+"""),
            "$1"
        )

        return text.trim()
    }

    private fun sentence(
        input: String
    ): String {

        var text = input.trim()

        if (text.isEmpty()) {
            return ""
        }

        text = text.replace(
            Regex("""\s+"""),
            " "
        )

        if (
            !text.endsWith(".") &&
            !text.endsWith("?") &&
            !text.endsWith("!")
        ) {
            text += "."
        }

        return text
    }

    private fun clean(
        input: String
    ): String {

        return input
            .lowercase()
            .replace(
                Regex("""\s+"""),
                " "
            )
            .trim()
    }

    // -------------------------------------------------------------
    // Retrieval validation
    // -------------------------------------------------------------

    /**
     * Detects obvious cases where the retrieved answer does not match
     * the user's question.
     *
     * This is deliberately generic and does not contain names of
     * specific people.
     */
    private fun looksUnrelated(
        question: String,
        answer: String
    ): Boolean {

        if (question.isEmpty() || answer.isEmpty()) {
            return true
        }

        val q = question.lowercase()
        val a = answer.lowercase()

        // ---------------------------------------------------------
        // Capital-city question
        // ---------------------------------------------------------

        if (
            q.matches(
                Regex(
                    """^(?:what\s+is\s+)?the\s+capital(?:\s+city)?\s+of\s+.+"""
                )
            ) ||
            q.matches(
                Regex(
                    """^what\s+is\s+the\s+capital(?:\s+city)?\s+of\s+.+"""
                )
            )
        ) {

            // An answer containing "capital punishment" is almost
            // certainly a bad search result for a capital-city query.
            if (
                a.contains("capital punishment") ||
                a.contains("legal penalty") ||
                a.contains("death penalty")
            ) {
                return true
            }
        }

        // ---------------------------------------------------------
        // "Who" question
        // ---------------------------------------------------------

        if (
            q.matches(
                Regex("""^who\b.*""")
            )
        ) {

            // Reject answers that are obviously generic unrelated
            // descriptions rather than a person/entity answer.
            if (
                a.startsWith("what is ") &&
                !a.contains(" is ")
            ) {
                return true
            }
        }

        // ---------------------------------------------------------
        // Query echo detection
        // ---------------------------------------------------------

        val normalizedQuestion =
            q.replace(
                Regex("""[^a-z0-9\s]"""),
                " "
            )
                .replace(
                    Regex("""\s+"""),
                    " "
                )
                .trim()

        val normalizedAnswer =
            a.replace(
                Regex("""[^a-z0-9\s]"""),
                " "
            )
                .replace(
                    Regex("""\s+"""),
                    " "
                )
                .trim()

        if (normalizedQuestion.isNotEmpty()) {

            val words =
                normalizedQuestion
                    .split(" ")
                    .filter {
                        it.length >= 4 &&
                            it !in QUESTION_WORDS
                    }

            if (words.isNotEmpty()) {

                val matchingWords =
                    words.count {
                        normalizedAnswer.contains(
                            Regex(
                                """\b${Regex.escape(it)}\b"""
                            )
                        )
                    }

                // If practically none of the important question
                // words occur in the answer, the result is suspicious.
                if (
                    words.size >= 3 &&
                    matchingWords == 0
                ) {
                    return true
                }
            }
        }

        return false
    }

    private val QUESTION_WORDS =
        setOf(
            "what",
            "when",
            "where",
            "who",
            "whom",
            "whose",
            "which",
            "why",
            "how",
            "is",
            "are",
            "was",
            "were",
            "the",
            "a",
            "an",
            "of",
            "to",
            "in",
            "on",
            "for",
            "does",
            "did",
            "do",
            "can",
            "could",
            "would",
            "will",
            "please",
            "tell",
            "about",
            "give",
            "me"
        )
}
