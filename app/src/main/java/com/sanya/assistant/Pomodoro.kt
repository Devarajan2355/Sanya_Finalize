package com.sanya.assistant

import android.content.Context

/**
 * Pomodoro: focus sessions with breaks, tied to your study log and routines.
 *
 *   start pomodoro / start pomodoro for japanese / start focus mode
 *   stop pomodoro            pomodoro status (or: how long is left in my pomodoro)
 *   skip break
 *   set pomodoro focus to 50 minutes     set pomodoro break to 10 minutes
 *   set pomodoro long break to 20 minutes
 *
 * The cycle:  focus, short break, focus, short break, focus, short break, focus, LONG break, ...
 * Defaults are 25 / 5 / 15 minutes and 4 focus sessions before the long break.
 * Change them in the Brain file (Settings section):
 *
 *   setting | pomodoro focus = 25
 *   setting | pomodoro break = 5
 *   setting | pomodoro long break = 15
 *   setting | pomodoro cycles = 4        focus sessions before the long break
 *   setting | pomodoro sessions = 8      she stops by herself after this many focus sessions
 *
 * LINKED TO STUDY AND ROUTINES: every finished focus session is logged with Study.log
 * (source "pomodoro"), so it counts toward a routine activity of the same name and can
 * mark it done. Stopping early logs the part you did if it was 5 minutes or more.
 *
 * Nothing here runs on a timer of its own. The state is just the start time and the
 * settings, and the current phase is worked out from the clock, so it survives restarts.
 * BubbleService calls due() once a minute: that logs finished sessions and returns what
 * she should say at the change of phase (or null).
 */
object Pomodoro {

    enum class Phase { FOCUS, SHORT_BREAK, LONG_BREAK }

    /** The settings are copied in at the start, so changing them mid-run does not shift the clock. */
    data class State(
        val task: String,
        val startedAt: Long,
        val focus: Int,
        val short: Int,
        val long: Int,
        val cycles: Int
    )

    /** Where the clock says you are. [session] counts focus sessions from 1 across the whole run. */
    data class Now(
        val phase: Phase,
        val index: Int,
        val start: Long,
        val end: Long,
        val session: Int
    )

    private const val PREFS = "sanya_pomodoro"
    private const val MIN_MS = 60_000L
    private const val MIN_PARTIAL = 5          // minutes: a stopped session shorter than this is not logged

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------

    private fun setting(key: String, def: Int, range: IntRange): Int {
        val v = Brain.setting(key, def.toString()).trim().toIntOrNull() ?: def
        return if (v in range) v else def
    }

    fun focusMinutes(): Int = setting("pomodoro focus", 25, 1..180)
    fun shortMinutes(): Int = setting("pomodoro break", 5, 1..60)
    fun longMinutes(): Int = setting("pomodoro long break", 15, 1..90)
    fun cycles(): Int = setting("pomodoro cycles", 4, 1..12)
    fun maxSessions(): Int = setting("pomodoro sessions", 8, 1..24)

    // ---------------------------------------------------------------
    // State and the clock
    // ---------------------------------------------------------------

    fun state(ctx: Context): State? {

        val p = prefs(ctx)
        val start = p.getLong("start", 0L)

        if (start == 0L) return null

        return State(
            p.getString("task", "study") ?: "study",
            start,
            p.getInt("focus", 25),
            p.getInt("short", 5),
            p.getInt("long", 15),
            p.getInt("cycles", 4)
        )
    }

    fun running(ctx: Context): Boolean = state(ctx) != null

    // Phase k of the run: even numbers are focus, odd numbers are breaks,
    // and the last break of every round of [cycles] sessions is the long one.
    private fun isFocus(k: Int) = k % 2 == 0

    private fun isLong(s: State, k: Int) = !isFocus(k) && k % (2 * s.cycles) == 2 * s.cycles - 1

    private fun minutesOf(s: State, k: Int): Int =
        if (isFocus(k)) s.focus else if (isLong(s, k)) s.long else s.short

