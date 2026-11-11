package com.jakubjirak.terminalsafety.lint

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Command-line entry point for the team-rule linter.
 *
 * Usage:
 *
 * ```
 * rule-lint --source=<path> [--format=markdown|json|plain] [--fail-on=error|warning]
 * ```
 *
 * Exit codes:
 *
 *  * `0` — success, no issues at or above the fail threshold.
 *  * `1` — bad arguments or source file not found.
 *  * `2` — issues found at or above the configured fail threshold.
 *
 * This class intentionally has **no IntelliJ platform dependencies** so it
 * can be shipped as a standalone JAR in CI images.
 */
object RuleLintCli {

    /** Output formats supported by the CLI. */
    enum class Format { MARKDOWN, JSON, PLAIN }

    /** Fail threshold severities. */
    enum class FailOn { ERROR, WARNING }

    /** Return value of a single CLI invocation; visible for testing. */
    data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    /**
     * Runs the linter and exits the process. Prefer [run] in tests so
     * assertions can inspect the captured output.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val result = run(args)
        if (result.stdout.isNotEmpty()) print(result.stdout)
        if (result.stderr.isNotEmpty()) System.err.print(result.stderr)
        exitProcess(result.exitCode)
    }

    /**
     * Pure form of [main] — returns a [Result] instead of exiting. Safe to
     * call from tests.
     */
    fun run(args: Array<String>): Result {
        var source: String? = null
        var format = Format.MARKDOWN
        var failOn = FailOn.ERROR

        for (arg in args) {
            when {
                arg.startsWith("--source=") -> source = arg.removePrefix("--source=")
                arg.startsWith("--format=") -> {
                    val v = arg.removePrefix("--format=").lowercase()
                    format = when (v) {
                        "markdown", "md" -> Format.MARKDOWN
                        "json" -> Format.JSON
                        "plain", "text" -> Format.PLAIN
                        else -> return Result(1, "", "Unknown format: $v\n${usage()}\n")
                    }
                }
                arg.startsWith("--fail-on=") -> {
                    val v = arg.removePrefix("--fail-on=").lowercase()
                    failOn = when (v) {
                        "error" -> FailOn.ERROR
                        "warning", "warn" -> FailOn.WARNING
                        else -> return Result(1, "", "Unknown --fail-on value: $v\n${usage()}\n")
                    }
                }
                arg == "-h" || arg == "--help" -> return Result(0, usage() + "\n", "")
                else -> return Result(1, "", "Unknown argument: $arg\n${usage()}\n")
            }
        }

        val src = source
            ?: return Result(1, "", "Missing required --source=<path>\n${usage()}\n")

        val path: Path = Paths.get(src)
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return Result(1, "", "Source file not found: $src\n")
        }

        val text = Files.readString(path)
        val issues = RuleLinter.lintSource(text)

        val rendered = when (format) {
            Format.MARKDOWN -> LintMarkdownFormatter.format(issues, src)
            Format.JSON -> renderJson(issues, src)
            Format.PLAIN -> renderPlain(issues, src)
        }

        val errorCount = issues.count { it.severity == LintSeverity.ERROR }
        val warnCount = issues.count { it.severity == LintSeverity.WARNING }
        val exit = when (failOn) {
            FailOn.ERROR -> if (errorCount > 0) 2 else 0
            FailOn.WARNING -> if (errorCount + warnCount > 0) 2 else 0
        }
        return Result(exit, rendered, "")
    }

    internal fun usage(): String = """
        Usage: rule-lint --source=<path> [--format=markdown|json|plain] [--fail-on=error|warning]

        Options:
          --source=<path>        Path to .terminal-safety-rules.yaml (required)
          --format=<format>      Output format: markdown (default), json, plain
          --fail-on=<severity>   Exit with code 2 when an issue of this severity or
                                 higher is found. Default: error.
          -h, --help             Show this help text.

        Exit codes: 0 = success, 1 = bad args / missing file, 2 = issues at threshold.
    """.trimIndent()

    internal fun renderPlain(issues: List<RuleLintIssue>, source: String): String {
        if (issues.isEmpty()) return "$source: no issues\n"
        return buildString {
            for (i in issues) {
                append(source).append(':')
                append(i.line?.toString() ?: "-").append(':')
                append(i.severity.name).append(':')
                append(i.code).append(':')
                append(i.ruleId ?: "-").append(": ")
                append(i.message)
                if (i.suggestion != null) append(" — ").append(i.suggestion)
                append('\n')
            }
        }
    }

    internal fun renderJson(issues: List<RuleLintIssue>, source: String): String {
        val sb = StringBuilder()
        sb.append("{\n  \"source\": ").append(jsonString(source)).append(",\n")
        sb.append("  \"summary\": {")
            .append(" \"error\": ").append(issues.count { it.severity == LintSeverity.ERROR })
            .append(", \"warning\": ").append(issues.count { it.severity == LintSeverity.WARNING })
            .append(", \"info\": ").append(issues.count { it.severity == LintSeverity.INFO })
            .append(" },\n")
        sb.append("  \"issues\": [")
        for ((idx, i) in issues.withIndex()) {
            if (idx > 0) sb.append(',')
            sb.append("\n    {")
                .append(" \"severity\": ").append(jsonString(i.severity.name))
                .append(", \"code\": ").append(jsonString(i.code))
                .append(", \"ruleId\": ").append(i.ruleId?.let { jsonString(it) } ?: "null")
                .append(", \"line\": ").append(i.line ?: "null")
                .append(", \"column\": ").append(i.column ?: "null")
                .append(", \"message\": ").append(jsonString(i.message))
                .append(", \"suggestion\": ").append(i.suggestion?.let { jsonString(it) } ?: "null")
                .append(" }")
        }
        if (issues.isNotEmpty()) sb.append("\n  ")
        sb.append("]\n}\n")
        return sb.toString()
    }

    private fun jsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch.code < 0x20) {
                    sb.append("\\u").append(String.format(Locale.ROOT, "%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
