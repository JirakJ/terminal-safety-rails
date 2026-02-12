package com.jakubjirak.terminalsafety.model

/**
 * A rule that defines a dangerous command pattern.
 */
data class CommandRule(
    val pattern: String,
    val riskLevel: RiskLevel,
    val category: CommandCategory,
    val description: String,
    val isRegex: Boolean = false,
)
