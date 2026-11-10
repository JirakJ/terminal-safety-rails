/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags privilege-escalation wrappers.
 *
 * By itself `sudo` is not catastrophic — it is a signal that **elevates**
 * the risk of whatever follows, so the weight is intentionally modest.
 * [com.jakubjirak.terminalsafety.risk.CommandRiskScorer] composes weights
 * additively, which is how `sudo rm -rf /` ends up in CRITICAL even though
 * neither detector alone would get there.
 *
 * False positives: legitimate admin work uses `sudo` constantly. That's
 * acceptable because the resulting score alone is still only LOW unless
 * paired with another detector.
 */
class PrivilegeEscalationDetector : RiskSignalDetector {
    override val id: String = "privilege-escalation"

    override fun detect(command: String): List<RiskSignal> {
        val out = mutableListOf<RiskSignal>()
        val patterns = listOf(
            Triple("sudo", Regex("""(^|[\s|&;])sudo\b"""), 20),
            Triple("su-dash", Regex("""(^|[\s|&;])su\s+-"""), 20),
            Triple("su", Regex("""(^|[\s|&;])su\b(?!\w|\s+-)"""), 15),
            Triple("doas", Regex("""(^|[\s|&;])doas\b"""), 18),
            Triple("pkexec", Regex("""(^|[\s|&;])pkexec\b"""), 20),
        )
        val seen = mutableSetOf<String>()
        for ((label, rx, weight) in patterns) {
            val m = rx.find(command) ?: continue
            if (!seen.add(label)) continue
            out += RiskSignal(
                id = "privilege.$label",
                description = "Privilege escalation via `$label` raises blast radius",
                weight = weight,
                matched = m.value.trim(),
            )
        }
        return out
    }
}
