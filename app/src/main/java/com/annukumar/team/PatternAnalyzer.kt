package com.annukumar.team

/**
 * History is newest-first. Strategy 1 searches the latest SIX verified periods
 * (its three-result signature may occur anywhere in that six-period window).
 * Strategies 2-4 compare only the latest THREE periods. A match from ANY
 * strategy is a valid signal; SKIP is returned only when none match.
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
        val signal: String,
        val lookback: Int
    )

    // Preserve the four existing signatures and apply independent 6/3/3/3 windows.
    private val strategies = listOf(
        Strategy("STRATEGY_1", listOf("SMALL", "BIG", "BIG"), "SMALL", 6),
        Strategy("STRATEGY_2", listOf("SMALL", "SMALL", "BIG"), "BIG", 3),
        Strategy("STRATEGY_3", listOf("BIG", "SMALL", "SMALL"), "BIG", 3),
        Strategy("STRATEGY_4", listOf("BIG", "BIG", "SMALL"), "SMALL", 3)
    )

    fun analyzeNewestFirst(rawValues: List<String>): Summary {
        val values = rawValues.map { it.trim().uppercase() }
            .filter { it == "BIG" || it == "SMALL" }
        val big = values.count { it == "BIG" }
        val small = values.size - big
        val latestThree = values.take(3)

        val matched = strategies.firstOrNull { strategy ->
            if (values.size < strategy.lookback) return@firstOrNull false
            val window = values.take(strategy.lookback)
            if (strategy.lookback == strategy.pattern.size) {
                window == strategy.pattern
            } else {
                // Strategy 1 checks every consecutive 3-result signature within
                // the latest 6 verified periods.
                window.windowed(strategy.pattern.size).any { it == strategy.pattern }
            }
        }

        if (matched != null) {
            return Summary(
                matched.signal,
                "${matched.name} मैच हुई • ${matched.lookback} पीरियड विंडो",
                big, small, values.size, matched.name, latestThree
            )
        }

        val reason = if (values.size < 3) {
            "सत्यापित परिणाम कम हैं: कम-से-कम 3 चाहिए"
        } else if (values.size < 6) {
            "Strategy 1 के लिए 6 पीरियड पूरे नहीं; बाकी तीन में पैटर्न मैच नहीं हुआ"
        } else {
            "चारों रणनीतियों में कोई पैटर्न मैच नहीं हुआ; latest 6: ${values.take(6).joinToString(" / ")}; SKIP"
        }
        return Summary(null, reason, big, small, values.size, "SKIP", latestThree)
    }

    /** Backward-compatible parser: raw text is interpreted as newest-first. */
    fun analyze(raw: String): Summary {
        val values = raw.split(Regex("[,\\s]+"))
            .map { it.trim().uppercase() }
            .filter { it == "BIG" || it == "SMALL" }
        return analyzeNewestFirst(values)
    }
}
