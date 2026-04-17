package com.jakubjirak.terminalsafety.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AuditLogStoreTest {

    private fun newEvent(seq: Int, cmd: String = "cmd-$seq"): AuditEvent {
        val ts = 1_700_000_000_000L + seq
        return AuditEvent(
            eventId = "evt-$seq",
            timestampEpochMs = ts,
            isoTimestamp = AuditEvent.formatIso(ts),
            user = "tester",
            host = "unit",
            projectName = "p",
            projectPath = "/p",
            ruleId = "r${seq % 3}",
            ruleDescription = "desc $seq",
            ruleAction = "BLOCK",
            decision = UserDecision.values()[seq % UserDecision.values().size],
            command = cmd,
            commandHash = "hash-$seq",
        )
    }

    @Test
    fun `append writes one parseable JSONL line per event`(@TempDir dir: Path) {
        val store = AuditLogStore(dir)
        val events = (0 until 5).map { newEvent(it) }
        events.forEach { store.record(it) }

        val lines = Files.readAllLines(store.activeFile)
        assertEquals(5, lines.size)
        lines.zip(events).forEach { (line, ev) ->
            val parsed = AuditJson.decode(line)
            assertEquals(ev, parsed)
        }
    }

    @Test
    fun `query applies filter and honours limit`(@TempDir dir: Path) {
        val store = AuditLogStore(dir)
        (0 until 10).forEach { store.record(newEvent(it)) }

        val ruleR1 = store.query(AuditFilter(ruleIds = setOf("r1"))).toList()
        assertTrue(ruleR1.all { it.ruleId == "r1" })

        val limited = store.query(AuditFilter(limit = 3)).toList()
        assertEquals(3, limited.size)
    }

    @Test
    fun `rotation produces archives and keeps total line count`(@TempDir dir: Path) {
        // maxArchives set high so rotation doesn't discard archives — the invariant tested
        // here is "no events lost during rotation", not the retention cap.
        val store = AuditLogStore(dir, maxFileBytes = 4_096L, maxArchives = 10_000)
        val total = 600
        repeat(total) { store.record(newEvent(it, cmd = "x".repeat(40))) }

        val archives = store.listArchives()
        assertTrue(archives.isNotEmpty(), "expected at least one archive")

        var lines = 0
        for (p in archives) lines += Files.readAllLines(p).size
        lines += if (Files.exists(store.activeFile)) Files.readAllLines(store.activeFile).size else 0
        assertEquals(total, lines)
    }

    @Test
    fun `archive count is capped at maxArchives`(@TempDir dir: Path) {
        val store = AuditLogStore(dir, maxFileBytes = 1_024L, maxArchives = 5)
        // Make each event line ~200 bytes so rotation fires quickly.
        repeat(400) { store.record(newEvent(it, cmd = "y".repeat(150))) }
        assertTrue(store.listArchives().size <= 5, "archives=${store.listArchives().size}")
    }

    @Test
    fun `concurrent appends from 16 threads produce no corruption`(@TempDir dir: Path) {
        val store = AuditLogStore(dir, maxFileBytes = 10L * 1024L * 1024L)
        val threads = 16
        val perThread = 500
        val executor = Executors.newFixedThreadPool(threads)
        val counter = AtomicInteger(0)
        val futures = (0 until threads).map {
            executor.submit {
                repeat(perThread) {
                    val id = counter.getAndIncrement()
                    store.record(newEvent(id, cmd = "concurrent-$id"))
                }
            }
        }
        futures.forEach { it.get(60, TimeUnit.SECONDS) }
        executor.shutdown()

        val expected = threads * perThread
        val all = store.query(AuditFilter()).toList()
        assertEquals(expected, all.size)
        // Every line in the active file must parse (no interleaving).
        val parsed = Files.readAllLines(store.activeFile)
            .filter { it.isNotBlank() }
            .map { AuditJson.decode(it) }
        assertEquals(expected, parsed.size)
    }

    @Test
    fun `clear removes active file and archives`(@TempDir dir: Path) {
        val store = AuditLogStore(dir, maxFileBytes = 1_024L, maxArchives = 3)
        repeat(200) { store.record(newEvent(it, cmd = "z".repeat(100))) }
        assertTrue(store.listArchives().isNotEmpty())
        store.clear()
        assertTrue(!Files.exists(store.activeFile))
        assertEquals(0, store.listArchives().size)
    }
}
