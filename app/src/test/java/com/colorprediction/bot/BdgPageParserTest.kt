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

    @Test fun refusesIncompleteOrAmbiguousPageText() {
        assertNull(BdgPageParser.parse("Current Period Number: 202611050001 Result: waiting"))
        assertNull(BdgPageParser.parse("BIG SMALL BIG"))
    }
}
