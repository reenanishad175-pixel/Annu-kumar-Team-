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
        assertEquals(4, StrategyEngine.onOutcome(EngineState(), "1", p1, Side.BIG, 1000L).skipRemaining)
        assertEquals(1, StrategyEngine.onOutcome(EngineState(), "2", p2, Side.BIG, 1000L).skipRemaining)
        assertEquals(1, StrategyEngine.onOutcome(EngineState(), "3", p3, Side.BIG, 1000L).skipRemaining)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(), "4", p4, Side.BIG, 1000L).skipRemaining)
        assertEquals(1, StrategyEngine.onOutcome(EngineState(level = 2), "5", p4, Side.BIG, 1000L).skipRemaining)
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

    @Test fun duplicatePeriodDoesNotConsumeSkipAgain() {
        val state = EngineState(skipRemaining = 3, lastProcessedPeriod = "P1")
        val result = StrategyEngine.onNewPeriod(state, "P1", s(Side.BIG, Side.BIG, Side.BIG))
        assertEquals(3, result.state.skipRemaining)
    }
}
