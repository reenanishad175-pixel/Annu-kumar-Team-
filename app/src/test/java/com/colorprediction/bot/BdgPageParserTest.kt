package com.colorprediction.bot

import org.junit.Assert.*
import org.junit.Test

class BdgPageParserTest {
    @Test fun parsesOnlyExplicitPeriodResultRows() {
        val text = """
            Current Period Number: 202611050001
            History
            Period Number: 202611050000 Result: BIG
            Period Number: 202611049999 Result: SMALL
            Period Number: 202611049998 Result: BIG
        """.trimIndent()
        val parsed = BdgPageParser.parse(text)
        assertNotNull(parsed)
        assertEquals("202611050001", parsed!!.currentPeriod)
        assertEquals(listOf(Side.BIG, Side.SMALL, Side.BIG), parsed.recentOutcomes.take(3).map { it.side })
    }

    @Test fun parsesVisibleWinGoPageWithStandaloneCurrentPeriod() {
        val text = """
            Win Go 1Min 0 0:49
            20261105100010966
            Green Violet Red
            Random X1 X5 X10
            Big Small
            Game history Chart My history
            Period Number Big Small Color
            20261105100010965 0 Small
            20261105100010964 6 Big
            20261105100010963 4 Small
            20261105100010962 8 Big
        """.trimIndent()
        val parsed = BdgPageParser.parse(text)
        assertNotNull(parsed)
        assertEquals("20261105100010966", parsed!!.currentPeriod)
        assertEquals(listOf(Side.SMALL, Side.BIG, Side.SMALL), parsed.recentOutcomes.take(3).map { it.side })
    }

    @Test fun refusesIncompleteOrAmbiguousPageText() {
        assertNull(BdgPageParser.parse("Current Period Number: 202611050001 Result: waiting"))
        assertNull(BdgPageParser.parse("BIG SMALL BIG"))
    }
}
