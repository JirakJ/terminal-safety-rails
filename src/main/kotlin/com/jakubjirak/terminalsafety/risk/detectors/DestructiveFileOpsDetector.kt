/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags recursive/force filesystem deletions.
 *
 * Heuristics (and false-positive trade-offs):
 *
 *  * `rm -rf /` variants — very high confidence. Matches when any of `r`/
 *    `R`/`f` flags are present and the target is literally `/`, not
 *    `/tmp/...`.
 *  * `rm -rf ~` or `rm -rf $HOME` — high confidence; users rarely wipe
 *    their home dir intentionally.
 *  * `--no-preserve-root` — always a strong signal regardless of path.
 *  * `find … -delete` — moderate weight because legitimate build scripts
 *    use this; pairs with other signals to escalate.
 */
class DestructiveFileOpsDetector : RiskSignalDetector {
    override val id: String = "destructive-file-ops"

    override fun detect(command: String): List<RiskSignal> {
        val out = mutableListOf<RiskSignal>()
        val trimmed = command.trim()

        val rmRoot = Regex("""\brm\b[^|;&\n]*?-[a-zA-Z]*[rRf][a-zA-Z]*[^|;&\n]*?\s/(?=\s|$)""")
        rmRoot.find(trimmed)?.let {
            out += RiskSignal(
                id = "destructive.rm-rf-root",
                description = "Recursive force-delete targeting filesystem root (rm -rf /)",
                weight = 95,
                matched = it.value.trim(),
            )
        }

        val rmHome = Regex("""\brm\b[^|;&\n]*?-[a-zA-Z]*[rRf][a-zA-Z]*[^|;&\n]*?\s(?:~|\${'$'}HOME)(?=\s|$)""")
        rmHome.find(trimmed)?.let {
            out += RiskSignal(
                id = "destructive.rm-rf-home",
                description = "Recursive force-delete of user home directory",
                weight = 75,
                matched = it.value.trim(),
            )
        }

        if (trimmed.contains("--no-preserve-root")) {
            val idx = trimmed.indexOf("--no-preserve-root")
            out += RiskSignal(
                id = "destructive.no-preserve-root",
                description = "Explicit --no-preserve-root bypasses rm's safety guard",
                weight = 85,
                matched = trimmed.substring(idx, idx + "--no-preserve-root".length),
            )
        }

        val findDelete = Regex("""\bfind\b[^|;&]*\s-delete\b""")
        findDelete.find(trimmed)?.let {
            out += RiskSignal(
                id = "destructive.find-delete",
                description = "find … -delete recursively removes matching paths",
                weight = 35,
                matched = it.value.trim(),
            )
        }

        return out
    }
}
