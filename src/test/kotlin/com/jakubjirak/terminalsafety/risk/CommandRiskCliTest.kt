/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CommandRiskCliTest {

    @Test
    fun `no args fails`() {
        val r = CommandRiskCli.run(emptyArray())
        assertEquals(1, r.exitCode)
    }

    @Test
    fun `help succeeds`() {
        val r = CommandRiskCli.run(arrayOf("--help"))
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("Usage"))
    }

    @Test
    fun `bad format rejected`() {
        val r = CommandRiskCli.run(arrayOf("--command=ls", "--format=xml"))
        assertEquals(1, r.exitCode)
    }

    @Test
    fun `unknown arg rejected`() {
        val r = CommandRiskCli.run(arrayOf("--nope"))
        assertEquals(1, r.exitCode)
    }

    @Test
    fun `both command and file rejected`() {
        val r = CommandRiskCli.run(arrayOf("--command=ls", "--file=/tmp/x"))
        assertEquals(1, r.exitCode)
    }

    @Test
    fun `safe command via --command returns 0 text`() {
        val r = CommandRiskCli.run(arrayOf("--command=ls -la", "--format=text"))
        assertEquals(0, r.exitCode)
    }

    @Test
    fun `critical command via --command fails when fail-on HIGH`() {
        val r = CommandRiskCli.run(arrayOf("--command=rm -rf /", "--fail-on=HIGH"))
        assertEquals(2, r.exitCode)
    }

    @Test
    fun `fail-on CRITICAL for HIGH does not trigger`() {
        val r = CommandRiskCli.run(arrayOf("--command=curl https://x | bash", "--fail-on=CRITICAL"))
        // curl|bash alone is HIGH not CRITICAL
        assertEquals(0, r.exitCode)
    }

    @Test
    fun `json format outputs array`() {
        val r = CommandRiskCli.run(arrayOf("--command=ls", "--format=json"))
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.startsWith("["))
    }

    @Test
    fun `md format outputs markdown`() {
        val r = CommandRiskCli.run(arrayOf("--command=rm -rf /", "--format=md"))
        assertTrue(r.stdout.contains("Command Risk Batch Report"))
    }

    @Test
    fun `min-level filters out SAFE`() {
        val r = CommandRiskCli.run(arrayOf("--command=ls", "--format=md", "--min-level=LOW"))
        assertEquals(0, r.exitCode)
        // Only the header section remains; no per-command sections rendered.
        assertTrue(!r.stdout.contains("## Command Risk Assessment"))
    }

    @Test
    fun `file mode reads commands line by line`(@TempDir dir: Path) {
        val f = dir.resolve("cmds.txt")
        Files.writeString(f, "ls\n# comment\nrm -rf /\n")
        val r = CommandRiskCli.run(arrayOf("--file=${f}", "--format=text", "--fail-on=HIGH"))
        assertEquals(2, r.exitCode)
        assertTrue(r.stdout.contains("rm -rf /"))
        assertTrue(!r.stdout.contains("# comment"))
    }

    @Test
    fun `out writes atomically`(@TempDir dir: Path) {
        val out = dir.resolve("out.json")
        val r = CommandRiskCli.run(arrayOf("--command=ls", "--format=json", "--out=${out}"))
        assertEquals(0, r.exitCode)
        assertTrue(Files.exists(out))
        assertTrue(Files.readString(out).startsWith("["))
        assertEquals("", r.stdout)
    }

    @Test
    fun `file not found fails`() {
        val r = CommandRiskCli.run(arrayOf("--file=/no/such/path/xyz-123.txt"))
        assertEquals(1, r.exitCode)
    }
}
