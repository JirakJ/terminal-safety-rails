/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags shell fork-bombs — the canonical `:(){ :|:& };:` and lightly
 * obfuscated variants where the function name is anything matching
 * `[A-Za-z_]\w*`.
 *
 * The heuristic is narrow: any command defining a function that then pipes
 * itself to itself and backgrounds the pipe is suspicious regardless of
 * the identifier.
 */
class ForkBombDetector : RiskSignalDetector {
    override val id: String = "fork-bomb"

    override fun detect(command: String): List<RiskSignal> {
        val normalised = command.replace("\\s+".toRegex(), "")
        if (normalised.contains(":(){:|:&};:")) {
            val idx = command.indexOf(":(")
            val preview = if (idx >= 0) command.substring(idx) else ":(){ :|:& };:"
            return listOf(
                RiskSignal(
                    id = "forkbomb.canonical",
                    description = "Classic POSIX shell fork bomb",
                    weight = 95,
                    matched = preview.take(64),
                )
            )
        }

        val generic = Regex(
            """([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*\)\s*\{\s*\1\s*\|\s*\1\s*&\s*\}\s*;\s*\1""",
        )
        generic.find(command)?.let { m ->
            return listOf(
                RiskSignal(
                    id = "forkbomb.generic",
                    description = "Self-piping recursive function definition (fork-bomb shape)",
                    weight = 90,
                    matched = m.value,
                )
            )
        }

        return emptyList()
    }
}
