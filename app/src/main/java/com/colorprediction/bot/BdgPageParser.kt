package com.colorprediction.bot

data class PeriodOutcome(val period: String, val side: Side)
/** recentOutcomes are newest first; strategy evaluation reverses the newest three. */
data class BdgPageSnapshot(val currentPeriod: String, val recentOutcomes: List<PeriodOutcome>, val gameDuration: String? = null)

/**
 * Parses the visible WinGo history layout. BDG commonly renders a live period ID
 * separately, followed by history rows containing period ID, digit, and Big/Small.
 */
object BdgPageParser {
    private val periodId = Regex("""\b(\d{12,20})\b""")
    private val explicitCurrent = Regex(
        """(?i)(?:current\s*)?(?:period|issue|round)\s*(?:number|no\.?|id)?\s*[:#-]?\s*(\d{12,20})"""
    )
    private val activeGameDurationPattern = Regex("""(?i)ACTIVE_GAME_MODE:\s*(?:win\s*go\s*)?(30\s*(?:sec(?:ond)?s?|s)|1\s*(?:min(?:ute)?s?|m)|3\s*(?:min(?:ute)?s?|m)|5\s*(?:min(?:ute)?s?|m))\b""")
    private val gameDurationPattern = Regex("""(?i)\b(?:win\s*go|wingo)\s*(30\s*(?:sec(?:ond)?s?|s)|1\s*(?:min(?:ute)?s?|m)|3\s*(?:min(?:ute)?s?|m)|5\s*(?:min(?:ute)?s?|m))\b""")
    private val resultRow = Regex(
        """(?i)\b(\d{12,20})\b[\s|,;:#-]*(?:[0-9]\s+)?(?:[Rr][Ee][Ss][Uu][Ll][Tt] *[:#-]? *)?(BIG|SMALL)\b"""
    )

    fun parse(pageText: String): BdgPageSnapshot? {
        if (pageText.isBlank()) return null
        val ids = periodId.findAll(pageText).map { it.groupValues[1] }.distinct().toList()
        if (ids.size < 4) return null
        val pairs = resultRow.findAll(pageText).map { match ->
            PeriodOutcome(match.groupValues[1],
                if (match.groupValues[2].equals("BIG", true)) Side.BIG else Side.SMALL)
        }.distinctBy { it.period }.toList()
        if (pairs.size < 3) return null
        val explicit = explicitCurrent.find(pageText)?.groupValues?.getOrNull(1)
        val pairedIds = pairs.map { it.period }.toSet()
        val standalone = ids.firstOrNull { it !in pairedIds }
        val current = explicit ?: standalone ?: ids.maxOrNull() ?: return null
        val history = pairs.filterNot { it.period == current }.take(30)
        if (history.size < 3) return null
        // The page text can list inactive durations too; prefer the selected tab marker.\n        val durationToken = (activeGameDurationPattern.find(pageText) ?: gameDurationPattern.find(pageText))\n            ?.groupValues?.getOrNull(1)?.replace(Regex("""\s+"""), "")?.lowercase()
        val duration = when {
            durationToken?.startsWith("30") == true -> "30 Seconds"
            durationToken?.startsWith("1") == true -> "1 Minute"
            durationToken?.startsWith("3") == true -> "3 Minutes"
            durationToken?.startsWith("5") == true -> "5 Minutes"
            else -> null
        }
        return BdgPageSnapshot(current, history, duration)
    }
}
