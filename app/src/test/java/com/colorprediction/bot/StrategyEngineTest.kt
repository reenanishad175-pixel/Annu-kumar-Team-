package com.colorprediction.bot

import org.junit.Assert.*
import org.junit.Test

class StrategyEngineTest {
    private fun s(vararg values: Side) = values.toList()

    @Test fun alternatingLastThreeAutomaticallySelectOneByOne() {
        assertEquals(
            Prediction(Side.SMALL, Strategy.ONE_BY_ONE),
            StrategyEngine.predict(s(Side.BIG, Side.SMALL, Side.BIG))
        )
        assertEquals(
            Prediction(Side.BIG, Strategy.ONE_BY_ONE),
            StrategyEngine.predict(s(Side.SMALL, Side.BIG, Side.SMALL))
        )
    }

    @Test fun followUpUsesLatestActualSideWhenOneByOnePatternIsAbsent() {
        assertEquals(
            Prediction(Side.BIG, Strategy.FOLLOW_UP),
            StrategyEngine.predict(s(Side.BIG, Side.BIG, Side.SMALL))
        )
        assertEquals(
            Prediction(Side.SMALL, Strategy.FOLLOW_UP),
            StrategyEngine.predict(s(Side.SMALL, Side.SMALL, Side.BIG))
        )
    }

    @Test fun oneByOneLossBreaksAlternationAndNextPredictionFallsBackToFollowUp() {
        val afterLoss = StrategyEngine.onOutcome(
            EngineState(),
            "P1",
            Prediction(Side.SMALL, Strategy.ONE_BY_ONE),
            Side.BIG,
            now = 1000L
        )
        val next = StrategyEngine.predict(s(Side.BIG, Side.BIG, Side.SMALL))
        assertEquals(2, afterLoss.level)
        assertEquals("LOSS", afterLoss.lastOutcome)
        assertEquals(Strategy.FOLLOW_UP, next?.strategy)
        assertEquals(Side.BIG, next?.side)
    }

    @Test fun firstHistoryReadOnlyEstablishesBaseline() {
        val result = StrategyEngine.onNewPeriod(
            EngineState(),
            "FIRST_OPEN_PERIOD",
            s(Side.BIG, Side.BIG, Side.BIG)
        )
        assertNull(result.prediction)
        assertEquals("FIRST_OPEN_PERIOD", result.state.lastProcessedPeriod)
        assertTrue(result.status.startsWith("SKIP"))
    }

    @Test fun duplicatePeriodDoesNotConsumeSkipAgain() {
        val state = EngineState(skipRemaining = 3, lastProcessedPeriod = "P1")
        val result = StrategyEngine.onNewPeriod(state, "P1", s(Side.BIG, Side.BIG, Side.BIG))
        assertEquals(3, result.state.skipRemaining)
        assertEquals("P1", result.state.lastProcessedPeriod)
    }

    @Test fun customStandaloneLossAdvancesLevelAndWinResetsIt() {
        val customPrediction = Prediction(Side.BIG, Strategy.CUSTOM)
        val afterLoss = StrategyEngine.onOutcome(
            EngineState(level = 3), "C1", customPrediction, Side.SMALL,
            now = 1000L, customStandalone = true
        )
        assertEquals(4, afterLoss.level)
        assertEquals("LOSS", afterLoss.lastOutcome)

        val afterWin = StrategyEngine.onOutcome(
            afterLoss, "C2", customPrediction, Side.BIG,
            now = 2000L, customStandalone = true
        )
        assertEquals(1, afterWin.level)
        assertEquals("WIN", afterWin.lastOutcome)
    }

    @Test fun customPatternMatchingRemainsAvailableDuringBuiltInSkip() {
        val custom = StrategyConfig(
            mode = StrategyMode.PATTERN,
            skipOnLoss = null,
            startingSide = Side.BIG,
            rules = listOf(StrategyRule("BSBB", Side.SMALL)),
            patternLength = 4
        )
        val matched = StrategyEngine.onNewPeriod(
            EngineState(skipRemaining = 1, skipTotal = 1, lastProcessedPeriod = "P0"),
            "P1", s(Side.BIG, Side.SMALL, Side.BIG, Side.BIG),
            customStrategyEnabled = true, customConfig = custom
        )
        assertEquals(Prediction(Side.SMALL, Strategy.CUSTOM), matched.prediction)
        assertEquals(0, matched.state.skipRemaining)
    }
}
