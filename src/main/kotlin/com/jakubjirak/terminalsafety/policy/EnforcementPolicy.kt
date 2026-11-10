/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.policy

import com.jakubjirak.terminalsafety.model.RiskLevel as RuleRiskLevel
import com.jakubjirak.terminalsafety.risk.RiskLevel as HeuristicRiskLevel

/**
 * How strictly Terminal Safety Rails enforces its findings.
 *
 * - [WARN] — never blocks: matched commands run immediately and a warning
 *   notification is shown. For teams that want visibility without friction.
 * - [CONFIRM] — the classic behaviour: a confirmation dialog must be
 *   approved before the command runs. The default.
 * - [BLOCK_CRITICAL] — CRITICAL findings are blocked outright (no override
 *   dialog); everything else asks for confirmation. For shared/staging
 *   machines.
 * - [LOCKDOWN] — HIGH and CRITICAL findings are blocked outright; the rest
 *   asks for confirmation. For incident response / production freeze
 *   windows and compliance-controlled environments.
 */
enum class EnforcementMode(val displayName: String, val description: String) {
    WARN("Warn only", "Never block — show a warning notification and let the command run."),
    CONFIRM("Confirm (default)", "Show a confirmation dialog; the user decides."),
    BLOCK_CRITICAL("Block critical", "Auto-block CRITICAL findings; confirm everything else."),
    LOCKDOWN("Lockdown", "Auto-block HIGH and CRITICAL findings; confirm everything else."),
    ;

    companion object {
        /** Lenient parse falling back to [CONFIRM] — settings values survive renames. */
        fun fromString(value: String?): EnforcementMode =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: CONFIRM
    }
}

/** What the interceptor must do with a matched command. */
enum class PolicyDecision {
    /** Nothing matched — let the shell have it. */
    ALLOW_SILENT,

    /** Run the command, but show a warning notification (warn-only mode). */
    NOTIFY_ONLY,

    /** Show the confirmation dialog; the user decides. */
    REQUIRE_CONFIRMATION,

    /** Block the command outright with a notification — no override offered. */
    BLOCK,
}

/**
 * Pure decision matrix combining the [EnforcementMode] with the rule-based
 * risk level and/or the heuristic risk level of a command.
 *
 * The "danger tier" of a command is the worst of:
 *  * the matched rule's [RuleRiskLevel], and
 *  * the heuristic [HeuristicRiskLevel] (HIGH and CRITICAL only — lower
 *    heuristic bands never trigger interception on their own).
 *
 * | mode \ tier      | none         | LOW..MEDIUM          | HIGH                 | CRITICAL |
 * |------------------|--------------|----------------------|----------------------|----------|
 * | WARN             | ALLOW_SILENT | NOTIFY_ONLY          | NOTIFY_ONLY          | NOTIFY_ONLY |
 * | CONFIRM          | ALLOW_SILENT | REQUIRE_CONFIRMATION | REQUIRE_CONFIRMATION | REQUIRE_CONFIRMATION |
 * | BLOCK_CRITICAL   | ALLOW_SILENT | REQUIRE_CONFIRMATION | REQUIRE_CONFIRMATION | BLOCK |
 * | LOCKDOWN         | ALLOW_SILENT | REQUIRE_CONFIRMATION | BLOCK                | BLOCK |
 *
 * No IntelliJ dependencies — fully unit-testable.
 */
object EnforcementPolicyEngine {

    /** Internal severity tiers used by the decision matrix. */
    private enum class Tier { NONE, NOTABLE, HIGH, CRITICAL }

    /**
     * Decides what to do with a command.
     *
     * @param mode Active enforcement mode.
     * @param ruleRisk Risk level of the matched rule, or `null` when no rule matched.
     * @param heuristicLevel Heuristic risk band, or `null` when scoring was unavailable.
     */
    fun decide(
        mode: EnforcementMode,
        ruleRisk: RuleRiskLevel?,
        heuristicLevel: HeuristicRiskLevel?,
    ): PolicyDecision {
        val tier = tierOf(ruleRisk, heuristicLevel)
        if (tier == Tier.NONE) return PolicyDecision.ALLOW_SILENT
        return when (mode) {
            EnforcementMode.WARN -> PolicyDecision.NOTIFY_ONLY
            EnforcementMode.CONFIRM -> PolicyDecision.REQUIRE_CONFIRMATION
            EnforcementMode.BLOCK_CRITICAL ->
                if (tier == Tier.CRITICAL) PolicyDecision.BLOCK else PolicyDecision.REQUIRE_CONFIRMATION
            EnforcementMode.LOCKDOWN ->
                if (tier == Tier.HIGH || tier == Tier.CRITICAL) PolicyDecision.BLOCK
                else PolicyDecision.REQUIRE_CONFIRMATION
        }
    }

    private fun tierOf(ruleRisk: RuleRiskLevel?, heuristic: HeuristicRiskLevel?): Tier {
        val fromRule = when (ruleRisk) {
            RuleRiskLevel.CRITICAL -> Tier.CRITICAL
            RuleRiskLevel.HIGH -> Tier.HIGH
            RuleRiskLevel.MEDIUM, RuleRiskLevel.LOW -> Tier.NOTABLE
            null -> Tier.NONE
        }
        val fromHeuristic = when (heuristic) {
            HeuristicRiskLevel.CRITICAL -> Tier.CRITICAL
            HeuristicRiskLevel.HIGH -> Tier.HIGH
            else -> Tier.NONE // SAFE/LOW/MEDIUM never intercept on their own
        }
        return maxOf(fromRule, fromHeuristic)
    }
}
