package com.annukumar.team

/**
 * History is newest-first. Every strategy checks exactly the latest THREE
 * verified periods. If none of the four exact signatures matches, return SKIP.
 */
object PatternAnalyzer {
    data class Summary(
        val signal: String?,
        val reason: String,
        val big: Int,
        val small: Int,
        val count: Int,
        val strategy: String? = null,
        val latestThree: List<String> = emptyList()
    )

    private data class Strategy(
        val name: String,
        val pattern: List<String>,
        val signal: String
    )

    // All four strategies use exactly the same three-period lookback.
    private val strategies = listOf(
        Strategy("STRATEGY_1", listOf("SMALL", "BIG", "BIG"), "SMALL"),
        Strategy("STRATEGY_2", listOf("SMALL", "SMALL", "BIG"), "BIG"),
        Strategy("STRATEGY_3", listOf("BIG", "SMALL", "SMALL"), "BIG"),
        Strategy("STRATEGY_4", listOf("BIG", "BIG", "SMALL"), "SMALL")
    )

    fun analyzeNewestFirst(rawValues: List<String>): Summary {
        val values = rawValues.map { it.trim().uppercase() }
            .filter { it == "BIG" || it == "SMALL" }
            .take(3)
        val big = values.count { it == "BIG" }
        val small = values.size - big
        val latestThree = values

        if (values.size < 3) {
            return Summary(
                null,
                "सत्यापित परिणाम केवल ${values.size} मिले; 3 चाहिए • SKIP",
                big, small, values.size, "SKIP", latestThree
            )
        }

        val matched = strategies.firstOrNull { it.pattern == latestThree }
        if (matched != null) {
            return Summary(
                matched.signal,
                "${matched.name} मैच हुई • केवल 3 पीरियड",
                big, small, values.size, matched.name, latestThree
            )
        }

        return Summary(
            null,
            "तीन पीरियड में किसी भी रणनीति का पैटर्न मैच नहीं हुआ • SKIP",
            big, small, values.size, "SKIP", latestThree
        )
    }

    /** Backward-compatible parser: raw text is interpreted as newest-first. */
    fun analyze(raw: String): Summary {
        val values = raw.split(Regex("[,\\s]+"))
            .map { it.trim().uppercase() }
            .filter { it == "BIG" || it == "SMALL" }
        return analyzeNewestFirst(values)
    }
}
