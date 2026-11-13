package com.assmeth.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var toggle: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48); gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(TextView(this).apply { text = "Assmeth"; textSize = 32f; gravity = Gravity.CENTER })
        status = TextView(this).apply { textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 24, 0, 24) }
        toggle = Button(this).apply { setOnClickListener { flip() } }
        val perms = Button(this).apply { text = "1. Grant permissions"; setOnClickListener { askPerms() } }
        val overlay = Button(this).apply {
            text = "2. Allow display over other apps"
            setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        }
        val battery = Button(this).apply {
            text = "3. Battery: set to Unrestricted"
            setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        }
        listOf(status, toggle, perms, overlay, battery).forEach { if (it.parent == null) box.addView(it) }
        box.addView(TextView(this).apply {
            text = "Say: \"Assmeth, call mom\", \"Assmeth, remind me to drink water in 10 minutes\", \"Assmeth, correct: i has went home\", \"Assmeth, set voice password\", \"Assmeth, lock\"."
            textSize = 13f; setPadding(0, 32, 0, 0)
        })
        setContentView(box)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val on = AssmethService.active
        status.text = if (on) "Assmeth is ON and listening" else "Assmeth is OFF"
        toggle.text = if (on) "Turn OFF" else "Turn ON"
    }

    private fun flip() {
        if (AssmethService.active) startService(Intent(this, AssmethService::class.java).setAction(AssmethService.STOP))
        else startForegroundService(Intent(this, AssmethService::class.java))
        toggle.postDelayed({ refresh() }, 600)
    }

    private fun askPerms() {
        val p = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(p.toTypedArray(), 1)
    }
}
