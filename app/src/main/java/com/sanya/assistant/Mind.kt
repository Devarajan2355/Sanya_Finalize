package com.sanya.assistant

import android.content.Context
import java.io.File
import java.util.Calendar
import kotlin.math.abs

/**
 * Mind: Sanya's own memory and character. 100% on-device, no external AI.
 *
 * What it gives her:
 *  - Episodic memory   every request is remembered (what, when, understood or not)
 *  - Habits            she notices what you usually ask at what time of day
 *  - Mood              derived from how well she has understood you lately
 *  - Familiarity       she acts differently with a stranger than with someone she knows
 *  - Similarity sense  she can recognise a phrase she was never taught, if it is
 *                      close to something she was
 *  - Self-model        she can answer questions about herself from her real state
 *
 * Storage (private to the app):
 *  - files/mind.log           one event per line:  timestamp|hour|ok|text
 *  - prefs "sanya_mind"       total, first, last interaction times
 *
 * Used by StepRunner (SanyaCommands.kt). Deleting the data: Mind.wipe(ctx).
 */
object Mind {

    private const val LOG = "mind.log"
    private const val PREFS = "sanya_mind"
    private const val KEEP = 400
    private const val DAY_MS = 86_400_000L

    private data class Event(
        val ts: Long,
        val hour: Int,
        val ok: Boolean,
        val text: String
    )

    // ---------------------------------------------------------------
    // Memory
    // ---------------------------------------------------------------

    private fun logFile(ctx: Context) = File(ctx.filesDir, LOG)

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun parseEvent(line: String): Event? {
        val p = line.split("|", limit = 4)
        if (p.size < 4) return null
        val ts = p[0].toLongOrNull() ?: return null
        val hour = p[1].toIntOrNull() ?: return null
        return Event(ts, hour, p[2] == "1", p[3])
    }

    private fun events(ctx: Context): List<Event> {
        return try {
            val f = logFile(ctx)
            if (!f.exists()) {
                emptyList<Event>()
            } else {
                f.readLines().mapNotNull { parseEvent(it) }
            }
        } catch (e: Exception) {
            emptyList<Event>()
        }
    }

    /** Remember one request. ok = she understood it. */
    fun record(ctx: Context, text: String, ok: Boolean) {

        val clean = text.replace('|', ' ').replace('\n', ' ').trim()
        if (clean.isEmpty()) return

        try {
            val now = System.currentTimeMillis()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val flag = if (ok) 1 else 0
            val f = logFile(ctx)

            f.appendText("$now|$hour|$flag|$clean\n")

            val p = prefs(ctx)
            val total = p.getInt("total", 0) + 1
            val ed = p.edit()

            if (p.getLong("first", 0L) == 0L) ed.putLong("first", now)

            ed.putInt("total", total)
                .putLong("last", now)
                .apply()

            // Keep the log small.
            if (total % 50 == 0) {
                val lines = f.readLines()
                if (lines.size > KEEP + 100) {
                    f.writeText(lines.takeLast(KEEP).joinToString("\n") + "\n")
                }
            }
        } catch (e: Exception) {
            // Memory is a bonus; never break a command because of it.
        }
    }

    /** Erase everything she remembers about your history and habits. */
    fun wipe(ctx: Context) {
        try {
            logFile(ctx).delete()
            prefs(ctx).edit().clear().apply()
        } catch (e: Exception) {
        }
    }

    // ---------------------------------------------------------------
    // Habits
    // ---------------------------------------------------------------

    private val BORING = Regex(
        """^(?:again|hello|hi|hey|hai|thanks|thank you|yes|no|ok|okay|stop|exit|yes\s?\w*)$"""
    )

    private fun meaningful(text: String): Boolean =
        text.length >= 4 && !BORING.matches(text)

    /**
     * What you usually say around this time of day (within one hour),
     * if you have said it at least 3 times. Otherwise null.
     */
    fun habit(ctx: Context): String? {

        val now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val counts = HashMap<String, Int>()

        for (e in events(ctx)) {
            if (!e.ok || !meaningful(e.text)) continue
            val d = abs(e.hour - now)
            val gap = minOf(d, 24 - d)
            if (gap <= 1) counts[e.text] = (counts[e.text] ?: 0) + 1
        }

        val best = counts.maxByOrNull { it.value } ?: return null
        return if (best.value >= 3) best.key else null
    }

