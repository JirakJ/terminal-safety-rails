/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.history

import com.jakubjirak.terminalsafety.risk.RiskLevel

/** Markdown rendering of a [HistoryAuditReport] (PR / ticket friendly). */
object HistoryAuditMarkdownReporter {

    fun render(report: HistoryAuditReport, top: Int = 25): String = buildString {
        appendLine("# Shell History Risk Audit — `${report.sourceLabel.replace("`", "\\`")}`")
        appendLine()
        appendLine("- Safety score: **${report.safetyScore}/100**")
        appendLine("- Entries: **${report.totalEntries}** (unique commands: **${report.uniqueCommands}**)")
        appendLine("- Commands at MEDIUM+ risk: **${report.riskyCount}**")
        appendLine()
        appendLine("## Risk distribution")
        appendLine()
        appendLine("| Level | Distinct commands |")
        appendLine("|---|---|")
        for (level in RiskLevel.values().reversed()) {
            appendLine("| ${level.displayName} | ${report.distribution[level] ?: 0} |")
        }
        appendLine()
        appendLine("## Top risky commands")
        appendLine()
        val risky = report.topRisky(top)
        if (risky.isEmpty()) {
            appendLine("_No risky commands found — nice and boring history!_")
        } else {
            appendLine("| Score | Level | × | Command | Top signal |")
            appendLine("|---|---|---|---|---|")
            for (e in risky) {
                val signal = e.assessment.signals.firstOrNull()?.description ?: ""
                appendLine(
                    "| ${e.assessment.score} | ${e.assessment.level.displayName} | ${e.occurrences} " +
                        "| ${escape(e.command)} | ${escape(signal)} |"
                )
            }
        }
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace("\r", " ")
        .replace("\n", " ")
}

/** Stable JSON rendering of a [HistoryAuditReport] (no external deps). */
object HistoryAuditJsonReporter {

