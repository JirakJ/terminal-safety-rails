/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.history

import com.jakubjirak.terminalsafety.risk.RiskLevel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/**
 * Headless entry point for the Shell History Risk Audit.
 *
 * Usage:
 *
 * ```
 * history-audit --file=~/.zsh_history
 *   [--format=md|json|html]
 *   [--top=N]
 *   [--out=<file>]
 *   [--fail-below=<0-100>]
 * ```
 *
 * Exit codes:
 *
 *  * `0` — completed; safety score at or above `--fail-below` (or no gate).
 *  * `1` — bad arguments or IO error.
 *  * `2` — safety score below `--fail-below`.
 *
 * Output writes use an atomic rename so partial files are never observed.
 */
object HistoryAuditCli {

    enum class Format { MD, JSON, HTML }

    /** Return value of a CLI invocation — visible for tests. */
    data class Result(val exitCode: Int, val stdout: String, val stderr: String, val report: HistoryAuditReport?)

    @JvmStatic
    fun main(args: Array<String>) {
        val r = run(args)
        if (r.stdout.isNotEmpty()) print(r.stdout)
        if (r.stderr.isNotEmpty()) System.err.print(r.stderr)
        exitProcess(r.exitCode)
    }

    /** Pure form of [main]. Safe to call from tests. */
    fun run(args: Array<String>): Result {
        var fileArg: String? = null
        var format = Format.MD
        var top = 25
        var outPath: String? = null
        var failBelow: Int? = null

        for (arg in args) {
            when {
                arg.startsWith("--file=") -> fileArg = arg.removePrefix("--file=")
                arg.startsWith("--format=") -> {
                    format = runCatching { Format.valueOf(arg.removePrefix("--format=").uppercase()) }.getOrNull()
                        ?: return Result(1, "", "Invalid --format: ${arg.removePrefix("--format=")}\n", null)
                }
                arg.startsWith("--top=") -> {
                    top = arg.removePrefix("--top=").toIntOrNull()?.takeIf { it > 0 }
                        ?: return Result(1, "", "Invalid --top: ${arg.removePrefix("--top=")}\n", null)
                }
                arg.startsWith("--out=") -> outPath = arg.removePrefix("--out=")
                arg.startsWith("--fail-below=") -> {
                    failBelow = arg.removePrefix("--fail-below=").toIntOrNull()?.takeIf { it in 0..100 }
                        ?: return Result(1, "", "Invalid --fail-below: ${arg.removePrefix("--fail-below=")}\n", null)
                }
                arg == "--help" || arg == "-h" -> return Result(0, HELP, "", null)
                else -> return Result(1, "", "Unknown argument: $arg\n", null)
            }
        }

        if (fileArg == null) return Result(1, "", "Missing required --file=<path>\n$HELP", null)
        val expanded = if (fileArg.startsWith("~/")) {
            System.getProperty("user.home") + fileArg.substring(1)
        } else fileArg
        val path = Paths.get(expanded)
        if (!Files.isRegularFile(path)) return Result(1, "", "File not found: $fileArg\n", null)

        val content = try {
            String(Files.readAllBytes(path), Charsets.UTF_8)
        } catch (t: Throwable) {
            return Result(1, "", "Failed reading $fileArg: ${t.message}\n", null)
        }

        val report = HistoryAuditor().audit(content, fileArg)

        val rendered = when (format) {
            Format.MD -> HistoryAuditMarkdownReporter.render(report, top)
            Format.JSON -> HistoryAuditJsonReporter.render(report, top)
            Format.HTML -> HistoryAuditHtmlReporter.render(report, top)
        }

        val summary = "History audit: safety score ${report.safetyScore}/100, " +
            "${report.totalEntries} entries, ${report.riskyCount} MEDIUM+ commands\n"

        val stdout: String
        if (outPath != null) {
            try {
                writeAtomic(Paths.get(outPath), rendered)
            } catch (t: Throwable) {
                return Result(1, "", "Failed writing $outPath: ${t.message}\n", null)
            }
            stdout = "Report written to $outPath\n$summary"
        } else {
            stdout = rendered + "\n" + summary
        }

        val exit = if (failBelow != null && report.safetyScore < failBelow) 2 else 0
        return Result(exit, stdout, "", report)
    }

    private fun writeAtomic(target: Path, content: String) {
        val parent = target.toAbsolutePath().parent
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
        val tmp = Files.createTempFile(parent, target.fileName.toString() + ".", ".tmp")
        Files.write(tmp, content.toByteArray(Charsets.UTF_8))
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private const val HELP = """Usage:
  history-audit --file=<path> [options]

Options:
  --format=md|json|html     Output format (default: md)
  --top=N                   Number of risky commands in the report (default: 25)
  --out=<file>              Write report to file (atomic)
  --fail-below=<0-100>      Exit 2 if the safety score is below the threshold
"""
}
