package com.jakubjirak.terminalsafety.lint

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LintMarkdownFormatterTest {

    private fun issue(
        severity: LintSeverity,
        id: String = "r1",
        code: String = "X",
        message: String = "m",
        suggestion: String? = null,
    ) = RuleLintIssue(
        ruleId = id,
        line = 1,
        column = null,
        severity = severity,
        code = code,
        message = message,
        suggestion = suggestion,
    )

    @Test
    fun `empty issues prints green check`() {
        val md = LintMarkdownFormatter.format(emptyList(), "rules.yaml")
        assertTrue(md.contains("✅ No issues found"))
        assertTrue(md.contains("rules.yaml"))
    }

    @Test
    fun `groups by severity with ERROR first`() {
        val issues = listOf(
            issue(LintSeverity.WARNING, code = "W1"),
            issue(LintSeverity.ERROR, code = "E1"),
            issue(LintSeverity.INFO, code = "I1"),
        )
        val md = LintMarkdownFormatter.format(issues, null)
        val errorIdx = md.indexOf("## Error")
        val warnIdx = md.indexOf("## Warning")
        val infoIdx = md.indexOf("## Info")
        assertTrue(errorIdx in 0 until warnIdx)
        assertTrue(warnIdx < infoIdx)
    }

    @Test
    fun `more than threshold issues are collapsed`() {
        val many = (1..LintMarkdownFormatter.COLLAPSE_THRESHOLD + 1).map {
            issue(LintSeverity.WARNING, id = "r$it")
        }
        val md = LintMarkdownFormatter.format(many, null)
        assertTrue(md.contains("<details>"))
        assertTrue(md.contains("<summary>Warning (${many.size})</summary>"))
    }

    @Test
    fun `at or below threshold is not collapsed`() {
        val some = (1..LintMarkdownFormatter.COLLAPSE_THRESHOLD).map {
            issue(LintSeverity.WARNING, id = "r$it")
        }
        val md = LintMarkdownFormatter.format(some, null)
        assertFalse(md.contains("<details>"))
    }

    @Test
    fun `pipes and backticks in messages are escaped`() {
        val issues = listOf(
            issue(
                LintSeverity.ERROR,
                message = "bad `code` with | pipe",
                suggestion = "use `foo | bar`",
            ),
        )
        val md = LintMarkdownFormatter.format(issues, null)
        assertTrue(md.contains("bad \\`code\\` with \\| pipe"))
        assertTrue(md.contains("use \\`foo \\| bar\\`"))
    }

    @Test
    fun `newlines in messages are flattened in cells`() {
        val escaped = LintMarkdownFormatter.escapeCell("line1\nline2\r\nline3")
        assertFalse(escaped.contains('\n'))
        assertFalse(escaped.contains('\r'))
    }

    @Test
    fun `summary counts all severities`() {
        val issues = listOf(
            issue(LintSeverity.ERROR),
            issue(LintSeverity.ERROR),
            issue(LintSeverity.WARNING),
        )
        val md = LintMarkdownFormatter.format(issues, null)
        assertTrue(md.contains("2 error(s)"))
        assertTrue(md.contains("1 warning(s)"))
        assertTrue(md.contains("0 info"))
    }
}
