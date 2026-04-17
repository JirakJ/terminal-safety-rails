package com.jakubjirak.terminalsafety.analytics

import com.intellij.util.xmlb.XmlSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [RuleAnalyticsService].
 *
 * The service contains no IntelliJ project dependencies aside from the
 * `PersistentStateComponent` contract, so it can be exercised as a plain
 * Kotlin object here.
 */
class RuleAnalyticsServiceTest {

    @Test
    fun `recordHit increments per-action counters and total hit count`() {
        val svc = RuleAnalyticsService()
        svc.recordHit("r1", MatchAction.BLOCKED, "cmd")
        svc.recordHit("r1", MatchAction.BLOCKED, "cmd")
        svc.recordHit("r1", MatchAction.ALLOWED, "cmd")
        svc.recordHit("r1", MatchAction.CONFIRM_BLOCKED, "cmd")
        svc.recordHit("r1", MatchAction.CONFIRM_ALLOWED, "cmd")
        svc.recordHit("r1", MatchAction.CONFIRM_ALLOWED, "cmd")

        val s = svc.getSnapshot().single()
        assertEquals(6L, s.hitCount)
        assertEquals(2L, s.blockCount)
        assertEquals(1L, s.allowCount)
        assertEquals(1L, s.confirmBlockedCount)
        assertEquals(2L, s.confirmAllowedCount)
        assertEquals(
            s.hitCount,
            s.blockCount + s.allowCount + s.confirmBlockedCount + s.confirmAllowedCount,
        )
    }

    @Test
    fun `snapshot is sorted by hit count descending`() {
        val svc = RuleAnalyticsService()
        repeat(1) { svc.recordHit("low", MatchAction.BLOCKED, "c") }
        repeat(5) { svc.recordHit("high", MatchAction.BLOCKED, "c") }
        repeat(3) { svc.recordHit("mid", MatchAction.BLOCKED, "c") }

        val ids = svc.getSnapshot().map { it.ruleId }
        assertEquals(listOf("high", "mid", "low"), ids)
    }

    @Test
    fun `reset clears all counters`() {
        val svc = RuleAnalyticsService()
        svc.recordHit("a", MatchAction.BLOCKED, "c")
        svc.recordHit("b", MatchAction.BLOCKED, "c")
        svc.reset()
        assertTrue(svc.getSnapshot().isEmpty())
    }

    @Test
    fun `resetRule removes only the targeted rule`() {
        val svc = RuleAnalyticsService()
        svc.recordHit("keep", MatchAction.BLOCKED, "c")
        svc.recordHit("drop", MatchAction.BLOCKED, "c")
        svc.resetRule("drop")

        val snapshot = svc.getSnapshot()
        assertEquals(1, snapshot.size)
        assertEquals("keep", snapshot[0].ruleId)
    }

    @Test
    fun `resetRule on unknown id is a no-op`() {
        val svc = RuleAnalyticsService()
        svc.recordHit("keep", MatchAction.BLOCKED, "c")
        svc.resetRule("does-not-exist")
        assertEquals(1, svc.getSnapshot().size)
    }

    @Test
    fun `mask long hex token`() {
        val cmd = "curl https://api.example.com/v1/auth?t=deadbeef1234567890abcdef1234567890"
        val masked = RuleAnalyticsService.maskAndTruncate(cmd)
        assertFalse(masked.contains("deadbeef1234567890abcdef1234567890"))
        assertTrue(masked.contains("***"))
    }

    @Test
    fun `mask base64-like token`() {
        val cmd = "echo QWxhZGRpbjpPcGVuU2VzYW1lQWxhZGRpbk9wZW4= done"
        val masked = RuleAnalyticsService.maskAndTruncate(cmd)
        assertFalse(masked.contains("QWxhZGRpbjpPcGVuU2VzYW1lQWxhZGRpbk9wZW4="))
        assertTrue(masked.contains("***"))
    }

    @Test
    fun `mask password equals value`() {
        val cmd = "mysql --password=supersecret123 -h localhost"
        val masked = RuleAnalyticsService.maskAndTruncate(cmd)
        assertFalse(masked.contains("supersecret123"))
        assertTrue(masked.contains("password=***"))
    }

    @Test
    fun `mask token equals value`() {
        val cmd = "deploy --token=abcxyz123 --region eu"
        val masked = RuleAnalyticsService.maskAndTruncate(cmd)
        assertFalse(masked.contains("abcxyz123"))
        assertTrue(masked.contains("token=***"))
    }

    @Test
    fun `mask -p password flag`() {
        val cmd = "mysql -u root -p hunter2 -h db.example.com"
        val masked = RuleAnalyticsService.maskAndTruncate(cmd)
        assertFalse(masked.contains("hunter2"))
        assertTrue(masked.contains("-p ***"))
    }

