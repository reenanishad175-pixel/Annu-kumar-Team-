package com.annukumar.team

/** Four-rule pattern reader. Input order is NEWEST first, as returned by BDG history. */
object PatternAnalyzer {
    data class Summary(val signal: String?, val reason: String, val big: Int, val small: Int, val count: Int, val strategy: String? = null)

    fun analyzeNewestFirst(rawValues: List<String>): Summary {
        val values = rawValues.map { it.trim().uppercase() }.filter { it == "BIG" || it == "SMALL" }
        val big = values.count { it == "BIG" }
        val small = values.size - big
        if (values.size < 3) return Summary(null, "कम-से-कम 3 सत्यापित परिणाम चाहिए।", big, small, values.size)
        val newest = values.take(3)

        // Original strategy 1: continue a clear three-result run.
        if (newest.all { it == newest[0] }) return Summary(newest[0], "लगातार तीन समान परिणाम: ट्रेंड जारी", big, small, values.size, "Trend continuation")

        // Original strategy 2: continue a three-result alternating sequence.
        if (newest[0] == newest[2] && newest[0] != newest[1]) return Summary(opposite(newest[0]), "आखिरी तीन परिणाम alternating हैं", big, small, values.size, "Alternation")

        // New patterns are read newest-to-older: S,B,B -> S and S,S,B -> B.
        val normal = when (newest) {
            listOf("SMALL", "BIG", "BIG") -> "SMALL"
            listOf("SMALL", "SMALL", "BIG") -> "BIG"
            else -> null
        } ?: return Summary(null, "चारों नियमों में स्पष्ट मैच नहीं मिला — SKIP", big, small, values.size)

        // If the same shape (or its BIG/SMALL inverse) repeats in the preceding three, invert.
        if (values.size >= 6) {
            val previous = values.subList(3, 6)
            if (previous == newest || previous == newest.map { opposite(it) }) {
                val reversed = opposite(normal)
                val label = if (newest == listOf("SMALL", "BIG", "BIG")) "S-B-B" else "S-S-B"
                return Summary(reversed, "पैटर्न पिछले तीन में भी दोहराया/उल्टा दोहराया गया; संकेत उल्टा किया", big, small, values.size, "New pattern $label (repeated: inverted)")
            }
        }
        val label = if (newest == listOf("SMALL", "BIG", "BIG")) "S-B-B" else "S-S-B"
        return Summary(normal, "नया तीन-परिणाम पैटर्न मिला; पिछले तीन में वही पैटर्न नहीं", big, small, values.size, "New pattern $label")
    }

    private fun opposite(value: String): String = if (value == "BIG") "SMALL" else "BIG"

    /** Backward-compatible parser; raw text is oldest-to-newest. */
    fun analyze(raw: String): Summary {
        val values = raw.split(Regex("[,\\s]+")).map { it.trim().uppercase() }.filter { it == "BIG" || it == "SMALL" }.asReversed()
        return analyzeNewestFirst(values)
    }
}
