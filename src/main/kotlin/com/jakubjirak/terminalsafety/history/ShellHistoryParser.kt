/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.history

/**
 * One parsed history entry.
 *
 * @property command The command line as typed (multi-line zsh entries are
 *   joined with `\n` replaced by `; `).
 * @property timestampEpochSec Unix timestamp when the shell recorded it, or
 *   `null` for plain-format histories without timestamps.
 */
data class HistoryEntry(
    val command: String,
    val timestampEpochSec: Long?,
)

/**
 * Parses shell history files into [HistoryEntry]s. Supported formats:
 *
 * * **zsh extended** — `: 1700000000:0;command` (multi-line commands are
 *   continued with a trailing backslash),
 * * **bash with timestamps** — `#1700000000` comment line followed by the
 *   command,
 * * **plain** — one command per line (bash default, sh).
 *
 * Blank lines are skipped. Plain-format lines starting with `#` are treated
 * as comments **unless** they are bash timestamp markers. The parser never
 * throws on malformed lines — they fall back to plain commands.
 */
class ShellHistoryParser {

    /** Parses [content] auto-detecting the format per line. */
    fun parse(content: String): List<HistoryEntry> {
        val out = ArrayList<HistoryEntry>()
        var pendingTimestamp: Long? = null
        var zshContinuation: StringBuilder? = null
        var zshTimestamp: Long? = null

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trimEnd('\r')

            if (zshContinuation != null) {
                if (line.endsWith("\\")) {
                    zshContinuation.append(line.dropLast(1).trim()).append("; ")
                } else {
                    zshContinuation.append(line.trim())
                    addIfNotBlank(out, zshContinuation.toString(), zshTimestamp)
                    zshContinuation = null
                    zshTimestamp = null
                }
                continue
            }

            if (line.isBlank()) continue

            val zsh = ZSH_EXTENDED.matchEntire(line)
            if (zsh != null) {
                val ts = zsh.groupValues[1].toLongOrNull()
                val cmd = zsh.groupValues[3]
                if (cmd.endsWith("\\")) {
                    zshContinuation = StringBuilder(cmd.dropLast(1).trim()).append("; ")
                    zshTimestamp = ts
                } else {
                    addIfNotBlank(out, cmd, ts)
                }
                continue
            }

            val bashTs = BASH_TIMESTAMP.matchEntire(line)
            if (bashTs != null) {
                pendingTimestamp = bashTs.groupValues[1].toLongOrNull()
                continue
            }

            if (line.startsWith("#")) continue

            addIfNotBlank(out, line, pendingTimestamp)
            pendingTimestamp = null
        }
        return out
    }

    private fun addIfNotBlank(out: MutableList<HistoryEntry>, command: String, ts: Long?) {
        val trimmed = command.trim()
        if (trimmed.isNotEmpty()) out += HistoryEntry(trimmed, ts)
    }

    private companion object {
        val ZSH_EXTENDED = Regex("""^:\s*(\d+):(\d+);(.*)$""")
        val BASH_TIMESTAMP = Regex("""^#(\d{9,12})$""")
    }
}
