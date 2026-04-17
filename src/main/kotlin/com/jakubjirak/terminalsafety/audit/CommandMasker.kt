package com.jakubjirak.terminalsafety.audit

/**
 * Pure Kotlin utility that redacts obvious secrets from a command line before
 * it is persisted to the Incident Audit Log.
 *
 * The masker favours redacting a token slightly too aggressively over leaking
 * one. All matched secret values are replaced with [MASK], with the semantic
 * key prefix preserved where possible (e.g. `password=***MASKED***`) so audit
 * readers can still see what kind of secret was masked.
 *
 * Side-effect free and thread-safe.
 */
object CommandMasker {

    /** Placeholder written in place of any matched secret fragment. */
    const val MASK: String = "***MASKED***"

    /** Maximum command length retained in the audit log; anything longer is truncated. */
    const val MAX_LENGTH: Int = 2000

    /** Suffix appended when a command is truncated. */
    const val TRUNCATION_SUFFIX: String = "…"

    // Ordered, most-specific-first. Order matters: key=value style rules run
    // before generic long-token rules so we can preserve the key prefix.
    private val PATTERNS: List<Regex> = listOf(
        Regex("(?i)Authorization:\\s*Bearer\\s+\\S+"),
        Regex("(?i)--password=\\S+"),
        Regex("(?i)\\b(password|passwd|token|secret|api[_-]?key)=\\S+"),
        // -p <arg> (MySQL / Redis): require whitespace BEFORE -p so unrelated
        // occurrences inside a word are left alone.
        Regex("(?:^|(?<=\\s))-p\\s+\\S+"),
        // Credit-card-like: 13-19 digits.
        Regex("\\b\\d{13,19}\\b"),
        // Long hex tokens (>= 20 hex chars).
        Regex("(?i)\\b[0-9a-f]{20,}\\b"),
        // Base64-ish tokens (>= 24 chars from the base64 alphabet).
        Regex("\\b[A-Za-z0-9+/=]{24,}\\b"),
    )

    /**
     * Masks sensitive fragments of [command] and truncates the result to
     * [MAX_LENGTH] characters (appending [TRUNCATION_SUFFIX] if truncated).
     */
    fun mask(command: String): String {
        var s = command
        for (p in PATTERNS) {
            s = p.replace(s) { m -> maskMatch(m.value) }
        }
        if (s.length > MAX_LENGTH) {
            s = s.substring(0, MAX_LENGTH - TRUNCATION_SUFFIX.length) + TRUNCATION_SUFFIX
        }
        return s
    }

    private fun maskMatch(value: String): String {
        val lower = value.lowercase()
        return when {
            lower.startsWith("authorization") -> "Authorization: Bearer $MASK"
            value.startsWith("--password=") -> "--password=$MASK"
            value.startsWith("-p ") || value.startsWith("-p\t") -> "-p $MASK"
            value.contains('=') -> value.substringBefore('=') + "=" + MASK
            else -> MASK
        }
    }
}
