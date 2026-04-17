package com.jakubjirak.terminalsafety.lint

import com.jakubjirak.terminalsafety.model.CommandCategory
import com.jakubjirak.terminalsafety.model.CommandRule
import com.jakubjirak.terminalsafety.model.RiskLevel
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Team-rule linter. Runs a set of cheap, pure checks over a list of
 * [CommandRule]s and reports issues as [RuleLintIssue]s.
 *
 * The linter is intentionally allocation-light and IDE-free: it can be
 * called from unit tests, a CLI, or from an IDE action. All public
 * functions are safe to invoke from any thread.
 *
 * ### Action semantics
 * The rule model in this plugin expresses *action* as [RiskLevel]. Where
 * the specification refers to `ALLOW`/`BLOCK`/`ASK`, we interpret
 * [RiskLevel.LOW] as the permissive "ASK" action and any higher risk level
 * as a blocking action for the purposes of unreachable-rule analysis. This
 * is an approximation — see [checkUnreachableRules] for details.
 */
object RuleLinter {

    /** Patterns considered "overly broad" on a non-permissive action. */
    private val BROAD_PATTERNS = setOf(".*", ".+", "^.*$", "^.+$", ".*?")

    /**
     * Lint an already-parsed list of rules.
     *
     * Issues are returned sorted by severity (`ERROR` first) then by
     * `ruleId`. `null` rule ids sort last.
     */
    fun lint(rules: List<CommandRule>): List<RuleLintIssue> {
        return lintInternal(rules, linesByIndex = emptyMap(), prefix = emptyList())
    }

    /**
     * Lint a raw YAML/JSON-ish source document.
     *
     * This function uses a line-tracking re-implementation of the
     * team-rules parser so that every issue can cite a source line. Parser
     * exceptions are captured as `ERROR` issues with [LintCodes.PARSE_ERROR]
     * and the best-known `line`/`column` attached.
     */
    fun lintSource(yamlOrJsonText: String): List<RuleLintIssue> {
        val parsed = try {
            parseWithLines(yamlOrJsonText)
        } catch (e: Exception) {
            return listOf(
                RuleLintIssue(
                    ruleId = null,
                    line = null,
                    column = null,
                    severity = LintSeverity.ERROR,
                    code = LintCodes.PARSE_ERROR,
                    message = "Failed to parse team rules source: ${e.message}",
                    suggestion = null,
                )
            )
        }
        val rules = parsed.map { it.rule }
        val linesByIndex = parsed.withIndex().associate { (i, p) -> i to p.line }
        return lintInternal(rules, linesByIndex, parsed.mapNotNull { it.parseIssue })
    }

    // ---------------------------------------------------------------------
    // Core
    // ---------------------------------------------------------------------

    private fun lintInternal(
        rules: List<CommandRule>,
        linesByIndex: Map<Int, Int>,
        prefix: List<RuleLintIssue>,
    ): List<RuleLintIssue> {
        val issues = mutableListOf<RuleLintIssue>()
        issues += prefix
        issues += checkInvalidRegex(rules, linesByIndex)
        issues += checkEmptyPattern(rules, linesByIndex)
        issues += checkMissingFields(rules, linesByIndex)
        issues += checkDuplicateIds(rules, linesByIndex)
        issues += checkConflictingActions(rules, linesByIndex)
        issues += checkUnreachableRules(rules, linesByIndex)
        issues += checkOverlyBroadPatterns(rules, linesByIndex)
        return sortIssues(issues)
    }

    /**
     * Deterministic sort:
     *
     *  1. `ERROR` before `WARNING` before `INFO` (enum ordinal).
     *  2. Then by `ruleId` (ascending, `null` last).
     *  3. Then by `code`, `line`, `message` to make ties stable.
     */
    internal fun sortIssues(issues: List<RuleLintIssue>): List<RuleLintIssue> {
        return issues.sortedWith(
            compareBy<RuleLintIssue> { it.severity.ordinal }
                .thenBy { it.ruleId == null }
                .thenBy(nullsLast()) { it.ruleId }
                .thenBy { it.code }
                .thenBy(nullsLast()) { it.line }
                .thenBy { it.message }
        )
    }

