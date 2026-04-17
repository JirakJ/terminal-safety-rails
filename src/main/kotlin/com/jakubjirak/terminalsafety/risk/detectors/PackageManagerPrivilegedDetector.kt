/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags privileged global installs and "curl | sudo apt" patterns.
 *
 * Heuristics:
 *
 *  * `sudo npm install -g …`, `sudo yarn global add …`, `sudo pnpm … -g`.
 *  * `sudo pip install …` — pip as root runs arbitrary `setup.py`.
 *  * `curl … | sudo (apt|apt-get|yum|dnf|pacman|bash|sh)`.
 *  * `sudo gem install`.
 *
 * False positives: some distros require `sudo apt`. Weight stays moderate
 * so it only compounds with other signals.
 */
class PackageManagerPrivilegedDetector : RiskSignalDetector {
    override val id: String = "package-manager-privileged"

    override fun detect(command: String): List<RiskSignal> {
        val out = mutableListOf<RiskSignal>()

        val sudoNpm = Regex("""\bsudo\s+(?:npm|yarn|pnpm)\b[^|;&]*?(?:\s-g\b|\bglobal\s+add\b)""")
        sudoNpm.find(command)?.let {
            out += RiskSignal(
                id = "pkg.sudo-npm-global",
                description = "Global JS package install as root (sudo npm -g / yarn global)",
                weight = 30,
                matched = it.value.trim(),
            )
        }

        val sudoPip = Regex("""\bsudo\s+pip[0-9]?\s+install\b""")
        sudoPip.find(command)?.let {
            out += RiskSignal(
                id = "pkg.sudo-pip-install",
                description = "pip install as root executes arbitrary setup.py with elevated rights",
                weight = 35,
                matched = it.value.trim(),
            )
        }

        val curlApt = Regex(
            """\b(?:curl|wget)\b[^|]*\|\s*sudo\s+(?:apt|apt-get|yum|dnf|pacman|bash|sh)\b""",
        )
        curlApt.find(command)?.let {
            out += RiskSignal(
                id = "pkg.curl-pipe-sudo-pm",
                description = "Network download piped into a privileged package manager or shell",
                weight = 55,
                matched = it.value.trim(),
            )
        }

        val sudoGem = Regex("""\bsudo\s+gem\s+install\b""")
        sudoGem.find(command)?.let {
            out += RiskSignal(
                id = "pkg.sudo-gem-install",
                description = "gem install as root runs native-extension builds with elevated rights",
                weight = 30,
                matched = it.value.trim(),
            )
        }

        return out
    }
}
