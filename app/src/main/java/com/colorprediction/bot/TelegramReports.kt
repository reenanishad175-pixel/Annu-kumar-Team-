package com.colorprediction.bot

import android.content.Context

/**
 * Formats concise Telegram updates. Session promotion is sent after five
 * confirmed result reports for the same session key.
 */
object TelegramReports {
    fun prediction(
        context: Context,
        period: String,
        duration: String,
        level: Int,
        prediction: Prediction
    ) {
        send(context, "PREDICTION • ${prediction.side}\nPeriod: $period\nGame: $duration\nLevel: $level\n${prediction.strategy.title}")
    }

    fun result(
        context: Context,
        sessionKey: String,
        period: String,
        duration: String,
        level: Int,
        prediction: Prediction,
        outcome: String
    ) {
        send(context, "RESULT • $outcome\nPrediction: ${prediction.side}\nPeriod: $period\nGame: $duration\nLevel: $level\n${prediction.strategy.title}")
        val prefs = context.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
        val key = "session_results_$sessionKey"
        val count = prefs.getInt(key, 0) + 1
        if (count >= 5) {
            val referral = prefs.getString("referral_url", "https://bdgwin98.com/#/register?invitationCode=5152785394").orEmpty()
            send(context, TelegramNotifier.referralPromotion(referral))
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putInt(key, count).apply()
        }
    }

    fun skip(context: Context, period: String, remaining: Int, level: Int) {
        send(context, "SKIP • Period: $period\nLevel: $level\nRemaining: $remaining")
    }

    private fun send(context: Context, message: String) {
        val prefs = context.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
        val token = prefs.getString("tg_token", "").orEmpty()
        val chat = prefs.getString("tg_chat", "").orEmpty()
        if (token.isNotBlank() && chat.isNotBlank()) TelegramNotifier.send(token, chat, message)
    }
}
