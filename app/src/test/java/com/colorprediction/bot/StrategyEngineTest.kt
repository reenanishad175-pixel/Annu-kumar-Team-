package com.colorprediction.bot

import org.junit.Assert.*
import org.junit.Test

class StrategyEngineTest {
    private fun s(vararg values: Side) = values.toList()

    @Test fun parserSortsHistoryNewestFirstEvenWhenPageTextIsOldestFirst() {
        val page = listOf(
            "Current Period: 202611051234",
            "202611051231 2 SMALL",
            "202611051232 8 BIG",
            "202611051233 1 SMALL"
        ).joinToString("\n")

        val snapshot = BdgPageParser.parse(page)!!
        assertEquals("202611051234", snapshot.currentPeriod)
        assertEquals(
            listOf("202611051233", "202611051232", "202611051231"),
            snapshot.recentOutcomes.map { it.period }
        )
        assertEquals(
            listOf(Side.SMALL, Side.BIG, Side.SMALL),
            snapshot.recentOutcomes.map { it.side }
        )
        assertEquals(
            listOf(Side.SMALL, Side.BIG, Side.SMALL),
            StrategyEngine.patternFromRecentResults(snapshot.recentOutcomes.map { it.side })
        )
    }

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

    @Test fun newestFirstHistoryKeepsAllFourPatternsInTheirConfirmedStrategies() {
        // History rows are newest-first. Do not reverse them: the displayed
        // three-line pattern must map to the same strategy the user specified.
        val strategy3a = StrategyEngine.predict(
            StrategyEngine.patternFromRecentResults(s(Side.SMALL, Side.SMALL, Side.BIG, Side.BIG, Side.SMALL))
        )!!
        assertEquals(Strategy.LAST_THREE_B, strategy3a.strategy)
        assertEquals("Strategy 3", strategy3a.strategy.title)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(), "P1", strategy3a, Side.SMALL, 1000L).skipRemaining)

        val strategy3b = StrategyEngine.predict(
            StrategyEngine.patternFromRecentResults(s(Side.BIG, Side.BIG, Side.SMALL, Side.SMALL, Side.BIG))
        )!!
        assertEquals(Strategy.LAST_THREE_B, strategy3b.strategy)
        assertEquals("Strategy 3", strategy3b.strategy.title)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(), "P2", strategy3b, Side.BIG, 1000L).skipRemaining)

        val strategy4a = StrategyEngine.predict(
            StrategyEngine.patternFromRecentResults(s(Side.SMALL, Side.BIG, Side.BIG, Side.BIG, Side.SMALL))
        )!!
        assertEquals(Strategy.LAST_THREE_A, strategy4a.strategy)
        assertEquals("Strategy 4", strategy4a.strategy.title)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(), "P3", strategy4a, Side.BIG, 1000L).skipRemaining)

        val strategy4b = StrategyEngine.predict(
            StrategyEngine.patternFromRecentResults(s(Side.BIG, Side.SMALL, Side.SMALL, Side.SMALL, Side.BIG))
        )!!
        assertEquals(Strategy.LAST_THREE_A, strategy4b.strategy)
        assertEquals("Strategy 4", strategy4b.strategy.title)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(), "P4", strategy4b, Side.SMALL, 1000L).skipRemaining)
    }

    @Test fun dashboardPatternNumbersMatchTheirSkipRules() {
        val patternA = StrategyEngine.predict(s(Side.SMALL, Side.BIG, Side.BIG))!!
        val patternB = StrategyEngine.predict(s(Side.BIG, Side.BIG, Side.SMALL))!!
        assertEquals("Strategy 4", patternA.strategy.title)
        assertEquals("Strategy 3", patternB.strategy.title)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(), "A", patternA, Side.BIG, 1000L).skipRemaining)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(), "B", patternB, Side.BIG, 1000L).skipRemaining)
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
        assertEquals(0L, recovered.pausedUntilMillis)
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
        assertEquals(1, StrategyEngine.onOutcome(EngineState(level = 1), "2", p2, Side.BIG, 1000L).skipRemaining)
        assertEquals(1, StrategyEngine.onOutcome(EngineState(level = 2), "2b", p2, Side.BIG, 1000L).skipRemaining)
        assertEquals(1, StrategyEngine.onOutcome(EngineState(level = 5), "2c", p2, Side.BIG, 1000L).skipRemaining)
        val oneSkip = StrategyEngine.onOutcome(EngineState(level = 3), "2d", p2, Side.BIG, 1000L)
        assertEquals(1, oneSkip.skipRemaining)
        val afterOnePeriod = StrategyEngine.onNewPeriod(oneSkip.copy(lastProcessedPeriod = "2d"), "2e", s(Side.BIG, Side.BIG, Side.BIG))
        assertNull(afterOnePeriod.prediction)
        assertEquals(0, afterOnePeriod.state.skipRemaining)
        val followingPeriod = StrategyEngine.onNewPeriod(afterOnePeriod.state, "2f", s(Side.BIG, Side.BIG, Side.BIG))
        assertNotNull(followingPeriod.prediction)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(), "3", p3, Side.BIG, 1000L).skipRemaining)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(), "4", p4, Side.BIG, 1000L).skipRemaining)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(level = 2), "5", p4, Side.BIG, 1000L).skipRemaining)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(level = 8), "6", p3, Side.BIG, 1000L).skipRemaining)
        assertEquals(0, StrategyEngine.onOutcome(EngineState(level = 8), "7", p4, Side.BIG, 1000L).skipRemaining)
    }

    @Test fun levelEightLossDoesNotPauseOrCapPredictions() {
        val result = StrategyEngine.onOutcome(
            EngineState(level = 8), "P8",
            Prediction(Side.BIG, Strategy.ONE_BY_ONE), Side.SMALL, 10_000L
        )
        assertEquals(0L, result.pausedUntilMillis)
        assertEquals(9, result.level)
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
    @Test fun enabledCustomStrategyTradesInsideBuiltInSkipWindowForExactSkipCount() {
        val custom = StrategyConfig(
            mode = StrategyMode.ALTERNATE,
            skipOnLoss = null,
            startingSide = Side.BIG,
            rules = emptyList()
        )
        var state = EngineState(
            level = 4,
            skipRemaining = 2,
            skipTotal = 2,
            lastProcessedPeriod = "P0"
        )

        val first = StrategyEngine.onNewPeriod(
            state, "P1", s(Side.BIG, Side.BIG, Side.BIG),
            customStrategyEnabled = true, customConfig = custom
        )
        assertEquals(Prediction(Side.BIG, Strategy.CUSTOM), first.prediction)
        assertEquals(1, first.state.skipRemaining)
        assertTrue(first.status.startsWith("CUSTOM STRATEGY ACTIVE"))

        // A custom LOSS must not change the original strategy's level or
        // extend/cancel the two-period skip window.
        state = StrategyEngine.onOutcome(
            first.state, "P1", first.prediction!!, Side.SMALL, 1000L
        )
        assertEquals(4, state.level)
        assertEquals(1, state.skipRemaining)
        assertEquals(2, state.skipTotal)

        val second = StrategyEngine.onNewPeriod(
            state, "P2", s(Side.BIG, Side.BIG, Side.BIG),
            customStrategyEnabled = true, customConfig = custom
        )
        assertEquals(Prediction(Side.SMALL, Strategy.CUSTOM), second.prediction)
        assertEquals(0, second.state.skipRemaining)

        state = StrategyEngine.onOutcome(
            second.state, "P2", second.prediction!!, Side.SMALL, 2000L
        )
        assertEquals(4, state.level)
        assertEquals(0, state.skipRemaining)
        assertEquals(0, state.skipTotal)
    }

    @Test fun standaloneCustomLossAdvancesDashboardLevelAndWinResetsIt() {
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

    @Test fun disabledCustomStrategyKeepsNormalSkipBehavior() {
        val result = StrategyEngine.onNewPeriod(
            EngineState(skipRemaining = 2, skipTotal = 2, lastProcessedPeriod = "P0"),
            "P1",
            s(Side.BIG, Side.BIG, Side.BIG),
            customStrategyEnabled = false
        )
        assertNull(result.prediction)
        assertTrue(result.status.startsWith("SKIP"))
        assertEquals(1, result.state.skipRemaining)
    }

    @Test fun customPatternModeOnlyPredictsWhenItsConfiguredPatternMatches() {
        val custom = StrategyConfig(
            mode = StrategyMode.PATTERN,
            skipOnLoss = null,
            startingSide = Side.BIG,
            rules = listOf(StrategyRule("BSB", Side.SMALL))
        )
        val matched = StrategyEngine.onNewPeriod(
            EngineState(skipRemaining = 1, skipTotal = 1, lastProcessedPeriod = "P0"),
            "P1", s(Side.BIG, Side.SMALL, Side.BIG),
            customStrategyEnabled = true, customConfig = custom
        )
        assertEquals(Prediction(Side.SMALL, Strategy.CUSTOM), matched.prediction)
        val unmatched = StrategyEngine.onNewPeriod(
            EngineState(skipRemaining = 1, skipTotal = 1, lastProcessedPeriod = "P0"),
            "P1", s(Side.BIG, Side.BIG, Side.BIG),
            customStrategyEnabled = true, customConfig = custom
        )
        assertNull(unmatched.prediction)
        assertTrue(unmatched.status.startsWith("CUSTOM STRATEGY"))
        assertEquals(0, unmatched.state.skipRemaining)
    }

}
