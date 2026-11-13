package com.assmeth.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Ai {
    fun ask(prompt: String, cb: (String) -> Unit) {
        if (BuildConfig.CLAUDE_KEY.isBlank()) { cb("Add your Claude API key to local.properties to turn on chat."); return }
        Thread {
            try {
                val u = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
                u.requestMethod = "POST"; u.doOutput = true
                u.setRequestProperty("x-api-key", BuildConfig.CLAUDE_KEY)
                u.setRequestProperty("anthropic-version", "2023-06-01")
                u.setRequestProperty("content-type", "application/json")
                val body = JSONObject().put("model", "claude-sonnet-5-5").put("max_tokens", 300)
                    .put("system", "You are Assmeth, a friendly voice assistant. Reply in at most 2 short plain-text sentences.")
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                u.outputStream.use { it.write(body.toString().toByteArray()) }
                val r = JSONObject(u.inputStream.bufferedReader().readText())
                cb(r.getJSONArray("content").getJSONObject(0).getString("text"))
            } catch (e: Exception) { cb("I could not reach my AI brain.") }
        }.start()
    }
}
