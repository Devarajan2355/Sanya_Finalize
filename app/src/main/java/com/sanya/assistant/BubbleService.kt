package com.sanya.assistant

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

class BubbleService : Service() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var bubble: TextView
    private lateinit var caption: TextView
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var runner: StepRunner
    private val circle = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(IDLE) }
    private val am by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val muted = mutableListOf<Int>()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sr: SpeechRecognizer? = null
    private var pulse: ObjectAnimator? = null
    private var handsFree = false   // always-listening mode (long-press the bubble)
    private var awaiting = false    // next phrase is a command, no wake word needed
    private var listening = false
    private var pending = 0         // spoken sentences still queued
    private var size = 0
    private val hideCaption = Runnable { caption.visibility = View.GONE }

    companion object {
        const val IDLE = 0xFF6C5CE7.toInt()
        const val LISTENING = 0xFF00B894.toInt()
        const val HANDS_FREE = 0xFFE17055.toInt()
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Brain.setup(this)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("sanya", "Sanya", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, "sanya")
            .setContentTitle("Sanya is ready")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build())
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                ttsReady = true
                val r = tts?.setLanguage(Locale.forLanguageTag(Brain.setting("language", "en-IN")))
                if (r == null || r < 0) tts?.setLanguage(Locale.getDefault())
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { ui.post { pending = maxOf(0, pending - 1); resume() } }
                    override fun onError(id: String?) { ui.post { pending = maxOf(0, pending - 1); resume() } }
                })
            }
        }
        runner = StepRunner(this, { say(it) }, { show(it, 8000L) })
        showBubble()
        makeCaption()
    }

    // Wake words come from the brain file, so you can add the ways the phone mishears "Sanya".
    private fun wake(): Regex {
        val w = Brain.setting("wake words", "sanya").split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val alt = w.ifEmpty { listOf("sanya") }.joinToString("|") { Regex.escape(it) }
        return Regex("^\\s*(?:(?:hey|hi|ok|okay)\\s+)?(?:$alt)\\b[ ,.]*")
    }

    // ---------- Bubble ----------
    private fun showBubble() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        size = (56 * resources.displayMetrics.density).toInt()
        bubble = TextView(this).apply {
            text = "S"; textSize = 22f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); background = circle
        }
        lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 400 }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
            var moved = false; var longPressed = false
            val longPress = Runnable { longPressed = true; buzz(); setHandsFree(!handsFree) }

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY
                        moved = false; longPressed = false
                        ui.postDelayed(longPress, 700)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - tx; val dy = e.rawY - ty
                        if (abs(dx) > 12 || abs(dy) > 12) { moved = true; ui.removeCallbacks(longPress) }
                        if (moved) { lp.x = sx + dx.toInt(); lp.y = sy + dy.toInt(); wm.updateViewLayout(bubble, lp) }
                    }
                    MotionEvent.ACTION_UP -> {
                        ui.removeCallbacks(longPress)
                        if (moved) snap() else if (!longPressed) tap()
                    }
                    MotionEvent.ACTION_CANCEL -> ui.removeCallbacks(longPress)
                }
                return true
            }
        })
        wm.addView(bubble, lp)
    }

    private fun snap() {
        val w = resources.displayMetrics.widthPixels
        lp.x = if (lp.x + size / 2 < w / 2) 0 else w - size
        wm.updateViewLayout(bubble, lp)
    }

    private fun buzz() { bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }

    private fun look(color: Int, pulseOn: Boolean) {
        circle.setColor(color); bubble.invalidate()
        pulse?.cancel(); bubble.alpha = 1f
        if (pulseOn) {
            pulse = ObjectAnimator.ofFloat(bubble, "alpha", 1f, 0.45f).apply {
                duration = 500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
            }
        }
    }

    // ---------- Caption pill (top of screen) ----------
    private fun makeCaption() {
        caption = TextView(this).apply {
            textSize = 16f; setTextColor(Color.WHITE); setPadding(40, 20, 40, 20)
            background = GradientDrawable().apply { cornerRadius = 60f; setColor(0xE6222222.toInt()) }
            visibility = View.GONE
        }
        val cp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 140 }
        wm.addView(caption, cp)
    }

    private fun show(text: String, ms: Long = 3500L) {
        caption.text = text; caption.visibility = View.VISIBLE
        ui.removeCallbacks(hideCaption); ui.postDelayed(hideCaption, ms)
    }

    // ---------- Beep control (each hands-free restart beeps) ----------
    private fun muteBeep() {
        for (st in intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_SYSTEM)) {
            if (st == AudioManager.STREAM_MUSIC && am.isMusicActive) continue   // never cut your video audio
            if (muted.contains(st) || am.isStreamMute(st)) continue
            runCatching {
                am.adjustStreamVolume(st, AudioManager.ADJUST_MUTE, 0)
                if (am.isStreamMute(st)) muted.add(st)
            }
        }
    }

    private fun unmuteBeep() {
        for (st in muted) runCatching { am.adjustStreamVolume(st, AudioManager.ADJUST_UNMUTE, 0) }
        muted.clear()
    }

    // ---------- Speaking ----------
    private fun say(s: String) {
        show(s)
        unmuteBeep()
        sr?.let { if (listening) { it.cancel(); finish(it) } }   // never listen to my own voice
        val t = tts ?: return
        if (!ttsReady) return
        pending++
        if (t.speak(s, TextToSpeech.QUEUE_ADD, null, "u" + System.nanoTime()) == TextToSpeech.ERROR) pending--
    }

    // After she finishes talking, go back to listening if hands-free (or waiting for a command)
    private fun resume() {
        if ((handsFree || awaiting) && !listening && pending == 0) {
            ui.postDelayed({ if ((handsFree || awaiting) && !listening && pending == 0) listen() }, 500)
        }
    }

    // ---------- Listening (online first, offline fallback) ----------
    private fun tap() {
        buzz()
        if (pending > 0) { tts?.stop(); pending = 0 }   // tap = stop talking
        awaiting = true                                  // a tap means: no wake word needed
        if (listening) sr?.stopListening() else listen()
    }

    private fun listen(offline: Boolean = false) {
        if (listening || pending > 0) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { say("No speech service on this phone"); return }
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        sr = r
        listening = true
        look(LISTENING, true)
        if (handsFree) muteBeep()
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(res: Bundle?) {
                finish(r)
                val list = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (list.isNullOrEmpty()) resume() else heard(list)
            }
            override fun onError(code: Int) {
                finish(r)
                if (!offline && (code == 1 || code == 2 || code == 11)) {   // no internet: try the offline pack
                    show("Offline mode")
                    ui.post { listen(true) }
                    return
                }
                awaiting = false
                if (handsFree && (code == 6 || code == 7 || code == 8)) {   // silence or busy: keep waiting
                    ui.postDelayed({ resume() }, if (code == 8) 1500L else 300L); return
                }
                if (handsFree) handsFree = false                              // real error: stop looping
                look(IDLE, false)
                say(explain(code))
            }
            override fun onPartialResults(p: Bundle?) {
                p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { show("🎤 $it") }
            }
            override fun onReadyForSpeech(p: Bundle?) { if (!handsFree) show(if (offline) "Listening (offline)…" else "Listening…") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Brain.setting("language", "en-IN"))
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, Brain.hints())
        if (offline) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        r.startListening(i)
    }

    private fun finish(r: SpeechRecognizer) {
        listening = false
        unmuteBeep()
        look(if (handsFree) HANDS_FREE else IDLE, false)
        if (sr === r) sr = null
        ui.post { r.destroy() }
    }

    private fun explain(c: Int) = when (c) {
        1, 2 -> "No internet and no offline speech pack. Check your connection."
        3 -> "Microphone problem"
        6 -> "I didn't hear anything"
        7 -> "I didn't catch that"
        8 -> "Speech service is busy, try again"
        9 -> "Microphone permission is missing"
        12, 13 -> "Your language isn't supported for speech here"
        else -> "Speech error $c"
    }

    // ---------- Understanding ----------
    // The recognizer gives up to 5 guesses. Pick the one Sanya actually understands.
    private fun heard(all: List<String>) {
        val w = wake()
        val lows = all.map { it.lowercase() }
        val pool = if (handsFree && !awaiting) lows.filter { w.containsMatchIn(it) } else lows
        if (pool.isEmpty()) { resume(); return }   // not meant for me, keep waiting
        val pick = pool.firstOrNull { val c = w.replace(it, "").trim(); c.isNotEmpty() && runner.understands(c) } ?: pool.first()
        show("“$pick”")
        val cmd = w.replace(pick, "").trim()
        awaiting = false
        if (cmd.isEmpty()) { awaiting = true; buzz(); say("Yes?") } else execute(cmd)
    }

    private fun execute(c: String) {
        when {
            Regex("^(?:exit|quit|close|shut down)$").containsMatchIn(c) -> { say("Bye"); ui.postDelayed({ stopSelf() }, 1500) }
            Regex("stop listening|hands.?free off").containsMatchIn(c) -> setHandsFree(false)
            Regex("hands.?free on|hands.?free mode|start listening").containsMatchIn(c) -> setHandsFree(true)
            else -> { val n = runner.handle(c); ui.postDelayed({ resume() }, n * 2500L + 1500L) }
        }
    }

    private fun setHandsFree(on: Boolean) {
        handsFree = on; awaiting = false
        if (!on) { sr?.destroy(); sr = null; listening = false; unmuteBeep() }
        look(if (on) HANDS_FREE else IDLE, false)
        say(if (on) "Hands free on. Say Sanya, then your command." else "Hands free off")
        if (on) resume()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        unmuteBeep()
        pulse?.cancel()
        if (::bubble.isInitialized) wm.removeView(bubble)
        if (::caption.isInitialized) wm.removeView(caption)
        sr?.destroy(); tts?.shutdown()
        super.onDestroy()
    }
}
