package com.jakubjirak.terminalsafety.audit

/**
 * Filter criteria applied to an audit-log query.
 *
 * All fields are combined with AND semantics; unset fields are ignored. A
 * default-constructed filter matches every event up to [limit].
 *
 * @property sinceEpochMs Inclusive lower bound on [AuditEvent.timestampEpochMs].
 * @property untilEpochMs Inclusive upper bound on [AuditEvent.timestampEpochMs].
 * @property ruleIds Accept only events whose [AuditEvent.ruleId] is in this set.
 * @property decisions Accept only events whose [AuditEvent.decision] is in this set.
 * @property commandContains Case-insensitive substring match on the (masked) command.
 * @property userEquals Exact match against [AuditEvent.user].
 * @property limit Maximum number of events returned by [AuditLogStore.query].
 */
data class AuditFilter(
    val sinceEpochMs: Long? = null,
    val untilEpochMs: Long? = null,
    val ruleIds: Set<String>? = null,
    val decisions: Set<UserDecision>? = null,
    val commandContains: String? = null,
    val userEquals: String? = null,
    val limit: Int = Int.MAX_VALUE,
) {
    /** Returns `true` if [event] satisfies every set criterion on this filter. */
    fun matches(event: AuditEvent): Boolean =
        (sinceEpochMs == null || event.timestampEpochMs >= sinceEpochMs) &&
            (untilEpochMs == null || event.timestampEpochMs <= untilEpochMs) &&
            (ruleIds == null || event.ruleId in ruleIds) &&
            (decisions == null || event.decision in decisions) &&
            (commandContains.isNullOrEmpty() || event.command.contains(commandContains, ignoreCase = true)) &&
            (userEquals == null || event.user == userEquals)
}
