package com.colorprediction.bot

import android.content.SharedPreferences

/** Pattern decision engine. BDG history is passed newest-first. */
enum class Side { BIG, SMALL }
enum class Strategy(val title: String) {
    ONE_BY_ONE("One-by-One Pattern"),
    FOLLOW_UP("Follow Up"),
    CUSTOM("Custom Strategy"),
    // Retained only so preferences saved by older app versions can be read safely.
    TREND_BASE("Legacy Strategy 1"),
    LAST_THREE_A("Legacy Strategy 4"),
    LAST_THREE_B("Legacy Strategy 3")
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
    val lastOutcome: String? = null,
    val customSkipRemaining: Int = 0
)
data class EngineUpdate(val state: EngineState, val prediction: Prediction? = null, val status: String)

object StrategyEngine {
    fun patternFromRecentResults(newestFirst: List<Side>, count: Int = 3): List<Side> = newestFirst.take(count.coerceIn(3, 5))

    fun resetForGameDuration(latestPeriod: String): EngineState =
        EngineState(lastProcessedPeriod = latestPeriod)

    fun recoverExpiredPending(state: EngineState, latestPeriod: String): EngineState =
        state.copy(level = 1, skipRemaining = 0, skipTotal = 0, customSkipRemaining = 0, pausedUntilMillis = 0L, lastProcessedPeriod = latestPeriod)

    /**
     * Consume only skip periods that have already closed while monitoring was
     * paused. The currentPeriod is still open and must be handled by onNewPeriod
     * so an enabled Custom strategy can replace the built-in skip there.
     */
    fun catchUpClosedSkipPeriods(state: EngineState, closedPeriods: List<String>): EngineState {
        val lastProcessed = state.lastProcessedPeriod ?: return state
        if (state.skipRemaining <= 0) return state
        val elapsed = closedPeriods.filter { comparePeriodIds(it, lastProcessed) > 0 }
        if (elapsed.isEmpty()) return state
        val latestClosed = elapsed.maxWithOrNull { a, b -> comparePeriodIds(a, b) } ?: return state
        val consumed = minOf(state.skipRemaining, elapsed.size)
        val remaining = state.skipRemaining - consumed
        return state.copy(
            skipRemaining = remaining,
            skipTotal = if (remaining == 0) 0 else state.skipTotal,
            lastProcessedPeriod = latestClosed
        )
    }

    private fun comparePeriodIds(a: String, b: String): Int {
        val left = a.trimStart('0')
        val right = b.trimStart('0')
        return when {
            left.length != right.length -> left.length.compareTo(right.length)
            else -> left.compareTo(right)
        }
    }

    fun predict(lastThree: List<Side>): Prediction? = predict(lastThree, null, null)

    /** One-by-One takes priority when the last three results alternate; Follow Up is the fallback. */
    fun predict(lastThree: List<Side>, prefs: SharedPreferences?, lastPrediction: Side?, currentLevel: Int = 1): Prediction? {
        if (lastThree.size != 3) return null
        val oneByOneEnabled = StrategyConfigStore.isStrategyEnabled(prefs, Strategy.ONE_BY_ONE)
        val oneByOneConfig = if (prefs == null) StrategyConfigStore.defaults(Strategy.ONE_BY_ONE) else StrategyConfigStore.load(prefs, Strategy.ONE_BY_ONE)
        if (oneByOneEnabled && currentLevel <= oneByOneConfig.levelCount &&
            lastThree[0] != lastThree[1] && lastThree[1] != lastThree[2]) {
            val nextSide = if (lastThree[0] == Side.BIG) Side.SMALL else Side.BIG
            return Prediction(nextSide, Strategy.ONE_BY_ONE)
        }

        val followEnabled = StrategyConfigStore.isStrategyEnabled(prefs, Strategy.FOLLOW_UP)
        val followConfig = if (prefs == null) StrategyConfigStore.defaults(Strategy.FOLLOW_UP) else StrategyConfigStore.load(prefs, Strategy.FOLLOW_UP)
        if (followEnabled && currentLevel <= followConfig.levelCount) {
            return Prediction(lastThree.first(), Strategy.FOLLOW_UP)
        }
        return null
    }

