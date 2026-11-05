package com.colorprediction.bot

/**
 * Pure decision/state engine. Results are ordered oldest -> newest.
 * No prediction is emitted unless one of the user's eight exact patterns matches.
 */
enum class Side { BIG, SMALL }
enum class Strategy(val title: String) {
    TREND_BASE("Strategy 1 • Trend Base"),
    ONE_BY_ONE("Strategy 2 • One by One"),
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

data class EngineUpdate(
    val state: EngineState,
    val prediction: Prediction? = null,
    val status: String
)

object StrategyEngine {
    /** BDG history is newest-first. The three latest entries form the current pattern; reverse them only to match the engine's oldest-to-newest convention. */
    fun patternFromRecentResults(newestFirst: List<Side>): List<Side> =
        newestFirst.take(3).asReversed()

    /** Start clean on a newly selected WinGo duration; old bets/skips never carry across games. */
    fun resetForGameDuration(latestPeriod: String): EngineState = EngineState(
        level = 1, skipRemaining = 0, skipTotal = 0, lastProcessedPeriod = latestPeriod
    )

    /** Recover safely when a pending period has disappeared from BDG history. */
    fun recoverExpiredPending(state: EngineState, latestPeriod: String): EngineState =
        state.copy(
            level = 1,
            skipRemaining = 0,
            skipTotal = 0,
            lastProcessedPeriod = latestPeriod
        )

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
        // First successful BDG history read is a baseline only. Never place a
        // prediction on a period that was already open when the app started.
        if (state.lastProcessedPeriod == null) {
            return EngineUpdate(
                state.copy(lastProcessedPeriod = period),
                status = "SKIP • Initial history loaded; waiting for next period"
            )
        }
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
        // Keep the skip policy identical at every level, including Level 8.
        // Strategy 4 skips one period; Strategy 3 skips two.
        // Trend Base keeps its Level 1 / Level 2-8 rule.
        val skips = when (prediction.strategy) {
            Strategy.TREND_BASE -> if (state.level >= 2) 5 else 0
            Strategy.ONE_BY_ONE, Strategy.LAST_THREE_A -> 1
            Strategy.LAST_THREE_B -> 2
        }
        if (state.level >= 8) {
            return state.copy(
                skipRemaining = skips,
                skipTotal = skips,
                pausedUntilMillis = now + 60L * 60L * 1000L,
                lastBetPeriod = period, lastPrediction = prediction.side,
                lastStrategy = prediction.strategy, lastOutcome = "LOSS"
            )
        }
        val nextLevel = state.level + 1
        return state.copy(
            level = nextLevel, skipRemaining = skips, skipTotal = skips, lastBetPeriod = period,
            lastPrediction = prediction.side, lastStrategy = prediction.strategy, lastOutcome = "LOSS"
        )
    }
}
