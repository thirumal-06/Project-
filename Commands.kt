package com.assmeth.app

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import java.text.DateFormat
import java.util.Date

object Commands {
    private var torch = false

    /** Returns true if the command was handled locally. [t] is the normalized text after the wake word. */
    fun run(c: Context, t: String, say: (String) -> Unit): Boolean {
        fun go(i: Intent) { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

        Regex("^remind me (?:to )?(.+?) in (\\d+) (second|minute|hour)").find(t)?.let {
            timer(c, it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3]); say("Okay, I will remind you to ${it.groupValues[1]}."); return true
        }
        Regex("^remind me in (\\d+) (second|minute|hour)s? (?:to )?(.+)").find(t)?.let {
            timer(c, it.groupValues[3], it.groupValues[1].toInt(), it.groupValues[2]); say("Okay, I will remind you to ${it.groupValues[3]}."); return true
        }
        Regex("^call (.+)").find(t)?.let {
            val q = it.groupValues[1]
            val num = if (q.replace(" ", "").all { ch -> ch.isDigit() }) q.replace(" ", "") else number(c, q)
            if (num == null) say("I could not find $q in your contacts.")
            else { say("Calling $q."); go(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num"))) }
            return true
        }
        Regex("^(?:text|message) (.+?) (?:saying|say|that) (.+)").find(t)?.let {   // opens a prefilled draft; you tap Send
            val q = it.groupValues[1]
            val num = if (q.replace(" ", "").all { ch -> ch.isDigit() }) q.replace(" ", "") else number(c, q)
            if (num == null) say("I could not find $q in your contacts.")
            else { say("Message ready for $q. Tap send."); go(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", it.groupValues[2])) }
            return true
        }
        Regex("^(?:navigate|directions|take me) (?:to )?(.+)").find(t)?.let {
            say("Navigating to ${it.groupValues[1]}."); go(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(it.groupValues[1])}"))); return true
        }
        Regex("^play (.+)").find(t)?.let {
            say("Playing ${it.groupValues[1]}."); go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(it.groupValues[1])}"))); return true
        }
        Regex("^(?:search|google|look up) (?:for )?(.+)").find(t)?.let {
            say("Searching."); go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(it.groupValues[1])}"))); return true
        }
        Regex("^open (.+)").find(t)?.let {
            val pm = c.packageManager
            val hit = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .firstOrNull { r -> r.loadLabel(pm).toString().lowercase().contains(it.groupValues[1]) }
            val launch = hit?.let { r -> pm.getLaunchIntentForPackage(r.activityInfo.packageName) }
            if (launch == null) say("I could not find an app called ${it.groupValues[1]}.") else { say("Opening ${it.groupValues[1]}."); go(launch) }
            return true
        }
        if (Regex("flashlight|torch").containsMatchIn(t)) {
            val on = !t.contains("off")
            try { val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager; cm.setTorchMode(cm.cameraIdList[0], on); torch = on; say("Flashlight ${if (on) "on" else "off"}.") }
            catch (e: Exception) { say("I could not use the flashlight.") }
            return true
        }
        if (Regex("what.?s the time|what time is it|^time$").containsMatchIn(t)) { say("It is ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}."); return true }
        if (Regex("what.?s the date|what day is it").containsMatchIn(t)) { say("Today is ${DateFormat.getDateInstance(DateFormat.FULL).format(Date())}."); return true }
        return false
    }

    private fun timer(c: Context, msg: String, n: Int, unit: String) {
        val secs = n * when (unit) { "hour" -> 3600; "minute" -> 60; else -> 1 }
        c.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_MESSAGE, msg)
            .putExtra(AlarmClock.EXTRA_LENGTH, secs).putExtra(AlarmClock.EXTRA_SKIP_UI, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun number(c: Context, name: String): String? {
        val col = ContactsContract.CommonDataKinds.Phone.NUMBER
        c.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(col),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null)?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }
}
