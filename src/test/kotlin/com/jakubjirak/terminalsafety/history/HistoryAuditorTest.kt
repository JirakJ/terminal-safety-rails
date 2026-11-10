/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.history

import com.jakubjirak.terminalsafety.risk.RiskLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryAuditorTest {

    private val auditor = HistoryAuditor()

    @Test
    fun `empty history scores 100 and has no entries`() {
        val r = auditor.audit("", "empty")
        assertEquals(100, r.safetyScore)
        assertEquals(0, r.totalEntries)
        assertEquals(0, r.uniqueCommands)
        assertTrue(r.assessed.isEmpty())
        assertTrue(r.topRisky(10).isEmpty())
    }

    @Test
    fun `boring history scores 100`() {
        val r = auditor.audit("ls\ncd src\ngit status\nls\n", "boring")
        assertEquals(100, r.safetyScore)
        assertEquals(4, r.totalEntries)
        assertEquals(3, r.uniqueCommands)
        assertEquals(0, r.riskyCount)
    }

    @Test
    fun `duplicates are scored once and counted`() {
        val r = auditor.audit("ls\nls\nls\nrm -rf /\n", "dups")
        assertEquals(4, r.totalEntries)
        assertEquals(2, r.uniqueCommands)
        val ls = r.assessed.single { it.command == "ls" }
        assertEquals(3, ls.occurrences)
    }

    @Test
    fun `assessed entries are ordered by descending score`() {
        val r = auditor.audit("ls\nrm -rf /\ngit push --force origin main\n", "ordering")
        val scores = r.assessed.map { it.assessment.score }
        assertEquals(scores.sortedDescending(), scores)
        assertTrue(r.assessed.first().command.contains("rm -rf /"))
    }

    @Test
    fun `distribution counts distinct commands per level`() {
        val r = auditor.audit("ls\ncd ..\nrm -rf /\n", "dist")
        val total = r.distribution.values.sum()
        assertEquals(r.uniqueCommands, total)
        assertTrue((r.distribution[RiskLevel.CRITICAL] ?: 0) >= 1)
        assertTrue((r.distribution[RiskLevel.SAFE] ?: 0) >= 2)
    }

    @Test
    fun `safety score is occurrence-weighted`() {
        // 9 safe + 1 critical → 90; 1 safe + 1 critical → 50
        val nineSafe = (1..9).joinToString("\n") { "ls" } + "\nrm -rf /\n"
        assertEquals(90, auditor.audit(nineSafe, "a").safetyScore)
        assertEquals(50, auditor.audit("ls\nrm -rf /\n", "b").safetyScore)
    }

    @Test
    fun `risky commands get safer alternatives attached`() {
        val r = auditor.audit("git push --force origin main\n", "alts")
        val entry = r.assessed.single()
        assertTrue(entry.assessment.level.ordinal >= RiskLevel.MEDIUM.ordinal)
        assertTrue(entry.alternatives.any { it.id == "safer.git-force-with-lease" })
    }

    @Test
    fun `safe commands carry no alternatives`() {
        val r = auditor.audit("ls\n", "noalts")
        assertTrue(r.assessed.single().alternatives.isEmpty())
    }

    @Test
    fun `topRisky filters zero scores and respects limit`() {
        val r = auditor.audit("ls\ncd ..\nrm -rf /\nrm -rf ~\n", "top")
        val top1 = r.topRisky(1)
        assertEquals(1, top1.size)
        val all = r.topRisky(100)
        assertEquals(2, all.size)
        assertTrue(all.all { it.assessment.score > 0 })
    }

    @Test
    fun `zsh extended history is parsed transparently`() {
        val content = ": 1700000000:0;rm -rf /\n: 1700000001:0;ls\n"
        val r = auditor.audit(content, "zsh")
        assertEquals(2, r.totalEntries)
        assertTrue(r.assessed.any { it.command == "rm -rf /" })
    }
}
