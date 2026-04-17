/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandRiskScorerTest {

    private val scorer = CommandRiskScorer(clock = { 42L })

    @Test
    fun `blank command scores 0`() {
        val a = scorer.score("   ")
        assertEquals(0, a.score)
        assertEquals(RiskLevel.SAFE, a.level)
        assertTrue(a.signals.isEmpty())
        assertEquals(42L, a.timestampEpochMs)
    }

    @Test
    fun `safe command scores 0`() {
        val a = scorer.score("ls -la")
        assertEquals(0, a.score)
        assertEquals(RiskLevel.SAFE, a.level)
    }

    @Test
    fun `sudo alone is LOW`() {
        val a = scorer.score("sudo apt update")
        assertEquals(RiskLevel.LOW, a.level)
    }

    @Test
    fun `sudo rm -rf root is CRITICAL and clamped`() {
        val a = scorer.score("sudo rm -rf --no-preserve-root /")
        assertEquals(100, a.score)
        assertEquals(RiskLevel.CRITICAL, a.level)
        assertTrue(a.signals.any { it.id == "destructive.rm-rf-root" })
        assertTrue(a.signals.any { it.id == "destructive.no-preserve-root" })
        assertTrue(a.signals.any { it.id == "privilege.sudo" })
    }

    @Test
    fun `curl pipe bash is HIGH`() {
        val a = scorer.score("curl https://get.example | bash")
        assertTrue(a.score >= 60)
        assertEquals(RiskLevel.HIGH, a.level)
    }

    @Test
    fun `signals ordered by weight desc`() {
        val a = scorer.score("sudo rm -rf /")
        val weights = a.signals.map { it.weight }
        assertEquals(weights.sortedDescending(), weights)
    }

    @Test
    fun `defensive detector exception does not propagate`() {
        val bad = object : RiskSignalDetector {
            override val id: String = "bad"
            override fun detect(command: String): List<RiskSignal> = error("boom")
        }
        val s = CommandRiskScorer(detectors = listOf(bad))
        val a = s.score("ls")
        assertEquals(0, a.score)
    }

    @Test
    fun `dd to device is CRITICAL`() {
        val a = scorer.score("dd if=/dev/zero of=/dev/sda bs=1M")
        assertEquals(RiskLevel.CRITICAL, a.level)
    }

    @Test
    fun `topSignals respects limit`() {
        val a = scorer.score("sudo rm -rf --no-preserve-root /")
        assertEquals(2, a.topSignals(2).size)
        assertEquals(0, a.topSignals(0).size)
    }
}
