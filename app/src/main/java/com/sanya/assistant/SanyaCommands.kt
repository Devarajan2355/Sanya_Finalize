package com.sanya.assistant

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.MediaStore
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.KeyEvent
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.abs
import kotlin.math.floor

sealed class Step {
    data class OpenApp(val name: String) : Step()
    data class Call(val contact: String, val video: Boolean, val whatsapp: Boolean) : Step()
    data class Sms(val contact: String, val text: String) : Step()
    data class Timer(val seconds: Int) : Step()
    data class Alarm(val hour: Int, val min: Int) : Step()
    data class Remind(val text: String, val seconds: Int) : Step()
    data class Event(val title: String, val start: Long) : Step()
    data class Toggle(val what: String, val on: Boolean) : Step()
    data class Volume(val up: Boolean) : Step()
    data class Media(val key: String) : Step()
    object Photo : Step()
    data class Search(val q: String, val youtube: Boolean) : Step()
    data class Navigate(val place: String) : Step()
    data class Info(val kind: String) : Step()
    data class AddTodo(val text: String) : Step()
    data class RemoveTodo(val text: String) : Step()
    object ReadTodos : Step()
    object ClearTodos : Step()
    data class Note(val text: String) : Step()
    object ReadNotes : Step()
    object ClearNotes : Step()
    data class Nick(val nicks: List<String>, val real: String) : Step()
    data class Teach(val phrases: List<String>, val cmd: String) : Step()
    data class WordMap(val word: String, val meaning: String) : Step()
    data class Ignore(val word: String) : Step()
    data class Mean(val cmd: String) : Step()
    data class Remember(val key: String, val value: String) : Step()
    data class Recall(val key: String) : Step()
    data class Copy(val key: String) : Step()
    data class Forget(val key: String) : Step()
    data class SetName(val name: String) : Step()
    object Brief : Step()
    object Stats : Step()
    object OpenBrain : Step()
    object Again : Step()
    data class Reply(val text: String) : Step()
    data class Unknown(val raw: String) : Step()
}

