package com.sanya.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var editor: EditText
    private var loaded = ""

    private fun same(a: String, b: String) = a.trimEnd() == b.trimEnd()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Brain.setup(this)
        val pad = (12 * resources.displayMetrics.density).toInt()
        fun btn(label: String, w: Float, click: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
        }
        loaded = Brain.text()
        editor = EditText(this).apply {
            setText(loaded)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btn("Save", 1f) { saveNow(); toast("Saved") })
            addView(btn("Share / backup", 1f) { share() })
        }
        val start = btn("Start Sanya", 0f) { go() }.apply {
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            addView(start)
            addView(row)
            addView(editor)
        })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun saveNow() {
        Brain.save(editor.text.toString())
        loaded = Brain.text()
    }

    private fun share() {
        saveNow()
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, Brain.text()), "Back up Sanya's brain"))
    }

    override fun onPause() {
        super.onPause()
        if (!same(editor.text.toString(), loaded)) saveNow()
    }

    override fun onResume() {
        super.onResume()
        val cur = Brain.text()
        if (same(editor.text.toString(), loaded) && !same(cur, loaded)) { editor.setText(cur); loaded = cur }
    }

    private fun go() {
        saveNow()
        if (!Settings.canDrawOverlays(this)) {
            // Turn on "Display over other apps" for Sanya, then come back and tap Start again
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        val need = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1) else start()
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, g: IntArray) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
    }

    private fun start() {
        startForegroundService(Intent(this, BubbleService::class.java))
        finish()
    }
}
