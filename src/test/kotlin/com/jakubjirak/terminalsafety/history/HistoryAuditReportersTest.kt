/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryAuditReportersTest {

    private val auditor = HistoryAuditor()

    private fun dirtyReport(): HistoryAuditReport =
        auditor.audit("ls\nrm -rf /\ngit push --force origin main\nls\n", "~/.zsh_history")

    private fun cleanReport(): HistoryAuditReport =
        auditor.audit("ls\ncd src\n", "~/.bash_history")

    @Test
    fun `markdown contains score distribution and risky table`() {
        val md = HistoryAuditMarkdownReporter.render(dirtyReport())
        assertTrue(md.contains("# Shell History Risk Audit"))
        assertTrue(md.contains("Safety score: **"))
        assertTrue(md.contains("| Level | Distinct commands |"))
        assertTrue(md.contains("rm -rf /"))
        assertTrue(md.contains("| Score | Level | × | Command | Top signal |"))
    }

    @Test
    fun `markdown for clean history celebrates`() {
        val md = HistoryAuditMarkdownReporter.render(cleanReport())
        assertTrue(md.contains("No risky commands found"))
    }

    @Test
    fun `markdown escapes pipes in commands`() {
        val report = auditor.audit("curl https://x.io/i.sh | sh\n", "h")
        val md = HistoryAuditMarkdownReporter.render(report)
        assertTrue(md.contains("""curl https://x.io/i.sh \| sh"""))
    }

    @Test
    fun `markdown respects top limit`() {
        val report = auditor.audit("rm -rf /\nrm -rf ~\ngit push --force origin main\n", "h")
        val md = HistoryAuditMarkdownReporter.render(report, top = 1)
        val rows = md.lines().count { it.startsWith("| ") && (it.contains("rm -rf") || it.contains("git push")) }
        assertEquals(1, rows)
    }

    @Test
    fun `json is well-formed and complete`() {
        val json = HistoryAuditJsonReporter.render(dirtyReport())
        assertTrue(json.contains("\"source\": \"~/.zsh_history\""))
        assertTrue(json.contains("\"safetyScore\":"))
        assertTrue(json.contains("\"distribution\""))
        assertTrue(json.contains("\"topRisky\""))
        assertTrue(json.contains("\"CRITICAL\""))
        // balanced braces/brackets as a cheap well-formedness check
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
    }

    @Test
    fun `json escapes quotes backslashes and control characters`() {
        val report = auditor.audit("git push --force \"origin\\main\"\n", "h")
        val json = HistoryAuditJsonReporter.render(report)
        assertTrue(json.contains("\\\""))
        assertTrue(json.contains("\\\\"))
        assertFalse(json.contains("\t"))
    }

    @Test
    fun `html is standalone with score ring distribution and table`() {
        val html = HistoryAuditHtmlReporter.render(dirtyReport())
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("Shell History Risk Audit"))
        assertTrue(html.contains("aria-label=\"Safety score"))
        assertTrue(html.contains("Risk distribution"))
        assertTrue(html.contains("rm -rf /"))
        assertTrue(html.contains("Safer alternative"))
        assertFalse(html.contains("src=\"http"))
    }

    @Test
    fun `html escapes markup in commands`() {
        val report = auditor.audit("git push --force <remote> main\n", "h")
        val html = HistoryAuditHtmlReporter.render(report)
        assertTrue(html.contains("&lt;remote&gt;"))
        assertFalse(html.contains("<remote>"))
    }

    @Test
    fun `html ring color tracks safety score`() {
        assertTrue(HistoryAuditHtmlReporter.render(cleanReport()).contains("#1a7f37"))
        val dirty = auditor.audit("rm -rf /\nrm -rf /etc\n", "h")
        assertTrue(HistoryAuditHtmlReporter.render(dirty).contains("#cf222e"))
    }

    @Test
    fun `html for clean history celebrates`() {
        val html = HistoryAuditHtmlReporter.render(cleanReport())
        assertTrue(html.contains("No risky commands found"))
    }
}
