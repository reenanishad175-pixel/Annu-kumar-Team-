package com.colorprediction.bot

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Runs the prediction engine continuously whenever fresh BDG periods arrive.
 * Telegram reporting is independently limited to five confirmed WINs per
 * scheduled session; that limit never pauses the on-device dashboard engine.
 */
object BdgAutomationController {
    fun process(context: Context, snapshot: BdgPageSnapshot): String {
        val prefs = context.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
        var state = loadState(prefs)
        var pendingPeriod = prefs.getString("pending_period", null)
        val outcomes = snapshot.recentOutcomes

        // If the phone was asleep long enough that the pending period has
        // fallen outside BDG's visible history, its outcome is unknowable.
        // Never invent WIN/LOSS: discard that stale pending bet and recover
        // the engine at the latest period so it cannot remain stuck forever.
        val oldestVisiblePeriod = outcomes.lastOrNull()?.period
        // A history gap can also happen while the engine is counting SKIPs:
        // in that state there is no pending bet, so checking pendingPeriod
        // alone misses the phone-sleep case. If the last processed period has
        // fallen behind the oldest visible result, the skipped periods are no
        // longer reconcilable; reset the stale run instead of retaining Level
        // and SKIP counters that can never catch up.
        val pendingExpired = pendingPeriod != null && oldestVisiblePeriod != null &&
            comparePeriodIds(pendingPeriod, oldestVisiblePeriod) < 0
        val skipHistoryExpired = state.lastProcessedPeriod != null && oldestVisiblePeriod != null &&
            comparePeriodIds(state.lastProcessedPeriod, oldestVisiblePeriod) < 0
        val staleRunExpired = pendingExpired || skipHistoryExpired
        if (staleRunExpired) {
            state = StrategyEngine.recoverExpiredPending(state, snapshot.currentPeriod)
            saveState(prefs, state)
            prefs.edit()
                .remove("pending_period")
                .remove("pending_side")
                .remove("pending_strategy")
                .remove("pending_level")
                .remove("pending_session_key")
                .putString("current_prediction", "WAITING")
                .putString("current_strategy", "")
                .putString("current_period", snapshot.currentPeriod)
                .putInt("current_level", 1)
                .putString("recent_activity", "Previous run expired • unknown result not counted • Level 1 • SKIP 0")
                .putString("bot_status", if (pendingExpired) "OLD RESULT EXPIRED • Level 1 • SKIP 0 • Waiting for next period" else "MISSED PERIODS RECONCILED • Level 1 • SKIP 0 • Waiting for next period")
                .apply()
            pendingPeriod = null
        }

        if (pendingPeriod != null) {
            val actual = outcomes.firstOrNull { it.period == pendingPeriod }
            if (actual != null) {
                val side = runCatching { Side.valueOf(prefs.getString("pending_side", "")!!) }.getOrNull()
                val strategy = runCatching { Strategy.valueOf(prefs.getString("pending_strategy", "")!!) }.getOrNull()
                if (side != null && strategy != null) {
                    val prediction = Prediction(side, strategy)
                    val level = prefs.getInt("pending_level", state.level)
                    val sessionKey = prefs.getString("pending_session_key", "").orEmpty()
                    val outcome = if (side == actual.side) "WIN" else "LOSS"
                    state = StrategyEngine.onOutcome(state, pendingPeriod, prediction, actual.side)
                    saveState(prefs, state)
                    TelegramReports.result(
                        context, sessionKey, pendingPeriod,
                        prefs.getString("game_duration", "30 Seconds").orEmpty(),
                        level, prediction, outcome
                    )
                    prefs.edit().remove("pending_period").remove("pending_side")
                        .remove("pending_strategy").remove("pending_level")
                        .remove("pending_session_key")
                        .putString("current_prediction", "WAITING")
                        .apply()
                    val outcomeLabel = if (outcome == "WIN") "YOUR WIN!" else "YOUR LOSS"
                    prefs.edit()
                        .putString("result_flash", outcome)
                        .putLong("result_flash_id", System.currentTimeMillis())
                        .putString(
                            "recent_activity",
                            "$pendingPeriod • ${prediction.side} • Level $level • ${prediction.strategy.title} • $outcomeLabel"
                        ).apply()
                }
            }
        }

        // When the phone was asleep during an active SKIP sequence, consume
        // every period that has already elapsed in BDG's visible history, not
        // just one period on resume. Do not emit predictions for missed periods.
        val pendingStillExistsBeforeCatchUp = prefs.getString("pending_period", null) != null
        if (!pendingStillExistsBeforeCatchUp && state.skipRemaining > 0 && state.lastProcessedPeriod != null) {
            val elapsed = outcomes.count { comparePeriodIds(it.period, state.lastProcessedPeriod!!) > 0 }
            val currentIsNew = comparePeriodIds(snapshot.currentPeriod, state.lastProcessedPeriod!!) > 0
            val periodsToConsume = (elapsed + if (currentIsNew && outcomes.none { it.period == snapshot.currentPeriod }) 1 else 0)
            if (periodsToConsume > 0) {
                val remaining = (state.skipRemaining - periodsToConsume).coerceAtLeast(0)
                state = state.copy(
                    skipRemaining = remaining,
                    skipTotal = if (remaining == 0) 0 else state.skipTotal,
                    lastProcessedPeriod = snapshot.currentPeriod
                )
                saveState(prefs, state)
                prefs.edit()
                    .putString("current_prediction", if (remaining > 0) "SKIP" else "WAITING")
                    .putString("current_strategy", "")
                    .putString("current_period", snapshot.currentPeriod)
                    .putInt("current_level", state.level)
                    .putString("bot_status", if (remaining > 0) "SKIP CAUGHT UP • $remaining remaining" else "SKIP COMPLETE • Waiting for next period")
                    .apply()
                if (sessionKeyOrNullForCatchUp() != null) {
                    TelegramReports.skip(context, sessionKeyOrNullForCatchUp()!!, snapshot.currentPeriod, remaining, state.level)
                }
                return prefs.getString("bot_status", "BDG DATA DETECTED").orEmpty()
            }
        }

        val pendingStillExists = prefs.getString("pending_period", null) != null
        val sessionKey = activeSessionKey()
        // Do not stop here outside Telegram hours: the local prediction engine
        // and dashboard continue to process every new BDG period all day.
        if (!pendingStillExists && snapshot.currentPeriod != state.lastProcessedPeriod) {
            val newestThree = outcomes.take(3).asReversed().map { it.side }
            val update = StrategyEngine.onNewPeriod(state, snapshot.currentPeriod, newestThree)
            state = update.state
            saveState(prefs, state)
            val prediction = update.prediction
            if (prediction != null) {
                val level = state.level
                prefs.edit()
                    .putString("pending_period", snapshot.currentPeriod)
                    .putString("pending_side", prediction.side.name)
                    .putString("pending_strategy", prediction.strategy.name)
                    .putInt("pending_level", level)
                    .putString("pending_session_key", sessionKey.orEmpty())
                    .putString("current_prediction", prediction.side.name)
                    .putString("current_strategy", prediction.strategy.title)
                    .putString("current_period", snapshot.currentPeriod)
                    .putInt("current_level", level)
                    .apply()
                if (sessionKey != null) {
                    TelegramReports.prediction(
                        context, sessionKey, snapshot.currentPeriod,
                        prefs.getString("game_duration", "30 Seconds").orEmpty(),
                        level, prediction
                    )
                }
                prefs.edit().putString(
                    "bot_status",
                    if (sessionKey != null && TelegramReports.canSendSession(context, sessionKey))
                        "PREDICTION ACTIVE • ${prediction.side}"
                    else "DASHBOARD PREDICTION ACTIVE • ${prediction.side}"
                ).apply()
            } else {
                prefs.edit().putString("current_prediction", if (update.status.startsWith("SKIP")) "SKIP" else "WAITING")
                    .putString("current_strategy", "")
                    .putString("current_period", snapshot.currentPeriod)
                    .putInt("current_level", state.level)
                    .putString("bot_status", update.status).apply()
                if (sessionKey != null) {
                    TelegramReports.skip(context, sessionKey, snapshot.currentPeriod, state.skipRemaining, state.level)
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

    /** Compare numeric BDG period IDs without overflowing a Long. */
    private fun comparePeriodIds(a: String, b: String): Int {
        val left = a.trimStart('0')
        val right = b.trimStart('0')
        return when {
            left.length != right.length -> left.length.compareTo(right.length)
            else -> left.compareTo(right)
        }
    }

    private fun sessionKeyOrNullForCatchUp(): String? = activeSessionKey()

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