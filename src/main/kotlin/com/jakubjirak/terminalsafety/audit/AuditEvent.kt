package com.jakubjirak.terminalsafety.audit

import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Single, immutable forensic audit entry appended to the Incident Audit Log
 * every time a rule matches a terminal command.
 *
 * Designed to carry enough signal to satisfy SOC2 / ISO 27001 / PCI DSS
 * evidence requirements: who (user/host), what (rule id + action + decision),
 * where (project name/path), when (epoch + ISO-8601 UTC), and a masked
 * command plus a SHA-256 hash of the original command so two audit log
 * entries for the same raw command can be correlated without storing any
 * cleartext secret.
 *
 * The class is intentionally a `data class` of immutable primitives so it
 * can be safely shared across threads and JSON-serialized as a single line.
 */
data class AuditEvent(
    /** UUID v4 unique id for this audit event. */
    val eventId: String,
    /** Event time in milliseconds since the UNIX epoch. */
    val timestampEpochMs: Long,
    /** Event time rendered as ISO-8601 in UTC (e.g. `2026-04-17T12:34:56.789Z`). */
    val isoTimestamp: String,
    /** Value of `user.name` on the machine that produced the event. */
    val user: String,
    /** Short host name of the machine (lazily cached by [AuditEvent.hostName]). */
    val host: String,
    /** Project display name (`Project.name`) or `"<none>"` when unavailable. */
    val projectName: String,
    /** Absolute path of the project base dir, or empty string when unavailable. */
    val projectPath: String,
    /** Stable rule identifier from [com.jakubjirak.terminalsafety.model.CommandRule.id]. */
    val ruleId: String,
    /** Human-readable rule description (may be null). */
    val ruleDescription: String?,
    /** Name of the rule action enum (e.g. the analytics [com.jakubjirak.terminalsafety.analytics.MatchAction]). */
    val ruleAction: String,
    /** Final user/system decision for this match. */
    val decision: UserDecision,
    /** The command, after secret masking by [CommandMasker]. */
    val command: String,
    /** SHA-256 hex of the unmasked original command so duplicates correlate without leaking secrets. */
    val commandHash: String,
    /** Schema version of this record; bump when fields are added/renamed. */
    val schemaVersion: Int = 1,
) {
    companion object {
        private val ISO: DateTimeFormatter =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)

        @Volatile
        private var cachedHost: String? = null

        /**
         * Returns the short host name of the local machine, cached after the
         * first successful lookup. Falls back to `"unknown-host"` on error.
         */
        fun hostName(): String {
            cachedHost?.let { return it }
            val resolved = try {
                InetAddress.getLocalHost().hostName ?: "unknown-host"
            } catch (_: UnknownHostException) {
                "unknown-host"
            } catch (_: SecurityException) {
                "unknown-host"
            }
            cachedHost = resolved
            return resolved
        }

        /** Visible for tests — resets the cached host name. */
        internal fun resetHostCacheForTests() {
            cachedHost = null
        }

        /** Formats [epochMs] as ISO-8601 in UTC. */
        fun formatIso(epochMs: Long): String = ISO.format(Instant.ofEpochMilli(epochMs))

        /** Generates a new UUID v4 string. */
        fun newEventId(): String = UUID.randomUUID().toString()
    }
}