    /** Custom strategy is used only as a substitute during an enabled built-in skip window. */
    fun predictCustom(
        lastThree: List<Side>,
        config: StrategyConfig,
        lastPrediction: Side?,
        previousLoss: Boolean = false
    ): Prediction? {
        if (lastThree.size < config.patternLength) return null
        val pattern = lastThree.take(config.patternLength).joinToString("") { if (it == Side.BIG) "B" else "S" }
        val base = when (config.mode) {
            StrategyMode.PATTERN -> config.rules.firstOrNull { it.pattern == pattern }?.let { Prediction(it.prediction, Strategy.CUSTOM) }
            StrategyMode.ALTERNATE -> Prediction(if (lastPrediction == null) config.startingSide else if (lastPrediction == Side.BIG) Side.SMALL else Side.BIG, Strategy.CUSTOM)
            StrategyMode.FOLLOW_LAST -> Prediction(lastThree.first(), Strategy.CUSTOM)
        }
        return if (base != null && config.reverseOnLoss && previousLoss) base.copy(side = if (base.side == Side.BIG) Side.SMALL else Side.BIG) else base
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
            val config = customConfig ?: if (prefs == null) StrategyConfigStore.defaults(Strategy.CUSTOM)
                else StrategyConfigStore.load(prefs, Strategy.CUSTOM)
            val next = state.copy(skipRemaining = remaining, skipTotal = if (remaining == 0) 0 else state.skipTotal,
                customSkipRemaining = (state.customSkipRemaining - 1).coerceAtLeast(0), lastProcessedPeriod = period)
            if (customStrategyEnabled) {
                if (state.customSkipRemaining > 0) return EngineUpdate(next, status = "CUSTOM SKIP • ${next.customSkipRemaining} remaining")
                val customPrediction = predictCustom(lastThree, config, if (state.lastStrategy == Strategy.CUSTOM) state.lastPrediction else null, state.lastStrategy == Strategy.CUSTOM && state.lastOutcome == "LOSS")
                if (customPrediction != null) return EngineUpdate(next, customPrediction, "CUSTOM STRATEGY ACTIVE • ${remaining} BUILT-IN SKIP PERIODS LEFT")
                return EngineUpdate(next, status = "CUSTOM STRATEGY • Pattern not matched")
            }
            return EngineUpdate(next, status = "SKIP • ${remaining} remaining")
        }
        val builtInEnabled = StrategyConfigStore.hasAnyBuiltInEnabled(prefs)
        val customEnabled = customStrategyEnabled || (prefs != null && StrategyConfigStore.isStrategyEnabled(prefs, Strategy.CUSTOM))
        if (state.customSkipRemaining > 0) {
            val next = state.copy(
                customSkipRemaining = state.customSkipRemaining - 1,
                lastProcessedPeriod = period
            )
            // A Custom-only skip must not switch off the two built-ins.
            // If built-ins are enabled, let their normal predictor run in
            // this period. If Custom is standalone, its own skip is a real skip.
            if (!builtInEnabled) {
                return EngineUpdate(next, status = "CUSTOM SKIP • ${next.customSkipRemaining} remaining")
            }
            // Built-ins retain first priority during a Custom-only skip.
            val builtInPrediction = predict(lastThree.take(3), prefs, next.lastPrediction, next.level)
            return if (builtInPrediction != null) {
                EngineUpdate(next, builtInPrediction, "PREDICTION READY")
            } else {
                EngineUpdate(next, status = "SKIP • No configured built-in pattern")
            }
        }
        val prediction = if (!builtInEnabled && customEnabled) {
            val config = customConfig ?: if (prefs == null) StrategyConfigStore.defaults(Strategy.CUSTOM) else StrategyConfigStore.load(prefs, Strategy.CUSTOM)
            predictCustom(lastThree, config, if (state.lastStrategy == Strategy.CUSTOM) state.lastPrediction else null, state.lastStrategy == Strategy.CUSTOM && state.lastOutcome == "LOSS")
        } else predict(lastThree.take(3), prefs, state.lastPrediction, state.level)
        if (prediction == null) return EngineUpdate(state.copy(lastProcessedPeriod = period), status = if (!builtInEnabled && customEnabled) "CUSTOM STRATEGY • Pattern not matched" else "SKIP • No configured pattern")
        return EngineUpdate(state.copy(lastProcessedPeriod = period), prediction, "PREDICTION READY")
    }

    fun onOutcome(
        state: EngineState,
        period: String,
        prediction: Prediction,
        actual: Side,
        now: Long = System.currentTimeMillis(),
        prefs: SharedPreferences? = null,
        customStandalone: Boolean = false
    ): EngineState {
        val win = prediction.side == actual
        // Every settled Custom prediction counts as a level in the active
        // sequence, whether Custom is standalone or replacing a built-in skip.
        // WIN resets the sequence; LOSS advances it by one.
        if (prediction.strategy == Strategy.CUSTOM) {
            val customSkips = if (win) 0 else StrategyConfigStore.effectiveSkip(prefs, Strategy.CUSTOM, state.level)
            val nextLevel = if (win || state.level >= 8) 1 else (state.level + 1).coerceAtMost(8)
            return state.copy(
                level = nextLevel,
                skipRemaining = state.skipRemaining,
                skipTotal = if (state.skipRemaining > 0) state.skipTotal else 0,
                customSkipRemaining = customSkips,
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
            level = if (state.level >= 8) 1 else (state.level + 1).coerceAtMost(8), skipRemaining = skips, skipTotal = skips,
            pausedUntilMillis = 0L, lastBetPeriod = period,
            lastPrediction = prediction.side, lastStrategy = prediction.strategy, lastOutcome = "LOSS"
        )
    }
}
