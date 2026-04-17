package com.jakubjirak.terminalsafety.interceptor

import com.jakubjirak.terminalsafety.analytics.MatchAction
import com.jakubjirak.terminalsafety.analytics.RuleAnalyticsService
import com.jakubjirak.terminalsafety.audit.AuditLogService
import com.jakubjirak.terminalsafety.audit.UserDecision
import com.jakubjirak.terminalsafety.model.DangerousCommand
import com.jakubjirak.terminalsafety.risk.RiskAlertDialog
import com.jakubjirak.terminalsafety.risk.RiskLevel as HeuristicRiskLevel
import com.jakubjirak.terminalsafety.risk.RiskScoringService
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
        if (CommandRuleEngine.evaluate(command, allRules) != null) return true
        // Proactive layer: also intercept on HIGH+ heuristic risk so the risk
        // alert dialog can be shown before the shell receives the command.
        return try {
            RiskScoringService.getInstance(project).assess(command).level.ordinal >=
                HeuristicRiskLevel.HIGH.ordinal
        } catch (_: Throwable) {
            false
        }
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
        val ruleResult = CommandRuleEngine.evaluate(command, allRules)

        // Heuristic risk scoring runs BEFORE rule evaluation gating so we can
        // (a) surface a proactive warning layer even when no rule matches, and
        // (b) enrich audit telemetry with the computed score when a rule does
        // match. Scorer is pure, in-memory, and safe to call on the EDT.
        val riskAssessment = try {
            RiskScoringService.getInstance(project).assess(command)
        } catch (t: Throwable) {
            LOG.warn("Risk scoring failed", t); null
        }

        if (ruleResult == null) {
            if (riskAssessment != null &&
                riskAssessment.level.ordinal >= HeuristicRiskLevel.HIGH.ordinal
            ) {
                val proceed = RiskAlertDialog.showAndGet(project, riskAssessment)
                if (!proceed) {
                    LOG.info("Heuristic risk alert blocked command: ${riskAssessment.score}")
                    return true
                }
            }
            return false
        }

        val result = ruleResult

        LOG.info("Dangerous command detected: ${result.matchedRule.description} — ${result.command}")

        val allowed = DangerousCommandDialog.showAndGet(project, result)

        val analytics = RuleAnalyticsService.getInstance(project)
        val audit = AuditLogService.getInstance(project)
        val ruleAction = result.matchedRule.riskLevel.name
        if (allowed) {
            analytics.recordHit(result.matchedRule.id, MatchAction.CONFIRM_ALLOWED, result.command)
            audit.record(
                ruleId = result.matchedRule.id,
                ruleDescription = result.matchedRule.description,
                ruleAction = ruleAction,
                decision = UserDecision.CONFIRMED_ALLOWED,
                rawCommand = command,
            )
            notifyAllowed(project, result)
            settings.addToAuditLog(command, result.matchedRule.description, true)
            return false
        }

        analytics.recordHit(result.matchedRule.id, MatchAction.CONFIRM_BLOCKED, result.command)
        audit.record(
            ruleId = result.matchedRule.id,
            ruleDescription = result.matchedRule.description,
            ruleAction = ruleAction,
            decision = UserDecision.CONFIRMED_BLOCKED,
            rawCommand = command,
        )
        notifyBlocked(project, result)
        settings.addToAuditLog(command, result.matchedRule.description, false)
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
