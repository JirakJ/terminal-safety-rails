package com.jakubjirak.terminalsafety.model

/**
 * Risk level for dangerous commands, ordered from most to least severe.
 */
enum class RiskLevel(val displayName: String, val colorHex: String) {
    CRITICAL("Critical", "#E53935"),
    HIGH("High", "#FB8C00"),
    MEDIUM("Medium", "#FDD835"),
    LOW("Low", "#66BB6A"),
}
