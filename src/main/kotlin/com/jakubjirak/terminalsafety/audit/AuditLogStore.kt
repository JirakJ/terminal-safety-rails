package com.jakubjirak.terminalsafety.audit

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.streams.asSequence

/**
 * Append-only, size-rotated JSON-Lines store for [AuditEvent]s.
 *
 * Events are persisted to `<baseDir>/audit.log.jsonl`, one JSON object per
 * line. When the active file grows past [maxFileBytes] it is renamed to
 * `audit-<epochMs>.jsonl` and a fresh active file starts; at most
 * [maxArchives] historical files are retained — older archives are deleted.
 *
 * All mutating operations (record / clear / rotation) acquire an internal
 * [ReentrantLock], guaranteeing that concurrent callers from any thread
 * observe a consistent file state. Query reads take the same lock briefly
 * to snapshot archive paths, then stream files lazily outside the lock.
 *
 * @param baseDir Directory that will hold the active log and archives. Created on demand.
 * @param maxFileBytes Rotation threshold in bytes. Must be >= 1024.
 * @param maxArchives Maximum number of rotated archives to retain (>= 0).
 */
class AuditLogStore(
    private val baseDir: Path,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxArchives: Int = DEFAULT_MAX_ARCHIVES,
) {

    init {
        require(maxFileBytes >= MIN_ROTATION_BYTES) {
            "maxFileBytes must be >= $MIN_ROTATION_BYTES"
        }
        require(maxArchives >= 0) { "maxArchives must be >= 0" }
    }

    private val lock = ReentrantLock()

    /** Path of the active (currently-being-written) log file. */
    val activeFile: Path get() = baseDir.resolve(ACTIVE_FILE_NAME)

    /**
     * Appends a single event to the active log file, rotating first if the
     * file would exceed [maxFileBytes]. Safe to call from any thread.
     *
     * @throws IOException if the file cannot be created or written.
     */
    @Throws(IOException::class)
    fun record(event: AuditEvent) {
        val line = AuditJson.encode(event) + "\n"
        val bytes = line.toByteArray(StandardCharsets.UTF_8)
        lock.withLock {
            ensureBaseDir()
            rotateIfNeeded(bytes.size.toLong())
            Files.write(
                activeFile,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND,
            )
        }
    }

    /**
     * Streams events across all archives + active file, newest-archive first,
     * applying [filter] and honouring [AuditFilter.limit].
     *
     * The returned sequence is lazy and safe to iterate after the lock is
     * released; it will, however, reflect the file set that existed at the
     * time [query] was called — events recorded concurrently are not guaranteed
     * to appear. Corrupt lines are skipped rather than thrown.
     */
    fun query(filter: AuditFilter = AuditFilter()): Sequence<AuditEvent> {
        val snapshot: List<Path> = lock.withLock {
            val archives = listArchivesInternal().reversed()
            val out = ArrayList<Path>(archives.size + 1)
            out.addAll(archives)
            if (Files.exists(activeFile)) out.add(activeFile)
            out
        }
        return sequence {
            var emitted = 0
            for (p in snapshot) {
                if (!Files.exists(p)) continue
                Files.lines(p, StandardCharsets.UTF_8).use { lines ->
                    val it = lines.asSequence().iterator()
                    while (it.hasNext()) {
                        if (emitted >= filter.limit) return@use
                        val line = it.next()
                        if (line.isBlank()) continue
                        val ev = try {
                            AuditJson.decode(line)
                        } catch (_: RuntimeException) {
                            continue
                        }
                        if (filter.matches(ev)) {
                            yield(ev)
                            emitted += 1
                        }
                    }
                }
                if (emitted >= filter.limit) break
            }
        }
    }

    /** Deletes the active log and every archive. */
    fun clear() {
        lock.withLock {
            if (Files.exists(activeFile)) Files.delete(activeFile)
            for (p in listArchivesInternal()) {
                try { Files.deleteIfExists(p) } catch (_: IOException) { /* ignore */ }
            }
        }
    }

    /** Returns archive paths sorted oldest-first. Does not include the active file. */
    fun listArchives(): List<Path> = lock.withLock { listArchivesInternal() }

    private fun listArchivesInternal(): List<Path> {
        if (!Files.isDirectory(baseDir)) return emptyList()
        return Files.list(baseDir).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) }
                .filter {
                    val n = it.fileName.toString()
                    n.startsWith(ARCHIVE_PREFIX) && n.endsWith(ARCHIVE_SUFFIX)
                }
                .sortedBy { it.fileName.toString() }
                .toList()
        }
    }

    private fun ensureBaseDir() {
        if (!Files.isDirectory(baseDir)) Files.createDirectories(baseDir)
    }

    private fun rotateIfNeeded(nextLineBytes: Long) {
        val current = if (Files.exists(activeFile)) Files.size(activeFile) else 0L
        if (current + nextLineBytes <= maxFileBytes) return
        if (current == 0L) return // single line larger than threshold — still write it

        val archiveName = uniqueArchiveName()
        val target = baseDir.resolve(archiveName)
        Files.move(activeFile, target, StandardCopyOption.ATOMIC_MOVE)
        enforceArchiveCap()
    }

    private fun uniqueArchiveName(): String {
        val ts = System.currentTimeMillis()
        var candidate = "$ARCHIVE_PREFIX$ts$ARCHIVE_SUFFIX"
        var suffix = 0
        while (Files.exists(baseDir.resolve(candidate))) {
            suffix += 1
            candidate = "$ARCHIVE_PREFIX$ts-$suffix$ARCHIVE_SUFFIX"
        }
        return candidate
    }

    private fun enforceArchiveCap() {
        val archives = listArchivesInternal()
        val excess = archives.size - maxArchives
        if (excess <= 0) return
        for (i in 0 until excess) {
            try { Files.deleteIfExists(archives[i]) } catch (_: IOException) { /* ignore */ }
        }
    }

    companion object {
        /** Default rotation threshold: 10 MiB. */
        const val DEFAULT_MAX_FILE_BYTES: Long = 10L * 1024L * 1024L

        /** Default number of archives to retain. */
        const val DEFAULT_MAX_ARCHIVES: Int = 5

        /** Minimum rotation threshold enforced by the ctor. */
        const val MIN_ROTATION_BYTES: Long = 1024L

        /** File name of the active, currently-appended log file. */
        const val ACTIVE_FILE_NAME: String = "audit.log.jsonl"

        internal const val ARCHIVE_PREFIX: String = "audit-"
        internal const val ARCHIVE_SUFFIX: String = ".jsonl"
    }
}
