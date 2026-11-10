/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags raw-disk overwrite/format/shred commands.
 *
 * Heuristics: `dd of=/dev/sd?`, `mkfs[.ext4]` with `/dev/…` target,
 * `shred /dev/…`. Skips `/dev/null` and `/dev/std*` targets which are
 * harmless.
 */
class DiskWipeDetector : RiskSignalDetector {
    override val id: String = "disk-wipe"

    override fun detect(command: String): List<RiskSignal> {
        val out = mutableListOf<RiskSignal>()

        val dd = Regex("""\bdd\b[^|;&]*?\bof=(/dev/[A-Za-z0-9]+)""")
        dd.find(command)?.let { m ->
            val target = m.groupValues[1]
            if (!target.startsWith("/dev/null") && !target.startsWith("/dev/std")) {
                out += RiskSignal(
                    id = "disk.dd-of-device",
                    description = "dd writing raw bytes to block device `$target`",
                    weight = 95,
                    matched = m.value.trim(),
                )
            }
        }

        val mkfs = Regex("""\bmkfs(?:\.[a-z0-9]+)?\b[^|;&]*?(/dev/[A-Za-z0-9]+)""")
        mkfs.find(command)?.let { m ->
            out += RiskSignal(
                id = "disk.mkfs-device",
                description = "mkfs formatting block device `${m.groupValues[1]}`",
                weight = 90,
                matched = m.value.trim(),
            )
        }

        val shred = Regex("""\bshred\b[^|;&]*?(/dev/[A-Za-z0-9]+)""")
        shred.find(command)?.let { m ->
            val target = m.groupValues[1]
            if (!target.startsWith("/dev/null") && !target.startsWith("/dev/std")) {
                out += RiskSignal(
                    id = "disk.shred-device",
                    description = "shred overwriting block device `$target`",
                    weight = 85,
                    matched = m.value.trim(),
                )
            }
        }

        return out
    }
}
