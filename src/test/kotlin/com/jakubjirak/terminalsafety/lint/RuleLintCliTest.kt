package com.jakubjirak.terminalsafety.lint

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RuleLintCliTest {

    private fun writeRules(tempDir: Path, content: String): Path {
        val file = tempDir.resolve("rules.yaml")
        Files.writeString(file, content)
        return file
    }

    @Test
    fun `missing --source yields exit 1`() {
        val r = RuleLintCli.run(arrayOf("--format=plain"))
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("Missing required"))
    }

    @Test
    fun `unknown flag yields exit 1`() {
        val r = RuleLintCli.run(arrayOf("--nope=1"))
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("Unknown argument"))
    }

    @Test
    fun `unknown format yields exit 1`() {
        val r = RuleLintCli.run(arrayOf("--source=/none", "--format=xml"))
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("Unknown format"))
    }

    @Test
    fun `unknown fail-on yields exit 1`() {
        val r = RuleLintCli.run(arrayOf("--source=/none", "--fail-on=panic"))
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("Unknown --fail-on"))
    }

    @Test
    fun `missing source file yields exit 1`() {
        val r = RuleLintCli.run(arrayOf("--source=/definitely/not/here.yaml"))
        assertEquals(1, r.exitCode)
        assertTrue(r.stderr.contains("not found"))
    }

    @Test
    fun `clean rules produce exit 0 and markdown output`(@TempDir tempDir: Path) {
        val file = writeRules(tempDir, """
            - pattern: "rm -rf /"
              risk: CRITICAL
              description: "Recursive delete of root"
        """.trimIndent())
        val r = RuleLintCli.run(arrayOf("--source=$file"))
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("No issues found") || r.stdout.contains("Summary"))
    }

    @Test
    fun `plain format includes severity and code`(@TempDir tempDir: Path) {
        val file = writeRules(tempDir, """
            - pattern: "(unclosed"
              regex: true
              risk: HIGH
              description: bad
        """.trimIndent())
        val r = RuleLintCli.run(arrayOf("--source=$file", "--format=plain"))
        assertEquals(2, r.exitCode)
        assertTrue(r.stdout.contains(":ERROR:"))
        assertTrue(r.stdout.contains(LintCodes.INVALID_REGEX))
    }

    @Test
    fun `json format is parseable and contains summary`(@TempDir tempDir: Path) {
        val file = writeRules(tempDir, """
            - pattern: "(unclosed"
              regex: true
              risk: HIGH
              description: bad
        """.trimIndent())
        val r = RuleLintCli.run(arrayOf("--source=$file", "--format=json"))
        assertEquals(2, r.exitCode)
        assertTrue(r.stdout.trim().startsWith("{"))
        assertTrue(r.stdout.contains("\"summary\""))
        assertTrue(r.stdout.contains("\"issues\""))
        assertTrue(r.stdout.contains("\"severity\": \"ERROR\""))
    }

    @Test
    fun `fail-on=error does not fail on warnings`(@TempDir tempDir: Path) {
        // Produces only a WARNING (overly broad pattern on non-LOW).
        val file = writeRules(tempDir, """
            - pattern: ".*"
              risk: HIGH
              description: catches everything
        """.trimIndent())
        val defaultRun = RuleLintCli.run(arrayOf("--source=$file", "--format=plain"))
        assertEquals(0, defaultRun.exitCode)
    }

    @Test
    fun `fail-on=warning fails on warning`(@TempDir tempDir: Path) {
        val file = writeRules(tempDir, """
            - pattern: ".*"
              risk: HIGH
              description: catches everything
        """.trimIndent())
        val warnRun = RuleLintCli.run(
            arrayOf("--source=$file", "--format=plain", "--fail-on=warning"),
        )
        assertEquals(2, warnRun.exitCode)
    }

    @Test
    fun `help flag exits 0 with usage`() {
        val r = RuleLintCli.run(arrayOf("--help"))
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("Usage:"))
    }

    @Test
    fun `clean file with warnings only but fail-on default is 0`(@TempDir tempDir: Path) {
        val file = writeRules(tempDir, """
            - pattern: "foo"
              risk: MEDIUM
              description: "Team rule: foo"
        """.trimIndent())
        // Missing-field warning expected, but default --fail-on=error.
        val r = RuleLintCli.run(arrayOf("--source=$file"))
        assertEquals(0, r.exitCode)
        assertNotEquals("", r.stdout)
    }
}
