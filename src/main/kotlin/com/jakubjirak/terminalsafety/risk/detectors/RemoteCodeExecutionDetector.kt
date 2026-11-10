/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags the classic "pipe untrusted network bytes straight into a shell
 * interpreter" pattern: `curl … | bash`, `wget -qO- … | sh`, plus the
 * Windows equivalent `iex (iwr …)`.
 *
 * HIGH risk because the command does not show the user what will run —
 * the payload is fetched at run time.
 *
 * False positives: some install docs instruct this pattern
 * (`curl https://sh.rustup.rs | sh`). That is still objectively risky and
 * worth a warning layer.
 */
class RemoteCodeExecutionDetector : RiskSignalDetector {
    override val id: String = "remote-code-execution"

    override fun detect(command: String): List<RiskSignal> {
        val out = mutableListOf<RiskSignal>()

        val curlPipe = Regex("""\b(?:curl|wget|fetch)\b[^|]*\|\s*(?:sudo\s+)?(?:ba|z|k)?sh\b""")
        curlPipe.find(command)?.let {
            out += RiskSignal(
                id = "rce.curl-pipe-shell",
                description = "Network download piped directly into a shell interpreter",
                weight = 75,
                matched = it.value.trim(),
            )
        }

        val iex = Regex("""\biex\s*\(|\bInvoke-Expression\b""", RegexOption.IGNORE_CASE)
        val iwr = Regex(
            """\b(?:iwr|Invoke-WebRequest|DownloadString|DownloadFile)\b""",
            RegexOption.IGNORE_CASE,
        )
        if (iex.containsMatchIn(command) && iwr.containsMatchIn(command)) {
            out += RiskSignal(
                id = "rce.powershell-iex-web",
                description = "PowerShell Invoke-Expression on network-fetched payload",
                weight = 75,
                matched = iex.find(command)!!.value,
            )
        }

        return out
    }
}
