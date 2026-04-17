package com.jakubjirak.terminalsafety.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuditFilterTest {

    private fun event(
        id: String = "e1",
        ts: Long = 1_000L,
        ruleId: String = "r1",
        decision: UserDecision = UserDecision.CONFIRMED_BLOCKED,
        cmd: String = "rm -rf /",
        user: String = "alice",
    ) = AuditEvent(
        eventId = id,
        timestampEpochMs = ts,
        isoTimestamp = AuditEvent.formatIso(ts),
        user = user,
        host = "h",
        projectName = "p",
        projectPath = "/p",
        ruleId = ruleId,
        ruleDescription = null,
        ruleAction = "CRITICAL",
        decision = decision,
        command = cmd,
        commandHash = "abc",
    )

    @Test
    fun `empty filter matches everything`() {
        val f = AuditFilter()
        assertTrue(f.matches(event()))
        assertTrue(f.matches(event(ts = 999_999L)))
    }

    @Test
    fun `date range inclusive bounds`() {
        val f = AuditFilter(sinceEpochMs = 100, untilEpochMs = 200)
        assertTrue(f.matches(event(ts = 100)))
        assertTrue(f.matches(event(ts = 150)))
        assertTrue(f.matches(event(ts = 200)))
        assertFalse(f.matches(event(ts = 99)))
        assertFalse(f.matches(event(ts = 201)))
    }

    @Test
    fun `ruleIds subset filters out others`() {
        val f = AuditFilter(ruleIds = setOf("r1", "r2"))
        assertTrue(f.matches(event(ruleId = "r1")))
        assertTrue(f.matches(event(ruleId = "r2")))
        assertFalse(f.matches(event(ruleId = "r3")))
    }

    @Test
    fun `decisions subset filters out others`() {
        val f = AuditFilter(decisions = setOf(UserDecision.CONFIRMED_ALLOWED))
        assertTrue(f.matches(event(decision = UserDecision.CONFIRMED_ALLOWED)))
        assertFalse(f.matches(event(decision = UserDecision.CONFIRMED_BLOCKED)))
    }

    @Test
    fun `commandContains is case insensitive`() {
        val f = AuditFilter(commandContains = "RM -RF")
        assertTrue(f.matches(event(cmd = "rm -rf /tmp")))
        assertFalse(f.matches(event(cmd = "ls -la")))
    }

    @Test
    fun `userEquals exact match`() {
        val f = AuditFilter(userEquals = "bob")
        assertTrue(f.matches(event(user = "bob")))
        assertFalse(f.matches(event(user = "alice")))
    }

    @Test
    fun `AND semantics across fields`() {
        val f = AuditFilter(
            sinceEpochMs = 100,
            untilEpochMs = 200,
            ruleIds = setOf("r1"),
            decisions = setOf(UserDecision.BLOCKED),
            commandContains = "rm",
        )
        assertTrue(
            f.matches(event(ts = 150, ruleId = "r1", decision = UserDecision.BLOCKED, cmd = "rm x"))
        )
        assertFalse(
            f.matches(event(ts = 150, ruleId = "r1", decision = UserDecision.BLOCKED, cmd = "ls"))
        )
        assertFalse(
            f.matches(event(ts = 50, ruleId = "r1", decision = UserDecision.BLOCKED, cmd = "rm x"))
        )
    }

    @Test
    fun `limit default is effectively infinite`() {
        assertEquals(Int.MAX_VALUE, AuditFilter().limit)
    }
}
