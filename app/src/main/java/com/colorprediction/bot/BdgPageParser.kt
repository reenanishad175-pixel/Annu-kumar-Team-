package com.colorprediction.bot

data class PeriodOutcome(val period: String, val side: Side)
data class BdgPageSnapshot(val currentPeriod: String, val recentOutcomes: List<PeriodOutcome>)

/**
 * Conservative text parser. It only accepts explicit period/result pairs and never
 * guesses from unrelated BIG/SMALL words or random page content.
 */
object BdgPageParser {
    private val currentPeriodRegex = Regex(
        "(?i)(?:current\\s*)?(?:period|issue|round)\\s*(?:number|no\\.?|id)?\\s*[:#-]?\\s*(\\d{6,})"
    )
    private val pairRegex = Regex(
        "(?is)(?:period|issue|round)\\s*(?:number|no\\.?|id)?\\s*[:#-]?\\s*(\\d{6,})[\\s\\S]{0,100}?\\b(BIG|SMALL)\\b"
    )

    fun parse(pageText: String): BdgPageSnapshot? {
        val current = currentPeriodRegex.find(pageText)?.groupValues?.getOrNull(1) ?: return null
        val pairs = pairRegex.findAll(pageText)
            .map { match ->
                PeriodOutcome(
                    match.groupValues[1],
                    if (match.groupValues[2].equals("BIG", true)) Side.BIG else Side.SMALL
                )
            }
            .distinctBy { it.period }
            .take(10)
            .toList()
        if (pairs.size < 3) return null
        return BdgPageSnapshot(current, pairs)
    }
}
