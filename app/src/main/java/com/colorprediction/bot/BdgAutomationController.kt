package com.colorprediction.bot

import android.content.Context

/**
 * Processes each newly confirmed BDG period. Telegram reporting has no clock,
 * session, win quota or daily cap; the monitor controls when this is invoked.
 */
object BdgAutomationController {
    @Synchronized
    fun process(context: Context, snapshot: BdgPageSnapshot): String {
        val prefs = context.getSharedPreferences("bot_settings", Context.MODE_PRIVATE)
        var state = loadState(prefs)
        var pendingPeriod = prefs.getString("pending_period", null)
        val outcomes = snapshot.recentOutcomes
        var resolvedPendingThisCycle = false

        val detectedDuration = snapshot.gameDuration
        val activeDuration = prefs.getString("active_game_duration", null)
        if (detectedDuration != null && detectedDuration != activeDuration) {
            state = StrategyEngine.resetForGameDuration(snapshot.currentPeriod)
            saveState(prefs, state)
            prefs.edit()
                .putString("active_game_duration", detectedDuration)
                .putString("game_duration", detectedDuration)
                .remove("pending_period").remove("pending_side").remove("pending_strategy").remove("pending_level").remove("pending_telegram_bin")
                .putString("current_prediction", "WAITING")
                .putString("current_strategy", "")
                .putString("current_period", snapshot.currentPeriod)
                .putInt("current_level", 1)
                .putString("recent_activity", "Game changed to $detectedDuration • waiting for next period")
                .putString("bot_status", "$detectedDuration SELECTED • HISTORY REFRESHED • Waiting for next period")
                .apply()
            return prefs.getString("bot_status", "GAME DURATION UPDATED").orEmpty()
        }

        // Do not invent a result if a pending period has disappeared from the
        // visible history. Re-baseline safely and continue on the next period.
        val oldestVisiblePeriod = outcomes.lastOrNull()?.period
        val pendingExpired = pendingPeriod != null && oldestVisiblePeriod != null &&
            comparePeriodIds(pendingPeriod, oldestVisiblePeriod) < 0
        val skipHistoryExpired = state.lastProcessedPeriod != null && oldestVisiblePeriod != null &&
            comparePeriodIds(state.lastProcessedPeriod, oldestVisiblePeriod) < 0
        if (pendingExpired || skipHistoryExpired) {
            state = StrategyEngine.recoverExpiredPending(state, snapshot.currentPeriod)
            saveState(prefs, state)
            prefs.edit()
                .remove("pending_period").remove("pending_side").remove("pending_strategy").remove("pending_level").remove("pending_telegram_bin")
                .putString("current_prediction", "WAITING")
                .putString("current_strategy", "")
                .putString("current_period", snapshot.currentPeriod)
                .putInt("current_level", state.level)
                .putString("recent_activity", "History gap • unknown result not counted • monitoring continues")
                .putString("bot_status", "HISTORY RECONCILED • Waiting for next period")
                .apply()
            pendingPeriod = null
        }

        if (pendingPeriod != null) {
            val actual = outcomes.firstOrNull { it.period == pendingPeriod }
            if (actual != null) {
                val side = runCatching { Side.valueOf(prefs.getString("pending_side", "")!!) }.getOrNull()
                val strategy = runCatching { Strategy.valueOf(prefs.getString("pending_strategy", "")!!) }.getOrNull()
                if (side != null && strategy != null) {
                    resolvedPendingThisCycle = true
                    val prediction = Prediction(side, strategy)
                    val level = prefs.getInt("pending_level", state.level)
                    val outcome = if (side == actual.side) "WIN" else "LOSS"
                    state = StrategyEngine.onOutcome(state, pendingPeriod, prediction, actual.side, prefs = prefs)
                    saveState(prefs, state)
                    val countsAsTelegramPrediction = prefs.getBoolean("pending_telegram_bin", false)
                    val winsBefore = prefs.getInt("telegram_wins_completed", 0)
                    val completedTelegramWins = if (countsAsTelegramPrediction && outcome == "WIN") {
                        winsBefore + 1
                    } else {
                        winsBefore
                    }
                    if (countsAsTelegramPrediction && outcome == "WIN") {
                        prefs.edit().putInt("telegram_wins_completed", completedTelegramWins).apply()
                    }
                    val finalTelegramWin = countsAsTelegramPrediction &&
                        outcome == "WIN" && completedTelegramWins >= 10
                    TelegramReports.result(
                        context, pendingPeriod,
                        prefs.getString("game_duration", "30 Seconds").orEmpty(),
                        level, prediction, outcome, finalBin = finalTelegramWin
                    )
                    prefs.edit().remove("pending_period").remove("pending_side")
                        .remove("pending_strategy").remove("pending_level").remove("pending_telegram_bin")
                        .putString("current_prediction", "WAITING").apply()
                    prefs.edit()
                        .putString("result_flash", outcome)
                        .putLong("result_flash_id", System.currentTimeMillis())
                        .putString("recent_activity", "$pendingPeriod • ${prediction.side} • Label $level • $outcome")
                        .apply()

                    if (finalTelegramWin) {
                        // The tenth confirmed WIN sends its result, then pauses all
                        // automated Telegram reports. BDG monitoring continues.
                        prefs.edit()
                            .putBoolean("stop_after_win_requested", false)
                            .putBoolean("predictions_stopped", false)
                            .putBoolean("telegram_predictions_paused", true)
                            .putString("current_strategy", "")
                            .putString("bot_status", "BDG READING ACTIVE • 10 Telegram wins completed")
                            .putString("telegram_status", "COMPLETED • 10/10 WINS")
                            .putString("recent_activity", "$pendingPeriod • Label $level • WIN • Telegram 10/10 wins complete")
                            .apply()
                        return "BDG READING ACTIVE • Telegram 10/10 wins complete"
                    }
                }
            }
        }

        // STOP pauses the prediction sequence only. Keep processing a pending
        // period above so its final WIN/LOSS can still be reported, then do not
        // create another prediction. The foreground BDG reader keeps running.
        if (prefs.getBoolean("predictions_stopped", false)) {
            // Migrate a previously persisted global stop into a Telegram-only
            // pause so old app state cannot freeze the live dashboard.
            prefs.edit()
                .putBoolean("predictions_stopped", false)
                .putBoolean("telegram_predictions_paused", true)
                .putString("bot_status", "BDG READING ACTIVE • live dashboard predictions continue")
                .apply()
        }

        // Consume already elapsed skip periods after a pause/screen-off without
        // creating predictions for periods that have already closed.
        val pendingStillExistsBeforeCatchUp = prefs.getString("pending_period", null) != null
        if (!pendingStillExistsBeforeCatchUp && !resolvedPendingThisCycle &&
            state.skipRemaining > 0 && state.lastProcessedPeriod != null) {
            val elapsed = outcomes.count { comparePeriodIds(it.period, state.lastProcessedPeriod!!) > 0 }
            val currentIsNew = comparePeriodIds(snapshot.currentPeriod, state.lastProcessedPeriod!!) > 0
            val periodsToConsume = elapsed + if (currentIsNew && outcomes.none { it.period == snapshot.currentPeriod }) 1 else 0
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
                    .putString("bot_status", if (remaining > 0) "SKIP • $remaining remaining" else "SKIP COMPLETE • Waiting for next period")
                    .apply()
                TelegramReports.skip(context, snapshot.currentPeriod, remaining, state.level)
                return prefs.getString("bot_status", "BDG DATA DETECTED").orEmpty()
            }
        }

