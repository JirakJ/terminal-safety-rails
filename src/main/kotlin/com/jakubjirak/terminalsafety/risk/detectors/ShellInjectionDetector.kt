/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags shell-substitution and dynamic evaluation that make static
 * analysis of a command impossible.
 *
 * Heuristics: backticks, `$(…)`, `eval`, and `bash -c "…"` / `sh -c` /
 * `zsh -c`.
 *
 * False positives: scripts and Makefiles use `$(…)` frequently, so its
 * weight is moderate. Combined with [RemoteCodeExecutionDetector] or
 * [CredentialExposureDetector] it escalates quickly.
 */
class ShellInjectionDetector : RiskSignalDetector {
    override val id: String = "shell-injection"

    override fun detect(command: String): List<RiskSignal> {
        val out = mutableListOf<RiskSignal>()

        Regex("""`[^`]+`""").find(command)?.let {
            out += RiskSignal(
                id = "shell.backticks",
                description = "Backtick command substitution obscures the executed command",
                weight = 25,
                matched = it.value,
            )
        }
        Regex("""\$\([^)]+\)""").find(command)?.let {
            out += RiskSignal(
                id = "shell.dollar-paren",
                description = "\$( … ) command substitution obscures the executed command",
                weight = 20,
                matched = it.value,
            )
        }
        Regex("""(^|[\s|&;])eval\b""").find(command)?.let {
            out += RiskSignal(
                id = "shell.eval",
                description = "eval executes dynamically constructed shell code",
                weight = 35,
                matched = it.value.trim(),
            )
        }
        Regex("""\b(?:bash|sh|zsh)\s+-c\s+['"]""").find(command)?.let {
            out += RiskSignal(
                id = "shell.dash-c",
                description = "Shell invoked with -c runs an inline script string",
                weight = 25,
                matched = it.value,
            )
        }
        return out
    }
}