    @Test
    fun `mask Authorization bearer header`() {
        val cmd = "curl -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig' https://x"
        val masked = RuleAnalyticsService.maskAndTruncate(cmd)
        assertFalse(masked.contains("eyJhbGciOiJIUzI1NiJ9.payload.sig"))
        assertTrue(masked.contains("Authorization: Bearer ***"))
    }

    @Test
    fun `lastMatchedCommand is truncated at 200 chars`() {
        val svc = RuleAnalyticsService()
        // Use a command that does not trigger any masking regex so we can
        // observe truncation in isolation: short tokens separated by spaces.
        val long = (1..500).joinToString(" ") { "x" }
        svc.recordHit("r", MatchAction.BLOCKED, long)
        val snap = svc.getSnapshot().single()
        assertEquals(RuleAnalyticsService.MAX_COMMAND_LENGTH, snap.lastMatchedCommand.length)
    }

    @Test
    fun `XML state survives serialize-deserialize round-trip`() {
        val svc = RuleAnalyticsService()
        svc.recordHit("alpha", MatchAction.BLOCKED, "rm -rf /")
        svc.recordHit("alpha", MatchAction.CONFIRM_ALLOWED, "rm -rf /tmp")
        svc.recordHit("beta", MatchAction.ALLOWED, "kubectl delete pod foo")

        val original = svc.getState()
        val element = XmlSerializer.serialize(original)
        val deserialized = XmlSerializer.deserialize(element, RuleAnalyticsState::class.java)
        assertNotNull(deserialized)

        val restored = RuleAnalyticsService()
        restored.loadState(deserialized!!)

        val before = svc.getSnapshot().sortedBy { it.ruleId }
        val after = restored.getSnapshot().sortedBy { it.ruleId }
        assertEquals(before, after)
    }

    @Test
    fun `concurrent recordHit from 16 threads produces exact total`() {
        val svc = RuleAnalyticsService()
        val threads = 16
        val perThread = 500
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        try {
            repeat(threads) { t ->
                pool.submit {
                    start.await()
                    repeat(perThread) { i ->
                        val action = MatchAction.values()[i % MatchAction.values().size]
                        svc.recordHit("shared-rule", action, "cmd-$t-$i")
                    }
                    done.countDown()
                }
            }
            start.countDown()
            assertTrue(done.await(30, TimeUnit.SECONDS), "threads did not finish in time")
        } finally {
            pool.shutdownNow()
        }

        val snap = svc.getSnapshot().single()
        assertEquals((threads * perThread).toLong(), snap.hitCount)
        assertEquals(
            snap.hitCount,
            snap.blockCount + snap.allowCount + snap.confirmBlockedCount + snap.confirmAllowedCount,
        )
    }

    @Test
    fun `loadState replaces existing counters`() {
        val svc = RuleAnalyticsService()
        svc.recordHit("old", MatchAction.BLOCKED, "c")

        val state = RuleAnalyticsState().apply {
            stats = mutableListOf(
                RuleStatEntry(
                    ruleId = "new",
                    hitCount = 42L,
                    blockCount = 10L,
                    allowCount = 10L,
                    confirmBlockedCount = 11L,
                    confirmAllowedCount = 11L,
                    firstSeenEpoch = 1L,
                    lastSeenEpoch = 2L,
                    lastMatchedCommand = "hello",
                ),
            )
        }
        svc.loadState(state)

        val snap = svc.getSnapshot()
        assertEquals(1, snap.size)
        assertEquals("new", snap[0].ruleId)
        assertEquals(42L, snap[0].hitCount)
    }

    @Test
    fun `empty state after construction`() {
        val svc = RuleAnalyticsService()
        assertTrue(svc.getSnapshot().isEmpty())
        assertTrue(svc.getState().stats.isEmpty())
        // sanity: unknown rule lookup yields nothing
        assertNull(svc.getSnapshot().firstOrNull { it.ruleId == "nope" })
    }

    @Test
    fun `block percentage computed from blocked plus confirm blocked`() {
        val svc = RuleAnalyticsService()
        svc.recordHit("r", MatchAction.BLOCKED, "c")
        svc.recordHit("r", MatchAction.CONFIRM_BLOCKED, "c")
        svc.recordHit("r", MatchAction.ALLOWED, "c")
        svc.recordHit("r", MatchAction.CONFIRM_ALLOWED, "c")
        val snap = svc.getSnapshot().single()
        assertEquals(50.0, snap.blockPercentage, 0.0001)
    }
}
