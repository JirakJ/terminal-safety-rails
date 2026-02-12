package com.jakubjirak.terminalsafety.interceptor

import com.jakubjirak.terminalsafety.licensing.FreemiumGate
import com.jakubjirak.terminalsafety.model.DangerousCommand
import com.jakubjirak.terminalsafety.rules.CommandRuleEngine
import com.jakubjirak.terminalsafety.settings.TerminalSafetySettings
import com.jakubjirak.terminalsafety.ui.DangerousCommandDialog
import com.intellij.execution.Executor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.terminal.TerminalShellCommandHandler

/**
 * Intercepts terminal commands before execution and checks them against safety rules.
 * Registered via the `com.intellij.terminal.shellCommandHandler` extension point.
 */
class CommandInterceptor : TerminalShellCommandHandler {

    override fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean {
        val settings = TerminalSafetySettings.getInstance()
        if (!settings.enabled) return false
        val allRules = settings.getEffectiveRules()
        return CommandRuleEngine.evaluate(command, allRules) != null
    }

    override fun execute(
        project: Project,
        workingDirectory: String?,
        localSession: Boolean,
        command: String,
        @Suppress("UNUSED_PARAMETER") executor: Executor,
    ): Boolean {
        val settings = TerminalSafetySettings.getInstance()
        if (!settings.enabled) return false

        val allRules = settings.getEffectiveRules()
        val result = CommandRuleEngine.evaluate(command, allRules) ?: return false

        LOG.info("Dangerous command detected: ${result.matchedRule.description} — ${result.command}")

        val allowed = DangerousCommandDialog.showAndGet(project, result)

        if (allowed) {
            notifyAllowed(project, result)
            if (FreemiumGate.getInstance().isPaidFeatureAvailable(FreemiumGate.FEATURE_AUDIT_LOG)) {
                settings.addToAuditLog(command, result.matchedRule.description, true)
            }
            return false
        }

        notifyBlocked(project, result)
        if (FreemiumGate.getInstance().isPaidFeatureAvailable(FreemiumGate.FEATURE_AUDIT_LOG)) {
            settings.addToAuditLog(command, result.matchedRule.description, false)
        }
        return true
    }

    private fun notifyBlocked(project: Project, result: DangerousCommand) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Terminal Safety Rails")
            .createNotification(
                "⚠️ Dangerous Command Blocked",
                "Command blocked: <b>${escapeHtml(result.command)}</b> — ${result.matchedRule.description}",
                NotificationType.WARNING,
            )
            .notify(project)
    }

    private fun notifyAllowed(project: Project, result: DangerousCommand) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Terminal Safety Rails")
            .createNotification(
                "Command Allowed",
                "User confirmed execution of: <b>${escapeHtml(result.command)}</b>",
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    companion object {
        private val LOG = Logger.getInstance(CommandInterceptor::class.java)
    }
}
