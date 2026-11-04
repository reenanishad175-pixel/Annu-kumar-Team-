package com.annukumar.team

/**
 * History is newest-first. Strategies 1–6 remain active exactly as before.
 * Strategies 7 and 8 are deliberately disabled; unmatched history returns SKIP.
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

    // Keep strategies 1–6 unchanged. Strategies 7 and 8 are intentionally omitted.
    private val strategies = listOf(
        // 1. One by One
        Strategy("ONE_BY_ONE_BIG_SMALL_BIG", listOf("BIG", "SMALL", "BIG"), "SMALL"),
        Strategy("ONE_BY_ONE_SMALL_BIG_SMALL", listOf("SMALL", "BIG", "SMALL"), "BIG"),

        // 2. Trend Base
        Strategy("TREND_BASE_BIG", listOf("BIG", "BIG", "BIG"), "BIG"),
        Strategy("TREND_BASE_SMALL", listOf("SMALL", "SMALL", "SMALL"), "SMALL"),

        // 3. Third strategy
        Strategy("STRATEGY_THREE_SMALL_BIG_BIG", listOf("SMALL", "BIG", "BIG"), "SMALL"),
        Strategy("STRATEGY_THREE_BIG_SMALL_SMALL", listOf("BIG", "SMALL", "SMALL"), "BIG"),

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
                "${matched.name} मैच हुई • 3 पीरियड",
                big, small, values.size, matched.name, latestThree
            )
        }

        return Summary(
            null,
            "तीन पीरियड में किसी भी सक्रिय पैटर्न (1–6) का मैच नहीं हुआ • SKIP",
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
