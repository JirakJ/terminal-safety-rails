package com.jakubjirak.terminalsafety.audit

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Project-level entry point to the Incident Audit Log.
 *
 * The service owns a single [AuditLogStore] rooted at
 * `<projectStoragePath>/terminal-safety/`. It is hooked by
 * [com.jakubjirak.terminalsafety.interceptor.CommandInterceptor] alongside
 * the existing [com.jakubjirak.terminalsafety.analytics.RuleAnalyticsService]
 * so every rule match yields a forensic record for compliance evidence
 * (SOC2 CC7.2, ISO 27001 A.12.4, PCI DSS 10.x).
 *
 * All disk I/O is dispatched to [AppExecutorUtil.getAppExecutorService] so
 * the EDT is never blocked by audit writes.
 */
@Service(Service.Level.PROJECT)
class AuditLogService(private val project: Project) {

    private val store: AuditLogStore = AuditLogService.createStore(project)

    /**
     * Builds an [AuditEvent] for a single rule match and appends it to the
     * audit log off the EDT. Exceptions are swallowed and logged — an audit
     * write must never break the terminal UX.
     *
     * @param ruleId Stable rule identifier.
     * @param ruleDescription Human-readable rule description (null when absent).
     * @param ruleAction Name of the rule action enum (e.g. `BLOCK`, `WARN`).
     * @param decision Final decision taken for this match.
     * @param rawCommand The unmasked command line as typed.
     */
    fun record(
        ruleId: String,
        ruleDescription: String?,
        ruleAction: String,
        decision: UserDecision,
        rawCommand: String,
    ) {
        val now = System.currentTimeMillis()
        val event = AuditEvent(
            eventId = AuditEvent.newEventId(),
            timestampEpochMs = now,
            isoTimestamp = AuditEvent.formatIso(now),
            user = System.getProperty("user.name", "unknown-user"),
            host = AuditEvent.hostName(),
            projectName = project.name,
            projectPath = project.basePath ?: "",
            ruleId = ruleId,
            ruleDescription = ruleDescription,
            ruleAction = ruleAction,
            decision = decision,
            command = CommandMasker.mask(rawCommand),
            commandHash = AuditJson.sha256Hex(rawCommand),
        )
        AppExecutorUtil.getAppExecutorService().execute {
            try {
                store.record(event)
            } catch (io: IOException) {
                LOG.warn("Failed to append audit event for rule=$ruleId", io)
            }
        }
    }

    /** Returns the backing store for dialogs / exporters. */
    fun store(): AuditLogStore = store

    companion object {
        private val LOG = Logger.getInstance(AuditLogService::class.java)

        /** Convenience accessor mirroring other project services. */
        fun getInstance(project: Project): AuditLogService =
            project.getService(AuditLogService::class.java)

        /**
         * Resolves the on-disk directory used by the project's audit store.
         * Prefers the per-project `.idea` / workspace location via
         * `PathManager`-style resolution but falls back to a stable directory
         * under the project base path so the log survives IDE restarts.
         */
        internal fun createStore(project: Project): AuditLogStore {
            val base: Path = resolveBaseDir(project)
            return AuditLogStore(base)
        }

        private fun resolveBaseDir(project: Project): Path {
            val projectBase = project.basePath
            val root: Path = if (!projectBase.isNullOrBlank()) {
                Paths.get(projectBase, ".idea", "terminal-safety")
            } else {
                Paths.get(
                    System.getProperty("java.io.tmpdir", "."),
                    "terminal-safety-audit",
                    project.locationHash,
                )
            }
            return root
        }
    }
}
