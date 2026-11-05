package com.colorprediction.bot

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/** Telegram sender with visible API error details; never logs the bot token. */
object TelegramNotifier {
    fun send(token: String, chatId: String, message: String, parseMode: String? = null, callback: (Boolean, String) -> Unit = { _, _ -> }) {
        thread(name = "telegram-send") {
            var connection: HttpURLConnection? = null
            var success = false
            var detail = "Unknown Telegram error"
            try {
                val endpoint = URL("https://api.telegram.org/bot${token}/sendMessage")
                connection = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
                val body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") + "&text=" + URLEncoder.encode(message, "UTF-8") + (parseMode?.let { "&parse_mode=" + URLEncoder.encode(it, "UTF-8") } ?: "")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(response) }.getOrNull()
                success = code in 200..299 && json?.optBoolean("ok", false) == true
                detail = if (success) "Telegram accepted message" else json?.optString("description")?.takeIf { it.isNotBlank() } ?: "HTTP $code • Telegram response not understood"
            } catch (e: Exception) {
                detail = when (e) {
                    is java.net.UnknownHostException -> "Internet/DNS connection नहीं है"
                    is java.net.SocketTimeoutException -> "Telegram server timeout"
                    else -> e.localizedMessage ?: "Network error"
                }
            } finally {
                connection?.disconnect()
                callback(success, detail)
            }
        }
    }

    fun testConnection(token: String, chatId: String, callback: (Boolean, String) -> Unit) {
        send(token, chatId, "Telegram connection test successful. Annu Kumar Team is connected.", null, callback)
    }

}