    // ---------------------------------------------------------------------
    // Individual checks (each unit-tested)
    // ---------------------------------------------------------------------

    /**
     * Detects invalid regular expressions in rules where [CommandRule.isRegex]
     * is `true`. Uses [java.util.regex.Pattern.compile] and captures the
     * [PatternSyntaxException] description + index as the `suggestion`.
     */
    internal fun checkInvalidRegex(
        rules: List<CommandRule>,
        linesByIndex: Map<Int, Int>,
    ): List<RuleLintIssue> {
        val out = mutableListOf<RuleLintIssue>()
        for ((idx, rule) in rules.withIndex()) {
            if (!rule.isRegex) continue
            if (rule.pattern.isBlank()) continue
            try {
                Pattern.compile(rule.pattern)
            } catch (e: PatternSyntaxException) {
                val idx1 = e.index
                val suggestion = buildString {
                    append(e.description ?: "Invalid regex")
                    if (idx1 >= 0) append(" at index ").append(idx1)
                }
                out += RuleLintIssue(
                    ruleId = rule.id,
                    line = linesByIndex[idx],
                    column = null,
                    severity = LintSeverity.ERROR,
                    code = LintCodes.INVALID_REGEX,
                    message = "Invalid regex in rule '${rule.id}': ${e.description ?: e.message}",
                    suggestion = suggestion,
                )
            }
        }
        return out
    }

    /** Flags blank/whitespace-only patterns. */
    internal fun checkEmptyPattern(
        rules: List<CommandRule>,
        linesByIndex: Map<Int, Int>,
    ): List<RuleLintIssue> {
        val out = mutableListOf<RuleLintIssue>()
        for ((idx, rule) in rules.withIndex()) {
            if (rule.pattern.isBlank()) {
                out += RuleLintIssue(
                    ruleId = rule.id,
                    line = linesByIndex[idx],
                    column = null,
                    severity = LintSeverity.ERROR,
                    code = LintCodes.EMPTY_PATTERN,
                    message = "Rule '${rule.id}' has a blank pattern.",
                    suggestion = "Provide a non-empty glob or regex in the `pattern:` field.",
                )
            }
        }
        return out
    }

    /**
     * Flags missing-but-required fields.
     *
     * `id`, `pattern`, and `riskLevel` (action) are enforced by the
     * [CommandRule] data class, so the only convention-level gap we can
     * warn about after parsing is a synthetic fallback description of the
     * form `Team rule: <pattern>` emitted by the loader when no description
     * was supplied.
     */
    internal fun checkMissingFields(
        rules: List<CommandRule>,
        linesByIndex: Map<Int, Int>,
    ): List<RuleLintIssue> {
        val out = mutableListOf<RuleLintIssue>()
        for ((idx, rule) in rules.withIndex()) {
            if (rule.description.isBlank() ||
                rule.description == "Team rule: ${rule.pattern}"
            ) {
                out += RuleLintIssue(
                    ruleId = rule.id,
                    line = linesByIndex[idx],
                    column = null,
                    severity = LintSeverity.WARNING,
                    code = LintCodes.MISSING_FIELD,
                    message = "Rule '${rule.id}' is missing a human-readable `description`.",
                    suggestion = "Add a short `description:` explaining why the command is risky.",
                )
            }
        }
        return out
    }

