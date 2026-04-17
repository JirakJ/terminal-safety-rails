package com.jakubjirak.terminalsafety.audit

/**
 * Pure-Kotlin exporter that renders a sequence of [AuditEvent]s as CSV
 * (RFC 4180) or as a single JSON array.
 *
 * Column order is stable and documented on [CSV_COLUMNS]; it is part of the
 * feature's compliance contract — external audit tooling depends on it.
 */
object AuditExporter {

    /** Fixed CSV column order. Do NOT reorder without a schema bump. */
    val CSV_COLUMNS: List<String> = listOf(
        "eventId",
        "isoTimestamp",
        "user",
        "host",
        "projectName",
        "ruleId",
        "decision",
        "command",
        "commandHash",
    )

    /**
     * Renders [events] as an RFC 4180 CSV document with a header row.
     * Fields containing commas, quotes, `\r` or `\n` are quoted and inner
     * quotes are doubled.
     */
    fun toCsv(events: Sequence<AuditEvent>): String {
        val sb = StringBuilder()
        sb.append(CSV_COLUMNS.joinToString(",")).append("\r\n")
        for (e in events) {
            sb.append(csvEscape(e.eventId)).append(',')
                .append(csvEscape(e.isoTimestamp)).append(',')
                .append(csvEscape(e.user)).append(',')
                .append(csvEscape(e.host)).append(',')
                .append(csvEscape(e.projectName)).append(',')
                .append(csvEscape(e.ruleId)).append(',')
                .append(csvEscape(e.decision.name)).append(',')
                .append(csvEscape(e.command)).append(',')
                .append(csvEscape(e.commandHash))
                .append("\r\n")
        }
        return sb.toString()
    }

    /** Renders [events] as a pretty-printed JSON array. */
    fun toJson(events: Sequence<AuditEvent>): String {
        val list = events.toList()
        if (list.isEmpty()) return "[]"
        val sb = StringBuilder()
        sb.append("[\n")
        for ((i, e) in list.withIndex()) {
            sb.append("  ").append(AuditJson.encode(e))
            if (i != list.size - 1) sb.append(',')
            sb.append('\n')
        }
        sb.append(']')
        return sb.toString()
    }

    /** RFC 4180 escape: quote if field contains `,` `"` `\r` `\n`; inner `"` doubled. */
    internal fun csvEscape(field: String): String {
        val needsQuote = field.contains(',') ||
            field.contains('"') ||
            field.contains('\n') ||
            field.contains('\r')
        if (!needsQuote) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }
}
