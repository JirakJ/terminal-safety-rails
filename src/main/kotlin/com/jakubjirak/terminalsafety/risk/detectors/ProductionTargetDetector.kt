/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags commands that appear to target production environments.
 *
 * Heuristics:
 *
 *  * `kubectl --context=prod*` / `-n prod*`.
 *  * Hostnames in ssh/scp/db URLs whose first label starts with `prod`
 *    or `production`.
 *  * `psql`/`mysql`/`mongo`/`redis-cli` connecting to a `prod*` host.
 *
 * False positives: we require a host-boundary prefix (`@`, `://`, `-h`,
 * `host=`, `--context=`, `-n `) so file paths named `reproduction/`
 * do not match.
 */
class ProductionTargetDetector : RiskSignalDetector {
    override val id: String = "production-target"

    override fun detect(command: String): List<RiskSignal> {
        val out = mutableListOf<RiskSignal>()

        val kubectlCtx = Regex(
            """\bkubectl\b[^|;&]*?(?:--context[= ]|--namespace[= ]|-n\s+)['"]?(prod[\w.-]*)""",
            RegexOption.IGNORE_CASE,
        )
        kubectlCtx.find(command)?.let { m ->
            out += RiskSignal(
                id = "prod.kubectl-context",
                description = "kubectl targeting a production context/namespace (`${m.groupValues[1]}`)",
                weight = 40,
                matched = m.value.trim(),
            )
        }

        val db = Regex(
            """\b(?:psql|mysql|mongo|redis-cli)\b[^|;&]*?(?:-h\s+|--host[= ])(prod[\w.-]*)""",
            RegexOption.IGNORE_CASE,
        )
        db.find(command)?.let { m ->
            out += RiskSignal(
                id = "prod.db-host",
                description = "Database client connecting to production host `${m.groupValues[1]}`",
                weight = 45,
                matched = m.value.trim(),
            )
        }

        val hostProd = Regex(
            """(?:@|://|\bhost=)(prod(?:uction)?[\w.-]*)""",
            RegexOption.IGNORE_CASE,
        )
        hostProd.find(command)?.let { m ->
            if (out.none { it.id.startsWith("prod.") }) {
                out += RiskSignal(
                    id = "prod.hostname",
                    description = "Command targets a hostname containing `prod`/`production`",
                    weight = 35,
                    matched = m.groupValues[1],
                )
            }
        }

        return out
    }
}
