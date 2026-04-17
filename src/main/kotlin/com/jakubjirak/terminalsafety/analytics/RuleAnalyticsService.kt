package com.jakubjirak.terminalsafety.analytics

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Project-level service that records every rule match so teams can audit
 * which safety rules actually fire, what noise they produce, and which
 * rules are dead and can be pruned.
 *
 * Thread-safe: counters live in per-rule [RuleCounters] objects stored in a
 * [ConcurrentHashMap] and mutated under a short synchronized block; reads
 * (snapshots) do not block writers of other rules.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "TerminalSafetyRuleAnalytics",
    storages = [Storage("terminalSafetyRuleAnalytics.xml")],
)
class RuleAnalyticsService : PersistentStateComponent<RuleAnalyticsState> {

    private val counters = ConcurrentHashMap<String, RuleCounters>()

    /**
     * Records a single rule match. Safe to call from any thread and from
     * the command-evaluation hot path.
     *
     * @param ruleId Stable identifier from [com.jakubjirak.terminalsafety.model.CommandRule.id].
     * @param action Outcome of the match.
     * @param commandSnippet The raw command that matched. Will be masked
     *   and truncated before storage — callers should not pre-process it.
     */
    fun recordHit(ruleId: String, action: MatchAction, commandSnippet: String) {
        val now = System.currentTimeMillis()
        val masked = maskAndTruncate(commandSnippet)
        val c = counters.computeIfAbsent(ruleId) { RuleCounters(ruleId, now) }
        c.record(action, masked, now)
    }

    /**
     * Returns an immutable snapshot of all tracked rules, sorted by
     * [RuleStatSnapshot.hitCount] descending.
     */
    fun getSnapshot(): List<RuleStatSnapshot> =
        counters.values
            .map { it.snapshot() }
            .sortedByDescending { it.hitCount }

    /** Clears every tracked rule. */
    fun reset() {
        counters.clear()
    }

    /** Clears a single tracked rule. No-op if [ruleId] is unknown. */
    fun resetRule(ruleId: String) {
        counters.remove(ruleId)
    }

    override fun getState(): RuleAnalyticsState {
        val state = RuleAnalyticsState()
        state.stats = counters.values
            .map { it.toEntry() }
            .sortedBy { it.ruleId }
            .toMutableList()
        return state
    }

    override fun loadState(state: RuleAnalyticsState) {
        counters.clear()
        for (entry in state.stats) {
            if (entry.ruleId.isBlank()) continue
            counters[entry.ruleId] = RuleCounters.fromEntry(entry)
        }
    }

    /**
     * Per-rule mutable counter object. Protected by the instance monitor
     * so that counters stay mutually consistent in snapshots.
     */
    private class RuleCounters(
        val ruleId: String,
        firstSeenEpoch: Long,
    ) {
        private val firstSeen = firstSeenEpoch
        private var hitCount = 0L
        private var blockCount = 0L
        private var allowCount = 0L
        private var confirmBlockedCount = 0L
        private var confirmAllowedCount = 0L
        private var lastSeenEpoch = firstSeenEpoch
        private val lastCommand = AtomicReference("")

        @Synchronized
        fun record(action: MatchAction, maskedCommand: String, now: Long) {
            hitCount += 1
            when (action) {
                MatchAction.BLOCKED -> blockCount += 1
                MatchAction.ALLOWED -> allowCount += 1
                MatchAction.CONFIRM_BLOCKED -> confirmBlockedCount += 1
                MatchAction.CONFIRM_ALLOWED -> confirmAllowedCount += 1
            }
            lastSeenEpoch = now
            lastCommand.set(maskedCommand)
        }

        @Synchronized
        fun snapshot(): RuleStatSnapshot = RuleStatSnapshot(
            ruleId = ruleId,
            hitCount = hitCount,
            blockCount = blockCount,
            allowCount = allowCount,
            confirmBlockedCount = confirmBlockedCount,
            confirmAllowedCount = confirmAllowedCount,
            firstSeenEpoch = firstSeen,
            lastSeenEpoch = lastSeenEpoch,
            lastMatchedCommand = lastCommand.get(),
        )

        @Synchronized
        fun toEntry(): RuleStatEntry = RuleStatEntry(
            ruleId = ruleId,
            hitCount = hitCount,
            blockCount = blockCount,
            allowCount = allowCount,
            confirmBlockedCount = confirmBlockedCount,
            confirmAllowedCount = confirmAllowedCount,
            firstSeenEpoch = firstSeen,
            lastSeenEpoch = lastSeenEpoch,
            lastMatchedCommand = lastCommand.get(),
        )

        companion object {
            fun fromEntry(e: RuleStatEntry): RuleCounters {
                val c = RuleCounters(e.ruleId, e.firstSeenEpoch)
                c.hitCount = e.hitCount
                c.blockCount = e.blockCount
                c.allowCount = e.allowCount
                c.confirmBlockedCount = e.confirmBlockedCount
                c.confirmAllowedCount = e.confirmAllowedCount
                c.lastSeenEpoch = e.lastSeenEpoch
                c.lastCommand.set(e.lastMatchedCommand)
                return c
            }
        }
    }

    companion object {
        /** Max length of a stored last-matched command after masking. */
        const val MAX_COMMAND_LENGTH = 200

        private val MASK_PATTERNS: List<Regex> = listOf(
            // Authorization: Bearer <token>
            Regex("(?i)Authorization:\\s*Bearer\\s+\\S+"),
            // password=... / token=... / secret=... / api_key=...
            Regex("(?i)\\b(password|passwd|token|secret|api[_-]?key)\\s*=\\s*[^\\s&]+"),
            // -p <password>   (mysql/redis style)
            Regex("(?<=\\s)-p\\s+\\S+"),
            // long hex tokens
            Regex("\\b[0-9a-fA-F]{20,}\\b"),
            // base64-ish tokens (>= 24 chars). Placed last so it does not
            // clobber hex already masked.
            Regex("\\b[A-Za-z0-9+/=]{24,}\\b"),
        )

        /**
         * Masks sensitive fragments of [raw] and truncates to
         * [MAX_COMMAND_LENGTH]. Exposed as `internal` so tests can exercise
         * the masking rules directly without standing up a service.
         */
        internal fun maskAndTruncate(raw: String): String {
            var s = raw
            for (p in MASK_PATTERNS) {
                s = p.replace(s) { match ->
                    val v = match.value
                    when {
                        v.lowercase().startsWith("authorization") ->
                            "Authorization: Bearer ***"
                        v.startsWith("-p ") -> "-p ***"
                        v.contains('=') -> v.substringBefore('=') + "=***"
                        else -> "***"
                    }
                }
            }
            if (s.length > MAX_COMMAND_LENGTH) {
                s = s.substring(0, MAX_COMMAND_LENGTH)
            }
            return s
        }

        /** Convenience accessor mirroring other project services. */
        fun getInstance(project: Project): RuleAnalyticsService =
            project.getService(RuleAnalyticsService::class.java)
    }
}
