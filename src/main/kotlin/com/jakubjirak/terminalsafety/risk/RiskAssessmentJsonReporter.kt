/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk

/**
 * Minimal dependency-free JSON renderer for [RiskAssessment] so batch
 * reports can be produced from the CLI without dragging in Jackson/Gson.
 *
 * Output is a single JSON object (or array, for `renderAll`). Strings are
 * escaped according to RFC 8259.
 */
object RiskAssessmentJsonReporter {

    /** Renders one assessment as a compact JSON object. */
    fun render(assessment: RiskAssessment): String = buildString {
        append('{')
        appendKv("command", assessment.command); append(',')
        appendKv("score", assessment.score.toString(), quoted = false); append(',')
        appendKv("level", assessment.level.name); append(',')
        appendKv("timestampEpochMs", assessment.timestampEpochMs.toString(), quoted = false); append(',')
        append("\"signals\":[")
        assessment.signals.forEachIndexed { i, s ->
            if (i > 0) append(',')
            append('{')
            appendKv("id", s.id); append(',')
            appendKv("description", s.description); append(',')
            appendKv("weight", s.weight.toString(), quoted = false); append(',')
            appendKv("matched", s.matched)
            append('}')
        }
        append("]}")
    }

    /** Renders a list as a JSON array. */
    fun renderAll(assessments: List<RiskAssessment>): String = buildString {
        append('[')
        assessments.forEachIndexed { i, a ->
            if (i > 0) append(',')
            append(render(a))
        }
        append(']')
    }

    private fun StringBuilder.appendKv(key: String, value: String, quoted: Boolean = true) {
        append('"').append(escape(key)).append('"').append(':')
        if (quoted) append('"').append(escape(value)).append('"') else append(value)
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length + 2)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }
}
