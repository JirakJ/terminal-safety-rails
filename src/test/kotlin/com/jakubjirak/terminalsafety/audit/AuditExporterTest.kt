package com.jakubjirak.terminalsafety.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuditExporterTest {

    private fun ev(
        id: String = "e1",
        ts: Long = 1_700_000_000_000L,
        user: String = "alice",
        host: String = "host1",
        project: String = "proj",
        ruleId: String = "FS:rm -rf /",
        decision: UserDecision = UserDecision.CONFIRMED_BLOCKED,
        cmd: String = "rm -rf /",
        hash: String = "abcd1234",
    ) = AuditEvent(
        eventId = id,
        timestampEpochMs = ts,
        isoTimestamp = AuditEvent.formatIso(ts),
        user = user,
        host = host,
        projectName = project,
        projectPath = "/$project",
        ruleId = ruleId,
        ruleDescription = "desc",
        ruleAction = "CRITICAL",
        decision = decision,
        command = cmd,
        commandHash = hash,
    )

    @Test
    fun `csv header matches stable column order`() {
        val csv = AuditExporter.toCsv(emptySequence())
        val header = csv.lineSequence().first()
        assertEquals(AuditExporter.CSV_COLUMNS.joinToString(","), header)
    }

    @Test
    fun `csv quotes fields with commas quotes and newlines`() {
        val cmd = "echo \"hello, world\"\nthen this"
        val csv = AuditExporter.toCsv(sequenceOf(ev(cmd = cmd)))
        // Must contain an escaped (doubled) quote and an enclosing pair around command field.
        assertTrue(csv.contains("\"echo \"\"hello, world\"\"\nthen this\""), csv)
    }

    @Test
    fun `csv emits one data row per event`() {
        val csv = AuditExporter.toCsv(sequenceOf(ev(id = "a"), ev(id = "b"), ev(id = "c")))
        val rows = csv.split("\r\n").filter { it.isNotEmpty() }
        assertEquals(4, rows.size) // header + 3
    }

    @Test
    fun `json empty is empty array`() {
        assertEquals("[]", AuditExporter.toJson(emptySequence()))
    }

    @Test
    fun `json array round-trips through AuditJson decode`() {
        val events = listOf(ev(id = "a"), ev(id = "b"))
        val json = AuditExporter.toJson(events.asSequence())
        assertTrue(json.startsWith("["))
        assertTrue(json.trimEnd().endsWith("]"))
        // Each inner object line must decode back via AuditJson.
        val lines = json.lines()
            .map { it.trim().trimEnd(',') }
            .filter { it.startsWith("{") && it.endsWith("}") }
        assertEquals(2, lines.size)
        val decoded = lines.map { AuditJson.decode(it) }
        assertEquals(events.map { it.eventId }, decoded.map { it.eventId })
        assertEquals(events.map { it.command }, decoded.map { it.command })
    }

    @Test
    fun `csv does not quote plain fields`() {
        val csv = AuditExporter.toCsv(sequenceOf(ev(cmd = "plaincommand")))
        val line = csv.lines()[1]
        assertFalse(line.contains("\"plaincommand\""), line)
        assertTrue(line.contains(",plaincommand,"), line)
    }
}
