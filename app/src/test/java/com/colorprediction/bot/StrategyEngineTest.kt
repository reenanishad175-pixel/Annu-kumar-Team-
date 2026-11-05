package com.colorprediction.bot

import org.junit.Assert.*
import org.junit.Test

class StrategyEngineTest {
    private fun s(vararg values: Side) = values.toList()

    @Test fun allEightConfiguredPatternsMatchExactly() {
        assertEquals(Prediction(Side.SMALL, Strategy.TREND_BASE), StrategyEngine.predict(s(Side.SMALL, Side.SMALL, Side.SMALL)))
        assertEquals(Prediction(Side.BIG, Strategy.TREND_BASE), StrategyEngine.predict(s(Side.BIG, Side.BIG, Side.BIG)))
        assertEquals(Prediction(Side.SMALL, Strategy.ONE_BY_ONE), StrategyEngine.predict(s(Side.BIG, Side.SMALL, Side.BIG)))
        assertEquals(Prediction(Side.BIG, Strategy.ONE_BY_ONE), StrategyEngine.predict(s(Side.SMALL, Side.BIG, Side.SMALL)))
        assertEquals(Prediction(Side.SMALL, Strategy.LAST_THREE_A), StrategyEngine.predict(s(Side.SMALL, Side.BIG, Side.BIG)))
        assertEquals(Prediction(Side.BIG, Strategy.LAST_THREE_A), StrategyEngine.predict(s(Side.BIG, Side.SMALL, Side.SMALL)))
        assertEquals(Prediction(Side.SMALL, Strategy.LAST_THREE_B), StrategyEngine.predict(s(Side.BIG, Side.BIG, Side.SMALL)))
        assertEquals(Prediction(Side.BIG, Strategy.LAST_THREE_B), StrategyEngine.predict(s(Side.SMALL, Side.SMALL, Side.BIG)))
    }

    @Test fun topThreeHistoryResultsFormBothOneSkipPatterns() {
        // The screenshot may be taken after a result arrives; at prediction
        // time the current pattern is simply the three newest history rows.
        val bigTwoSmall = StrategyEngine.patternFromRecentResults(
            s(Side.SMALL, Side.SMALL, Side.BIG, Side.BIG, Side.SMALL)
        )
        assertEquals(s(Side.BIG, Side.SMALL, Side.SMALL), bigTwoSmall)
        val p1 = StrategyEngine.predict(bigTwoSmall)!!
        assertEquals(Strategy.LAST_THREE_A, p1.strategy)
        assertEquals(1, StrategyEngine.onOutcome(EngineState(), "P1", p1, Side.BIG, 1000L).skipRemaining)

        val smallTwoBig = StrategyEngine.patternFromRecentResults(
            s(Side.BIG, Side.BIG, Side.SMALL, Side.SMALL, Side.BIG)
        )
        assertEquals(s(Side.SMALL, Side.BIG, Side.BIG), smallTwoBig)
        val p2 = StrategyEngine.predict(smallTwoBig)!!
        assertEquals(Strategy.LAST_THREE_A, p2.strategy)
        assertEquals(1, StrategyEngine.onOutcome(EngineState(), "P2", p2, Side.BIG, 1000L).skipRemaining)
    }

    @Test fun dashboardPatternNumbersMatchTheirSkipRules() {
        val patternA = StrategyEngine.predict(s(Side.SMALL, Side.BIG, Side.BIG))!!
        val patternB = StrategyEngine.predict(s(Side.BIG, Side.BIG, Side.SMALL))!!
        assertEquals("Strategy 4", patternA.strategy.title)
        assertEquals("Strategy 3", patternB.strategy.title)
        assertEquals(1, StrategyEngine.onOutcome(EngineState(), "A", patternA, Side.BIG, 1000L).skipRemaining)
        assertEquals(2, StrategyEngine.onOutcome(EngineState(), "B", patternB, Side.BIG, 1000L).skipRemaining)
    }

    @Test fun changingGameDurationStartsFreshAtLatestPeriod() {
        val reset = StrategyEngine.resetForGameDuration("NEW_GAME_PERIOD")
        assertEquals(1, reset.level)
        assertEquals(0, reset.skipRemaining)
        assertEquals(0, reset.skipTotal)
        assertEquals(0L, reset.pausedUntilMillis)
        assertEquals("NEW_GAME_PERIOD", reset.lastProcessedPeriod)
        assertNull(reset.lastPrediction)
        assertNull(reset.lastStrategy)
    }

    @Test fun expiredPendingRecoveryResetsLevelAndSkipsAtLatestPeriod() {
        val original = EngineState(
            level = 6,
            skipRemaining = 2,
            skipTotal = 5,
            pausedUntilMillis = 123456L,
            lastProcessedPeriod = "OLD"
        )
        val recovered = StrategyEngine.recoverExpiredPending(original, "LATEST")
        assertEquals(1, recovered.level)
        assertEquals(0, recovered.skipRemaining)
        assertEquals(0, recovered.skipTotal)
        assertEquals("LATEST", recovered.lastProcessedPeriod)
        assertEquals(123456L, recovered.pausedUntilMillis)
    }

