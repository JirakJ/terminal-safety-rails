package com.jakubjirak.terminalsafety.model

/**
 * Represents a command that was detected as dangerous.
 */
data class DangerousCommand(
    val command: String,
    val matchedRule: CommandRule,
    val matchedPortion: String,
)