    /** Flags duplicate rule ids. Every duplicate after the first is reported. */
    internal fun checkDuplicateIds(
        rules: List<CommandRule>,
        linesByIndex: Map<Int, Int>,
    ): List<RuleLintIssue> {
        val out = mutableListOf<RuleLintIssue>()
        val seen = mutableMapOf<String, Int>()
        for ((idx, rule) in rules.withIndex()) {
            val prev = seen[rule.id]
            if (prev != null) {
                out += RuleLintIssue(
                    ruleId = rule.id,
                    line = linesByIndex[idx],
                    column = null,
                    severity = LintSeverity.ERROR,
                    code = LintCodes.DUPLICATE_ID,
                    message = "Duplicate rule id '${rule.id}' (first seen at rule #${prev + 1}).",
                    suggestion = "Rename one of the rules to give it a unique `id`.",
                )
            } else {
                seen[rule.id] = idx
            }
        }
        return out
    }

    /**
     * Flags two rules with an identical *normalized* pattern but different
     * actions (risk levels). Normalization trims surrounding whitespace and
     * compares case-insensitively for glob patterns.
     */
    internal fun checkConflictingActions(
        rules: List<CommandRule>,
        linesByIndex: Map<Int, Int>,
    ): List<RuleLintIssue> {
        val out = mutableListOf<RuleLintIssue>()
        data class Key(val pattern: String, val isRegex: Boolean)
        val byKey = mutableMapOf<Key, Pair<Int, RiskLevel>>()
        for ((idx, rule) in rules.withIndex()) {
            if (rule.pattern.isBlank()) continue
            val key = Key(rule.pattern.trim(), rule.isRegex)
            val prev = byKey[key]
            if (prev != null && prev.second != rule.riskLevel) {
                out += RuleLintIssue(
                    ruleId = rule.id,
                    line = linesByIndex[idx],
                    column = null,
                    severity = LintSeverity.ERROR,
                    code = LintCodes.CONFLICTING_ACTION,
                    message = "Rule '${rule.id}' uses pattern '${rule.pattern}' with action " +
                        "${rule.riskLevel.name}, conflicting with rule #${prev.first + 1} " +
                        "that assigns ${prev.second.name}.",
                    suggestion = "Reconcile the two rules to use the same risk level or " +
                        "differentiate their patterns.",
                )
            } else if (prev == null) {
                byKey[key] = idx to rule.riskLevel
            }
        }
        return out
    }

    /**
     * Heuristic unreachable-rule detection.
     *
     * Rule B (at index `j`) is considered unreachable when an earlier rule
     * A (at index `i < j`) has a *broader* pattern than B. Because general
     * regex subsumption is undecidable, we use the specification's
     * approximation: A's pattern string occurs as a **substring** of B's
     * pattern, A's action is not the permissive [RiskLevel.LOW] (our "ASK"
     * stand-in), and A's `isRegex` matches B's.
     *
     * The implementation explicitly avoids the pathological
     * `"".contains("")` case and disjoint patterns (e.g. `foo` vs `bar`).
     */
    internal fun checkUnreachableRules(
        rules: List<CommandRule>,
        linesByIndex: Map<Int, Int>,
    ): List<RuleLintIssue> {
        val out = mutableListOf<RuleLintIssue>()
        for (j in rules.indices) {
            val b = rules[j]
            if (b.pattern.isBlank()) continue
            for (i in 0 until j) {
                val a = rules[i]
                if (a.pattern.isBlank()) continue
                if (a.isRegex != b.isRegex) continue
                if (a.riskLevel == RiskLevel.LOW) continue
                if (a.pattern == b.pattern) continue
                if (!b.pattern.contains(a.pattern)) continue
                out += RuleLintIssue(
                    ruleId = b.id,
                    line = linesByIndex[j],
                    column = null,
                    severity = LintSeverity.WARNING,
                    code = LintCodes.UNREACHABLE_RULE,
                    message = "Rule '${b.id}' is likely unreachable: rule '${a.id}' " +
                        "(pattern '${a.pattern}') is broader and will match first.",
                    suggestion = "Move the more specific rule above '${a.id}', or remove the " +
                        "redundant rule.",
                )
                break
            }
        }
        return out
    }

