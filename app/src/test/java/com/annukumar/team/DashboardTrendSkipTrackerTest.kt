package com.annukumar.team

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTrendSkipTrackerTest {
    @Test
    fun trendBaseLossStartsThreeDashboardSkips() {
        val pending = DashboardTrendSkipTracker.State(
            pendingPeriod = "102",
            pendingPick = "BIG",
            pendingStrategy = "TREND_BASE_BIG"
        )
        val result = DashboardTrendSkipTracker.update(
            listOf("102" to "SMALL", "101" to "BIG", "100" to "BIG"),
            "103",
            pending,
            "SMALL",
            "TREND_BASE_SMALL"
        )
        assertTrue(result.skipThisPeriod)
        assertEquals(1, result.skipNumber)
        assertEquals(2, result.skipRemainingAfterThisPeriod)
        assertEquals(2, result.state.skipRemaining)
    }

    @Test
    fun repeatedDashboardRefreshDoesNotConsumeSamePeriodTwice() {
        val once = DashboardTrendSkipTracker.State(skipRemaining = 2, lastSkippedPeriod = "103", lastSkipNumber = 1)
        val again = DashboardTrendSkipTracker.update(
            listOf("102" to "SMALL", "101" to "BIG", "100" to "BIG"),
            "103", once, null, null
        )
        assertTrue(again.skipThisPeriod)
        assertEquals(1, again.skipNumber)
        assertEquals(2, again.state.skipRemaining)
    }

    @Test
    fun nextTwoPeriodsAreSkippedAndThenCooldownEnds() {
        val second = DashboardTrendSkipTracker.update(emptyList(), "104",
            DashboardTrendSkipTracker.State(skipRemaining = 2, lastSkippedPeriod = "103", lastSkipNumber = 1), null, null)
        assertTrue(second.skipThisPeriod)
        assertEquals(2, second.skipNumber)
        val third = DashboardTrendSkipTracker.update(emptyList(), "105", second.state, null, null)
        assertTrue(third.skipThisPeriod)
        assertEquals(3, third.skipNumber)
        assertEquals(0, third.state.skipRemaining)
        val after = DashboardTrendSkipTracker.update(emptyList(), "106", third.state, null, null)
        assertFalse(after.skipThisPeriod)
    }

    @Test
    fun trendBaseWinDoesNotStartCooldown() {
        val pending = DashboardTrendSkipTracker.State(
            pendingPeriod = "102", pendingPick = "BIG", pendingStrategy = "TREND_BASE_BIG"
        )
        val result = DashboardTrendSkipTracker.update(
            listOf("102" to "BIG"), "103", pending, "SMALL", "TREND_BASE_SMALL"
        )
        assertFalse(result.skipThisPeriod)
        assertEquals(0, result.state.skipRemaining)
    }
}
