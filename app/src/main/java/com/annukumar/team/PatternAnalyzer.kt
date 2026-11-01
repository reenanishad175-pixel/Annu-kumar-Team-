package com.annukumar.team

object PatternAnalyzer {
    data class Summary(val signal: String?, val reason: String, val big: Int, val small: Int, val count: Int)
    // Educational paper-mode summary; not a reliable prediction. Oldest-to-newest input.
    fun analyze(raw: String): Summary {
        val values = raw.split(Regex("[,\\s]+")).map { it.trim().uppercase() }.filter { it == "BIG" || it == "SMALL" }.takeLast(10)
        val big = values.count { it == "BIG" }
        val small = values.size - big
        if (values.size < 3) return Summary(null, "कम-से-कम 3 सत्यापित परिणाम दर्ज करें।", big, small, values.size)
        val last3 = values.takeLast(3)
        if (last3[0] != last3[1] && last3[1] != last3[2] && last3[0] == last3[2]) {
            val next = if (last3.last() == "BIG") "SMALL" else "BIG"
            return Summary(next, "आखिरी 3 परिणाम alternating pattern दिखाते हैं (demo observation)।", big, small, values.size)
        }
        if (last3.all { it == last3[0] }) return Summary(last3[0], "आखिरी 3 परिणाम एक ही दिशा में हैं (demo observation)।", big, small, values.size)
        if (big - small >= 2) return Summary("BIG", "पिछले ${values.size} परिणामों में BIG अधिक है; यह ऐतिहासिक गिनती है।", big, small, values.size)
        if (small - big >= 2) return Summary("SMALL", "पिछले ${values.size} परिणामों में SMALL अधिक है; यह ऐतिहासिक गिनती है।", big, small, values.size)
        return Summary(null, "स्पष्ट पैटर्न नहीं मिला — BET SKIP (demo)।", big, small, values.size)
    }
}
