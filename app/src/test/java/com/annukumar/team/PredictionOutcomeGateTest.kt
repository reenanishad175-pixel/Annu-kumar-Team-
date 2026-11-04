package com.annukumar.team

import org.junit.Assert.assertEquals
import org.junit.Test

class PredictionOutcomeGateTest {
    @Test
    fun resultSeenWhileSendingIsSavedButNotCountedUntilDeliveryConfirmed() {
        val pending = PredictionOutcomeGate.evaluate("SENDING", "BIG", "SMALL", null)
        assertEquals("SENDING", pending.status)
        assertEquals("SMALL", pending.observedActual)
        assertEquals(null, pending.outcome)

        val confirmed = PredictionOutcomeGate.evaluate("SENT", "BIG", null, pending.observedActual)
        assertEquals("RESOLVED", confirmed.status)
        assertEquals("SMALL", confirmed.resolvedActual)
        assertEquals("LOSS", confirmed.outcome)
    }

    @Test
    fun trendPredictionLossIsResolvedWhenHistoryRowHasAlreadyRolledOff() {
        val confirmed = PredictionOutcomeGate.evaluate("SENT", "BIG", null, "SMALL")
        assertEquals("RESOLVED", confirmed.status)
        assertEquals("LOSS", confirmed.outcome)
    }

    @Test
    fun failedTelegramDeliveryDoesNotCountAsPredictionResult() {
        val failed = PredictionOutcomeGate.evaluate("FAILED", "BIG", "SMALL", null)
        assertEquals("FAILED", failed.status)
        assertEquals(null, failed.outcome)
    }

    @Test
    fun winIsResolvedNormally() {
        val win = PredictionOutcomeGate.evaluate("SENT", "SMALL", "SMALL", null)
        assertEquals("RESOLVED", win.status)
        assertEquals("WIN", win.outcome)
    }
}
