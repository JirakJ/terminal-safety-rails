package com.jakubjirak.terminalsafety.analytics

/**
 * Outcome of a rule match, recorded by [RuleAnalyticsService].
 *
 * - [BLOCKED]: rule blocked the command without user interaction (auto-block).
 * - [ALLOWED]: rule matched but command was auto-allowed (e.g. warn-only mode).
 * - [CONFIRM_BLOCKED]: confirmation dialog was shown and the user cancelled.
 * - [CONFIRM_ALLOWED]: confirmation dialog was shown and the user accepted.
 */
enum class MatchAction {
    BLOCKED,
    ALLOWED,
    CONFIRM_BLOCKED,
    CONFIRM_ALLOWED,
}
