/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags commands that echo/print secret-bearing environment variables or
 * embed literal credentials.
 *
 * Heuristics:
 *
 *  * `echo`/`printf`/`cat $PASSWORD` (or any `$VAR` whose name contains a
 *    secret keyword).
 *  * `printenv` — dumps everything.
 *  * Literal substrings like `AWS_SECRET`, `GITHUB_TOKEN`, `ghp_`, `AKIA`.
 *
 * False positives: variables named `PASSWORD_FILE` or `TOKEN_URL` match
 * by substring. Weight is moderate, not critical.
 */
class CredentialExposureDetector : RiskSignalDetector {
    override val id: String = "credential-exposure"

    private val secretKeywords = listOf(
        "PASSWORD", "PASSWD", "SECRET", "TOKEN", "API_KEY", "APIKEY",
        "AWS_SECRET", "AWS_SECRET_ACCESS_KEY", "GITHUB_TOKEN", "GH_TOKEN",
        "NPM_TOKEN", "PYPI_TOKEN", "DATABASE_URL", "PRIVATE_KEY",
    )

    override fun detect(command: String): List<RiskSignal> {
        val out = mutableListOf<RiskSignal>()

        val echoRegex = Regex(
            """\b(?:echo|printf|cat)\b[^|;&]*\$\{?([A-Z_][A-Z0-9_]*)\}?""",
        )
        for (m in echoRegex.findAll(command)) {
            val varName = m.groupValues[1]
            if (secretKeywords.any { varName.contains(it) }) {
                out += RiskSignal(
                    id = "cred.echo-secret-var",
                    description = "Printing env var `$varName` exposes a likely secret",
                    weight = 40,
                    matched = m.value.trim(),
                )
                break
            }
        }

        val printenv = Regex("""\bprintenv\b([^\n|;&]*)""")
        printenv.find(command)?.let { m ->
            val args = m.groupValues[1].trim()
            if (args.isEmpty() || secretKeywords.any { args.uppercase().contains(it) }) {
                out += RiskSignal(
                    id = "cred.printenv",
                    description = "printenv dumps environment, potentially leaking secrets",
                    weight = 40,
                    matched = m.value.trim(),
                )
            }
        }

        for (kw in listOf("AWS_SECRET", "GITHUB_TOKEN", "ghp_", "gho_", "AKIA")) {
            val idx = command.indexOf(kw)
            if (idx >= 0) {
                val end = (idx + kw.length + 12).coerceAtMost(command.length)
                out += RiskSignal(
                    id = "cred.literal.${kw.lowercase().trimEnd('_')}",
                    description = "Command contains a likely literal credential prefix (`$kw`)",
                    weight = 45,
                    matched = command.substring(idx, end),
                )
                break
            }
        }

        return out
    }
}
