package com.colorprediction.bot

import android.content.Context

/** Sends every new prediction/result while the dashboard monitor is active. No session, clock or quota. */
object TelegramReports {
    fun prediction(context: Context, period: String, duration: String, level: Int, prediction: Prediction) {
        send(
            context,
            "PREDICTION • ${prediction.side}\nPeriod: $period\nGame: $duration\nLevel: $level",
            "PREDICTION"
        )
    }

    fun result(context: Context, period: String, duration: String, level: Int, prediction: Prediction, outcome: String) {
        send(
            context,
            "RESULT • $outcome\nPrediction: ${prediction.side}\nPeriod: $period\nGame: $duration\nLevel: $level",
            "RESULT"
        )
    }

    fun skip(context: Context, period: String, remaining: Int, level: Int) {
        send(context, "SKIP • Period: $period\nLevel: $level\nRemaining: $remaining", "SKIP")
    }

    private fun send(context: Context, message: String, label: String) {
        val prefs = context.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
        val token = prefs.getString("tg_token", "").orEmpty()
        val chat = prefs.getString("tg_chat", "").orEmpty()
        if (token.isBlank() || chat.isBlank()) {
            prefs.edit().putString("telegram_status", "TOKEN/CHAT ID MISSING • Telegram settings खोलें").apply()
            return
        }
        prefs.edit().putString("telegram_status", "SENDING • $label").apply()
        TelegramNotifier.send(token, chat, message) { success, detail ->
            prefs.edit()
                .putString("telegram_status", if (success) "SENT • $label" else "FAILED • $label • $detail")
                .putString("telegram_last_error", if (success) "" else detail)
                .putLong("telegram_last_attempt_at", System.currentTimeMillis())
                .apply()
        }
    }
}