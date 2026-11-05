package com.colorprediction.bot

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Connects confirmed BDG text snapshots to the pure strategy engine.
 * It emits a new Telegram prediction only once per period and waits for that
 * period's explicit result before creating the next one.
 */
object BdgAutomationController {
    fun process(context: Context, snapshot: BdgPageSnapshot): String {
        val prefs = context.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
        var state = loadState(prefs)
        val pendingPeriod = prefs.getString("pending_period", null)
        val outcomes = snapshot.recentOutcomes

        if (pendingPeriod != null) {
            val actual = outcomes.firstOrNull { it.period == pendingPeriod }
            if (actual != null) {
                val side = runCatching { Side.valueOf(prefs.getString("pending_side", "")!!) }.getOrNull()
                val strategy = runCatching { Strategy.valueOf(prefs.getString("pending_strategy", "")!!) }.getOrNull()
                if (side != null && strategy != null) {
                    val prediction = Prediction(side, strategy)
                    val level = prefs.getInt("pending_level", state.level)
                    val sessionKey = prefs.getString("pending_session_key", (activeSessionKey() ?: "unknown")).orEmpty()
                    state = StrategyEngine.onOutcome(state, pendingPeriod, prediction, actual.side)
                    saveState(prefs, state)
                    TelegramReports.result(
                        context, sessionKey, pendingPeriod,
                        prefs.getString("game_duration", "30 Seconds").orEmpty(),
                        level, prediction, if (side == actual.side) "WIN" else "LOSS"
                    )
                    prefs.edit().remove("pending_period").remove("pending_side")
                        .remove("pending_strategy").remove("pending_level")
                        .remove("pending_session_key")
                        .putString("current_prediction", "WAITING")
                        .apply()
                    val outcomeLabel = if (side == actual.side) "YOUR WIN!" else "YOUR LOSS"
                    prefs.edit()
                        .putString("result_flash", if (side == actual.side) "WIN" else "LOSS")
                        .putLong("result_flash_id", System.currentTimeMillis())
                        .putString(
                            "recent_activity",
                            "$pendingPeriod • ${prediction.side} • Level $level • ${prediction.strategy.title} • $outcomeLabel"
                        ).apply()
                }
            }
        }

        val pendingStillExists = prefs.getString("pending_period", null) != null
        val sessionKey = activeSessionKey()
        if (!pendingStillExists && sessionKey == null) {
            prefs.edit().putString("bot_status", "OUTSIDE SESSION • Next session starts at 10:00, 15:00, 18:00 or 21:00").apply()
            return prefs.getString("bot_status", "OUTSIDE SESSION").orEmpty()
        }
        if (!pendingStillExists && sessionKey != null && snapshot.currentPeriod != state.lastProcessedPeriod) {
            val sentCount = prefs.getInt("session_predictions_${sessionKey}", 0)
            if (sentCount >= 5) {
                prefs.edit().putString("bot_status", "SESSION COMPLETE • 5 predictions sent").apply()
                return prefs.getString("bot_status", "").orEmpty()
            }
            val newestThree = outcomes.take(3).asReversed().map { it.side }
            val update = StrategyEngine.onNewPeriod(state, snapshot.currentPeriod, newestThree)
            state = update.state
            saveState(prefs, state)
            val prediction = update.prediction
            if (prediction != null) {
                val level = state.level
                prefs.edit()
                    .putInt("session_predictions_${sessionKey}", sentCount + 1)
                    .putString("pending_period", snapshot.currentPeriod)
                    .putString("pending_side", prediction.side.name)
                    .putString("pending_strategy", prediction.strategy.name)
                    .putInt("pending_level", level)
                    .putString("pending_session_key", sessionKey)
                    .putString("current_prediction", prediction.side.name)
                    .putString("current_strategy", prediction.strategy.title)
                    .putString("current_period", snapshot.currentPeriod)
                    .putInt("current_level", level)
                    .apply()
                TelegramReports.prediction(
                    context, snapshot.currentPeriod,
                    prefs.getString("game_duration", "30 Seconds").orEmpty(),
                    level, prediction
                )
                prefs.edit().putString("bot_status", "PREDICTION SENT • ${prediction.side}").apply()
            } else {
                prefs.edit().putString("current_prediction", if (update.status.startsWith("SKIP")) "SKIP" else "WAITING")
                    .putString("current_strategy", "")
                    .putString("current_period", snapshot.currentPeriod)
                    .putInt("current_level", state.level)
                    .putString("bot_status", update.status).apply()
                if (update.status.startsWith("SKIP")) {
                    TelegramReports.skip(context, snapshot.currentPeriod, state.skipRemaining, state.level)
                }
            }
        }

        return prefs.getString("bot_status", "BDG DATA DETECTED").orEmpty()
    }

    private fun loadState(prefs: android.content.SharedPreferences) = EngineState(
        level = prefs.getInt("engine_level", 1).coerceIn(1, 8),
        skipRemaining = prefs.getInt("engine_skip", 0).coerceAtLeast(0),
        skipTotal = prefs.getInt("engine_skip_total", 0).coerceAtLeast(0),
        pausedUntilMillis = prefs.getLong("engine_pause_until", 0L),
        lastBetPeriod = prefs.getString("engine_last_bet_period", null),
        lastProcessedPeriod = prefs.getString("engine_last_processed_period", null),
        lastPrediction = prefs.getString("engine_last_prediction", null)?.let { runCatching { Side.valueOf(it) }.getOrNull() },
        lastStrategy = prefs.getString("engine_last_strategy", null)?.let { runCatching { Strategy.valueOf(it) }.getOrNull() },
        lastOutcome = prefs.getString("engine_last_outcome", null)
    )

    private fun saveState(prefs: android.content.SharedPreferences, state: EngineState) {
        prefs.edit().putInt("engine_level", state.level)
            .putInt("engine_skip", state.skipRemaining)
            .putInt("engine_skip_total", state.skipTotal)
            .putLong("engine_pause_until", state.pausedUntilMillis)
            .putString("engine_last_bet_period", state.lastBetPeriod)
            .putString("engine_last_processed_period", state.lastProcessedPeriod)
            .putString("engine_last_prediction", state.lastPrediction?.name)
            .putString("engine_last_strategy", state.lastStrategy?.name)
            .putString("engine_last_outcome", state.lastOutcome)
            .putInt("current_level", state.level)
            .apply()
    }

    private fun activeSessionKey(): String? {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val sessionHour = when {
            hour in 10..14 -> 10
            hour in 15..17 -> 15
            hour in 18..20 -> 18
            hour in 21..23 -> 21
            else -> return null
        }
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + "_$sessionHour"
    }
}
