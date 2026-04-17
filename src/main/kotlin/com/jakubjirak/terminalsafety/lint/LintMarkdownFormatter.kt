package com.jakubjirak.terminalsafety.lint

/**
 * Renders a list of [RuleLintIssue]s as a GitHub-flavoured Markdown report
 * suitable for PR descriptions, CI annotations, or runbook attachments.
 *
 * Sections are grouped by severity (ERROR first). When a single severity
 * group contains more than [COLLAPSE_THRESHOLD] issues, it is wrapped in a
 * collapsible `<details>` block so long reports stay readable in GitHub.
 *
 * Pipe (`|`) and backtick (`` ` ``) characters in messages and suggestions
 * are escaped so table rendering is not corrupted.
 */
object LintMarkdownFormatter {

    /** Severity groups with more than this many issues are collapsed. */
    const val COLLAPSE_THRESHOLD = 20

    /**
     * Build the Markdown report.
     *
     * @param issues the issues to render. May be empty.
     * @param sourcePath optional path of the source file being linted, used
     *   in the report header.
     */
    fun format(issues: List<RuleLintIssue>, sourcePath: String?): String {
        val sb = StringBuilder()
        sb.append("# Terminal Safety — Team Rule Lint Report\n\n")
        if (sourcePath != null) {
            sb.append("**Source:** `").append(escapeInline(sourcePath)).append("`\n\n")
        }

        val errors = issues.count { it.severity == LintSeverity.ERROR }
        val warnings = issues.count { it.severity == LintSeverity.WARNING }
        val infos = issues.count { it.severity == LintSeverity.INFO }

        sb.append("**Summary:** ")
            .append(errors).append(" error(s), ")
            .append(warnings).append(" warning(s), ")
            .append(infos).append(" info.\n\n")

        if (issues.isEmpty()) {
            sb.append("✅ No issues found — team rules look good.\n")
            return sb.toString()
        }

        for (severity in LintSeverity.entries) {
            val group = issues.filter { it.severity == severity }
            if (group.isEmpty()) continue
            renderGroup(sb, severity, group)
        }
        return sb.toString()
    }

    private fun renderGroup(
        sb: StringBuilder,
        severity: LintSeverity,
        group: List<RuleLintIssue>,
    ) {
        val label = severity.name.lowercase().replaceFirstChar { it.uppercase() }
        val heading = "$label (${group.size})"
        val collapse = group.size > COLLAPSE_THRESHOLD
        if (collapse) {
            sb.append("<details>\n<summary>").append(heading).append("</summary>\n\n")
        } else {
            sb.append("## ").append(heading).append("\n\n")
        }
        sb.append("| Rule ID | Line | Code | Message | Suggestion |\n")
        sb.append("| --- | --- | --- | --- | --- |\n")
        for (issue in group) {
            sb.append("| ")
                .append(escapeCell(issue.ruleId ?: "—")).append(" | ")
                .append(issue.line?.toString() ?: "—").append(" | ")
                .append(escapeCell(issue.code)).append(" | ")
                .append(escapeCell(issue.message)).append(" | ")
                .append(escapeCell(issue.suggestion ?: "—"))
                .append(" |\n")
        }
        sb.append("\n")
        if (collapse) {
            sb.append("</details>\n\n")
        }
    }

    /** Escape pipes, backticks and newlines so table cells render cleanly. */
    internal fun escapeCell(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace("`", "\\`")
            .replace("\r\n", " ")
            .replace('\n', ' ')
            .replace('\r', ' ')
    }

    /** Escape inline (non-table) content — preserves newlines. */
    internal fun escapeInline(text: String): String {
        return text.replace("`", "\\`")
    }
}
