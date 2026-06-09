/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.history

import com.jakubjirak.terminalsafety.risk.CommandRiskScorer
import com.jakubjirak.terminalsafety.risk.RiskAssessment
import com.jakubjirak.terminalsafety.risk.RiskLevel
import com.jakubjirak.terminalsafety.safer.SaferAlternative
import com.jakubjirak.terminalsafety.safer.SaferAlternativeAdvisor

/**
 * One distinct command from the history with its occurrence count and risk
 * assessment.
 */
data class HistoryEntryAssessment(
    val command: String,
    val occurrences: Int,
    val assessment: RiskAssessment,
    val alternatives: List<SaferAlternative>,
)

/**
 * Aggregate result of auditing one shell-history file.
 *
 * @property sourceLabel Display label of the audited source (file path).
 * @property totalEntries All parsed history entries (including repeats).
 * @property uniqueCommands Number of distinct command lines.
 * @property assessed Every distinct command with count + assessment, ordered
 *   by descending score, then by descending occurrences.
 * @property distribution Count of distinct commands per [RiskLevel].
 * @property safetyScore 0–100: share of history entries (weighted by
 *   occurrences) that are below [RiskLevel.MEDIUM]. 100 = nothing risky.
 */
data class HistoryAuditReport(
    val sourceLabel: String,
    val totalEntries: Int,
    val uniqueCommands: Int,
    val assessed: List<HistoryEntryAssessment>,
    val distribution: Map<RiskLevel, Int>,
    val safetyScore: Int,
) {
    /** Top [n] riskiest distinct commands. */
    fun topRisky(n: Int): List<HistoryEntryAssessment> =
        assessed.asSequence().filter { it.assessment.score > 0 }.take(n.coerceAtLeast(0)).toList()

    val riskyCount: Int
        get() = assessed.count { it.assessment.level.ordinal >= RiskLevel.MEDIUM.ordinal }
}

/**
 * Scores every distinct command of a shell history with the existing
 * [CommandRiskScorer] and aggregates the result into a [HistoryAuditReport]
 * — a "postmortem" view of how risky the commands you actually run are.
 *
 * Deduplication: identical command lines are scored once and carry an
 * occurrence count, so a 50 000-line history with 3 000 distinct commands
 * costs 3 000 scorings.
 *
 * Pure Kotlin; reused by the IDE action and [HistoryAuditCli].
 */
class HistoryAuditor(
    private val scorer: CommandRiskScorer = CommandRiskScorer(),
    private val advisor: SaferAlternativeAdvisor = SaferAlternativeAdvisor(),
    private val parser: ShellHistoryParser = ShellHistoryParser(),
) {

    /** Audits raw history [content] attributed to [sourceLabel]. */
    fun audit(content: String, sourceLabel: String): HistoryAuditReport {
        val entries = parser.parse(content)
        val counts = LinkedHashMap<String, Int>()
        for (e in entries) counts.merge(e.command, 1, Int::plus)

        val assessed = counts.map { (command, count) ->
            val assessment = scorer.score(command)
            HistoryEntryAssessment(
                command = command,
                occurrences = count,
                assessment = assessment,
                alternatives = if (assessment.level.ordinal >= RiskLevel.MEDIUM.ordinal)
                    advisor.suggest(command) else emptyList(),
            )
        }.sortedWith(
            compareByDescending<HistoryEntryAssessment> { it.assessment.score }
                .thenByDescending { it.occurrences }
                .thenBy { it.command },
        )

        val distribution = RiskLevel.values().associateWith { level ->
            assessed.count { it.assessment.level == level }
        }

        return HistoryAuditReport(
            sourceLabel = sourceLabel,
            totalEntries = entries.size,
            uniqueCommands = counts.size,
            assessed = assessed,
            distribution = distribution,
            safetyScore = computeSafetyScore(assessed),
        )
    }

    /**
     * Weighted share of entries below MEDIUM risk, rounded to an integer
     * 0..100. An empty history scores 100.
     */
    private fun computeSafetyScore(assessed: List<HistoryEntryAssessment>): Int {
        val total = assessed.sumOf { it.occurrences }
        if (total == 0) return 100
        val safe = assessed
            .filter { it.assessment.level.ordinal < RiskLevel.MEDIUM.ordinal }
            .sumOf { it.occurrences }
        return Math.round(safe * 100.0 / total).toInt().coerceIn(0, 100)
    }
}
