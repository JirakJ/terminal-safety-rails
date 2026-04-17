/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags network-upload patterns that exfiltrate local files.
 *
 * Heuristics: `curl -T`, `curl --data-binary @file`, `curl -F ...@file`,
 * `scp local remote:` (non-local host), `rsync … user@host:`.
 *
 * False positives: `curl -F` and `scp` are commonly used. Weight is
 * moderate so it only becomes HIGH when paired with, e.g.,
 * [ProductionTargetDetector] or [CredentialExposureDetector].
 */
class NetworkExfiltrationDetector : RiskSignalDetector {
    override val id: String = "network-exfiltration"

    override fun detect(command: String): List<RiskSignal> {
        val out = mutableListOf<RiskSignal>()

        val curlUpload = Regex(
            """\bcurl\b[^|;&]*?(?:-T\s+\S+|--data-binary\s+@\S+|-F\s+['"]?[^'" ]*@\S+)""",
        )
        curlUpload.find(command)?.let {
            out += RiskSignal(
                id = "exfil.curl-upload",
                description = "curl uploading a local file to a remote endpoint",
                weight = 45,
                matched = it.value.trim(),
            )
        }

        val scp = Regex("""\bscp\b[^|;&]*?\s(\S+):""")
        scp.find(command)?.let { m ->
            val host = m.groupValues[1].substringAfterLast('@')
            if (host.isNotEmpty() && host != "localhost" && !host.startsWith("127.")) {
                out += RiskSignal(
                    id = "exfil.scp-out",
                    description = "scp transferring to non-local host `$host`",
                    weight = 40,
                    matched = m.value.trim(),
                )
            }
        }

        val rsync = Regex("""\brsync\b[^|;&]*?\s\S+@\S+:""")
        rsync.find(command)?.let {
            out += RiskSignal(
                id = "exfil.rsync-out",
                description = "rsync uploading to a remote user@host",
                weight = 35,
                matched = it.value.trim(),
            )
        }

        return out
    }
}
