package com.annukumar.team

/**
 * Four-rule pattern reader.
 *
 * BDG history arrives NEWEST first. Each three-result window is reversed
 * before matching so all four approved patterns are interpreted
 * chronologically (oldest -> newest).
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

    private data class Match(val signal: String, val strategy: String, val label: String)

    fun analyzeNewestFirst(rawValues: List<String>): Summary {
        val values = rawValues.map { it.trim().uppercase() }
            .filter { it == "BIG" || it == "SMALL" }
        val big = values.count { it == "BIG" }
        val small = values.size - big

        if (values.size < 3) {
            return Summary(null, "कम-से-कम 3 सत्यापित परिणाम चाहिए।", big, small, values.size)
        }

        // First inspect only the latest three. Convert newest-first to chronological order.
        val latestChronological = values.take(3).asReversed()
        val latestMatch = matchThree(latestChronological)
            ?: return Summary(null, "चारों नियमों में स्पष्ट मैच नहीं मिला — SKIP", big, small, values.size)

        // Then inspect the preceding three. A repeated pattern (same or inverse)
        // across the six-result window reverses the normal signal.
        if (values.size >= 6 && latestMatch.strategy.startsWith("New pattern")) {
            val previousChronological = values.subList(3, 6).asReversed()
            val inverseLatest = latestChronological.map { opposite(it) }
            if (previousChronological == latestChronological || previousChronological == inverseLatest) {
                val reversed = opposite(latestMatch.signal)
                return Summary(
                    reversed,
                    "पैटर्न पिछले तीन में भी दोहराया/उल्टा दोहराया गया; संकेत उल्टा किया",
                    big,
                    small,
                    values.size,
                    "${latestMatch.strategy} (repeated: inverted)"
                )
            }
        }

        val previousNote = if (values.size >= 6) {
            "पिछले तीन में यही पैटर्न नहीं मिला"
        } else {
            "पिछले तीन परिणाम उपलब्ध नहीं"
        }
        return Summary(
            latestMatch.signal,
            "${latestMatch.label}; $previousNote",
            big,
            small,
            values.size,
            latestMatch.strategy
        )
    }

    /** Input is chronological: oldest -> newest. */
    private fun matchThree(sequence: List<String>): Match? {
        if (sequence.size != 3) return null

        // Approved strategy 1: continue a clear three-result run.
        if (sequence.all { it == sequence[0] }) {
            return Match(sequence[0], "Trend continuation", "लगातार तीन समान परिणाम: ट्रेंड जारी")
        }

        // Approved strategy 2: continue a three-result alternating sequence.
        if (sequence[0] == sequence[2] && sequence[0] != sequence[1]) {
            return Match(opposite(sequence[2]), "Alternation", "आखिरी तीन परिणाम alternating हैं")
        }

        // Approved strategies 3 and 4, as specified by the user.
        return when (sequence) {
            listOf("SMALL", "BIG", "BIG") ->
                Match("SMALL", "New pattern S-B-B", "S-B-B पैटर्न मिला")
            listOf("SMALL", "SMALL", "BIG") ->
                Match("BIG", "New pattern S-S-B", "S-S-B पैटर्न मिला")
            else -> null
        }
    }

    private fun opposite(value: String): String = if (value == "BIG") "SMALL" else "BIG"

    /** Backward-compatible parser; raw text is oldest-to-newest. */
    fun analyze(raw: String): Summary {
        val values = raw.split(Regex("[,\\s]+"))
            .map { it.trim().uppercase() }
            .filter { it == "BIG" || it == "SMALL" }
            .asReversed()
        return analyzeNewestFirst(values)
    }
}
