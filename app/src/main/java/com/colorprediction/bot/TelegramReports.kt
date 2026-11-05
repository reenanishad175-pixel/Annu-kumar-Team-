package com.colorprediction.bot

import android.content.Context
import java.util.ArrayDeque

/**
 * Serial Telegram outbox. A result is acknowledged by Telegram before the
 * following period's prediction is allowed to leave the device.
 */
object TelegramReports {
    private data class Outgoing(
        val context: Context,
        val token: String,
        val chatId: String,
        val message: String,
        val label: String
    )

    private val queueLock = Any()
    private val queue = ArrayDeque<Outgoing>()
    private var sending = false

    fun prediction(context: Context, period: String, duration: String, level: Int, prediction: Prediction) {
        enqueue(
            context,
            "PREDICTION\nPeriod: $period\nPrediction: ${prediction.side}\nLabel: $level",
            "PREDICTION"
        )
    }

    fun result(context: Context, period: String, duration: String, level: Int, prediction: Prediction, outcome: String) {
        enqueue(
            context,
            "$outcome\nPeriod: $period\nPrediction: ${prediction.side}\nLabel: ${prediction.strategy.title}",
            "RESULT"
        )
    }

    fun skip(context: Context, period: String, remaining: Int, level: Int) {
        enqueue(context, "SKIP • Period: $period\nLevel: $level\nRemaining: $remaining", "SKIP")
    }

    private fun enqueue(context: Context, message: String, label: String) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
        val token = prefs.getString("tg_token", "").orEmpty()
        val chat = prefs.getString("tg_chat", "").orEmpty()
        if (token.isBlank() || chat.isBlank()) {
            prefs.edit().putString("telegram_status", "TOKEN/CHAT ID MISSING • Telegram settings खोलें").apply()
            return
        }

        val shouldStart = synchronized(queueLock) {
            queue.addLast(Outgoing(appContext, token, chat, message, label))
            if (sending) false else {
                sending = true
                true
            }
        }
        if (shouldStart) sendNext()
    }

    private fun sendNext() {
        val item = synchronized(queueLock) {
            if (queue.isEmpty()) {
                sending = false
                null
            } else queue.removeFirst()
        } ?: return

        val prefs = item.context.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("telegram_status", "SENDING • ${item.label}").apply()
        TelegramNotifier.send(item.token, item.chatId, item.message) { success, detail ->
            prefs.edit()
                .putString("telegram_status", if (success) "SENT • ${item.label}" else "FAILED • ${item.label} • $detail")
                .putString("telegram_last_error", if (success) "" else detail)
                .putLong("telegram_last_attempt_at", System.currentTimeMillis())
                .apply()
            // Never allow a later prediction to overtake a failed result.
            if (success) {
                sendNext()
            } else {
                synchronized(queueLock) {
                    queue.addFirst(item)
                    sending = false
                }
            }
        }
    }
}
