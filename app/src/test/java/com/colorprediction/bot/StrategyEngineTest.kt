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


    @Test fun levelEightLossResetsToLevelOne() {
        val result = StrategyEngine.onOutcome(
            EngineState(level = 8), "P8",
            Prediction(Side.BIG, Strategy.ONE_BY_ONE), Side.SMALL, 10_000L
        )
        assertEquals(0L, result.pausedUntilMillis)
        assertEquals(1, result.level)
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
    @Test fun catchUpDoesNotConsumeCurrentOpenPeriodBeforeCustomReplacement() {
        val initial = EngineState(
            skipRemaining = 2,
            skipTotal = 2,
            lastProcessedPeriod = "100"
        )
        val caughtUp = StrategyEngine.catchUpClosedSkipPeriods(initial, listOf("100"))
        assertEquals(2, caughtUp.skipRemaining)
        assertEquals("100", caughtUp.lastProcessedPeriod)

        val custom = StrategyConfig(
            mode = StrategyMode.ALTERNATE,
            skipOnLoss = null,
            startingSide = Side.BIG,
            rules = emptyList()
        )
        val current = StrategyEngine.onNewPeriod(
            caughtUp, "101", s(Side.BIG, Side.BIG, Side.BIG),
            customStrategyEnabled = true, customConfig = custom
        )
        assertEquals(Prediction(Side.BIG, Strategy.CUSTOM), current.prediction)
        assertEquals(1, current.state.skipRemaining)
    }

    @Test fun catchUpConsumesOnlyAlreadyClosedSkipPeriods() {
        val initial = EngineState(
            skipRemaining = 3,
            skipTotal = 3,
            lastProcessedPeriod = "100"
        )
        val caughtUp = StrategyEngine.catchUpClosedSkipPeriods(
            initial, listOf("102", "101", "100")
        )
        assertEquals(1, caughtUp.skipRemaining)
        assertEquals(3, caughtUp.skipTotal)
        assertEquals("102", caughtUp.lastProcessedPeriod)
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

        // A custom LOSS during the built-in skip window advances the level,
        // while preserving the remaining skip-period countdown.
        state = StrategyEngine.onOutcome(
            first.state, "P1", first.prediction!!, Side.SMALL, 1000L
        )
        assertEquals(5, state.level)
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
        assertEquals(1, state.level) // Custom WIN resets the active level sequence.
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

    @Test fun customOwnSkipDoesNotDisableOneByOneBuiltIn() {
        val state = EngineState(
            customSkipRemaining = 1,
            lastProcessedPeriod = "P0",
            lastStrategy = Strategy.CUSTOM,
            lastPrediction = Side.BIG,
            lastOutcome = "LOSS"
        )
        val result = StrategyEngine.onNewPeriod(
            state, "P1", s(Side.BIG, Side.SMALL, Side.BIG),
            customStrategyEnabled = true,
            customConfig = StrategyConfigStore.defaults(Strategy.CUSTOM)
        )
        assertEquals(Prediction(Side.SMALL, Strategy.ONE_BY_ONE), result.prediction)
        assertEquals(0, result.state.customSkipRemaining)
    }

    @Test fun customOwnSkipDoesNotDisableFollowUpBuiltIn() {
        val state = EngineState(
            customSkipRemaining = 1,
            lastProcessedPeriod = "P0",
            lastStrategy = Strategy.CUSTOM,
            lastPrediction = Side.BIG,
            lastOutcome = "LOSS"
        )
        val result = StrategyEngine.onNewPeriod(
            state, "P1", s(Side.BIG, Side.BIG, Side.SMALL),
            customStrategyEnabled = true,
            customConfig = StrategyConfigStore.defaults(Strategy.CUSTOM)
        )
        assertEquals(Prediction(Side.BIG, Strategy.FOLLOW_UP), result.prediction)
        assertEquals(0, result.state.customSkipRemaining)
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


    @Test fun builtInsUseOneByOneThenFollowUpFallback() {
        val oneByOne = StrategyEngine.predict(s(Side.BIG, Side.SMALL, Side.BIG))!!
        assertEquals(Side.SMALL, oneByOne.side)
        assertEquals(Strategy.ONE_BY_ONE, oneByOne.strategy)

        val followUp = StrategyEngine.predict(s(Side.BIG, Side.BIG, Side.SMALL))!!
        assertEquals(Side.BIG, followUp.side)
        assertEquals(Strategy.FOLLOW_UP, followUp.strategy)
    }

    @Test fun oneByOneLossHandsNextPredictionToFollowUp() {
        val first = StrategyEngine.predict(s(Side.BIG, Side.SMALL, Side.BIG))!!
        assertEquals(Strategy.ONE_BY_ONE, first.strategy)
        val afterLoss = StrategyEngine.onOutcome(
            EngineState(), "P1", first, Side.BIG, 1000L
        )
        val next = StrategyEngine.predict(s(Side.BIG, Side.BIG, Side.SMALL), null, afterLoss.lastPrediction, afterLoss.level)!!
        assertEquals(Strategy.FOLLOW_UP, next.strategy)
        assertEquals(Side.BIG, next.side)
    }

    @Test fun replacementBuiltInsDoNotInheritLegacySkipDefaults() {
        assertEquals(0, StrategyConfigStore.effectiveSkip(null, Strategy.ONE_BY_ONE, 1))
        assertEquals(0, StrategyConfigStore.effectiveSkip(null, Strategy.FOLLOW_UP, 1))
        assertEquals(0, StrategyConfigStore.effectiveSkip(null, Strategy.ONE_BY_ONE, 8))
        assertEquals(0, StrategyConfigStore.effectiveSkip(null, Strategy.FOLLOW_UP, 8))
    }

}