    private fun phaseOf(s: State, k: Int): Phase =
        if (isFocus(k)) Phase.FOCUS else if (isLong(s, k)) Phase.LONG_BREAK else Phase.SHORT_BREAK

    private fun endOfPhase(s: State, k: Int): Long {
        var t = s.startedAt
        for (i in 0..k) t += minutesOf(s, i) * MIN_MS
        return t
    }

    fun locate(s: State, nowMs: Long): Now {

        var k = 0
        var begin = s.startedAt

        while (true) {

            val end = begin + minutesOf(s, k) * MIN_MS

            if (nowMs < end || k > 5000) {
                val session = if (isFocus(k)) k / 2 + 1 else (k + 1) / 2
                return Now(phaseOf(s, k), k, begin, end, session)
            }

            begin = end
            k++
        }
    }

    // Focus sessions fully finished by the time the clock is in phase [index].
    private fun finishedSessions(index: Int) = (index + 1) / 2

    /** True once the last focus session of the run has finished. */
    fun isOver(ctx: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val s = state(ctx) ?: return false
        return finishedSessions(locate(s, nowMs).index) >= maxSessions()
    }

    private fun two(n: Long) = n.toString().padStart(2, '0')

    private fun roundOf(s: State, session: Int) = ((session - 1) % s.cycles) + 1

    private fun phaseWord(p: Phase) = when (p) {
        Phase.FOCUS -> "Focus"
        Phase.SHORT_BREAK -> "Break"
        Phase.LONG_BREAK -> "Long break"
    }

    /** For screens: "Focus 12:30 left, japanese, session 2 of 4". Empty if none is running. */
    fun statusLine(ctx: Context, nowMs: Long = System.currentTimeMillis()): String {

        val s = state(ctx) ?: return ""
        val cur = locate(s, nowMs)
        val left = maxOf(0L, (cur.end - nowMs) / 1000)

        return phaseWord(cur.phase) + " " + two(left / 60) + ":" + two(left % 60) + " left, " +
            s.task + ", session " + roundOf(s, cur.session) + " of " + s.cycles
    }

    // ---------------------------------------------------------------
    // Logging finished sessions
    // ---------------------------------------------------------------

    // Logs every focus session that has finished and is not logged yet. Returns how many.
    private fun settle(ctx: Context, s: State, cur: Now): Int {

        val p = prefs(ctx)
        val done = minOf(finishedSessions(cur.index), maxSessions())
        val logged = p.getInt("logged", 0)

        if (done <= logged) return 0

        for (n in logged until done) {
            Study.log(ctx, s.task, s.focus, endOfPhase(s, 2 * n), "pomodoro")
        }

        p.edit().putInt("logged", done).apply()

        return done - logged
    }

