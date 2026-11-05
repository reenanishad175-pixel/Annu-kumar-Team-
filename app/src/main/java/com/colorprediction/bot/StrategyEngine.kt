package com.colorprediction.bot

/**
 * Pure decision/state engine. Results are ordered oldest -> newest.
 * No prediction is emitted unless one of the user's eight exact patterns matches.
 */
enum class Side { BIG, SMALL }
enum class Strategy(val title: String) {
    TREND_BASE("Strategy 1 • Trend Base"),
    ONE_BY_ONE("Strategy 2 • One by One"),
    LAST_THREE_A("Strategy 3 • Last 3 Period"),
    LAST_THREE_B("Strategy 4 • Last 3 Period")
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

data class EngineUpdate(
    val state: EngineState,
    val prediction: Prediction? = null,
    val status: String
)

object StrategyEngine {
    fun predict(lastThree: List<Side>): Prediction? {
        if (lastThree.size != 3) return null
        val (a, b, c) = lastThree
        return when {
            a == Side.SMALL && b == Side.SMALL && c == Side.SMALL ->
                Prediction(Side.SMALL, Strategy.TREND_BASE)
            a == Side.BIG && b == Side.BIG && c == Side.BIG ->
                Prediction(Side.BIG, Strategy.TREND_BASE)

            a == Side.BIG && b == Side.SMALL && c == Side.BIG ->
                Prediction(Side.SMALL, Strategy.ONE_BY_ONE)
            a == Side.SMALL && b == Side.BIG && c == Side.SMALL ->
                Prediction(Side.BIG, Strategy.ONE_BY_ONE)

            a == Side.SMALL && b == Side.BIG && c == Side.BIG ->
                Prediction(Side.SMALL, Strategy.LAST_THREE_A)
            a == Side.BIG && b == Side.SMALL && c == Side.SMALL ->
                Prediction(Side.BIG, Strategy.LAST_THREE_A)

            a == Side.BIG && b == Side.BIG && c == Side.SMALL ->
                Prediction(Side.SMALL, Strategy.LAST_THREE_B)
            a == Side.SMALL && b == Side.SMALL && c == Side.BIG ->
                Prediction(Side.BIG, Strategy.LAST_THREE_B)
            else -> null
        }
    }

    /** Called once for each newly confirmed result period, never for duplicate periods. */
    fun onNewPeriod(state: EngineState, period: String, lastThree: List<Side>): EngineUpdate {
        if (period == state.lastProcessedPeriod) return EngineUpdate(state, status = "Waiting for a new period")
        if (state.pausedUntilMillis > System.currentTimeMillis()) {
            return EngineUpdate(state.copy(lastProcessedPeriod = period), status = "PAUSED • Level 8 loss cooldown")
        }
        if (state.skipRemaining > 0) {
            val next = state.copy(skipRemaining = state.skipRemaining - 1, lastProcessedPeriod = period)
            return EngineUpdate(next, status = "SKIP • ${next.skipRemaining} remaining")
        }
        val prediction = predict(lastThree)
            ?: return EngineUpdate(state.copy(lastProcessedPeriod = period), status = "SKIP • No configured pattern")
        return EngineUpdate(state.copy(lastProcessedPeriod = period), prediction, "PREDICTION READY")
    }

    /** Apply a confirmed outcome to the currently tracked bet. */
    fun onOutcome(state: EngineState, period: String, prediction: Prediction, actual: Side, now: Long = System.currentTimeMillis()): EngineState {
        val win = prediction.side == actual
        if (win) {
            return state.copy(
                level = 1, skipRemaining = 0, skipTotal = 0, lastBetPeriod = period,
                lastPrediction = prediction.side, lastStrategy = prediction.strategy, lastOutcome = "WIN"
            )
        }
        if (state.level >= 8) {
            return state.copy(
                skipRemaining = 0, skipTotal = 0, pausedUntilMillis = now + 60L * 60L * 1000L,
                lastBetPeriod = period, lastPrediction = prediction.side,
                lastStrategy = prediction.strategy, lastOutcome = "LOSS"
            )
        }
        val nextLevel = state.level + 1
        val skips = when (prediction.strategy) {
            Strategy.TREND_BASE -> 4
            Strategy.ONE_BY_ONE, Strategy.LAST_THREE_A -> 1
            Strategy.LAST_THREE_B -> if (state.level == 1) 0 else 1
        }
        return state.copy(
            level = nextLevel, skipRemaining = skips, skipTotal = skips, lastBetPeriod = period,
            lastPrediction = prediction.side, lastStrategy = prediction.strategy, lastOutcome = "LOSS"
        )
    }
}
