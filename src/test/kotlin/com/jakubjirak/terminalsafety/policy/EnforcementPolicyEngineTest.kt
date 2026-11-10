/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.policy

import com.jakubjirak.terminalsafety.model.RiskLevel as RuleRisk
import com.jakubjirak.terminalsafety.risk.RiskLevel as Heuristic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnforcementPolicyEngineTest {

    private fun decide(mode: EnforcementMode, rule: RuleRisk?, heuristic: Heuristic?) =
        EnforcementPolicyEngine.decide(mode, rule, heuristic)

    // ── Nothing matched ──────────────────────────────────────────────────

    @Test
    fun `no rule and no heuristic is always silent`() {
        for (mode in EnforcementMode.entries) {
            assertEquals(PolicyDecision.ALLOW_SILENT, decide(mode, null, null), "mode=$mode")
        }
    }

    @Test
    fun `low heuristic bands never trigger on their own`() {
        for (mode in EnforcementMode.entries) {
            for (h in listOf(Heuristic.SAFE, Heuristic.LOW, Heuristic.MEDIUM)) {
                assertEquals(PolicyDecision.ALLOW_SILENT, decide(mode, null, h), "mode=$mode h=$h")
            }
        }
    }

    // ── WARN ─────────────────────────────────────────────────────────────

    @Test
    fun `warn mode never blocks anything`() {
        assertEquals(PolicyDecision.NOTIFY_ONLY, decide(EnforcementMode.WARN, RuleRisk.LOW, null))
        assertEquals(PolicyDecision.NOTIFY_ONLY, decide(EnforcementMode.WARN, RuleRisk.CRITICAL, null))
        assertEquals(PolicyDecision.NOTIFY_ONLY, decide(EnforcementMode.WARN, null, Heuristic.CRITICAL))
        assertEquals(PolicyDecision.NOTIFY_ONLY, decide(EnforcementMode.WARN, null, Heuristic.HIGH))
    }

    // ── CONFIRM ──────────────────────────────────────────────────────────

    @Test
    fun `confirm mode always asks`() {
        for (rule in RuleRisk.entries) {
            assertEquals(PolicyDecision.REQUIRE_CONFIRMATION, decide(EnforcementMode.CONFIRM, rule, null), "rule=$rule")
        }
        assertEquals(PolicyDecision.REQUIRE_CONFIRMATION, decide(EnforcementMode.CONFIRM, null, Heuristic.HIGH))
        assertEquals(PolicyDecision.REQUIRE_CONFIRMATION, decide(EnforcementMode.CONFIRM, null, Heuristic.CRITICAL))
    }

    // ── BLOCK_CRITICAL ───────────────────────────────────────────────────

    @Test
    fun `block-critical blocks only the critical tier`() {
        assertEquals(PolicyDecision.BLOCK, decide(EnforcementMode.BLOCK_CRITICAL, RuleRisk.CRITICAL, null))
        assertEquals(PolicyDecision.BLOCK, decide(EnforcementMode.BLOCK_CRITICAL, null, Heuristic.CRITICAL))
        assertEquals(
            PolicyDecision.REQUIRE_CONFIRMATION,
            decide(EnforcementMode.BLOCK_CRITICAL, RuleRisk.HIGH, null),
        )
        assertEquals(
            PolicyDecision.REQUIRE_CONFIRMATION,
            decide(EnforcementMode.BLOCK_CRITICAL, RuleRisk.LOW, null),
        )
        assertEquals(
            PolicyDecision.REQUIRE_CONFIRMATION,
            decide(EnforcementMode.BLOCK_CRITICAL, null, Heuristic.HIGH),
        )
    }

    @Test
    fun `block-critical uses the worse of rule and heuristic`() {
        // medium rule, critical heuristic → blocked
        assertEquals(
            PolicyDecision.BLOCK,
            decide(EnforcementMode.BLOCK_CRITICAL, RuleRisk.MEDIUM, Heuristic.CRITICAL),
        )
    }

    // ── LOCKDOWN ─────────────────────────────────────────────────────────

    @Test
    fun `lockdown blocks high and critical tiers`() {
        assertEquals(PolicyDecision.BLOCK, decide(EnforcementMode.LOCKDOWN, RuleRisk.CRITICAL, null))
        assertEquals(PolicyDecision.BLOCK, decide(EnforcementMode.LOCKDOWN, RuleRisk.HIGH, null))
        assertEquals(PolicyDecision.BLOCK, decide(EnforcementMode.LOCKDOWN, null, Heuristic.CRITICAL))
        assertEquals(PolicyDecision.BLOCK, decide(EnforcementMode.LOCKDOWN, null, Heuristic.HIGH))
    }

    @Test
    fun `lockdown still confirms low and medium rules`() {
        assertEquals(PolicyDecision.REQUIRE_CONFIRMATION, decide(EnforcementMode.LOCKDOWN, RuleRisk.LOW, null))
        assertEquals(PolicyDecision.REQUIRE_CONFIRMATION, decide(EnforcementMode.LOCKDOWN, RuleRisk.MEDIUM, null))
    }

    @Test
    fun `lockdown blocks medium rule elevated by high heuristic`() {
        assertEquals(
            PolicyDecision.BLOCK,
            decide(EnforcementMode.LOCKDOWN, RuleRisk.MEDIUM, Heuristic.HIGH),
        )
    }

    // ── Mode parsing ─────────────────────────────────────────────────────

    @Test
    fun `fromString parses case-insensitively and defaults to CONFIRM`() {
        assertEquals(EnforcementMode.WARN, EnforcementMode.fromString("warn"))
        assertEquals(EnforcementMode.LOCKDOWN, EnforcementMode.fromString(" LOCKDOWN "))
        assertEquals(EnforcementMode.BLOCK_CRITICAL, EnforcementMode.fromString("Block_Critical"))
        assertEquals(EnforcementMode.CONFIRM, EnforcementMode.fromString(null))
        assertEquals(EnforcementMode.CONFIRM, EnforcementMode.fromString(""))
        assertEquals(EnforcementMode.CONFIRM, EnforcementMode.fromString("garbage"))
    }

    @Test
    fun `every mode has display name and description`() {
        for (mode in EnforcementMode.entries) {
            assert(mode.displayName.isNotBlank())
            assert(mode.description.isNotBlank())
        }
    }
}
