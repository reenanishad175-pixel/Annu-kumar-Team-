package com.annukumar.team

/**
 * History is newest-first. A missing exact three-item key must not automatically
 * become SKIP: evaluate continuation, alternation, then the recent majority.
 */
object PatternAnalyzer {
    data class Summary(
        val signal: String?,
        val reason: String,
        val big: Int,
        val small: Int,
        val count: Int,
        val strategy: String? = null
    )

    private val exactPatterns = mapOf(
        listOf("SMALL", "BIG", "BIG") to "SMALL",
        listOf("SMALL", "SMALL", "BIG") to "BIG",
        listOf("BIG", "SMALL", "SMALL") to "BIG",
        listOf("BIG", "BIG", "SMALL") to "SMALL"
    )

    fun analyzeNewestFirst(rawValues: List<String>): Summary {
        val values = rawValues.map { it.trim().uppercase() }
            .filter { it == "BIG" || it == "SMALL" }
        val big = values.count { it == "BIG" }
        val small = values.size - big

        if (values.size < 3) {
            return Summary(null, "कम-से-कम 3 सत्यापित परिणाम चाहिए।", big, small, values.size)
        }

        val latestThree = values.take(3)
        val exact = exactPatterns[latestThree]
        if (exact != null) {
            val repeated = values.size >= 6 && values.subList(3, 6) == latestThree
            val signal = if (repeated) opposite(exact) else exact
            return Summary(
                signal, 
                if (repeated) "तीन-तीन परिणामों में वही पैटर्न दोहराया; उल्टा संकेत"
                else "तय तीन-परिणाम पैटर्न मिला",
                big, small, values.size,
                latestThree.joinToString("-") + if (repeated) " (repeated/inverted)" else ""
            )
        }

        // Clear same-side run: follow it until it breaks.
        val newest = values.first()
        val streak = values.takeWhile { it == newest }.size
        if (streak >= 3) {
            return Summary(newest, "लगातार $streak $newest परिणाम; जारी ट्रेंड", big, small, values.size, "TREND_CONTINUATION")
        }

        // Alternating latest three: continue the alternation.
        if (latestThree[0] != latestThree[1] && latestThree[1] != latestThree[2]) {
            val signal = opposite(newest)
            return Summary(signal, "नए तीन परिणाम alternate हैं; alternation जारी", big, small, values.size, "ALTERNATING")
        }

        // Fallback: use the full available strategy window (3 or 6) majority.
        // In a tie, follow the newest observed side rather than suppressing every trade.
        val signal = when {
            big > small -> "BIG"
            small > big -> "SMALL"
            else -> newest
        }
        val reason = when {
            big > small -> "स्पष्ट exact pattern नहीं; उपलब्ध history में BIG अधिक"
            small > big -> "स्पष्ट exact pattern नहीं; उपलब्ध history में SMALL अधिक"
            else -> "गिनती बराबर; tie-break में सबसे नया परिणाम follow"
        }
        return Summary(signal, reason, big, small, values.size, "RECENT_MAJORITY")
    }

    private fun opposite(value: String): String =
        if (value == "BIG") "SMALL" else "BIG"

    /** Backward-compatible parser: raw text is interpreted as newest-first. */
    fun analyze(raw: String): Summary {
        val values = raw.split(Regex("[,\\s]+"))
            .map { it.trim().uppercase() }
            .filter { it == "BIG" || it == "SMALL" }
        return analyzeNewestFirst(values)
    }
}
