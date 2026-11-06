package com.colorprediction.bot

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class StrategyMode { PATTERN, ALTERNATE, FOLLOW_LAST }

data class StrategyRule(val pattern: String, val prediction: Side)
data class StrategyConfig(
    val mode: StrategyMode,
    val skipOnLoss: Int?,
    val startingSide: Side,
    val rules: List<StrategyRule>
)

object StrategyConfigStore {
    private const val KEY_PREFIX = "strategy_config_"

    fun defaults(strategy: Strategy): StrategyConfig {
        val rules = when (strategy) {
            Strategy.TREND_BASE -> listOf(
                StrategyRule("SSS", Side.SMALL),
                StrategyRule("BBB", Side.BIG)
            )
            Strategy.ONE_BY_ONE -> listOf(
                StrategyRule("BSB", Side.SMALL),
                StrategyRule("SBS", Side.BIG)
            )
            Strategy.LAST_THREE_B -> listOf(
                StrategyRule("BBS", Side.SMALL),
                StrategyRule("SSB", Side.BIG)
            )
            Strategy.LAST_THREE_A -> listOf(
                StrategyRule("SBB", Side.SMALL),
                StrategyRule("BSS", Side.BIG)
            )
            Strategy.CUSTOM -> emptyList()
        }
        return StrategyConfig(
            mode = StrategyMode.PATTERN,
            skipOnLoss = null,
            startingSide = Side.BIG,
            rules = rules
        )
    }

    fun load(prefs: SharedPreferences, strategy: Strategy): StrategyConfig {
        val raw = prefs.getString(KEY_PREFIX + strategy.name, null) ?: return defaults(strategy)
        return runCatching {
            val json = JSONObject(raw)
            val mode = runCatching { StrategyMode.valueOf(json.optString("mode", "PATTERN")) }.getOrDefault(StrategyMode.PATTERN)
            val skip = if (json.has("skipOnLoss") && !json.isNull("skipOnLoss")) json.optInt("skipOnLoss").coerceIn(0, 50) else null
            val starting = runCatching { Side.valueOf(json.optString("startingSide", "BIG")) }.getOrDefault(Side.BIG)
            val rulesJson = json.optJSONArray("rules") ?: JSONArray()
            val rules = (0 until rulesJson.length()).mapNotNull { i ->
                val item = rulesJson.optJSONObject(i) ?: return@mapNotNull null
                val pattern = item.optString("pattern", "").uppercase().filter { it == 'B' || it == 'S' }
                val side = runCatching { Side.valueOf(item.optString("prediction", "BIG")) }.getOrNull()
                if (pattern.length == 3 && side != null) StrategyRule(pattern, side) else null
            }.take(3)
            StrategyConfig(mode, skip, starting, rules)
        }.getOrDefault(defaults(strategy))
    }

    fun save(prefs: SharedPreferences, strategy: Strategy, config: StrategyConfig) {
        val json = JSONObject()
            .put("mode", config.mode.name)
            .put("skipOnLoss", config.skipOnLoss?.coerceIn(0, 50) ?: JSONObject.NULL)
            .put("startingSide", config.startingSide.name)
        val rules = JSONArray()
        config.rules.take(3).forEach { rule ->
            if (rule.pattern.length == 3) {
                rules.put(JSONObject().put("pattern", rule.pattern).put("prediction", rule.prediction.name))
            }
        }
        json.put("rules", rules)
        prefs.edit().putString(KEY_PREFIX + strategy.name, json.toString()).apply()
    }

    fun defaultSkipLabel(strategy: Strategy): String = when (strategy) {
        Strategy.TREND_BASE -> "5 (Level 2+)"
        Strategy.ONE_BY_ONE -> "1"
        Strategy.LAST_THREE_A, Strategy.LAST_THREE_B, Strategy.CUSTOM -> "0"
    }

    fun effectiveSkip(prefs: SharedPreferences?, strategy: Strategy, level: Int): Int {
        val config = if (prefs == null) defaults(strategy) else load(prefs, strategy)
        if (config.skipOnLoss != null) return config.skipOnLoss
        return when (strategy) {
            Strategy.TREND_BASE -> if (level >= 2) 5 else 0
            Strategy.ONE_BY_ONE -> 1
            Strategy.LAST_THREE_A, Strategy.LAST_THREE_B, Strategy.CUSTOM -> 0
        }
    }
}
