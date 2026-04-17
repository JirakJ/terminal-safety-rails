/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RiskAssessmentReportersTest {
    private val scorer = CommandRiskScorer(clock = { 1000L })

    @Test
    fun `markdown renders header table and signals`() {
        val a = scorer.score("sudo rm -rf /")
        val md = RiskAssessmentMarkdownReporter.render(a)
        assertTrue(md.contains("Command Risk Assessment"))
        assertTrue(md.contains("Score"))
        assertTrue(md.contains("| Signal |"))
        assertTrue(md.contains("destructive.rm-rf-root"))
    }

    @Test
    fun `markdown escapes pipes and backticks`() {
        val sig = RiskSignal("x", "desc|with|pipe", 10, "`bad`")
        val a = RiskAssessment("echo", 10, RiskLevel.SAFE, listOf(sig), 0L)
        val md = RiskAssessmentMarkdownReporter.render(a)
        assertTrue(md.contains("desc\\|with\\|pipe"))
        assertTrue(md.contains("\\`bad\\`"))
    }

    @Test
    fun `json renders valid structure`() {
        val a = scorer.score("rm -rf /")
        val json = RiskAssessmentJsonReporter.render(a)
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertTrue(json.contains("\"score\":"))
        assertTrue(json.contains("\"level\":\"CRITICAL\""))
        assertTrue(json.contains("\"signals\":["))
    }

    @Test
    fun `json escapes quotes and newlines`() {
        val sig = RiskSignal("x", "a\"b\nc", 5, "t\\ab")
        val a = RiskAssessment("cmd\"ok", 5, RiskLevel.SAFE, listOf(sig), 1L)
        val json = RiskAssessmentJsonReporter.render(a)
        assertTrue(json.contains("\\\"b\\nc"))
        assertTrue(json.contains("\\\\ab"))
        assertTrue(json.contains("cmd\\\"ok"))
    }

    @Test
    fun `renderAll markdown lists group counts`() {
        val list = listOf(scorer.score("ls"), scorer.score("rm -rf /"))
        val md = RiskAssessmentMarkdownReporter.renderAll(list)
        assertTrue(md.contains("Total commands: 2"))
        assertTrue(md.contains("Critical: 1"))
        assertTrue(md.contains("Safe: 1"))
    }

    @Test
    fun `renderAll json is valid array`() {
        val list = listOf(scorer.score("ls"), scorer.score("rm -rf /"))
        val json = RiskAssessmentJsonReporter.renderAll(list)
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        // two records, therefore exactly one top-level comma between objects
        assertEquals(1, json.count { it == ',' && false } + json.split("},{").size - 1)
    }
}
