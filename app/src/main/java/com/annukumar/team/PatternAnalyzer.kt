package com.annukumar.team

/**
 * The BDG history reader supplies outcomes newest-first.
 * Pattern keys below intentionally use that same newest-first order.
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

    private val predictionsNewestFirst = mapOf(
        listOf("SMALL", "BIG", "BIG") to "SMALL",
        listOf("SMALL", "SMALL", "BIG") to "BIG",
        // Reverse-side versions of the same two rules.
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

        // The first three entries are the latest three, already newest-first.
        val latestPattern = values.take(3)
        val baseSignal = predictionsNewestFirst[latestPattern]
            ?: return Summary(
                null,
                "नए तीन परिणामों में चारों तय पैटर्न में से कोई स्पष्ट मैच नहीं — SKIP",
                big,
                small,
                values.size
            )

        // Check the preceding three only after the latest group matches.
        // If exactly the same three-result pattern repeats in the six-result
        // window, invert its normal prediction as requested.
        val repeated = values.size >= 6 &&
            values.subList(3, 6) == latestPattern

        val signal = if (repeated) opposite(baseSignal) else baseSignal
        val reason = if (repeated) {
            "वही पैटर्न लगातार दोनों तीन-रिजल्ट समूहों में दोहराया; संकेत उल्टा"
        } else if (values.size >= 6) {
            "नए तीन में पैटर्न मिला; पिछले तीन में वही पैटर्न दोहराया नहीं"
        } else {
            "नए तीन में तय पैटर्न मिला; पुराने तीन परिणाम उपलब्ध नहीं"
        }

        return Summary(
            signal = signal,
            reason = reason,
            big = big,
            small = small,
            count = values.size,
            strategy = latestPattern.joinToString("-") + if (repeated) " (repeated/inverted)" else ""
        )
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
