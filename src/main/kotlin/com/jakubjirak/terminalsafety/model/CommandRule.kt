package com.jakubjirak.terminalsafety.model

/**
 * A rule that defines a dangerous command pattern.
 *
 * @property id Stable identifier for analytics/auditing. Defaults to
 *   `"<category>:<pattern>"` — unique in practice because default rules use
 *   distinct patterns per category and custom rules share a `CUSTOM` category
 *   with user-supplied distinct patterns.
 */
data class CommandRule(
    val pattern: String,
    val riskLevel: RiskLevel,
    val category: CommandCategory,
    val description: String,
    val isRegex: Boolean = false,
    val id: String = "${category.name}:$pattern",
)
