package com.jakubjirak.terminalsafety.analytics

import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

/**
 * XML-serializable persistent state backing [RuleAnalyticsService].
 *
 * Kept as a plain list of [RuleStatEntry] so it round-trips cleanly through
 * `XmlSerializer.serialize` / `deserialize`. The service converts this into
 * a [java.util.concurrent.ConcurrentHashMap] at load time for thread-safe
 * mutation under concurrent access.
 */
class RuleAnalyticsState {

    @XCollection(style = XCollection.Style.v2)
    var stats: MutableList<RuleStatEntry> = mutableListOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RuleAnalyticsState) return false
        return stats.sortedBy { it.ruleId } == other.stats.sortedBy { it.ruleId }
    }

    override fun hashCode(): Int = stats.sortedBy { it.ruleId }.hashCode()
}

/**
 * Single rule's counters, serialized as `<entry>` in XML.
 *
 * Fields are `var` and have default values because the IntelliJ
 * `XmlSerializer` requires JavaBean-style round-tripping.
 */
@Tag("entry")
data class RuleStatEntry(
    var ruleId: String = "",
    var hitCount: Long = 0L,
    var blockCount: Long = 0L,
    var allowCount: Long = 0L,
    var confirmBlockedCount: Long = 0L,
    var confirmAllowedCount: Long = 0L,
    var firstSeenEpoch: Long = 0L,
    var lastSeenEpoch: Long = 0L,
    var lastMatchedCommand: String = "",
)