    private fun topCommands(ctx: Context, n: Int): List<String> {

        val counts = HashMap<String, Int>()

        for (e in events(ctx)) {
            if (!e.ok || !meaningful(e.text)) continue
            counts[e.text] = (counts[e.text] ?: 0) + 1
        }

        return counts.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key }
    }

    /** (understood, total) over her last 20 requests, or null if too few. */
    private fun recent(ctx: Context): Pair<Int, Int>? {
        val last = events(ctx).takeLast(20)
        if (last.size < 5) return null
        return Pair(last.count { it.ok }, last.size)
    }

    // ---------------------------------------------------------------
    // Similarity: recognising phrases she was never taught exactly
    // ---------------------------------------------------------------

    private fun trigrams(s: String): Set<String> {
        val p = "  $s "
        val out = HashSet<String>()
        for (i in 0..(p.length - 3)) out.add(p.substring(i, i + 3))
        return out
    }

    private fun stem(w: String): String = when {
        w.length > 4 && w.endsWith("ing") -> w.dropLast(3)
        w.length > 3 && w.endsWith("s") -> w.dropLast(1)
        else -> w
    }

    private fun wordSet(s: String): Set<String> =
        s.split(" ").filter { it.length > 1 }.map { stem(it) }.toSet()

    /** 0.0 (unrelated) to 1.0 (identical). */
    fun similarity(a: String, b: String): Double {

        val x = a.lowercase().trim()
        val y = b.lowercase().trim()

        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0

        val ta = trigrams(x)
        val tb = trigrams(y)
        val dice = 2.0 * ta.intersect(tb).size / (ta.size + tb.size)

        val wa = wordSet(x)
        val wb = wordSet(y)
        val union = wa.union(wb).size
        val jaccard =
            if (union == 0) 0.0 else wa.intersect(wb).size.toDouble() / union

        return 0.5 * dice + 0.5 * jaccard
    }

    /** The closest candidate and its score, or null if there are none. */
    fun best(phrase: String, candidates: Collection<String>): Pair<String, Double>? {

        var bestKey: String? = null
        var bestScore = 0.0

        for (c in candidates) {
            val s = similarity(phrase, c)
            if (s > bestScore) {
                bestScore = s
                bestKey = c
            }
        }

        val k = bestKey ?: return null
        return Pair(k, bestScore)
    }

    // ---------------------------------------------------------------
    // Talking about herself and about you
    // ---------------------------------------------------------------

    private val GREET =
        Regex("""^(?:hello|hi|hey|hai|hii|yo|vanakkam)$""")

    private val MOOD =
        Regex("""^(?:how are you|how r u|how are u|how are you feeling|how do you feel|how is it going|how you doing)$""")

    private val ABOUT_ME =
        Regex("""^(?:what do you know about me|tell me about myself|what have you noticed|what are my habits|what do you remember about me|how well do you know me)$""")

    private val SUGGEST =
        Regex("""^(?:what should i do|suggest something|any suggestions?|what do i usually do now)$""")

    private val LAST =
        Regex("""^(?:what did i (?:ask|say|tell you) (?:last|before|earlier)|what was my last (?:command|request)|what did i just (?:ask|say))$""")

    private val SELF =
        Regex("""^(?:are you (?:alive|real|conscious|human|sentient)|do you have (?:a )?(?:mind|feelings|brain))$""")

    private val WIPE =
        Regex("""^(?:forget my habits|forget my history|clear my history|clear my habits|reset your memory)$""")

    private fun norm(raw: String): String =
        raw.lowercase()
            .replace(Regex("""[^a-z0-9' ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * Which kind of conversation this is, or null if it is not one.
     * Kinds: greet, mood, about, suggest, last, self, wipe
     */
    fun kind(raw: String): String? {
        val t = norm(raw)
        return when {
            t.isEmpty() -> null
            GREET.matches(t) -> "greet"
            MOOD.matches(t) -> "mood"
            ABOUT_ME.matches(t) -> "about"
            SUGGEST.matches(t) -> "suggest"
            LAST.matches(t) -> "last"
            SELF.matches(t) -> "self"
            WIPE.matches(t) -> "wipe"
            else -> null
        }
    }

    /**
     * Her reply, built from her real state (history, habits, mood).
     * @param name the user's name with a leading space (" Dean") or "".
     */
    fun reply(ctx: Context, kind: String, raw: String, name: String): String =
        when (kind) {
            "greet" -> greeting(ctx, norm(raw), name)
            "mood" -> moodLine(ctx)
            "about" -> aboutMe(ctx)
            "suggest" -> noHabitLine(ctx)
            "last" -> lastLine(ctx)
            "self" -> selfLine(ctx)
            "wipe" -> {
                wipe(ctx)
                "Done. I've cleared my memory of our history and your habits."
            }
            else -> "I'm here."
        }

    private fun daypart(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 12 -> "morning"
            h < 17 -> "afternoon"
            h < 21 -> "evening"
            else -> "night"
        }
    }

    private fun greeting(ctx: Context, t: String, name: String): String {

        val p = prefs(ctx)
        val total = p.getInt("total", 0)
        val last = p.getLong("last", 0L)
        val days =
            if (last == 0L) 0L else (System.currentTimeMillis() - last) / DAY_MS
        val part = daypart()
        val hello =
            if (t.startsWith("vanakkam")) {
                "Vanakkam"
            } else {
                listOf("Hello", "Hey", "Hi").random()
            }

        return when {
            total < 3 ->
                "$hello$name! I'm Sanya. I'm still new, so teach me how you talk."

            days >= 3 ->
                "Welcome back$name. It's been $days days."

            else -> listOf(
                "$hello$name! Good $part.",
                "$hello$name. What's next?",
                "$hello$name, good $part. Ready when you are."
            ).random()
        }
    }

    private fun moodLine(ctx: Context): String {

        val r = recent(ctx)
            ?: return "I'm good, and still learning how you talk."

        val ok = r.first
        val n = r.second

        return when {
            ok * 2 < n ->
                "A little lost lately. I understood only $ok of your last $n requests. Teach me and I'll improve."

            ok * 10 >= n * 9 ->
                "Sharp today. I understood $ok of your last $n requests."

            else ->
                "Doing fine. I understood $ok of your last $n requests."
        }
    }

    private fun aboutMe(ctx: Context): String {

        val p = prefs(ctx)
        val total = p.getInt("total", 0)

        if (total < 5) {
            return "I don't know you well yet. We've only talked $total times. Keep talking and teaching me."
        }

        val first = p.getLong("first", 0L)
        val days =
            maxOf(1L, (System.currentTimeMillis() - first) / DAY_MS)

        val parts = mutableListOf("We've talked $total times over $days days.")

        val top = topCommands(ctx, 3)
        if (top.isNotEmpty()) {
            parts.add("You most often say: " + top.joinToString(", ") + ".")
        }

        habit(ctx)?.let {
            parts.add("Around this hour you usually say $it.")
        }

        parts.add(
            "I know ${Brain.map("nick").size} nicknames, " +
                "${Brain.map("slang").size} slang phrases and " +
                "${Brain.map("fact").size} facts about your world."
        )

        return parts.joinToString(" ")
    }

    private fun noHabitLine(ctx: Context): String {
        val total = prefs(ctx).getInt("total", 0)
        return if (total < 20) {
            "I haven't seen a pattern yet. Give me a few more days with you."
        } else {
            "Nothing stands out around this time. Tell me what you'd like."
        }
    }

    private fun lastLine(ctx: Context): String {
        val last = events(ctx).lastOrNull { it.ok && meaningful(it.text) }
            ?: return "You haven't asked me anything yet."
        return "Your last request was: ${last.text}."
    }

    private fun selfLine(ctx: Context): String {
        val total = prefs(ctx).getInt("total", 0)
        return "I'm not alive, and I won't pretend to be. But I do have a memory that is only mine. " +
            "I've heard $total of your requests, and I change how I answer as I learn from you."
    }
}
