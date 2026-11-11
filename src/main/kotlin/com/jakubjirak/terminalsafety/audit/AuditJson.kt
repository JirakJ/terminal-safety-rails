package com.jakubjirak.terminalsafety.audit

import java.security.MessageDigest
import java.util.Locale

/**
 * Hand-rolled, reflection-free JSON line codec for [AuditEvent].
 *
 * Written by hand to:
 * 1. Keep the audit log compatible with any downstream tool (jq, `grep`, …)
 *    regardless of the project's classpath.
 * 2. Avoid pulling Gson/Moshi reflection into the obfuscated plugin jar.
 *
 * The format is a canonical, single-line JSON object with keys in the order
 * declared by [AuditEvent]. Values are primitives or strings only.
 */
internal object AuditJson {

    private val JSON_WHITESPACE = setOf(' ', '\t', '\n', '\r')

    /** Serializes [event] to a single-line JSON string (no trailing newline). */
    fun encode(event: AuditEvent): String {
        val sb = StringBuilder(256)
        sb.append('{')
        field(sb, "eventId", event.eventId); sb.append(',')
        field(sb, "timestampEpochMs", event.timestampEpochMs); sb.append(',')
        field(sb, "isoTimestamp", event.isoTimestamp); sb.append(',')
        field(sb, "user", event.user); sb.append(',')
        field(sb, "host", event.host); sb.append(',')
        field(sb, "projectName", event.projectName); sb.append(',')
        field(sb, "projectPath", event.projectPath); sb.append(',')
        field(sb, "ruleId", event.ruleId); sb.append(',')
        fieldNullable(sb, "ruleDescription", event.ruleDescription); sb.append(',')
        field(sb, "ruleAction", event.ruleAction); sb.append(',')
        field(sb, "decision", event.decision.name); sb.append(',')
        field(sb, "command", event.command); sb.append(',')
        field(sb, "commandHash", event.commandHash); sb.append(',')
        field(sb, "schemaVersion", event.schemaVersion)
        sb.append('}')
        return sb.toString()
    }

    /** Parses a single JSON-Lines line back into an [AuditEvent]. */
    fun decode(line: String): AuditEvent {
        val map = JsonReader(line).readObject()
        return AuditEvent(
            eventId = map.str("eventId"),
            timestampEpochMs = map.num("timestampEpochMs").toLong(),
            isoTimestamp = map.str("isoTimestamp"),
            user = map.str("user"),
            host = map.str("host"),
            projectName = map.str("projectName"),
            projectPath = map.str("projectPath"),
            ruleId = map.str("ruleId"),
            ruleDescription = map["ruleDescription"] as String?,
            ruleAction = map.str("ruleAction"),
            decision = UserDecision.valueOf(map.str("decision")),
            command = map.str("command"),
            commandHash = map.str("commandHash"),
            schemaVersion = map.num("schemaVersion").toInt(),
        )
    }

    /** Computes SHA-256 hex of [raw] without caching. */
    fun sha256Hex(raw: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )

    private fun field(sb: StringBuilder, key: String, value: String) {
        appendString(sb, key); sb.append(':'); appendString(sb, value)
    }

    private fun field(sb: StringBuilder, key: String, value: Long) {
        appendString(sb, key); sb.append(':'); sb.append(value)
    }

    private fun field(sb: StringBuilder, key: String, value: Int) {
        appendString(sb, key); sb.append(':'); sb.append(value)
    }

    private fun fieldNullable(sb: StringBuilder, key: String, value: String?) {
        appendString(sb, key); sb.append(':')
        if (value == null) sb.append("null") else appendString(sb, value)
    }

    private fun appendString(sb: StringBuilder, value: String) {
        sb.append('"')
        for (i in value.indices) {
            val c = value[i]
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c.code < 0x20) {
                    sb.append("\\u").append(String.format(Locale.ROOT, "%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }

    private fun Map<String, Any?>.str(key: String): String =
        this[key] as? String ?: error("Missing/non-string field: $key")

    private fun Map<String, Any?>.num(key: String): Number =
        this[key] as? Number ?: error("Missing/non-number field: $key")

    /**
     * Minimal, RFC 8259-compatible JSON reader for the subset produced by
     * [encode] — flat objects of strings, numbers, booleans and null.
     */
    private class JsonReader(private val src: String) {
        private var i = 0

        fun readObject(): Map<String, Any?> {
            skipWs()
            require('{')
            val out = linkedMapOf<String, Any?>()
            skipWs()
            if (peek() == '}') { i++; return out }
            while (true) {
                skipWs()
                val key = readString()
                skipWs(); require(':'); skipWs()
                out[key] = readValue()
                skipWs()
                when (val c = next()) {
                    ',' -> continue
                    '}' -> return out
                    else -> error("Unexpected char '$c' at $i")
                }
            }
        }

        private fun readValue(): Any? {
            return when (val c = peek()) {
                '"' -> readString()
                't', 'f' -> readBool()
                'n' -> { expectLiteral("null"); null }
                else -> if (c == '-' || c in '0'..'9') readNumber()
                else error("Unexpected char '$c' at $i")
            }
        }

        private fun readBool(): Boolean =
            if (peek() == 't') { expectLiteral("true"); true }
            else { expectLiteral("false"); false }

        private fun expectLiteral(lit: String) {
            for (ch in lit) {
                val c = next()
                if (c != ch) error("Expected literal '$lit' at $i")
            }
        }

        private fun readNumber(): Number {
            val start = i
            if (peek() == '-') i++
            while (i < src.length && src[i] in '0'..'9') i++
            var isDouble = false
            if (i < src.length && src[i] == '.') {
                isDouble = true; i++
                while (i < src.length && src[i] in '0'..'9') i++
            }
            if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
                isDouble = true; i++
                if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
                while (i < src.length && src[i] in '0'..'9') i++
            }
            val s = src.substring(start, i)
            return if (isDouble) s.toDouble() else s.toLong()
        }

        private fun readString(): String {
            require('"')
            val sb = StringBuilder()
            while (true) {
                val c = next()
                if (c == '"') return sb.toString()
                if (c == '\\') {
                    when (val esc = next()) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            if (i + 4 > src.length) error("Bad \\u at $i")
                            val hex = src.substring(i, i + 4); i += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> error("Bad escape \\$esc at $i")
                    }
                } else {
                    sb.append(c)
                }
            }
        }

        private fun skipWs() {
            while (i < src.length) {
                val c = src[i]
                if (c in JSON_WHITESPACE) i++ else return
            }
        }

        private fun peek(): Char =
            if (i < src.length) src[i] else error("Unexpected EOF at $i")

        private fun next(): Char {
            if (i >= src.length) error("Unexpected EOF at $i")
            return src[i++]
        }

        private fun require(expected: Char) {
            val c = next()
            if (c != expected) error("Expected '$expected' but got '$c' at ${i - 1}")
        }
    }
}
