package com.colorprediction.bot

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Telegram Bot API sender. Tokens are read from local app settings and never logged.
 */
object TelegramNotifier {
    fun send(token: String, chatId: String, message: String, callback: (Boolean) -> Unit = {}) {
        thread(name = "telegram-send") {
            var success = false
            var connection: HttpURLConnection? = null
            try {
                val endpoint = URL("https://api.telegram.org/bot${token}/sendMessage")
                connection = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
                val body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
                    "&text=" + URLEncoder.encode(message, "UTF-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                success = connection.responseCode in 200..299
                if (success) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    success = response.contains("\"ok\":true")
                }
            } catch (_: Exception) {
                success = false
            } finally {
                connection?.disconnect()
                callback(success)
            }
        }
    }

    fun referralPromotion(referralUrl: String): String =
        "Session complete! Join using our BDG link: $referralUrl"
}