object CommandParser {
    private const val VERBS = "open|launch|start|run|call|video|voice|whatsapp|set|turn|search|play|watch|google|find|navigate|message|msg|text|sms|add|put|remove|delete|clear|read|show|what|take|volume|note|remember|remind|copy|forget|switch|ring|dial|tell|nickname|next|previous|pause|resume|skip|schedule|calculate|brief|stop|good|hello|hi|flip|roll|again|ignore|when|check|am"
    // Splits at "and"/"then" only when a new command follows, so "add milk and eggs" stays whole.
    private val splitter = Regex("\\b(?:and then|after that|then|and)\\s+(?=(?:$VERBS)\\b)")
    private val builtinFill = Regex("\\b(?:please|pls|plz|kindly|just|quickly|i want to|i wanna|i need to|lets|let's|for me)\\b")
    private val nums = listOf("twenty five" to 25, "thirty five" to 35, "forty five" to 45, "one" to 1, "two" to 2,
        "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "fifteen" to 15, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60)
    private val jokes = listOf(
        "Why do programmers prefer dark mode? Because light attracts bugs.",
        "I told my phone I needed space. It opened the gallery.",
        "Why did the developer go broke? He used up all his cache.")
    private val talk = listOf(
        Regex("^(?:hello|hi|hey|hai|hii|yo)$") to "Hello{n}! How can I help?",
        Regex("^vanakkam") to "Vanakkam{n}! How can I help?",
        Regex("good (?:evening|afternoon)") to "Good evening{n}!",
        Regex("good night") to "Good night{n}, sleep well",
        Regex("how are you|how r u|how are u|what.?s up|wassup|\\bsup\\b") to "I'm doing great, thanks for asking",
        Regex("\\bthank|\\bthx") to "You're welcome{n}",
        Regex("who are you|your name") to "I'm Sanya, your personal assistant",
        Regex("who am i|what.?s my name|what is my name") to "Your name is {name}",
        Regex("what can you do|^help$") to "I can open apps, call and message, set timers, alarms and reminders, play music, search, navigate, calculate, keep notes and secrets, and learn your nicknames and slang. Say show my brain to edit what I know.",
        Regex("love you") to "That's sweet. I'm always here to help")

    private fun digits(s: String): String {
        if (!Regex("timer|alarm|remind|minute|hour|second|schedule|event|meeting|appointment|percent|plus|minus|times|divided|multiplied").containsMatchIn(s)) return s
        var r = s.replace("half an hour", "30 minutes").replace(Regex("\\b(?:an|a) (hour|minute)")) { "1 " + it.groupValues[1] }
        for ((w, n) in nums) r = r.replace(Regex("\\b$w\\b"), n.toString())
        return r
    }

    private fun normalize(raw: String): String {
        var s = raw.lowercase().replace(Regex("[,!?]"), " ").trim()
        // Tanglish word order: "youtube open pannu", "ravi ku call pannu", "comedy songs podu"
        s = Regex("^(.+?)\\s+(?:ku\\s+)?(open|call|search|play)\\s+(?:pannu|pannunga|pannungal|seiyu)$")
            .replace(s) { it.groupValues[2] + " " + it.groupValues[1] }
        s = Regex("^(.+?)\\s+(?:podu|poduga|poodu)$").replace(s) { "play " + it.groupValues[1] }
        s = s.replace(Regex("^(?:hello|hi|hey|hai|hii|yo)\\s+"), "")
        s = s.replace(Regex("^(?:can|could|would|will)\\s+you\\s+"), "")
        s = builtinFill.replace(s, " ")
        val extra = Brain.words("filler")
        if (extra.isNotEmpty()) s = Regex("\\b(?:" + extra.joinToString("|") { Regex.escape(it) } + ")\\b").replace(s, " ")
        for ((k, v) in Brain.map("word")) s = s.replace(Regex("\\b" + Regex.escape(k) + "\\b"), v)
        s = s.replace(Regex("\\b(?:switch|put)\\s+(on|off)\\b")) { "turn " + it.groupValues[1] }
        s = s.replace(Regex("\\benable\\b"), "turn on").replace(Regex("\\bdisable\\b"), "turn off")
        return digits(s).replace(Regex("\\s+"), " ").trim()
    }

    fun parse(sentence: String): List<Step> {
        val s = normalize(sentence)
        if (s.isEmpty()) return emptyList()
        whole(s)?.let { return listOf(it) }
        var wa = false
        return s.split(splitter).map { it.trim() }.filter { it.isNotEmpty() }.map { p ->
            val st = one(p, wa)
            if (st is Step.OpenApp && st.name.contains("whatsapp")) wa = true
            st
        }
    }

    private fun parts(s: String) = s.split(Regex("\\s+(?:or|and)\\s+|,")).map { it.trim() }.filter { it.isNotEmpty() }
    private fun secs(n: Int, u: String) = if (u.startsWith("h")) n * 3600 else if (u.startsWith("m")) n * 60 else n
    private fun fmt(d: Double) = if (d == floor(d) && abs(d) < 1e12) d.toLong().toString() else "%.2f".format(d)

    // Sentences that must stay in one piece (they may contain "and"/"then").
    private fun whole(s: String): Step? {
        Regex("^(?:when i say|if i say|whenever i say)\\s+(.+?)\\s+i mean\\s+(.+)").find(s)?.let {
            return Step.WordMap(it.groupValues[1], it.groupValues[2]) }
        Regex("^(?:when i say|if i say|whenever i say)\\s+(.+?)\\s+(?:then\\s+)?(?:do|run)\\s+(.+)").find(s)?.let {
            return Step.Teach(parts(it.groupValues[1]), it.groupValues[2]) }
        Regex("^(?:that|it|this)\\s+means\\s+(.+)|^i meant\\s+(.+)").find(s)?.let {
            return Step.Mean(it.groupValues[1].ifEmpty { it.groupValues[2] }) }
        Regex("^(?:set\\s+|give\\s+|add\\s+)?(?:another\\s+)?nickname\\s+(.+?)\\s+(?:for|to|of)\\s+(.+)").find(s)?.let {
            return Step.Nick(parts(it.groupValues[1]), it.groupValues[2]) }
        Regex("^(.+?)\\s+is\\s+(?:my\\s+)?nickname\\s+(?:for|of)\\s+(.+)").find(s)?.let {
            return Step.Nick(parts(it.groupValues[1]), it.groupValues[2]) }
        Regex("^(?:call me|my name is|you can call me)\\s+(.+)").find(s)?.let { return Step.SetName(it.groupValues[1]) }
        Regex("^ignore\\s+(?:the\\s+)?(?:word\\s+)?(.+)").find(s)?.let { return Step.Ignore(it.groupValues[1]) }
        Regex("^remember\\s+(?:that\\s+)?(.+?)\\s+(?:is|are)\\s+(.+)").find(s)?.let {
            return Step.Remember(it.groupValues[1], it.groupValues[2]) }
        Regex("^remember\\s+(?:to\\s+|that\\s+)?(.+)").find(s)?.let { return Step.Note(it.groupValues[1]) }
        Regex("^(?:take a |make a |add a |write a |write )?note(?: down)?(?: that)?\\s+(.+)").find(s)?.let {
            return Step.Note(it.groupValues[1]) }
        return null
    }

    private fun one(p: String, wa: Boolean): Step {
        if (Regex("^(?:again|repeat|do (?:that|it) again|same again|once more)$").matches(p)) return Step.Again
        if (Regex("(?:show|open|edit)\\s+(?:my\\s+|your\\s+)?brain").containsMatchIn(p)) return Step.OpenBrain
        if (Regex("what (?:did|have|do) you (?:learn|know)").containsMatchIn(p)) return Step.Stats
        if (Regex("brief me|briefing|what.?s my day|^good morning").containsMatchIn(p)) return Step.Brief

        for ((r, reply) in talk) if (r.containsMatchIn(p)) return Step.Reply(reply)
        if (p.contains("joke")) return Step.Reply(jokes.random())
        if (Regex("flip a coin|toss a coin").containsMatchIn(p)) return Step.Reply(if ((0..1).random() == 0) "Heads" else "Tails")
        if (Regex("roll a (?:dice|die)").containsMatchIn(p)) return Step.Reply("You rolled a ${(1..6).random()}")

        // Calculator
        Regex("^(?:calculate|what.?s|what is)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:percent|%)\\s*of\\s*(\\d+(?:\\.\\d+)?)$").find(p)?.let {
            val a = it.groupValues[1].toDouble(); val b = it.groupValues[2].toDouble()
            return Step.Reply("${fmt(a)} percent of ${fmt(b)} is ${fmt(a * b / 100)}")
        }
        Regex("^(?:calculate|calc|compute|what.?s|what is|how much is)?\\s*(\\d+(?:\\.\\d+)?)\\s*(plus|minus|times|multiplied by|x|into|divided by|over|\\+|-|\\*|×|/|÷)\\s*(\\d+(?:\\.\\d+)?)$").find(p)?.let {
            val a = it.groupValues[1].toDouble(); val b = it.groupValues[3].toDouble(); val op = it.groupValues[2]
            val plus = op == "plus" || op == "+"
            val minus = op == "minus" || op == "-"
            val div = op in listOf("divided by", "over", "/", "÷")
            if (div && b == 0.0) return Step.Reply("I can't divide by zero")
            val r = if (plus) a + b else if (minus) a - b else if (div) a / b else a * b
            val word = if (plus) "plus" else if (minus) "minus" else if (div) "divided by" else "times"
            return Step.Reply("${fmt(a)} $word ${fmt(b)} is ${fmt(r)}")
        }

        // Music (works with any player)
        if (Regex("^(?:next|skip)(?: song| track| video)?$").matches(p)) return Step.Media("next")
        if (Regex("^(?:previous|prev|last)(?: song| track)?$").matches(p)) return Step.Media("prev")
        if (Regex("^(?:pause|stop)(?: the)?(?: music| song| video| playing)?$").matches(p)) return Step.Media("pause")
        if (Regex("^(?:resume|continue|play)(?: the)?(?: music| song| video)?$").matches(p)) return Step.Media("play")

        Regex("^(?:whatsapp\\s+)?(?:(?:make|give|place)\\s+(?:a\\s+)?)?(?:(video|voice)\\s+)?(?:call|ring|dial|phone)\\s+(?:to\\s+)?(?:my\\s+)?(.+?)(?:\\s+(?:on|in|using|via|through)\\s+whatsapp)?$").find(p)?.let {
            val video = it.groupValues[1] == "video"
            return Step.Call(it.groupValues[2], video, wa || video || p.contains("whatsapp"))
        }
        Regex("^(?:send\\s+)?(?:a\\s+)?(?:sms|text|message|msg)\\s+(?:to\\s+)?(?:my\\s+)?(.+?)\\s+(?:saying|that says|say)\\s+(.+)").find(p)?.let {
            return Step.Sms(it.groupValues[1], it.groupValues[2])
        }
        Regex("^(?:open|launch|start|run|load)\\s+(?:the\\s+)?(.+)").find(p)?.let { return Step.OpenApp(it.groupValues[1]) }

        // Time tools
        Regex("remind me (?:to\\s+)?(.+?)\\s+in\\s+(\\d+)\\s*(second|sec|minute|min|hour|hr)").find(p)?.let {
            return Step.Remind(it.groupValues[1], secs(it.groupValues[2].toInt(), it.groupValues[3]))
        }
        Regex("remind me in\\s+(\\d+)\\s*(second|sec|minute|min|hour|hr)\\w*\\s+to\\s+(.+)").find(p)?.let {
            return Step.Remind(it.groupValues[3], secs(it.groupValues[1].toInt(), it.groupValues[2]))
        }
        Regex("(?:timer|countdown).*?(\\d+)\\s*(second|sec|minute|min|hour|hr)").find(p)?.let {
            return Step.Timer(secs(it.groupValues[1].toInt(), it.groupValues[2]))
        }
        Regex("(\\d+)\\s*(second|sec|minute|min|hour|hr)\\w*\\s+timer").find(p)?.let {
            return Step.Timer(secs(it.groupValues[1].toInt(), it.groupValues[2]))
        }
        Regex("(?:alarm|wake me up)\\s+(?:for\\s+|at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?").find(p)?.let {
            var h = it.groupValues[1].toInt()
            val m = it.groupValues[2].toIntOrNull() ?: 0
            val ap = it.groupValues[3]
            if (ap.startsWith("p") && h < 12) h += 12
            if (ap.startsWith("a") && h == 12) h = 0
            return Step.Alarm(h % 24, m)
        }
        Regex("^(?:schedule|add event|add meeting|create event|add appointment)\\s+(.+?)\\s+(?:on\\s+)?(today|tomorrow)?\\s*at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?$").find(p)?.let {
            val cal = Calendar.getInstance()
            if (it.groupValues[2] == "tomorrow") cal.add(Calendar.DAY_OF_YEAR, 1)
            var h = it.groupValues[3].toInt()
            val ap = it.groupValues[5]
            if (ap.startsWith("p") && h < 12) h += 12
            if (ap.startsWith("a") && h == 12) h = 0
            cal.set(Calendar.HOUR_OF_DAY, h % 24)
            cal.set(Calendar.MINUTE, it.groupValues[4].toIntOrNull() ?: 0)
            cal.set(Calendar.SECOND, 0)
            if (it.groupValues[2].isEmpty() && cal.timeInMillis < System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
            return Step.Event(it.groupValues[1], cal.timeInMillis)
        }

        // Phone controls
        val devices = "wifi|wi-fi|bluetooth|data|network|airplane(?: mode)?|flight mode|location|gps|nfc|flash\\s?light|torch"
        Regex("turn\\s+(on|off)\\s+(?:the\\s+)?(?:mobile\\s+)?($devices)").find(p)?.let {
            return Step.Toggle(it.groupValues[2], it.groupValues[1] == "on")
        }
        Regex("turn\\s+(?:the\\s+)?(?:mobile\\s+)?($devices)\\s+(on|off)").find(p)?.let {
            return Step.Toggle(it.groupValues[1], it.groupValues[2] == "on")
        }
        if (Regex("\\b(?:volume|louder|quieter)\\b").containsMatchIn(p)) {
            val up = Regex("\\b(?:up|louder|increase|raise|more|high)\\b").containsMatchIn(p)
            val down = Regex("\\b(?:down|quieter|decrease|lower|less|low|mute)\\b").containsMatchIn(p)
            if (up || down) return Step.Volume(up)
        }
        if (Regex("take\\s+(?:a\\s+)?(?:photo|picture|pic|selfie)").containsMatchIn(p)) return Step.Photo

        // Search, maps, info
        Regex("^(?:search|play|watch|find)\\s+(.+?)\\s+on\\s+youtube").find(p)?.let { return Step.Search(it.groupValues[1], true) }
        Regex("^(?:play|watch)\\s+(.+)").find(p)?.let { return Step.Search(it.groupValues[1], true) }
        Regex("^(?:search|google|look up|find)\\s+(?:for\\s+)?(.+)").find(p)?.let { return Step.Search(it.groupValues[1], false) }
        Regex("^(?:navigate to|directions to|take me to|route to|go to)\\s+(.+)").find(p)?.let { return Step.Navigate(it.groupValues[1]) }
        if (Regex("am i online|am i offline|do i have internet|internet (?:on|connected|working)|network status").containsMatchIn(p)) return Step.Info("net")
        if (Regex("what.?s the time|what time|time now|current time|the time").containsMatchIn(p)) return Step.Info("time")
        if (Regex("what.?s the date|what day|today.?s date|the date").containsMatchIn(p)) return Step.Info("date")
        if (p.contains("battery")) return Step.Info("battery")

        // Notes and list
        if (Regex("(?:clear|delete|erase)\\s+(?:all\\s+)?(?:my\\s+)?notes").containsMatchIn(p)) return Step.ClearNotes
        if (Regex("(?:read|show|what.?s|what are|list)\\s+(?:my\\s+)?notes").containsMatchIn(p)) return Step.ReadNotes
        Regex("^(?:remove|delete)\\s+(.+?)\\s+from\\s+(?:my\\s+)?(?:to-?\\s?do\\s+)?list").find(p)?.let { return Step.RemoveTodo(it.groupValues[1]) }
        if (Regex("(?:clear|empty|delete)\\s+(?:my\\s+)?(?:to-?\\s?do\\s+)?list").containsMatchIn(p)) return Step.ClearTodos
        Regex("^(?:add|put)\\s+(.+?)\\s+(?:to|in|on)\\s+(?:my\\s+)?(?:to-?\\s?do\\s+)?list").find(p)?.let { return Step.AddTodo(it.groupValues[1]) }
        if (Regex("(?:read|show|what.?s(?:\\s+on)?|list)\\s+(?:my\\s+)?(?:to-?\\s?do\\s+)?list").containsMatchIn(p)) return Step.ReadTodos

        // Facts and secrets
        Regex("^copy\\s+(my\\s+.+)").find(p)?.let { return Step.Copy(it.groupValues[1]) }
        Regex("^forget\\s+(?:about\\s+)?(.+)").find(p)?.let { return Step.Forget(it.groupValues[1]) }
        Regex("^(?:what.?s|what is|what are|tell me|show me|show|read out|read)\\s+(my\\s+.+)").find(p)?.let { return Step.Recall(it.groupValues[1]) }
        return Step.Unknown(p)
    }
}

class StepRunner(private val ctx: Context, private val say: (String) -> Unit, private val quiet: (String) -> Unit) {
    private val ui = Handler(Looper.getMainLooper())
    private val prefs = ctx.getSharedPreferences("sanya", Context.MODE_PRIVATE)
    private val secretWords = Regex("\\b(?:password|passcode|pin|otp|cvv|card|secret|key)\\b")
    private val noSlang = Regex("^(?:when i say|if i say|whenever i say|forget|nickname|set nickname|give nickname|add nickname|another nickname|that means|it means|this means|i meant|ignore)")
    private var lastCommand = ""
    private var lastUnknown = ""
    private data class Contact(val id: Long, val name: String, val number: String)

    init { Brain.setup(ctx) }

    // Learned slang first, then normal parsing.
    // Natural-language understanding + learned slang + normal parsing.
private fun plan(raw: String): List<Step> {

    val original = Brain.clean(raw)

    if (original.isEmpty()) return emptyList()

    /*
     * 1. First check Sanya's learned slang.
     *
     * Example:
     * "ring my bro"
     * learned as:
     * "call Ravi"
     */
    val slang = Brain.map("slang")

    val slangHit =
        if (noSlang.containsMatchIn(original)) {
            null
        } else {
            slang.keys
                .filter {
                    it.isNotEmpty() &&
                        Regex("\\b" + Regex.escape(it) + "\\b")
                            .containsMatchIn(original)
                }
                .maxByOrNull { it.length }
        }

    val learnedCommand =
        if (slangHit != null) {
            slang[slangHit] ?: original
        } else {
            original
        }

    /*
     * 2. Send the command through IntentEngine.
     *
     * Example:
     *
     * "Sanya, can you please open YouTube for me?"
     *
     * becomes:
     *
     * "open youtube"
     */
    val candidates = IntentEngine.alternatives(learnedCommand)

    if (candidates.isEmpty()) {
        return CommandParser.parse(learnedCommand)
    }

    /*
     * 3. Try the primary interpretation first.
     */
    for (candidate in candidates) {

        val parsed = CommandParser.parse(candidate)

        if (parsed.isNotEmpty() &&
            parsed.none { it is Step.Unknown }
        ) {
            return parsed
        }
    }

    /*
     * 4. If the natural-language interpretation was not
     * understood, fall back to the original command parser.
     *
     * This keeps old Sanya commands working.
     */
    return CommandParser.parse(learnedCommand)
}

    // True if every part of the sentence matches a known command.
    fun understands(raw: String): Boolean {
        val p = plan(raw)
        return p.isNotEmpty() && p.none { it is Step.Unknown }
    }

    // Returns how many steps will run.
    fun handle(raw: String): Int {
        val steps = plan(raw)
        if (steps.any { it is Step.Unknown }) lastUnknown = Brain.clean(raw)
        else if (steps.isNotEmpty() && steps.none { it is Step.Again || it is Step.Mean || it is Step.Teach }) lastCommand = raw
        steps.forEachIndexed { i, s -> ui.postDelayed({ exec(s) }, i * 2500L) }
        return steps.size
    }

    private fun name() = Brain.setting("name", "").let { if (it.isEmpty()) "" else " " + it.replaceFirstChar { c -> c.uppercase() } }
    private fun now(f: String) = SimpleDateFormat(f, Locale.getDefault()).format(Date())
    private fun fk(x: String) = Brain.clean(x).removePrefix("my ").removePrefix("the ")

    private fun online(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun exec(s: Step) {
        when (s) {
            is Step.OpenApp -> openApp(s.name)
            is Step.Call -> call(s)
            is Step.Sms -> {
                val c = findContact(s.contact) ?: return say("I can't find ${s.contact} in your contacts")
                go(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(c.number))).putExtra("sms_body", s.text))
                say("Message ready for ${c.name}")
            }
            is Step.Timer -> {
                go(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s.seconds)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                say("Timer set")
            }
            is Step.Alarm -> {
                go(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, s.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, s.min).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                say("Alarm set for ${s.hour}:${"%02d".format(s.min)}")
            }
            is Step.Remind -> {
                // A system timer with a label rings even if Sanya is closed.
                go(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s.seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, s.text).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                say("Okay, I'll remind you")
            }
            is Step.Event -> {
                go(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, s.title)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, s.start)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, s.start + 3600000L))
                say("Event ready. Tap save.")
            }
            is Step.Toggle -> toggle(s)
            is Step.Volume -> {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                repeat(3) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        if (s.up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                }
                say("Volume ${if (s.up) "up" else "down"}")
            }
            is Step.Media -> {
                val code = when (s.key) {
                    "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                    else -> KeyEvent.KEYCODE_MEDIA_PLAY
                }
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                quiet("Music: ${s.key}")
            }
            Step.Photo -> go(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            is Step.Search -> {
                if (!online()) return say("You're offline, so I can't search")
                val base = if (s.youtube) "https://www.youtube.com/results?search_query=" else "https://www.google.com/search?q="
                go(Intent(Intent.ACTION_VIEW, Uri.parse(base + Uri.encode(s.q))))
                say("Searching ${s.q}")
            }
            is Step.Navigate -> {
                go(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(s.place))))
                say("Navigating to ${s.place}")
            }
            is Step.Info -> say(when (s.kind) {
                "time" -> "It's " + now("h:mm a")
                "date" -> "Today is " + now("EEEE, d MMMM")
                "net" -> if (online()) "You're online" else "You're offline. I still work for calls, apps, timers and notes."
                else -> {
                    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    "Battery is ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent"
                }
            })
            is Step.AddTodo -> { Brain.add("todo", s.text); say("Added ${s.text}") }
            is Step.RemoveTodo -> { Brain.drop("todo", s.text); say("Removed ${s.text}") }
            Step.ClearTodos -> { Brain.clear("todo"); say("List cleared") }
            Step.ReadTodos -> say(Brain.items("todo").let { if (it.isEmpty()) "Your list is empty" else it.joinToString(", ") })
            is Step.Note -> { Brain.add("note", s.text); say("Noted") }
            Step.ClearNotes -> { Brain.clear("note"); say("Notes cleared") }
            Step.ReadNotes -> say(Brain.items("note").let { l ->
                if (l.isEmpty()) "You have no notes"
                else "You have ${l.size} notes. " + l.mapIndexed { i, n -> "${i + 1}, $n" }.joinToString(". ")
            })
            is Step.Nick -> {
                val real = Brain.map("nick")[Brain.clean(s.real)] ?: s.real
                s.nicks.forEach { Brain.link("nick", it, real) }
                say("Okay, ${s.nicks.joinToString(" and ")} now means $real")
            }
            is Step.Teach -> {
                s.phrases.forEach { Brain.link("slang", it, s.cmd) }
                say("Got it. When you say ${s.phrases.first()}, I'll ${s.cmd}")
            }
            is Step.WordMap -> { Brain.link("word", s.word, s.meaning); say("Okay, ${s.word} means ${s.meaning}") }
            is Step.Ignore -> { Brain.add("filler", s.word); say("I'll ignore the word ${s.word}") }
            is Step.Mean -> {
                val phrase = lastUnknown
                if (phrase.isEmpty()) return say("I have nothing to learn from yet")
                Brain.link("slang", phrase, s.cmd)
                lastUnknown = ""
                say("Got it. Next time ${phrase} will ${s.cmd}")
                val c = s.cmd
                ui.postDelayed({ handle(c) }, 2000)
            }
            is Step.Remember -> {
                val k = fk(s.key)
                if (secretWords.containsMatchIn(k)) {
                    prefs.edit().putString("secret:$k", Vault.enc(s.value)).apply()
                    say("Saved securely. I won't say it aloud.")
                } else {
                    Brain.link("fact", k, s.value)
                    say("Okay, I'll remember that")
                }
            }
            is Step.Recall -> {
                val k = fk(s.key)
                if (k == "name") return say("Your name is ${Brain.setting("name", "not set yet")}")
                Brain.map("fact")[k]?.let { return say("${s.key} is $it") }
                val sec = prefs.getString("secret:$k", null) ?: return say("I don't remember ${s.key}")
                val text = runCatching { Vault.dec(sec) }.getOrNull() ?: return say("I couldn't unlock that")
                quiet("${s.key}: $text")
            }
            is Step.Copy -> {
                val k = fk(s.key)
                val sec = prefs.getString("secret:$k", null)
                val text = if (sec != null) runCatching { Vault.dec(sec) }.getOrNull() else Brain.map("fact")[k]
                if (text == null) return say("I don't have ${s.key}")
                val clip = ClipData.newPlainText("sanya", text)
                if (sec != null) runCatching {
                    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                }
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                say("Copied")
            }
            is Step.Forget -> {
                val k = fk(s.key)
                val removed = Brain.unlink(s.key) || Brain.unlink(k) ||
                    prefs.contains("secret:$k").also { if (it) prefs.edit().remove("secret:$k").apply() }
                say(if (removed) "Forgot ${s.key}" else "I don't have ${s.key}")
            }
            is Step.SetName -> { Brain.put("setting", "name", s.name); say("Nice to meet you${name()}") }
            Step.Brief -> {
                val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val greet = if (h < 12) "Good morning" else if (h < 17) "Good afternoon" else "Good evening"
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val todo = Brain.items("todo")
                val tail = if (todo.isEmpty()) "Your list is empty." else "You have ${todo.size} things on your list: " + todo.joinToString(", ") + "."
                say("$greet${name()}. It's ${now("h:mm a")}, ${now("EEEE, d MMMM")}. " +
                    "Battery ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent. $tail" +
                    (if (online()) "" else " You're offline."))
            }
            Step.Stats -> say("I know ${Brain.map("nick").size} nicknames, ${Brain.map("slang").size} slang phrases, " +
                "${Brain.map("word").size} word meanings and ${Brain.map("fact").size} facts. Say show my brain to edit them.")
            Step.OpenBrain -> { go(Intent(ctx, MainActivity::class.java)); say("Opening my brain") }
            Step.Again -> if (lastCommand.isEmpty()) say("There's nothing to repeat yet") else handle(lastCommand)
            is Step.Reply -> say(s.text.replace("{n}", name())
                .replace("{name}", Brain.setting("name", "").ifEmpty { "not set yet. Say call me, then your name" }))
            is Step.Unknown -> say("I didn't get that. Say 'that means' and what I should do, and I'll learn it.")
        }
    }

    private fun go(i: Intent) {
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { say("That didn't open") }
    }

    // ---------- fuzzy matching (nicknames, mishearing) ----------
    private fun norm(x: String) = x.lowercase().replace(Regex("[^a-z0-9]"), "")
    private fun resolve(x: String) = Brain.map("nick")[Brain.clean(x)] ?: x

    private fun lev(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length]
    }

    private fun score(n: String, q: String, raw: String): Int {
        if (q.isEmpty()) return 0
        if (n == q) return 100
        val tokens = raw.lowercase().split(" ").map { norm(it) }
        if (q in tokens) return 90
        if (n.startsWith(q)) return 80
        if (n.contains(q)) return 70
        val d = tokens.minOfOrNull { lev(it, q) } ?: 9
        return if (d <= 1 && q.length > 3) 60 else if (d <= 2 && q.length > 5) 50 else 0
    }

    private fun openApp(spoken: String) {
        val q = norm(resolve(spoken.removeSuffix(" app")))
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val best = apps.map { it to score(norm(it.loadLabel(pm).toString()), q, it.loadLabel(pm).toString()) }
            .filter { it.second > 0 }.maxByOrNull { it.second }
        val i = best?.let { pm.getLaunchIntentForPackage(it.first.activityInfo.packageName) }
        if (i != null) go(i) else say("I can't find $spoken")
    }

    // ---------- contacts and calls ----------
    private fun allContacts(): List<Contact> {
        val out = mutableListOf<Contact>()
        ctx.contentResolver.query(Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use {
            while (it.moveToNext()) out += Contact(it.getLong(0), it.getString(1) ?: "", it.getString(2) ?: "")
        }
        return out
    }

    private fun findContact(spoken: String): Contact? {
        val q = norm(resolve(spoken))
        val best = allContacts().map { it to score(norm(it.name), q, it.name) }
            .filter { it.second > 0 }.maxByOrNull { it.second } ?: return null
        if (best.second in 50..60) {   // a mishearing: remember it as a nickname
            Brain.link("nick", spoken, best.first.name)
            quiet("Learned: $spoken = ${best.first.name}")
        }
        return best.first
    }

    private fun whatsappRow(contactId: Long, mime: String): Long? {
        val c = ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(contactId.toString(), mime), null)
        return c?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    private fun call(s: Step.Call) {
        val c = findContact(s.contact) ?: return say("I can't find ${s.contact} in your contacts")
        if (s.whatsapp) {
            val mime = if (s.video) "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
                       else "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
            val id = whatsappRow(c.id, mime)
            if (id == null) { say("${c.name} isn't on WhatsApp, or WhatsApp contacts aren't synced"); return }
            go(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.contacts/data/$id"), mime)
                .setPackage("com.whatsapp"))
            say("${if (s.video) "Video calling" else "WhatsApp calling"} ${c.name}")
            return
        }
        val uri = Uri.fromParts("tel", c.number, null)
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            go(Intent(Intent.ACTION_CALL, uri)); say("Calling ${c.name}")
        } else {
            go(Intent(Intent.ACTION_DIAL, uri)); say("Allow phone permission so I can call directly")
        }
    }

    private fun toggle(s: Step.Toggle) {
        if (s.what.startsWith("flash") || s.what == "torch") {
            runCatching {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cm.setTorchMode(cm.cameraIdList[0], s.on)
            }.onSuccess { say("Flashlight ${if (s.on) "on" else "off"}") }.onFailure { say("Flashlight failed") }
            return
        }
        // Android blocks direct switching of these. For now this opens the right settings page.
        if (directToggle(ctx, s, say)) return
        go(Intent(when {
            s.what.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            s.what.contains("airplane") || s.what.contains("flight") -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            s.what.contains("location") || s.what == "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            s.what == "nfc" -> Settings.ACTION_NFC_SETTINGS
            s.what == "data" || s.what == "network" -> Settings.ACTION_WIRELESS_SETTINGS
            else -> Settings.ACTION_WIFI_SETTINGS
        }))
        say("Opening ${s.what} settings")
    }
}

// Encrypts secrets with a key that never leaves the phone's Keystore.
object Vault {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("sanya_vault", null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder("sanya_vault", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return kg.generateKey()
    }

    fun enc(s: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(s.toByteArray()), Base64.NO_WRAP)
    }

    fun dec(s: String): String {
        val b = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
        return String(c.doFinal(b, 12, b.size - 12))
    }
}