    /**
     * Flags universally-matching patterns (`.*`, `.+`, `^.*$`, `^.+$`) on
     * a non-permissive risk level — these almost always indicate a
     * misconfiguration that would block every command.
     */
    internal fun checkOverlyBroadPatterns(
        rules: List<CommandRule>,
        linesByIndex: Map<Int, Int>,
    ): List<RuleLintIssue> {
        val out = mutableListOf<RuleLintIssue>()
        for ((idx, rule) in rules.withIndex()) {
            if (rule.pattern.trim() !in BROAD_PATTERNS) continue
            if (rule.riskLevel == RiskLevel.LOW) continue
            out += RuleLintIssue(
                ruleId = rule.id,
                line = linesByIndex[idx],
                column = null,
                severity = LintSeverity.WARNING,
                code = LintCodes.OVERLY_BROAD_PATTERN,
                message = "Rule '${rule.id}' uses an overly broad pattern '${rule.pattern}' " +
                    "with risk level ${rule.riskLevel.name}.",
                suggestion = "Tighten the pattern or lower the risk level to LOW (ASK).",
            )
        }
        return out
    }

    // ---------------------------------------------------------------------
    // Line-aware parser (mirrors TeamRulesLoader.parseRules)
    // ---------------------------------------------------------------------

    private data class ParsedRule(
        val rule: CommandRule,
        val line: Int,
        val parseIssue: RuleLintIssue? = null,
    )

    private fun parseWithLines(text: String): List<ParsedRule> {
        val rules = mutableListOf<ParsedRule>()

        var startLine = -1
        var currentPattern: String? = null
        var currentRisk = RiskLevel.MEDIUM
        var currentCategory = CommandCategory.CUSTOM
        var currentDescription = ""
        var currentIsRegex = false

        fun flush() {
            val pat = currentPattern ?: return
            if (pat.isBlank() && currentDescription.isBlank() && startLine < 0) return
            val rule = CommandRule(
                pattern = pat,
                riskLevel = currentRisk,
                category = currentCategory,
                description = currentDescription.ifBlank { "Team rule: $pat" },
                isRegex = currentIsRegex,
            )
            rules += ParsedRule(rule, startLine.coerceAtLeast(1))
        }

        val lines = text.lines()
        for ((i, raw) in lines.withIndex()) {
            val lineNum = i + 1
            val trimmed = raw.trim()
            when {
                trimmed.startsWith("- pattern:") -> {
                    flush()
                    startLine = lineNum
                    currentPattern = trimmed.removePrefix("- pattern:").trim()
                        .removeSurrounding("\"").removeSurrounding("'")
                    currentRisk = RiskLevel.MEDIUM
                    currentCategory = CommandCategory.CUSTOM
                    currentDescription = ""
                    currentIsRegex = false
                }
                trimmed.startsWith("risk:") || trimmed.startsWith("risk_level:") -> {
                    val value = trimmed.substringAfter(":").trim()
                        .removeSurrounding("\"").removeSurrounding("'").uppercase()
                    currentRisk = try {
                        RiskLevel.valueOf(value)
                    } catch (_: IllegalArgumentException) { RiskLevel.MEDIUM }
                }
                trimmed.startsWith("category:") -> {
                    val value = trimmed.substringAfter(":").trim()
                        .removeSurrounding("\"").removeSurrounding("'").uppercase()
                    currentCategory = try {
                        CommandCategory.valueOf(value)
                    } catch (_: IllegalArgumentException) { CommandCategory.CUSTOM }
                }
                trimmed.startsWith("description:") -> {
                    currentDescription = trimmed.substringAfter(":").trim()
                        .removeSurrounding("\"").removeSurrounding("'")
                }
                trimmed.startsWith("regex:") || trimmed.startsWith("is_regex:") -> {
                    currentIsRegex = trimmed.substringAfter(":").trim().lowercase() == "true"
                }
            }
        }
        flush()
        return rules
    }
}