    fun render(report: HistoryAuditReport, top: Int = 100): String = buildString {
        appendLine("{")
        appendLine("  \"source\": ${json(report.sourceLabel)},")
        appendLine("  \"safetyScore\": ${report.safetyScore},")
        appendLine("  \"totalEntries\": ${report.totalEntries},")
        appendLine("  \"uniqueCommands\": ${report.uniqueCommands},")
        appendLine("  \"distribution\": {")
        val levels = RiskLevel.values()
        levels.forEachIndexed { i, level ->
            val comma = if (i < levels.size - 1) "," else ""
            appendLine("    ${json(level.name)}: ${report.distribution[level] ?: 0}$comma")
        }
        appendLine("  },")
        appendLine("  \"topRisky\": [")
        val risky = report.topRisky(top)
        risky.forEachIndexed { i, e ->
            val comma = if (i < risky.size - 1) "," else ""
            append("    {\"score\": ${e.assessment.score}, \"level\": ${json(e.assessment.level.name)}, ")
            append("\"occurrences\": ${e.occurrences}, \"command\": ${json(e.command)}, ")
            append("\"signals\": [")
            append(e.assessment.signals.joinToString(", ") { json(it.id) })
            appendLine("]}$comma")
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun json(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}

/**
 * Self-contained HTML report (inline CSS, no external assets) with a safety
 * score ring, risk-distribution bars and the top-risky-commands table.
 */
object HistoryAuditHtmlReporter {

    fun render(report: HistoryAuditReport, top: Int = 50): String {
        val ringColor = when {
            report.safetyScore >= 90 -> "#1a7f37"
            report.safetyScore >= 70 -> "#9a6700"
            else -> "#cf222e"
        }
        val circumference = 2 * Math.PI * 52
        val dash = circumference * report.safetyScore / 100.0
        val maxDistribution = report.distribution.values.maxOrNull()?.coerceAtLeast(1) ?: 1

        val sb = StringBuilder()
        sb.append(
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Shell History Risk Audit — ${esc(report.sourceLabel)}</title>
            <style>
              body { font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                     margin: 0; background: #f6f8fa; color: #1f2328; }
              .wrap { max-width: 1080px; margin: 0 auto; padding: 32px 24px 64px; }
              header { display: flex; align-items: center; gap: 32px; background: #fff;
                       border: 1px solid #d1d9e0; border-radius: 12px; padding: 24px; }
              h1 { font-size: 22px; margin: 0 0 6px; }
              .meta { color: #59636e; font-size: 13px; }
              .ring { position: relative; width: 120px; height: 120px; flex: none; }
              .ring svg { transform: rotate(-90deg); }
              .ring .value { position: absolute; inset: 0; display: flex; flex-direction: column;
                             align-items: center; justify-content: center; }
              .ring .value b { font-size: 28px; }
              .dist { background: #fff; border: 1px solid #d1d9e0; border-radius: 10px;
                      padding: 16px 20px; margin: 20px 0; }
              .dist .row { display: flex; align-items: center; gap: 12px; margin: 6px 0; font-size: 13px; }
              .dist .label { width: 70px; font-weight: 600; }
              .dist .bar { flex: 1; height: 14px; background: #eaeef2; border-radius: 7px; overflow: hidden; }
              .dist .bar i { display: block; height: 100%; }
              table { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #d1d9e0;
                      border-radius: 10px; overflow: hidden; font-size: 13px; }
              th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #eaeef2; vertical-align: top; }
              th { background: #f6f8fa; font-weight: 600; }
              .cmd { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
              .lvl { font-weight: 700; padding: 1px 8px; border-radius: 999px; font-size: 11px; white-space: nowrap; }
              footer { margin-top: 28px; color: #59636e; font-size: 12px; }
            </style>
            </head>
            <body>
            <div class="wrap">
            <header>
              <div class="ring" role="img" aria-label="Safety score ${report.safetyScore} out of 100">
                <svg width="120" height="120" viewBox="0 0 120 120" aria-hidden="true">
                  <circle cx="60" cy="60" r="52" fill="none" stroke="#eaeef2" stroke-width="12"/>
                  <circle cx="60" cy="60" r="52" fill="none" stroke="$ringColor" stroke-width="12"
                          stroke-linecap="round"
                          stroke-dasharray="${"%.2f".format(dash)} ${"%.2f".format(circumference)}"/>
                </svg>
                <div class="value"><b>${report.safetyScore}</b><span>/ 100</span></div>
              </div>
              <div>
                <h1>Shell History Risk Audit</h1>
                <div class="meta">Source: ${esc(report.sourceLabel)}</div>
                <div class="meta">Entries: ${report.totalEntries} · Unique commands: ${report.uniqueCommands}
                     · MEDIUM+ risk commands: ${report.riskyCount}</div>
              </div>
            </header>
            """.trimIndent()
        )

        sb.append("\n<div class=\"dist\"><b>Risk distribution (distinct commands)</b>\n")
        for (level in RiskLevel.values().reversed()) {
            val count = report.distribution[level] ?: 0
            val pct = count * 100 / maxDistribution
            sb.append("<div class=\"row\"><span class=\"label\">").append(esc(level.displayName))
                .append("</span><span class=\"bar\"><i style=\"width:").append(pct)
                .append("%;background:").append(level.colorHex).append("\"></i></span><span>")
                .append(count).append("</span></div>\n")
        }
        sb.append("</div>\n")

        val risky = report.topRisky(top)
        sb.append("<h2>Top risky commands (").append(risky.size).append(")</h2>\n")
        sb.append("<table>\n<thead><tr><th>Score</th><th>Level</th><th>×</th><th>Command</th><th>Signals</th><th>Safer alternative</th></tr></thead>\n<tbody>\n")
        if (risky.isEmpty()) {
            sb.append("<tr><td colspan=\"6\">No risky commands found — nice and boring history! 🎉</td></tr>\n")
        } else {
            for (e in risky) {
                val alt = e.alternatives.firstOrNull()
                sb.append("<tr>")
                    .append("<td><b>").append(e.assessment.score).append("</b></td>")
                    .append("<td><span class=\"lvl\" style=\"background:").append(e.assessment.level.colorHex)
                    .append("22;color:").append(e.assessment.level.colorHex).append("\">")
                    .append(esc(e.assessment.level.displayName)).append("</span></td>")
                    .append("<td>").append(e.occurrences).append("</td>")
                    .append("<td class=\"cmd\">").append(esc(e.command)).append("</td>")
                    .append("<td>").append(esc(e.assessment.signals.joinToString("; ") { it.description })).append("</td>")
                    .append("<td class=\"cmd\">").append(esc(alt?.command ?: "")).append("</td>")
                    .append("</tr>\n")
            }
        }
        sb.append("</tbody>\n</table>\n")
        sb.append("<footer>Generated by Terminal Safety Rails — Shell History Risk Audit.</footer>\n")
        sb.append("</div>\n</body>\n</html>\n")
        return sb.toString()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
