/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class HistoryAuditCliTest {

    private fun writeHistory(dir: Path, vararg lines: String): Path {
        val f = dir.resolve("history")
        Files.write(f, lines.toList())
        return f
    }

    @Test
    fun `help exits 0 with usage`() {
        val r = HistoryAuditCli.run(arrayOf("--help"))
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("--file=<path>"))
    }

    @Test
    fun `missing file argument exits 1`() {
        val r = HistoryAuditCli.run(arrayOf())
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("Missing required --file"))
    }

    @Test
    fun `unknown argument exits 1`() {
        val r = HistoryAuditCli.run(arrayOf("--nope"))
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("Unknown argument"))
    }

    @Test
    fun `nonexistent file exits 1`() {
        val r = HistoryAuditCli.run(arrayOf("--file=/definitely/not/here"))
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("File not found"))
    }

    @Test
    fun `invalid format top and fail-below are rejected`(@TempDir dir: Path) {
        val f = writeHistory(dir, "ls")
        assertEquals(1, HistoryAuditCli.run(arrayOf("--file=$f", "--format=xml")).exitCode)
        assertEquals(1, HistoryAuditCli.run(arrayOf("--file=$f", "--top=0")).exitCode)
        assertEquals(1, HistoryAuditCli.run(arrayOf("--file=$f", "--fail-below=101")).exitCode)
    }

    @Test
    fun `markdown to stdout by default with summary line`(@TempDir dir: Path) {
        val f = writeHistory(dir, "ls", "rm -rf /tmp/x")
        val r = HistoryAuditCli.run(arrayOf("--file=$f"))
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("# Shell History Risk Audit"))
        assertTrue(r.stdout.contains("History audit: safety score"))
        assertNotNull(r.report)
    }

    @Test
    fun `json format writes parseable-looking output file`(@TempDir dir: Path) {
        val f = writeHistory(dir, "rm -rf /tmp/x")
        val out = dir.resolve("report.json")
        val r = HistoryAuditCli.run(arrayOf("--file=$f", "--format=json", "--out=$out"))
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("Report written to"))
        val json = Files.readString(out)
        assertTrue(json.contains("\"safetyScore\""))
    }

    @Test
    fun `html format writes standalone document`(@TempDir dir: Path) {
        val f = writeHistory(dir, "ls")
        val out = dir.resolve("report.html")
        val r = HistoryAuditCli.run(arrayOf("--file=$f", "--format=html", "--out=$out"))
        assertEquals(0, r.exitCode)
        assertTrue(Files.readString(out).startsWith("<!DOCTYPE html>"))
    }

    @Test
    fun `out file is created in nested directories`(@TempDir dir: Path) {
        val f = writeHistory(dir, "ls")
        val out = dir.resolve("reports/nested/audit.md")
        val r = HistoryAuditCli.run(arrayOf("--file=$f", "--out=$out"))
        assertEquals(0, r.exitCode)
        assertTrue(Files.exists(out))
    }

    @Test
    fun `fail-below gates exit code on safety score`(@TempDir dir: Path) {
        val dirty = writeHistory(dir, "ls", "rm -rf /")
        val failing = HistoryAuditCli.run(arrayOf("--file=$dirty", "--fail-below=80"))
        assertEquals(2, failing.exitCode)

        val passing = HistoryAuditCli.run(arrayOf("--file=$dirty", "--fail-below=0"))
        assertEquals(0, passing.exitCode)
    }

    @Test
    fun `clean history passes strict gate`(@TempDir dir: Path) {
        val clean = writeHistory(dir, "ls", "git status", "cd ..")
        val r = HistoryAuditCli.run(arrayOf("--file=$clean", "--fail-below=100"))
        assertEquals(0, r.exitCode)
        assertEquals(100, r.report?.safetyScore)
    }

    @Test
    fun `zsh extended history file is parsed`(@TempDir dir: Path) {
        val f = writeHistory(dir, ": 1700000000:0;rm -rf /", ": 1700000001:0;ls")
        val r = HistoryAuditCli.run(arrayOf("--file=$f"))
        assertEquals(0, r.exitCode)
        assertEquals(2, r.report?.totalEntries)
        assertTrue(r.stdout.contains("rm -rf /"))
    }
}
