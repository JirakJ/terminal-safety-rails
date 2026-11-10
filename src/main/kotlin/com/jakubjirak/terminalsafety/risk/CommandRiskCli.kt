/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/**
 * Headless entry point for the Command Risk Scorer.
 *
 * Usage:
 *
 * ```
 * command-risk --command=<string>
 * command-risk --file=<path>
 *   [--format=text|md|json]
 *   [--min-level=SAFE|LOW|MEDIUM|HIGH|CRITICAL]
 *   [--out=<file>]
 *   [--fail-on=<level>]
 * ```
 *
 * Exit codes:
 *
 *  * `0` — completed and no assessment exceeded `--fail-on`.
 *  * `1` — bad arguments or IO error.
 *  * `2` — at least one command's level `>= --fail-on`.
 *
 * Report writes use an atomic-rename (`ATOMIC_MOVE`) so partial files are
 * never observed by downstream CI consumers.
 */
object CommandRiskCli {

    enum class Format { TEXT, MD, JSON }

    /** Return value of a CLI invocation — visible for tests. */
    data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    @JvmStatic
    fun main(args: Array<String>) {
        val r = run(args)
        if (r.stdout.isNotEmpty()) print(r.stdout)
        if (r.stderr.isNotEmpty()) System.err.print(r.stderr)
        exitProcess(r.exitCode)
    }

    /** Pure form of [main]. Safe to call from tests. */
    fun run(args: Array<String>): Result {
        var commandArg: String? = null
        var fileArg: String? = null
        var format = Format.TEXT
        var minLevel = RiskLevel.SAFE
        var outPath: String? = null
        var failOn: RiskLevel? = null

        for (arg in args) {
            when {
                arg.startsWith("--command=") -> commandArg = arg.removePrefix("--command=")
                arg.startsWith("--file=") -> fileArg = arg.removePrefix("--file=")
                arg.startsWith("--format=") -> {
                    format = parseFormat(arg.removePrefix("--format="))
                        ?: return Result(1, "", "Invalid --format: ${arg.removePrefix("--format=")}\n")
                }
                arg.startsWith("--min-level=") -> {
                    minLevel = parseLevel(arg.removePrefix("--min-level="))
                        ?: return Result(1, "", "Invalid --min-level: ${arg.removePrefix("--min-level=")}\n")
                }
                arg.startsWith("--out=") -> outPath = arg.removePrefix("--out=")
                arg.startsWith("--fail-on=") -> {
                    failOn = parseLevel(arg.removePrefix("--fail-on="))
                        ?: return Result(1, "", "Invalid --fail-on: ${arg.removePrefix("--fail-on=")}\n")
                }
                arg == "--help" || arg == "-h" -> return Result(0, HELP, "")
                else -> return Result(1, "", "Unknown argument: $arg\n")
            }
        }

        if (commandArg == null && fileArg == null) {
            return Result(1, "", "One of --command=<string> or --file=<path> is required.\n")
        }
        if (commandArg != null && fileArg != null) {
            return Result(1, "", "Specify only one of --command or --file.\n")
        }

        val commands: List<String> = when {
            commandArg != null -> listOf(commandArg)
            else -> try {
                val p = Paths.get(fileArg!!)
                if (!Files.exists(p)) {
                    return Result(1, "", "File not found: $fileArg\n")
                }
                Files.readAllLines(p).map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            } catch (t: Throwable) {
                return Result(1, "", "Failed reading $fileArg: ${t.message}\n")
            }
        }

        val scorer = CommandRiskScorer()
        val all = commands.map { scorer.score(it) }
        val filtered = all.filter { it.level.ordinal >= minLevel.ordinal }

        val rendered = when (format) {
            Format.TEXT -> renderText(filtered)
            Format.MD -> RiskAssessmentMarkdownReporter.renderAll(filtered)
            Format.JSON -> RiskAssessmentJsonReporter.renderAll(filtered)
        }

        if (outPath != null) {
            try {
                writeAtomic(Paths.get(outPath), rendered)
            } catch (t: Throwable) {
                return Result(1, "", "Failed writing $outPath: ${t.message}\n")
            }
        }

        val exit = if (failOn != null && all.any { it.level.ordinal >= failOn.ordinal }) 2 else 0
        val stdout = if (outPath == null) rendered else ""
        return Result(exit, stdout, "")
    }

    private fun renderText(assessments: List<RiskAssessment>): String = buildString {
        for (a in assessments) {
            appendLine("[${a.level.displayName} ${a.score}] ${a.command}")
            for (s in a.signals) {
                appendLine("    +${s.weight} ${s.id}: ${s.description} :: ${s.matched}")
            }
        }
    }

    private fun parseFormat(s: String): Format? =
        runCatching { Format.valueOf(s.uppercase()) }.getOrNull()

    private fun parseLevel(s: String): RiskLevel? =
        runCatching { RiskLevel.valueOf(s.uppercase()) }.getOrNull()

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
  command-risk --command=<string> [options]
  command-risk --file=<path>      [options]

Options:
  --format=text|md|json     Output format (default: text)
  --min-level=LEVEL         Only include assessments >= LEVEL
  --out=<file>              Write report to file (atomic)
  --fail-on=LEVEL           Exit 2 if any assessment >= LEVEL

Levels: SAFE, LOW, MEDIUM, HIGH, CRITICAL
"""
}
