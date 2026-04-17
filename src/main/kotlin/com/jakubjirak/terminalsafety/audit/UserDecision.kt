package com.jakubjirak.terminalsafety.audit

/**
 * Decision made for an intercepted command, recorded on each [AuditEvent].
 *
 * - [BLOCKED]: rule blocked the command without user interaction (auto-block policy).
 * - [ALLOWED]: rule matched but command was auto-allowed (warn-only policy).
 * - [CONFIRMED_ALLOWED]: confirmation dialog shown and the user accepted.
 * - [CONFIRMED_BLOCKED]: confirmation dialog shown and the user cancelled.
 * - [RAN_ANYWAY]: user bypassed the safety net (reserved for future force-run flow).
 */
enum class UserDecision {
    BLOCKED,
    ALLOWED,
    CONFIRMED_ALLOWED,
    CONFIRMED_BLOCKED,
    RAN_ANYWAY,
}
