package com.annukumar.team

/**
 * History is newest-first. Each of the four configured strategies is checked
 * independently against the latest three verified results. A match from ANY
 * strategy is a valid signal (OR); SKIP is returned only when NONE match.
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

    // Four independent strategy rules. Do not add trend/majority fallbacks here:
    // an unmatched three-result pattern must remain SKIP.
    private val strategies = listOf(
        Strategy("STRATEGY_1", listOf("SMALL", "BIG", "BIG"), "SMALL"),
        Strategy("STRATEGY_2", listOf("SMALL", "SMALL", "BIG"), "BIG"),
        Strategy("STRATEGY_3", listOf("BIG", "SMALL", "SMALL"), "BIG"),
        Strategy("STRATEGY_4", listOf("BIG", "BIG", "SMALL"), "SMALL")
    )

    fun analyzeNewestFirst(rawValues: List<String>): Summary {
        val values = rawValues.map { it.trim().uppercase() }
            .filter { it == "BIG" || it == "SMALL" }
        val big = values.count { it == "BIG" }
        val small = values.size - big

        if (values.size < 3) {
            return Summary(null, "कम-से-कम 3 सत्यापित परिणाम चाहिए।", big, small, values.size, latestThree = values.take(3))
        }

        val latestThree = values.take(3)
        // OR semantics: the first independently matching strategy is sufficient.
        val matched = strategies.firstOrNull { it.pattern == latestThree }
        if (matched != null) {
            return Summary(
                matched.signal,
                "${matched.name} मैच हुई; चार में से कम-से-कम एक रणनीति सफल",
                big,
                small,
                values.size,
                matched.name,
                latestThree
            )
        }

        return Summary(
            null,
            "चारों रणनीतियों में कोई पैटर्न मैच नहीं हुआ; latest 3: ${latestThree.joinToString(" / ")}; SKIP",
            big,
            small,
            values.size,
            "SKIP",
            latestThree
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