        val pendingStillExists = prefs.getString("pending_period", null) != null
        if (!pendingStillExists && snapshot.currentPeriod != state.lastProcessedPeriod) {
            val customConfig = StrategyConfigStore.load(prefs, Strategy.CUSTOM)
            val history = StrategyEngine.patternFromRecentResults(outcomes.map { it.side }, customConfig.patternLength)
            val update = StrategyEngine.onNewPeriod(state, snapshot.currentPeriod, history, prefs, StrategyConfigStore.isCustomEnabled(prefs), customConfig)
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
                    .putString("current_prediction", prediction.side.name)
                    .putString("current_strategy", prediction.strategy.title)
                    .putString("current_period", snapshot.currentPeriod)
                    .putInt("current_level", level)
                    .apply()
                val telegramConfigured = prefs.getString("tg_token", "").orEmpty().isNotBlank() &&
                    prefs.getString("tg_chat", "").orEmpty().isNotBlank()
                val countsAsTelegramPrediction = telegramConfigured &&
                    !prefs.getBoolean("telegram_predictions_paused", false)
                prefs.edit().putBoolean("pending_telegram_bin", countsAsTelegramPrediction).apply()
                TelegramReports.prediction(
                    context, snapshot.currentPeriod,
                    prefs.getString("game_duration", "30 Seconds").orEmpty(),
                    level, prediction
                )
                prefs.edit().putString("bot_status", "PREDICTION ACTIVE • ${prediction.side}").apply()
            } else {
                prefs.edit()
                    .putString("current_prediction", if (update.status.startsWith("SKIP")) "SKIP" else "WAITING")
                    .putString("current_strategy", "")
                    .putString("current_period", snapshot.currentPeriod)
                    .putInt("current_level", state.level)
                    .putString("bot_status", update.status)
                    .apply()
                if (!update.status.startsWith("CUSTOM STRATEGY")) {
                    TelegramReports.skip(context, snapshot.currentPeriod, state.skipRemaining, state.level)
                }
            }
        }
        return prefs.getString("bot_status", "BDG DATA DETECTED").orEmpty()
    }

    private fun loadState(prefs: android.content.SharedPreferences) = EngineState(
        level = prefs.getInt("engine_level", 1).coerceAtLeast(1),
        skipRemaining = prefs.getInt("engine_skip", 0).coerceAtLeast(0),
        skipTotal = prefs.getInt("engine_skip_total", 0).coerceAtLeast(0),
        pausedUntilMillis = 0L,
        lastBetPeriod = prefs.getString("engine_last_bet_period", null),
        lastProcessedPeriod = prefs.getString("engine_last_processed_period", null),
        lastPrediction = prefs.getString("engine_last_prediction", null)?.let { runCatching { Side.valueOf(it) }.getOrNull() },
        lastStrategy = prefs.getString("engine_last_strategy", null)?.let { runCatching { Strategy.valueOf(it) }.getOrNull() },
        lastOutcome = prefs.getString("engine_last_outcome", null),
        customSkipRemaining = prefs.getInt("engine_custom_skip", 0).coerceAtLeast(0)
    )

    private fun saveState(prefs: android.content.SharedPreferences, state: EngineState) {
        prefs.edit().putInt("engine_level", state.level)
            .putInt("engine_skip", state.skipRemaining)
            .putInt("engine_skip_total", state.skipTotal)
            .putLong("engine_pause_until", 0L)
            .putString("engine_last_bet_period", state.lastBetPeriod)
            .putString("engine_last_processed_period", state.lastProcessedPeriod)
            .putString("engine_last_prediction", state.lastPrediction?.name)
            .putString("engine_last_strategy", state.lastStrategy?.name)
            .putString("engine_last_outcome", state.lastOutcome)
            .putInt("engine_custom_skip", state.customSkipRemaining)
            .putInt("current_level", state.level)
            .apply()
    }

    private fun comparePeriodIds(a: String, b: String): Int {
        val left = a.trimStart('0')
        val right = b.trimStart('0')
        return when {
            left.length != right.length -> left.length.compareTo(right.length)
            else -> left.compareTo(right)
        }
    }
}