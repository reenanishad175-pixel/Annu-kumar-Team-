package com.annukumar.team

import java.math.BigInteger

/**
 * Dashboard-only Three Trend cooldown.
 * It tracks local dashboard signals and BDG results; it has no Telegram/session dependency.
 */
internal object DashboardTrendSkipTracker {
    data class State(
        val pendingPeriod: String = "",
        val pendingPick: String = "",
        val pendingStrategy: String = "",
        val skipRemaining: Int = 0,
        val lastSkippedPeriod: String = "",
        val lastSkipNumber: Int = 0
    )

    data class Result(
        val state: State,
        val skipThisPeriod: Boolean,
        val skipNumber: Int,
        val skipRemainingAfterThisPeriod: Int
    )

    fun update(
        newestFirstRows: List<Pair<String, String>>,
        targetPeriod: String,
        previous: State,
        previewSignal: String?,
        previewStrategy: String?
    ): Result {
        var state = previous.copy(skipRemaining = previous.skipRemaining.coerceIn(0, 3))
        val actualForPending = newestFirstRows.firstOrNull { it.first == state.pendingPeriod }?.second
            ?.uppercase()?.takeIf { it == "BIG" || it == "SMALL" }

        if (state.pendingPeriod.isNotBlank() && actualForPending != null) {
            val isTrendBase = state.pendingStrategy == "TREND_BASE_BIG" || state.pendingStrategy == "TREND_BASE_SMALL"
            if (isTrendBase && actualForPending != state.pendingPick) {
                state = state.copy(skipRemaining = 3, lastSkippedPeriod = "", lastSkipNumber = 0)
            }
            state = state.copy(pendingPeriod = "", pendingPick = "", pendingStrategy = "")
        }

        if (state.lastSkippedPeriod == targetPeriod) {
            return Result(state, true, state.lastSkipNumber.coerceIn(1, 3), state.skipRemaining)
        }

        if (state.skipRemaining > 0) {
            val number = 4 - state.skipRemaining
            state = state.copy(
                skipRemaining = state.skipRemaining - 1,
                lastSkippedPeriod = targetPeriod,
                lastSkipNumber = number
            )
            return Result(state, true, number, state.skipRemaining)
        }

        // Clear the previous period's final marker only after the target advances.
        if (state.lastSkippedPeriod.isNotBlank()) {
            state = state.copy(lastSkippedPeriod = "", lastSkipNumber = 0)
        }

        if (state.pendingPeriod.isBlank() &&
            previewSignal in listOf("BIG", "SMALL") &&
            previewStrategy in listOf("TREND_BASE_BIG", "TREND_BASE_SMALL")) {
            state = state.copy(
                pendingPeriod = targetPeriod,
                pendingPick = previewSignal!!,
                pendingStrategy = previewStrategy!!
            )
        }
        return Result(state, false, 0, state.skipRemaining)
    }

    fun periodAtLeast(first: String, second: String): Boolean = try {
        BigInteger(first) >= BigInteger(second)
    } catch (_: Exception) {
        false
    }
}