    private fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        SanyaWidget.refresh(ctx)
    }

    // ---------------------------------------------------------------
    // Start, stop, skip, status
    // ---------------------------------------------------------------

    fun start(ctx: Context, name: String): String {

        if (running(ctx)) {
            return "A pomodoro is already running. " + status(ctx) + " Say stop pomodoro to end it."
        }

        if (Study.running(ctx) != null) {
            return "A study timer is running. Say stop timer first, then start the pomodoro."
        }

        val task = Study.resolve(name)
        val f = focusMinutes()
        val sh = shortMinutes()
        val lg = longMinutes()
        val cy = cycles()

        prefs(ctx).edit()
            .putLong("start", System.currentTimeMillis())
            .putString("task", task)
            .putInt("focus", f)
            .putInt("short", sh)
            .putInt("long", lg)
            .putInt("cycles", cy)
            .putInt("logged", 0)
            .putInt("announced", 0)
            .apply()

        SanyaWidget.refresh(ctx)

        return "Pomodoro started for $task. Focus for ${Num.durText(f)}, then a ${Num.durText(sh)} break. " +
            "After $cy sessions you get a ${Num.durText(lg)} break."
    }

    fun stop(ctx: Context): String {

        val s = state(ctx) ?: return "No pomodoro is running."

        val now = System.currentTimeMillis()
        val cur = locate(s, now)

        settle(ctx, s, cur)

        var minutes = prefs(ctx).getInt("logged", 0) * s.focus
        var partial = 0

        // Stopped in the middle of a focus session: keep the part you did if it was long enough.
        if (cur.phase == Phase.FOCUS && finishedSessions(cur.index) < maxSessions()) {
            val done = ((now - cur.start) / MIN_MS).toInt()
            if (done >= MIN_PARTIAL) {
                Study.log(ctx, s.task, done, now, "pomodoro")
                partial = done
            }
        }

        clear(ctx)

        minutes += partial

        return if (minutes == 0) {
            "Pomodoro stopped. Nothing was long enough to log."
        } else {
            "Pomodoro stopped. Logged ${Num.durText(minutes)} on ${s.task}."
        }
    }

    fun skipBreak(ctx: Context): String {

        val s = state(ctx) ?: return "No pomodoro is running."

        val now = System.currentTimeMillis()
        val cur = locate(s, now)

        if (cur.phase == Phase.FOCUS) return "You're in a focus session, not a break."

        // Pull the start time back by what is left of the break, so the next focus starts now.
        val shifted = s.copy(startedAt = s.startedAt - (cur.end - now))
        val next = locate(shifted, now)

        prefs(ctx).edit()
            .putLong("start", shifted.startedAt)
            .putInt("announced", next.index)
            .apply()

        SanyaWidget.refresh(ctx)

        return "Skipped the break. Focus on ${s.task} for ${Num.durText(s.focus)}."
    }

    fun status(ctx: Context): String {

        val s = state(ctx) ?: return "No pomodoro is running. Say start pomodoro."

        val now = System.currentTimeMillis()
        val cur = locate(s, now)
        val mins = ((cur.end - now + MIN_MS - 1) / MIN_MS).toInt()
        val left = if (mins <= 0) "less than a minute" else Num.durText(mins)
        val what = if (cur.phase == Phase.FOCUS) "Focus on ${s.task}" else phaseWord(cur.phase)

        return "$what: $left left. Session ${roundOf(s, cur.session)} of ${s.cycles}."
    }

    private fun setLength(which: String, minutes: Int): String {

        val key = when (which) {
            "focus" -> "pomodoro focus"
            "break" -> "pomodoro break"
            else -> "pomodoro long break"
        }

        Brain.put("setting", key, minutes.toString())

        return "Okay. Pomodoro $which is now ${Num.durText(minutes)}. It applies from the next pomodoro."
    }

    /**
     * Called by BubbleService once a minute. Logs finished focus sessions and returns what she
     * should say now (a new phase began, or the run is over), or null.
     */
    fun due(ctx: Context, nowMs: Long = System.currentTimeMillis()): String? {

        val s = state(ctx) ?: return null
        val cur = locate(s, nowMs)
        val max = maxSessions()

        // The last focus session is finished: log everything and stop.
        if (finishedSessions(cur.index) >= max) {
            settle(ctx, s, cur)
            clear(ctx)
            return "Pomodoro finished. $max focus sessions, ${Num.durText(max * s.focus)} on ${s.task}. Well done."
        }

        settle(ctx, s, cur)

        val p = prefs(ctx)

        if (cur.index <= p.getInt("announced", 0)) return null

        p.edit().putInt("announced", cur.index).apply()

        val round = roundOf(s, cur.session)

        return when (cur.phase) {
            Phase.FOCUS ->
                "Break over. Focus on ${s.task} for ${Num.durText(s.focus)}. Session $round of ${s.cycles}."
            Phase.SHORT_BREAK ->
                "Good work. That was session $round of ${s.cycles}. Take a ${Num.durText(s.short)} break."
            Phase.LONG_BREAK ->
                "Round complete, ${s.cycles} sessions done. Take a ${Num.durText(s.long)} break."
        }
    }

    // ---------------------------------------------------------------
    // Voice commands
    // ---------------------------------------------------------------

    sealed class Cmd {
        data class Start(val task: String) : Cmd()
        data class Length(val which: String, val minutes: Int) : Cmd()
        data class Say(val text: String) : Cmd()
        object Stop : Cmd()
        object Status : Cmd()
        object Skip : Cmd()
    }

    private const val MODE = "(?:pomodoro|pomodoros|focus mode)"

    private val START_A = Regex(
        """^(?:start|begin|run|turn on) (?:a |an |the |my )?$MODE(?: (?:timer|session|mode))?(?: (?:for|on|with) (.+))?$"""
    )
    private val START_B = Regex("""^$MODE(?: (?:for|on) (.+))?$""")
    private val STOP = Regex(
        """^(?:stop|end|finish|cancel|quit|turn off) (?:the |my )?$MODE(?: (?:timer|session|mode))?$"""
    )
    private val STATUS = Regex(
        """^$MODE (?:status|time left|progress)$|^how (?:much time|long) (?:is )?left in (?:my |the )?(?:pomodoro|focus session)$|^(?:what is|what's|whats) my pomodoro(?: status)?$"""
    )
    private val SKIP = Regex("""^(?:skip|end) (?:the |my |this )?(?:pomodoro )?break$""")
    private val LENGTH = Regex(
        """^set (?:the |my )?pomodoros? (?:(focus|long break|break) )?(?:length |time |duration )?(?:to|as|at) (.+)$"""
    )

    private fun norm(raw: String): String =
        raw.lowercase()
            .replace(Regex("""[^a-z0-9' ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun tidy(s: String): String =
        s.trim().removePrefix("my ").removePrefix("the ").removePrefix("some ").removePrefix("a ").trim()

    private fun startCmd(what: String): Cmd {

        // "start pomodoro for 30 minutes": the lengths are settings, not part of the start command.
        if (what.isNotEmpty() && Num.parseDuration(what) != null) {
            return Cmd.Say("I set the pomodoro lengths separately. Say, for example: set pomodoro focus to 50 minutes.")
        }

        return Cmd.Start(tidy(what).ifEmpty { "study" })
    }

    /** Is [raw] a pomodoro command? */
    fun parse(raw: String): Cmd? {

        val t = norm(raw)
        if (t.isEmpty()) return null

        if (STOP.matches(t)) return Cmd.Stop
        if (STATUS.matches(t)) return Cmd.Status
        if (SKIP.matches(t)) return Cmd.Skip

        LENGTH.matchEntire(t)?.let { m ->

            val which = m.groupValues[1].ifEmpty { "focus" }
            val text = m.groupValues[2].trim()
            val minutes = Num.parseDuration(text) ?: text.toIntOrNull()
            val max = when (which) {
                "focus" -> 180
                "break" -> 60
                else -> 90
            }

            if (minutes == null || minutes !in 1..max) {
                return Cmd.Say(
                    "I didn't catch the length. Say it like: set pomodoro $which to " +
                        (if (which == "focus") "25" else "5") + " minutes."
                )
            }

            return Cmd.Length(which, minutes)
        }

        START_A.matchEntire(t)?.let { return startCmd(it.groupValues[1]) }
        START_B.matchEntire(t)?.let { return startCmd(it.groupValues[1]) }

        return null
    }

    /** Does what the command says and returns what she should say. */
    fun run(ctx: Context, c: Cmd): String = when (c) {
        is Cmd.Start -> start(ctx, c.task)
        is Cmd.Length -> setLength(c.which, c.minutes)
        is Cmd.Say -> c.text
        Cmd.Stop -> stop(ctx)
        Cmd.Status -> status(ctx)
        Cmd.Skip -> skipBreak(ctx)
    }
}
