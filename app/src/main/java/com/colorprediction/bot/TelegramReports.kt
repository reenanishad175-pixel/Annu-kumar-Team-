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
        val label: String,
        val parseMode: String? = null
    )

    private val queueLock = Any()
    private val queue = ArrayDeque<Outgoing>()
    private var sending = false

    fun prediction(context: Context, period: String, duration: String, level: Int, prediction: Prediction) {
        enqueue(
            context,
            "━━━━━━━━━━━━\n🚀 LIVE PREDICTION\n\n<b>${prediction.side.name}</b>\n🆔 Period: $period\n🏷️ Label: $level\n━━━━━━━━━━━━",
            "PREDICTION",
            "HTML"
        )
    }

    fun result(context: Context, period: String, duration: String, level: Int, prediction: Prediction, outcome: String) {
        enqueue(
            context,
            "$outcome\nPeriod: $period\nPrediction: ${prediction.side}\nLabel: $level",
            "RESULT"
        )
    }

    fun skip(context: Context, period: String, remaining: Int, level: Int) {
        enqueue(context, "SKIP • Period: $period\nLevel: $level\nRemaining: $remaining", "SKIP")
    }

    fun referral(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
        val link = prefs.getString(
            "referral_url",
            "https://bdgwin98.com/#/register?invitationCode=5152785394"
        ).orEmpty().trim()
        if (!link.startsWith("https://")) {
            prefs.edit().putString("telegram_status", "REFERRAL LINK INVALID • Bot settings खोलें").apply()
            return
        }
        enqueue(
            context,
            "🎁 JOIN BDG WIN\n\nReady to explore BDG WIN?\n✨ Register using our referral link and get started!\n\n👇 Register here:\n$link\n\nPlay responsibly. No guaranteed winnings.",
            "REFERRAL"
        )
    }

    private fun enqueue(context: Context, message: String, label: String, parseMode: String? = null) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
        val token = prefs.getString("tg_token", "").orEmpty()
        val chat = prefs.getString("tg_chat", "").orEmpty()
        if (token.isBlank() || chat.isBlank()) {
            prefs.edit().putString("telegram_status", "TOKEN/CHAT ID MISSING • Telegram settings खोलें").apply()
            return
        }

        val shouldStart = synchronized(queueLock) {
            queue.addLast(Outgoing(appContext, token, chat, message, label, parseMode))
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
        TelegramNotifier.send(item.token, item.chatId, item.message, item.parseMode) { success, detail ->
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
