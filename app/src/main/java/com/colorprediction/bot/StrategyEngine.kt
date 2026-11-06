package com.colorprediction.bot

import android.content.SharedPreferences

/** Pattern decision engine. BDG history is passed newest-first. */
enum class Side { BIG, SMALL }
enum class Strategy(val title: String) {
    TREND_BASE("Strategy 1"),
    ONE_BY_ONE("Strategy 2"),
    LAST_THREE_A("Strategy 4"),
    LAST_THREE_B("Strategy 3"),
    CUSTOM("Custom Strategy")
}

data class Prediction(val side: Side, val strategy: Strategy)
data class EngineState(
    val level: Int = 1,
    val skipRemaining: Int = 0,
    val skipTotal: Int = 0,
    val pausedUntilMillis: Long = 0L,
    val lastBetPeriod: String? = null,
    val lastProcessedPeriod: String? = null,
    val lastPrediction: Side? = null,
    val lastStrategy: Strategy? = null,
    val lastOutcome: String? = null
)
data class EngineUpdate(val state: EngineState, val prediction: Prediction? = null, val status: String)

object StrategyEngine {
    fun patternFromRecentResults(newestFirst: List<Side>): List<Side> = newestFirst.take(3)

    fun resetForGameDuration(latestPeriod: String): EngineState =
        EngineState(lastProcessedPeriod = latestPeriod)

    fun recoverExpiredPending(state: EngineState, latestPeriod: String): EngineState =
        state.copy(level = 1, skipRemaining = 0, skipTotal = 0, pausedUntilMillis = 0L, lastProcessedPeriod = latestPeriod)

    fun predict(lastThree: List<Side>): Prediction? = predict(lastThree, null, null)

    /** Predict using the four established strategies only. */
    fun predict(lastThree: List<Side>, prefs: SharedPreferences?, lastPrediction: Side?): Prediction? {
        if (lastThree.size != 3) return null
        val pattern = lastThree.joinToString("") { if (it == Side.BIG) "B" else "S" }
        val builtIns = listOf(Strategy.TREND_BASE, Strategy.ONE_BY_ONE, Strategy.LAST_THREE_B, Strategy.LAST_THREE_A)
        for (strategy in builtIns) {
            val config = if (prefs == null) StrategyConfigStore.defaults(strategy) else StrategyConfigStore.load(prefs, strategy)
            val rule = config.rules.firstOrNull { it.pattern == pattern }
            if (rule != null) return Prediction(rule.prediction, strategy)
        }
        return null
    }

    /** Custom strategy is used only as a substitute during an enabled built-in skip window. */
    fun predictCustom(
        lastThree: List<Side>,
        config: StrategyConfig,
        lastPrediction: Side?
    ): Prediction? {
        if (lastThree.size != 3) return null
        val pattern = lastThree.joinToString("") { if (it == Side.BIG) "B" else "S" }
        return when (config.mode) {
            StrategyMode.PATTERN -> config.rules.firstOrNull { it.pattern == pattern }
                ?.let { Prediction(it.prediction, Strategy.CUSTOM) }
            StrategyMode.ALTERNATE -> Prediction(
                if (lastPrediction == null) config.startingSide
                else if (lastPrediction == Side.BIG) Side.SMALL else Side.BIG,
                Strategy.CUSTOM
            )
            StrategyMode.FOLLOW_LAST -> Prediction(lastThree.first(), Strategy.CUSTOM)
        }
    }

    fun onNewPeriod(
        state: EngineState,
        period: String,
        lastThree: List<Side>,
        prefs: SharedPreferences? = null,
        customStrategyEnabled: Boolean = false,
        customConfig: StrategyConfig? = null
    ): EngineUpdate {
        if (period == state.lastProcessedPeriod) return EngineUpdate(state, status = "Waiting for a new period")
        if (state.lastProcessedPeriod == null) {
            return EngineUpdate(state.copy(lastProcessedPeriod = period), status = "SKIP • Initial history loaded; waiting for next period")
        }
        if (state.skipRemaining > 0) {
            val remaining = state.skipRemaining - 1
            val next = state.copy(skipRemaining = remaining, lastProcessedPeriod = period)
            if (customStrategyEnabled) {
                val config = customConfig ?: if (prefs == null) StrategyConfigStore.defaults(Strategy.CUSTOM)
                    else StrategyConfigStore.load(prefs, Strategy.CUSTOM)
                val customPrediction = predictCustom(lastThree, config, state.lastPrediction)
                if (customPrediction != null) {
                    return EngineUpdate(next, customPrediction, "CUSTOM STRATEGY ACTIVE • ${remaining} SKIP PERIODS LEFT")
                }
                return EngineUpdate(next, status = "CUSTOM STRATEGY • Pattern not matched")
            }
            return EngineUpdate(next, status = "SKIP • ${remaining} remaining")
        }
        val prediction = predict(lastThree, prefs, state.lastPrediction)
            ?: return EngineUpdate(state.copy(lastProcessedPeriod = period), status = "SKIP • No configured pattern")
        return EngineUpdate(state.copy(lastProcessedPeriod = period), prediction, "PREDICTION READY")
    }

    fun onOutcome(
        state: EngineState,
        period: String,
        prediction: Prediction,
        actual: Side,
        now: Long = System.currentTimeMillis(),
        prefs: SharedPreferences? = null
    ): EngineState {
        val win = prediction.side == actual
        // A custom prediction fills one of the built-in strategy's already
        // scheduled skip periods. Its result must not reset or extend that
        // original skip window or alter the original strategy's level.
        if (prediction.strategy == Strategy.CUSTOM && state.skipTotal > 0) {
            return state.copy(
                skipRemaining = state.skipRemaining,
                skipTotal = if (state.skipRemaining > 0) state.skipTotal else 0,
                lastBetPeriod = period,
                lastPrediction = prediction.side,
                lastStrategy = Strategy.CUSTOM,
                lastOutcome = if (win) "WIN" else "LOSS"
            )
        }
        if (win) return state.copy(
            level = 1, skipRemaining = 0, skipTotal = 0, pausedUntilMillis = 0L,
            lastBetPeriod = period, lastPrediction = prediction.side,
            lastStrategy = prediction.strategy, lastOutcome = "WIN"
        )
        val skips = StrategyConfigStore.effectiveSkip(prefs, prediction.strategy, state.level)
        return state.copy(
            level = state.level + 1, skipRemaining = skips, skipTotal = skips,
            pausedUntilMillis = 0L, lastBetPeriod = period,
            lastPrediction = prediction.side, lastStrategy = prediction.strategy, lastOutcome = "LOSS"
        )
    }
}
