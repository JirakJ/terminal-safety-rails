/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags git-history rewrites on shared branches.
 *
 * Heuristics:
 *
 *  * `git push --force`/`-f`/`--force-with-lease` targeting a branch named
 *    `main`, `master`, `develop`, or `release*`.
 *  * `git filter-branch` / `git filter-repo` — almost always a rewrite.
 *  * `git rebase -i main` / `…master`.
 *
 * False positives: `--force-with-lease` on a personal branch is fine, so
 * we only fire when the command line also mentions a shared-branch name.
 */
class GitHistoryRewriteDetector : RiskSignalDetector {
    override val id: String = "git-history-rewrite"

    private val sharedBranches = Regex("""(?:main|master|develop|release[\w/.-]*)""")

    override fun detect(command: String): List<RiskSignal> {
        val out = mutableListOf<RiskSignal>()

        val pushForce = Regex(
            """\bgit\s+push\b[^|;&]*?(?:--force\b|-f\b|--force-with-lease\b)[^|;&]*""",
        )
        pushForce.find(command)?.let { m ->
            if (sharedBranches.containsMatchIn(m.value)) {
                out += RiskSignal(
                    id = "git.push-force-shared",
                    description = "git push --force targeting a shared branch (main/master/develop/release*)",
                    weight = 60,
                    matched = m.value.trim(),
                )
            }
        }

        val filterBranch = Regex("""\bgit\s+filter-(?:branch|repo)\b""")
        filterBranch.find(command)?.let {
            out += RiskSignal(
                id = "git.filter-branch",
                description = "git filter-branch/filter-repo rewrites project history",
                weight = 55,
                matched = it.value,
            )
        }

        val rebaseInteractive = Regex(
            """\bgit\s+rebase\s+(?:-i|--interactive)\b[^|;&]*?\b(?:main|master)\b""",
        )
        rebaseInteractive.find(command)?.let {
            out += RiskSignal(
                id = "git.rebase-i-main",
                description = "Interactive rebase on main/master rewrites shared history",
                weight = 50,
                matched = it.value.trim(),
            )
        }

        return out
    }
}
