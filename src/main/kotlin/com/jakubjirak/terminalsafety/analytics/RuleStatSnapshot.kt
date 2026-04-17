package com.jakubjirak.terminalsafety.analytics

/**
 * Immutable, thread-safe DTO representing a snapshot of a single rule's
 * analytics at a moment in time. Safe to pass to UI components.
 */
data class RuleStatSnapshot(
    val ruleId: String,
    val hitCount: Long,
    val blockCount: Long,
    val allowCount: Long,
    val confirmBlockedCount: Long,
    val confirmAllowedCount: Long,
    val firstSeenEpoch: Long,
    val lastSeenEpoch: Long,
    val lastMatchedCommand: String,
) {
    /** Percentage of matches that resulted in the command being blocked (0..100). */
    val blockPercentage: Double
        get() {
            if (hitCount == 0L) return 0.0
            val blocked = blockCount + confirmBlockedCount
            return blocked.toDouble() * 100.0 / hitCount.toDouble()
        }
}
