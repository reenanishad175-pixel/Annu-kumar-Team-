package com.annukumar.team

/**
 * Makes result resolution resilient to Telegram latency.
 *
 * A result observed while a prediction is SENDING is retained, but is only
 * counted after Telegram confirms delivery and changes the record to SENT.
 */
internal object PredictionOutcomeGate {
    data class Decision(
        val status: String,
        val observedActual: String?,
        val resolvedActual: String?,
        val outcome: String?
    )

    fun evaluate(
        status: String,
        pick: String,
        historyActual: String?,
        previouslyObservedActual: String?
    ): Decision {
        if (pick !in VALID_SIDES) return Decision(status, previouslyObservedActual, null, null)

        val actual = historyActual?.takeIf { it in VALID_SIDES } ?: previouslyObservedActual?.takeIf { it in VALID_SIDES }
        return when (status) {
            "SENDING" -> Decision(status, actual, null, null)
            "SENT" -> if (actual != null) {
                Decision("RESOLVED", actual, actual, if (pick == actual) "WIN" else "LOSS")
            } else {
                Decision(status, previouslyObservedActual, null, null)
            }
            else -> Decision(status, previouslyObservedActual, null, null)
        }
    }

    private val VALID_SIDES = setOf("BIG", "SMALL")
}
