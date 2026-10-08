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
        """^(?:again|hello|hi|hey|hai|thanks|thank you|yes|no|ok|okay|stop|exit|good morning|good afternoon|good evening|good night|yes\s?\w*)$"""
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

    // ---- settings by voice ----
    private val PROACTIVE_OFF = Regex(
        """^(?:stop (?:suggesting|being proactive|talking first)|proactive off|turn off proactive|be quiet|don't talk first)$"""
    )
    private val PROACTIVE_ON = Regex(
        """^(?:proactive on|turn on proactive|start suggesting|be proactive)$"""
    )
    private val BRIEF_OFF = Regex(
        """^(?:stop|cancel|turn off) (?:my )?morning brief$"""
    )
    private val BRIEF_SET = Regex(
        """^(?:set )?(?:my )?morning brief (?:at|for|to) (.+)$"""
    )
    private val CONFIRM_OFF = Regex(
        """^(?:stop asking (?:me )?(?:before(?: calling| deleting| clearing)?|to confirm|for confirmation)|don't ask (?:me )?before calling|confirm off|no confirmation)$"""
    )
    private val CONFIRM_ON = Regex(
        """^(?:ask (?:me )?before calling|confirm on|ask for confirmation)$"""
    )

    // ---- people and dates ----
    private val REL_GROUPS: List<List<String>> = listOf(
        listOf("mother", "mom", "mum", "mummy", "amma"),
        listOf("father", "dad", "daddy", "appa"),
        listOf("brother", "anna", "thambi"),
        listOf("sister", "akka", "thangai", "sis"),
        listOf("grandmother", "grandma", "paati"),
        listOf("grandfather", "grandpa", "thatha"),
        listOf("friend"), listOf("wife"), listOf("husband"),
        listOf("son"), listOf("daughter"), listOf("uncle"), listOf("aunt"),
        listOf("boss"), listOf("teacher"), listOf("guide"), listOf("cousin"),
        listOf("boyfriend"), listOf("girlfriend"), listOf("partner"),
        listOf("colleague"), listOf("manager"), listOf("neighbour", "neighbor")
    )

    private val REL_WORDS: String = REL_GROUPS.flatten().joinToString("|")

    private val NOT_NAMES = setOf(
        "who", "what", "where", "which", "how", "when", "why",
        "that", "this", "it", "there", "here", "he", "she", "they"
    )

    private val PERSON_A = Regex("""^([a-z][a-z ]{1,25}?) is my ($REL_WORDS)$""")
    private val PERSON_B = Regex("""^my ($REL_WORDS) is (?:called |named )?([a-z][a-z ]{1,25})$""")
    private val DATE_SET = Regex(
        """^(my|[a-z][a-z ]{0,24}?)(?:'s| s)? (birthday|anniversary) is (?:on )?(.+)$"""
    )
    private val DATE_QUERY = Regex(
        """^when is (my|[a-z][a-z ]{0,24}?)(?:'s| s)? (birthday|anniversary)$"""
    )
    private val WHO_REL = Regex("""^who is my ($REL_WORDS)$""")
    private val WHO_IS = Regex("""^who is ([a-z][a-z ]{1,25})$""")
    private val WORLD_LIST = Regex(
        """^(?:what dates do you know|list my dates|who do you know|who are my people|list my people|show my people)$"""
    )
    private val WORLD_FORGET = Regex("""^forget (?:person|contact|date) ([a-z][a-z ]{1,30})$""")

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
            PROACTIVE_OFF.matches(t) -> "proactive_off"
            PROACTIVE_ON.matches(t) -> "proactive_on"
            BRIEF_OFF.matches(t) -> "brief_off"
            BRIEF_SET.matches(t) -> "brief_set"
            CONFIRM_OFF.matches(t) -> "confirm_off"
            CONFIRM_ON.matches(t) -> "confirm_on"
            DATE_SET.matches(t) -> "date_set"
            DATE_QUERY.matches(t) -> "date_query"
            isPersonSet(t) -> "person_set"
            isPersonSetB(t) -> "person_set"
            WHO_REL.matches(t) -> "who_rel"
            WORLD_LIST.matches(t) -> "world_list"
            WORLD_FORGET.matches(t) -> "world_forget"
            isKnownPerson(t) -> "who_is"
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
            "proactive_off" -> {
                Brain.put("setting", "proactive", "off")
                "Okay. I won't speak first anymore. Say proactive on to bring it back."
            }
            "proactive_on" -> {
                Brain.put("setting", "proactive", "on")
                "Okay. I'll speak up when something matters, like a birthday, low battery or a habit."
            }
            "brief_off" -> {
                Brain.put("setting", "morning brief", "off")
                "Morning brief is off."
            }
            "brief_set" -> briefSet(raw)
            "confirm_off" -> {
                Brain.put("setting", "confirm actions", "off")
                "Okay. I won't ask before calling or clearing your lists."
            }
            "confirm_on" -> {
                Brain.put("setting", "confirm actions", "on")
                "Okay. I'll check with you before calling or clearing your lists."
            }
            "date_set" -> dateSet(norm(raw))
            "date_query" -> dateQuery(norm(raw))
            "person_set" -> personSet(norm(raw))
            "who_rel" -> whoRel(norm(raw))
            "world_list" -> worldList()
            "world_forget" -> worldForget(norm(raw))
            "who_is" -> whoIs(norm(raw))
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

    /** Did she already hear this exact request within the last withinMs? */
    fun didRecently(ctx: Context, text: String, withinMs: Long): Boolean {
        val cutoff = System.currentTimeMillis() - withinMs
        return events(ctx).any { it.text == text && it.ts >= cutoff }
    }

    // ---------------------------------------------------------------
    // Her knowledge of your world: people and dates
    // Stored in the Brain file, so you can read and edit it:
    //   person | ammu = mother
    //   date   | ammu birthday = 12 march
    // ---------------------------------------------------------------

    private fun cap(s: String): String =
        s.split(" ").joinToString(" ") { w ->
            w.replaceFirstChar { c -> c.uppercase() }
        }

    private fun canonRel(word: String): String =
        REL_GROUPS.firstOrNull { word in it }?.first() ?: word

    private fun relSynonyms(canon: String): List<String> =
        REL_GROUPS.firstOrNull { it.first() == canon } ?: listOf(canon)

    private fun two(n: Int): String = n.toString().padStart(2, '0')

    // Words that are clearly not a person's name ("my friend is late", "my friend is calling me").
    private val NON_NAME_WORDS = setOf(
        "here", "there", "late", "busy", "fine", "good", "sick", "home", "outside",
        "ready", "back", "okay", "well", "angry", "happy", "sad", "tired", "me", "you"
    )

    private fun looksLikeName(s: String): Boolean {
        val w = s.trim().split(" ").filter { it.isNotEmpty() }
        return w.isNotEmpty() &&
            w.size <= 2 &&
            w.none { it.endsWith("ing") || it in NOT_NAMES || it in NON_NAME_WORDS }
    }

    private fun isPersonSet(t: String): Boolean {
        val m = PERSON_A.matchEntire(t) ?: return false
        return looksLikeName(m.groupValues[1])
    }

    private fun isPersonSetB(t: String): Boolean {
        val m = PERSON_B.matchEntire(t) ?: return false
        return looksLikeName(m.groupValues[2])
    }

    private fun isKnownPerson(t: String): Boolean {
        val m = WHO_IS.matchEntire(t) ?: return false
        val name = Brain.clean(m.groupValues[1])
        return Brain.map("person").containsKey(name)
    }

    private fun possessive(who: String): String =
        if (who == "my") "Your" else cap(who) + "'s"

    // ---- numbers spoken as words ("twelve", "twenty first", "seven thirty") ----

    private val UNITS: Map<String, Int> = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19,
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
        "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10,
        "eleventh" to 11, "twelfth" to 12, "thirteenth" to 13, "fourteenth" to 14,
        "fifteenth" to 15, "sixteenth" to 16, "seventeenth" to 17,
        "eighteenth" to 18, "nineteenth" to 19
    )

    private val TENS: Map<String, Int> = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "twentieth" to 20, "thirtieth" to 30
    )

    /** "twenty first of march" -> "21 of march" */
    fun numWords(s: String): String {

        val toks = s.split(" ").filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var i = 0

        while (i < toks.size) {

            val w = toks[i]
            val tens = TENS[w]
            val unit = UNITS[w]

            if (tens != null) {
                val next = if (i + 1 < toks.size) UNITS[toks[i + 1]] else null
                if (next != null && next in 1..9) {
                    out.add((tens + next).toString())
                    i += 2
                } else {
                    out.add(tens.toString())
                    i += 1
                }
            } else if (unit != null) {
                out.add(unit.toString())
                i += 1
            } else {
                out.add(w)
                i += 1
            }
        }

        return out.joinToString(" ")
    }

    // ---- clock times ("7 30", "seven thirty pm") -> "HH:MM" ----

    fun parseClock(text: String): String? {

        val t = numWords(text.lowercase())
            .replace(Regex("""\b([ap])\.? ?m\.?\b"""), "$1m")
            .replace(Regex("""[^a-z0-9: ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val m = Regex("""^(\d{1,2})(?:[: ](\d{2}))?(?: ?(am|pm))?$""")
            .find(t) ?: return null

        var h = m.groupValues[1].toInt()
        val min = if (m.groupValues[2].isEmpty()) 0 else m.groupValues[2].toInt()
        val ap = m.groupValues[3]

        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0

        if (h > 23 || min > 59) return null

        return two(h) + ":" + two(min)
    }

    private fun briefSet(raw: String): String {

        val m = BRIEF_SET.matchEntire(norm(raw))
            ?: return "I didn't catch the time."

        val clock = parseClock(m.groupValues[1])
            ?: return "I didn't catch the time. Say it like: morning brief at 7 30."

        Brain.put("setting", "morning brief", clock)
        Brain.put("setting", "proactive", "on")

        return "Okay. I'll give you a morning brief at $clock when you're using your phone."
    }

    // ---- dates ----

    private val MONTHS = listOf(
        "january", "february", "march", "april", "may", "june", "july",
        "august", "september", "october", "november", "december"
    )

    private val MAX_DAYS = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    /** "12 march", "12th of march", "march 12", "twelve march" -> (12, 3) */
    fun parseDM(text: String): Pair<Int, Int>? {

        val t = numWords(text.lowercase())
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .removePrefix("the ")

        val a = Regex("""^(\d{1,2})(?:st|nd|rd|th)?(?: of)? ([a-z]+)$""").find(t)
        val b = Regex("""^([a-z]+) (\d{1,2})(?:st|nd|rd|th)?$""").find(t)

        val parts: Pair<String, String> = when {
            a != null -> Pair(a.groupValues[1], a.groupValues[2])
            b != null -> Pair(b.groupValues[2], b.groupValues[1])
            else -> return null
        }

        val day = parts.first.toIntOrNull() ?: return null
        val name = parts.second

        val idx = MONTHS.indexOfFirst {
            it == name || (name.length >= 3 && it.startsWith(name))
        }

        if (idx < 0) return null
        if (day < 1 || day > MAX_DAYS[idx]) return null

        return Pair(day, idx + 1)
    }

    private fun daysUntil(day: Int, month: Int): Int {

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        fun at(y: Int): Calendar =
            Calendar.getInstance().apply {
                clear()
                set(y, month - 1, day, 0, 0, 0)
            }

        val year = today.get(Calendar.YEAR)
        var target = at(year)
        if (target.before(today)) target = at(year + 1)

        val diff = target.timeInMillis - today.timeInMillis
        return Math.round(diff / DAY_MS.toDouble()).toInt()
    }

    private fun dateLabel(key: String): String {
        val parts = key.split(" ")
        val event = parts.last()
        val who = parts.dropLast(1).joinToString(" ")
        return if (who == "my" || who.isEmpty()) "your $event" else cap(who) + "'s " + event
    }

    fun dateSentence(label: String, days: Int): String {
        val text = if (days == 0) "Today is $label." else "Tomorrow is $label."
        return if (days == 0 && label.startsWith("your birthday")) {
            "$text Happy birthday!"
        } else {
            text
        }
    }

    /** Saved dates that fall today or tomorrow: (label, days). */
    fun dueDates(): List<Pair<String, Int>> {

        val out = ArrayList<Pair<String, Int>>()

        for ((key, value) in Brain.map("date")) {
            val dm = parseDM(value) ?: continue
            val d = daysUntil(dm.first, dm.second)
            if (d <= 1) out.add(Pair(dateLabel(key), d))
        }

        return out
    }

    /** One sentence for the morning brief, or "". */
    fun upcomingLine(): String =
        dueDates().joinToString(" ") { (label, d) -> dateSentence(label, d) }

    // ---- people and relations ----

    /** "call my mother" -> "call ammu", using the people she knows. */
    fun resolveRelations(raw: String): String {

        val people = Brain.map("person")
        if (people.isEmpty()) return raw

        var out = raw

        for ((name, rel) in people) {
            val alts = relSynonyms(rel).joinToString("|") { Regex.escape(it) }
            val rx = Regex("""\bmy\s+(?:$alts)\b""", RegexOption.IGNORE_CASE)
            out = rx.replace(out, name)
        }

        return out
    }

    private fun personSet(t: String): String {

        val a = PERSON_A.matchEntire(t)
        val b = PERSON_B.matchEntire(t)

        val parts: Pair<String, String> = when {
            a != null -> Pair(a.groupValues[1], a.groupValues[2])
            b != null -> Pair(b.groupValues[2], b.groupValues[1])
            else -> return "I didn't catch that."
        }

        val name = Brain.clean(parts.first)
        val rel = canonRel(parts.second)

        if (name.isEmpty()) return "I didn't catch the name."

        Brain.put("person", name, rel)

        return "Got it. ${cap(name)} is your $rel. You can now say, call my $rel."
    }

    private fun whoRel(t: String): String {

        val m = WHO_REL.matchEntire(t) ?: return "I didn't catch that."
        val rel = canonRel(m.groupValues[1])

        val names = Brain.map("person")
            .filter { it.value == rel }
            .keys
            .map { cap(it) }

        return if (names.isEmpty()) {
            "I don't know who your $rel is yet. Tell me, like: ammu is my $rel."
        } else {
            "Your $rel is ${names.joinToString(" and ")}."
        }
    }

    private fun whoIs(t: String): String {

        val m = WHO_IS.matchEntire(t) ?: return "I didn't catch that."
        val name = Brain.clean(m.groupValues[1])
        val rel = Brain.map("person")[name] ?: return "I don't know ${cap(name)} yet."

        return "${cap(name)} is your $rel."
    }

    private fun dateSet(t: String): String {

        val m = DATE_SET.matchEntire(t) ?: return "I didn't catch that."

        val who = Brain.clean(m.groupValues[1])
        val event = m.groupValues[2]

        val dm = parseDM(m.groupValues[3])
            ?: return "I didn't catch the date. Say it like: twelve March."

        val month = MONTHS[dm.second - 1]

        Brain.put("date", "$who $event", "${dm.first} $month")

        return "Got it. ${possessive(who)} $event is on ${dm.first} ${cap(month)}. " +
            "I'll remind you the day before."
    }

    private fun dateQuery(t: String): String {

        val m = DATE_QUERY.matchEntire(t) ?: return "I didn't catch that."

        val who = Brain.clean(m.groupValues[1])
        val event = m.groupValues[2]

        val value = Brain.map("date")["$who $event"]
            ?: return "I don't know ${possessive(who).lowercase()} $event yet. " +
                "Tell me, like: ${if (who == "my") "my" else who + "'s"} $event is 12 March."

        val dm = parseDM(value)
            ?: return "I have ${possessive(who).lowercase()} $event saved as $value, but I can't read that date."

        val d = daysUntil(dm.first, dm.second)
        val when_ = when (d) {
            0 -> "today"
            1 -> "tomorrow"
            else -> "in $d days"
        }

        return "${possessive(who)} $event is on ${dm.first} ${cap(MONTHS[dm.second - 1])}, $when_."
    }

    private fun worldList(): String {

        val people = Brain.map("person")
        val dates = Brain.map("date")

        if (people.isEmpty() && dates.isEmpty()) {
            return "I don't know your people or dates yet. Tell me, like: ammu is my mother, " +
                "or: my birthday is 12 March."
        }

        val parts = ArrayList<String>()

        if (people.isNotEmpty()) {
            parts.add(
                "People: " + people.entries.joinToString(", ") {
                    cap(it.key) + " (" + it.value + ")"
                } + "."
            )
        }

        if (dates.isNotEmpty()) {
            parts.add(
                "Dates: " + dates.entries.joinToString(", ") {
                    dateLabel(it.key) + " on " + it.value
                } + "."
            )
        }

        return parts.joinToString(" ")
    }

    private fun worldForget(t: String): String {

        val m = WORLD_FORGET.matchEntire(t) ?: return "I didn't catch that."
        val name = Brain.clean(m.groupValues[1])

        Brain.drop("person", name)
        Brain.drop("date", name)

        return "Okay, I've forgotten $name."
    }
}
