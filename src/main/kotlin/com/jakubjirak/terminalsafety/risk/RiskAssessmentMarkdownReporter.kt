/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk

/**
 * Renders a [RiskAssessment] as GitHub-flavoured Markdown suitable for
 * pasting into pull requests, incident tickets, or Slack messages.
 *
 * The output is deterministic and safe for untrusted command strings:
 * pipes, backticks, and angle-brackets are escaped so a malicious command
 * cannot break the table layout.
 */
object RiskAssessmentMarkdownReporter {

    /** Renders a single assessment. */
    fun render(assessment: RiskAssessment): String = buildString {
        appendLine("## Command Risk Assessment")
        appendLine()
        appendLine("- **Score**: ${assessment.score}/100")
        appendLine("- **Level**: ${assessment.level.displayName}")
        appendLine("- **Signals**: ${assessment.signals.size}")
        appendLine("- **Command**: `${escapeInline(assessment.command)}`")
        appendLine()
        if (assessment.signals.isEmpty()) {
            appendLine("_No heuristic signals fired — command looks safe._")
            return@buildString
        }
        appendLine("| Signal | Weight | Description | Match |")
        appendLine("| --- | ---: | --- | --- |")
        for (s in assessment.signals) {
            appendLine(
                "| `${escapeCell(s.id)}` | ${s.weight} | ${escapeCell(s.description)} | `${escapeInline(s.matched)}` |",
            )
        }
    }

    /** Renders a batch of assessments, one section per command. */
    fun renderAll(assessments: List<RiskAssessment>): String = buildString {
        appendLine("# Command Risk Batch Report")
        appendLine()
        appendLine("Total commands: ${assessments.size}")
        val byLevel = assessments.groupingBy { it.level }.eachCount()
        for (lvl in RiskLevel.values()) {
            appendLine("- ${lvl.displayName}: ${byLevel[lvl] ?: 0}")
        }
        appendLine()
        for (a in assessments) {
            appendLine(render(a))
            appendLine("---")
        }
    }

    private fun escapeCell(s: String): String =
        s.replace("|", "\\|").replace("\n", " ").replace("`", "\\`")

    private fun escapeInline(s: String): String =
        s.replace("`", "\\`").replace("\n", " ")
}
