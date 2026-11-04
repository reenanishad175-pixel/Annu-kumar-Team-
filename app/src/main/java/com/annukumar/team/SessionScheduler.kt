package com.annukumar.team

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Telegram transport only.
 * Session reminders are checked by BackgroundMonitorService; this class does not
 * create Android alarms, PendingIntents, or local reminder notifications.
 */
object SessionScheduler {
    fun sendTelegram(context: Context, message: String, onComplete: ((Boolean, String) -> Unit)? = null) {
        val prefs = context.getSharedPreferences("paper", Context.MODE_PRIVATE)
        val token = prefs.getString("telegram_bot_token", "").orEmpty()
        val chatId = prefs.getString("telegram_chat_id", "").orEmpty()
        if (token.isBlank() || chatId.isBlank()) {
            onComplete?.invoke(false, "Bot token or Chat ID missing")
            return
        }
        thread(name = "session-telegram") {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://api.telegram.org/bot" + token + "/sendMessage")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
                val body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
                    "&text=" + URLEncoder.encode(message, "UTF-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val responseCode = connection.responseCode
                val response = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val ok = responseCode in 200..299 && JSONObject(response).optBoolean("ok", false)
                if (!ok) android.util.Log.w("SessionScheduler", "Telegram send failed: HTTP " + responseCode + " " + response)
                onComplete?.invoke(ok, if (ok) "sent" else "HTTP " + responseCode + ": " + response)
            } catch (error: Exception) {
                android.util.Log.e("SessionScheduler", "Telegram send error", error)
                onComplete?.invoke(false, error.localizedMessage ?: "network error")
            } finally {
                connection?.disconnect()
            }
        }
    }
}