    @Test fun firstHistoryReadOnlyEstablishesBaselineAndSkips() {
        val result = StrategyEngine.onNewPeriod(
            EngineState(),
            "FIRST_OPEN_PERIOD",
            s(Side.BIG, Side.BIG, Side.BIG)
        )
        assertNull(result.prediction)
        assertEquals("FIRST_OPEN_PERIOD", result.state.lastProcessedPeriod)
        assertTrue(result.status.startsWith("SKIP"))
    }

    @Test fun unmatchedPatternDoesNotPredict() {
        assertNull(StrategyEngine.predict(s(Side.BIG, Side.SMALL, Side.SMALL, Side.BIG)))
        assertNull(StrategyEngine.predict(s(Side.BIG, Side.SMALL)))
    }

    @Test fun winResetsLevelAndSkip() {
        val state = EngineState(level = 5, skipRemaining = 2)
        val result = StrategyEngine.onOutcome(state, "P1", Prediction(Side.BIG, Strategy.ONE_BY_ONE), Side.BIG, 1000L)
        assertEquals(1, result.level)
        assertEquals(0, result.skipRemaining)
        assertEquals("WIN", result.lastOutcome)
    }

    @Test fun lossAppliesStrategySpecificSkip() {
        val p1 = Prediction(Side.SMALL, Strategy.TREND_BASE)
        val p2 = Prediction(Side.SMALL, Strategy.ONE_BY_ONE)
        val p3 = Prediction(Side.SMALL, Strategy.LAST_THREE_A)
        val p4 = Prediction(Side.SMALL, Strategy.LAST_THREE_B)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(level = 1), "1", p1, Side.BIG, 1000L).skipRemaining)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(level = 1), "1", p1, Side.BIG, 1000L).skipTotal)
        assertEquals(5, StrategyEngine.onOutcome(EngineState(level = 2), "1", p1, Side.BIG, 1000L).skipRemaining)
        assertEquals(5, StrategyEngine.onOutcome(EngineState(level = 2), "1", p1, Side.BIG, 1000L).skipTotal)
        assertEquals(5, StrategyEngine.onOutcome(EngineState(level = 8), "1", p1, Side.BIG, 1000L).skipRemaining)
        assertEquals(1, StrategyEngine.onOutcome(EngineState(), "2", p2, Side.BIG, 1000L).skipRemaining)
        assertEquals(1, StrategyEngine.onOutcome(EngineState(), "3", p3, Side.BIG, 1000L).skipRemaining)
        assertEquals(2, StrategyEngine.onOutcome(EngineState(), "4", p4, Side.BIG, 1000L).skipRemaining)
        assertEquals(2, StrategyEngine.onOutcome(EngineState(level = 2), "5", p4, Side.BIG, 1000L).skipRemaining)
        assertEquals(1, StrategyEngine.onOutcome(EngineState(level = 8), "6", p3, Side.BIG, 1000L).skipRemaining)
        assertEquals(2, StrategyEngine.onOutcome(EngineState(level = 8), "7", p4, Side.BIG, 1000L).skipRemaining)
    }

    @Test fun levelEightLossPausesOneHour() {
        val result = StrategyEngine.onOutcome(
            EngineState(level = 8), "P8",
            Prediction(Side.BIG, Strategy.ONE_BY_ONE), Side.SMALL, 10_000L
        )
        assertEquals(10_000L + 3_600_000L, result.pausedUntilMillis)
        assertEquals(8, result.level)
        assertEquals("LOSS", result.lastOutcome)
    }

    @Test fun skipCountsDownOnlyOnNewPeriods() {
        var state = EngineState(skipRemaining = 3, lastProcessedPeriod = "P0")
        state = StrategyEngine.onNewPeriod(state, "P1", s(Side.BIG, Side.BIG, Side.BIG)).state
        assertEquals(2, state.skipRemaining)
        state = StrategyEngine.onNewPeriod(state, "P1", s(Side.BIG, Side.BIG, Side.BIG)).state
        assertEquals(2, state.skipRemaining)
        state = StrategyEngine.onNewPeriod(state, "P2", s(Side.BIG, Side.BIG, Side.BIG)).state
        assertEquals(1, state.skipRemaining)
    }

    @Test fun duplicatePeriodDoesNotConsumeSkipAgain() {
        val state = EngineState(skipRemaining = 3, lastProcessedPeriod = "P1")
        val result = StrategyEngine.onNewPeriod(state, "P1", s(Side.BIG, Side.BIG, Side.BIG))
        assertEquals(3, result.state.skipRemaining)
        assertEquals("P1", result.state.lastProcessedPeriod)
    }
}
