package com.colorprediction.bot

/** Pattern decision engine. BDG history is passed newest-first. */
enum class Side { BIG, SMALL }
enum class Strategy(val title: String) {
    TREND_BASE("Strategy 1"),
    ONE_BY_ONE("Strategy 2"),
    LAST_THREE_A("Strategy 4"),
    LAST_THREE_B("Strategy 3")
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

    fun predict(lastThree: List<Side>): Prediction? {
        if (lastThree.size != 3) return null
        val (a, b, c) = lastThree
        return when {
            a == Side.SMALL && b == Side.SMALL && c == Side.SMALL -> Prediction(Side.SMALL, Strategy.TREND_BASE)
            a == Side.BIG && b == Side.BIG && c == Side.BIG -> Prediction(Side.BIG, Strategy.TREND_BASE)
            a == Side.BIG && b == Side.SMALL && c == Side.BIG -> Prediction(Side.SMALL, Strategy.ONE_BY_ONE)
            a == Side.SMALL && b == Side.BIG && c == Side.SMALL -> Prediction(Side.BIG, Strategy.ONE_BY_ONE)
            a == Side.SMALL && b == Side.BIG && c == Side.BIG -> Prediction(Side.SMALL, Strategy.LAST_THREE_A)
            a == Side.BIG && b == Side.SMALL && c == Side.SMALL -> Prediction(Side.BIG, Strategy.LAST_THREE_A)
            a == Side.BIG && b == Side.BIG && c == Side.SMALL -> Prediction(Side.SMALL, Strategy.LAST_THREE_B)
            a == Side.SMALL && b == Side.SMALL && c == Side.BIG -> Prediction(Side.BIG, Strategy.LAST_THREE_B)
            else -> null
        }
    }

    fun onNewPeriod(state: EngineState, period: String, lastThree: List<Side>): EngineUpdate {
        if (period == state.lastProcessedPeriod) return EngineUpdate(state, status = "Waiting for a new period")
        if (state.lastProcessedPeriod == null) {
            return EngineUpdate(state.copy(lastProcessedPeriod = period), status = "SKIP • Initial history loaded; waiting for next period")
        }
        if (state.skipRemaining > 0) {
            val next = state.copy(skipRemaining = state.skipRemaining - 1, lastProcessedPeriod = period)
            return EngineUpdate(next, status = "SKIP • ${next.skipRemaining} remaining")
        }
        val prediction = predict(lastThree)
            ?: return EngineUpdate(state.copy(lastProcessedPeriod = period), status = "SKIP • No configured pattern")
        return EngineUpdate(state.copy(lastProcessedPeriod = period), prediction, "PREDICTION READY")
    }

    fun onOutcome(state: EngineState, period: String, prediction: Prediction, actual: Side, now: Long = System.currentTimeMillis()): EngineState {
        val win = prediction.side == actual
        if (win) return state.copy(
            level = 1, skipRemaining = 0, skipTotal = 0, pausedUntilMillis = 0L,
            lastBetPeriod = period, lastPrediction = prediction.side,
            lastStrategy = prediction.strategy, lastOutcome = "WIN"
        )
        val skips = when (prediction.strategy) {
            Strategy.TREND_BASE -> if (state.level >= 2) 5 else 0
            Strategy.ONE_BY_ONE -> 1
            Strategy.LAST_THREE_A, Strategy.LAST_THREE_B -> 0
        }
        // No Level 8 stop or timed cooldown: the monitor keeps running without a cap.
        return state.copy(
            level = state.level + 1, skipRemaining = skips, skipTotal = skips,
            pausedUntilMillis = 0L, lastBetPeriod = period,
            lastPrediction = prediction.side, lastStrategy = prediction.strategy, lastOutcome = "LOSS"
        )
    }
}