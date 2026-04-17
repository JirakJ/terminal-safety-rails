package com.jakubjirak.terminalsafety.lint

/**
 * Severity of a [RuleLintIssue].
 *
 * Ordered most-to-least severe so that `compareTo` yields a deterministic
 * ordering usable for sorting.
 */
enum class LintSeverity { ERROR, WARNING, INFO }

/**
 * Well-known lint codes. Using string constants (rather than an `enum`) so
 * callers can reference codes in documentation and CI configuration without
 * importing plugin classes.
 */
object LintCodes {
    const val INVALID_REGEX = "INVALID_REGEX"
    const val DUPLICATE_ID = "DUPLICATE_ID"
    const val UNREACHABLE_RULE = "UNREACHABLE_RULE"
    const val CONFLICTING_ACTION = "CONFLICTING_ACTION"
    const val MISSING_FIELD = "MISSING_FIELD"
    const val EMPTY_PATTERN = "EMPTY_PATTERN"
    const val OVERLY_BROAD_PATTERN = "OVERLY_BROAD_PATTERN"
    const val DISABLED_RULE_REFERENCES_MISSING_ID = "DISABLED_RULE_REFERENCES_MISSING_ID"
    const val PARSE_ERROR = "PARSE_ERROR"
}

/**
 * Immutable lint issue produced by [RuleLinter].
 *
 * @property ruleId The id of the rule the issue applies to, or `null` when
 *   the issue is file-level (e.g. [LintCodes.PARSE_ERROR]).
 * @property line Line number in the source file (1-based), or `null` when
 *   the issue was produced from an already-parsed rule list.
 * @property column Column number in the source file (1-based), or `null`.
 * @property severity See [LintSeverity].
 * @property code Machine-readable code (one of [LintCodes]).
 * @property message Human-readable description.
 * @property suggestion Optional remediation hint.
 */
data class RuleLintIssue(
    val ruleId: String?,
    val line: Int?,
    val column: Int?,
    val severity: LintSeverity,
    val code: String,
    val message: String,
    val suggestion: String? = null,
)
