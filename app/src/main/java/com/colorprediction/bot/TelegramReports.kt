package com.colorprediction.bot

import android.content.Context

/** Telegram session quota is five confirmed WINs, not five predictions. */
object TelegramReports {
    fun canSendSession(context: Context, sessionKey: String): Boolean {
        if (sessionKey.isBlank()) return false
        val prefs = context.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
        return prefs.getInt("session_wins_$sessionKey", 0) < 5
    }

    fun prediction(context: Context, sessionKey: String, period: String, duration: String, level: Int, prediction: Prediction) {
        if (!canSendSession(context, sessionKey)) return
        send(context, "PREDICTION • ${prediction.side}\nPeriod: $period\nGame: $duration\nLevel: $level\n${prediction.strategy.title}", "PREDICTION")
    }

    fun result(context: Context, sessionKey: String, period: String, duration: String, level: Int, prediction: Prediction, outcome: String) {
        if (!canSendSession(context, sessionKey)) return
        send(context, "RESULT • $outcome\nPrediction: ${prediction.side}\nPeriod: $period\nGame: $duration\nLevel: $level\n${prediction.strategy.title}", "RESULT")
        if (outcome == "WIN") {
            val prefs = context.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
            val key = "session_wins_$sessionKey"
            val wins = (prefs.getInt(key, 0) + 1).coerceAtMost(5)
            prefs.edit().putInt(key, wins).apply()
            if (wins == 5) {
                val referral = prefs.getString("referral_url", "https://bdgwin98.com/#/register?invitationCode=5152785394").orEmpty()
                send(context, TelegramNotifier.referralPromotion(referral), "SESSION PROMOTION")
            }
        }
    }

    fun skip(context: Context, sessionKey: String, period: String, remaining: Int, level: Int) {
        if (!canSendSession(context, sessionKey)) return
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
            prefs.edit().putString("telegram_status", if (success) "SENT • $label" else "FAILED • $label • $detail")
                .putString("telegram_last_error", if (success) "" else detail)
                .putLong("telegram_last_attempt_at", System.currentTimeMillis())
                .apply()
        }
    }
}