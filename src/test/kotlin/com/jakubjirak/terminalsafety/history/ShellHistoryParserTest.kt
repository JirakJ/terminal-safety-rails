/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShellHistoryParserTest {

    private val parser = ShellHistoryParser()

    @Test
    fun `empty content yields no entries`() {
        assertTrue(parser.parse("").isEmpty())
        assertTrue(parser.parse("\n\n  \n").isEmpty())
    }

    @Test
    fun `plain format one command per line`() {
        val entries = parser.parse("ls -la\ngit status\nmake build\n")
        assertEquals(listOf("ls -la", "git status", "make build"), entries.map { it.command })
        assertTrue(entries.all { it.timestampEpochSec == null })
    }

    @Test
    fun `zsh extended format extracts timestamp and command`() {
        val entries = parser.parse(": 1700000000:0;git push --force\n: 1700000060:5;ls\n")
        assertEquals(2, entries.size)
        assertEquals("git push --force", entries[0].command)
        assertEquals(1700000000L, entries[0].timestampEpochSec)
        assertEquals("ls", entries[1].command)
        assertEquals(1700000060L, entries[1].timestampEpochSec)
    }

    @Test
    fun `zsh multiline continuation is joined`() {
        val content = ": 1700000000:0;for f in *.log; do \\\n  rm \"${'$'}f\"; \\\ndone"
        val entries = parser.parse(content)
        assertEquals(1, entries.size)
        assertEquals(1700000000L, entries[0].timestampEpochSec)
        assertTrue(entries[0].command.startsWith("for f in *.log; do"))
        assertTrue(entries[0].command.endsWith("done"))
    }

    @Test
    fun `bash timestamp comment attaches to following command`() {
        val entries = parser.parse("#1700000000\nrm -rf build\n#1700000060\nls\n")
        assertEquals(2, entries.size)
        assertEquals("rm -rf build", entries[0].command)
        assertEquals(1700000000L, entries[0].timestampEpochSec)
        assertEquals(1700000060L, entries[1].timestampEpochSec)
    }

    @Test
    fun `bash timestamp applies only to the next command`() {
        val entries = parser.parse("#1700000000\nfirst\nsecond\n")
        assertEquals(1700000000L, entries[0].timestampEpochSec)
        assertNull(entries[1].timestampEpochSec)
    }

    @Test
    fun `plain comments are skipped`() {
        val entries = parser.parse("# just a note\nls\n#another note\n")
        assertEquals(listOf("ls"), entries.map { it.command })
    }

    @Test
    fun `crlf line endings are handled`() {
        val entries = parser.parse("ls\r\ngit status\r\n")
        assertEquals(listOf("ls", "git status"), entries.map { it.command })
    }

    @Test
    fun `malformed zsh-like lines fall back to plain commands`() {
        val entries = parser.parse(": not-a-timestamp;echo hi\n")
        assertEquals(1, entries.size)
        assertEquals(": not-a-timestamp;echo hi", entries[0].command)
    }

    @Test
    fun `mixed formats in one file`() {
        val content = """
            ls
            : 1700000000:0;git push --force
            #1700000060
            rm -rf /tmp/x
        """.trimIndent()
        val entries = parser.parse(content)
        assertEquals(3, entries.size)
        assertNull(entries[0].timestampEpochSec)
        assertEquals(1700000000L, entries[1].timestampEpochSec)
        assertEquals(1700000060L, entries[2].timestampEpochSec)
    }
}
