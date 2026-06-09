package com.jakubjirak.terminalsafety.interceptor

import com.jakubjirak.terminalsafety.analytics.MatchAction
import com.jakubjirak.terminalsafety.analytics.RuleAnalyticsService
import com.jakubjirak.terminalsafety.audit.AuditLogService
import com.jakubjirak.terminalsafety.audit.UserDecision
import com.jakubjirak.terminalsafety.model.DangerousCommand
import com.jakubjirak.terminalsafety.policy.EnforcementPolicyEngine
import com.jakubjirak.terminalsafety.policy.PolicyDecision
import com.jakubjirak.terminalsafety.risk.RiskAlertDialog
import com.jakubjirak.terminalsafety.risk.RiskAssessment
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
 *
 * The action taken for a matched command is decided by the pure
 * [EnforcementPolicyEngine] from the configured
 * [com.jakubjirak.terminalsafety.policy.EnforcementMode]:
 * warn-only notification, confirmation dialog, or an outright auto-block.
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

        val decision = EnforcementPolicyEngine.decide(
            mode = settings.enforcementModeEnum,
            ruleRisk = ruleResult?.matchedRule?.riskLevel,
            heuristicLevel = riskAssessment?.level,
        )

        if (ruleResult == null) {
            return handleHeuristicOnly(project, command, riskAssessment, decision)
        }

        LOG.info("Dangerous command detected: ${ruleResult.matchedRule.description} — ${ruleResult.command}")
        return when (decision) {
            PolicyDecision.ALLOW_SILENT -> false
            PolicyDecision.NOTIFY_ONLY -> { recordAllowed(project, settings, command, ruleResult, auto = true); false }
            PolicyDecision.REQUIRE_CONFIRMATION -> {
                if (DangerousCommandDialog.showAndGet(project, ruleResult)) {
                    recordAllowed(project, settings, command, ruleResult, auto = false)
                    false
                } else {
                    recordBlocked(project, settings, command, ruleResult, auto = false)
                    true
                }
            }
            PolicyDecision.BLOCK -> { recordBlocked(project, settings, command, ruleResult, auto = true); true }
        }
    }

    /** Heuristic-only path (no rule matched). */
    private fun handleHeuristicOnly(
        project: Project,
        command: String,
        assessment: RiskAssessment?,
        decision: PolicyDecision,
    ): Boolean {
        if (assessment == null) return false
        return when (decision) {
            PolicyDecision.ALLOW_SILENT -> false
            PolicyDecision.NOTIFY_ONLY -> {
                notify(
                    project,
                    "⚠️ Risky Command (warn-only mode)",
                    "Heuristic risk ${assessment.level.displayName} (${assessment.score}/100): <b>${escapeHtml(command)}</b>",
                    NotificationType.WARNING,
                )
                false
            }
            PolicyDecision.REQUIRE_CONFIRMATION -> {
                val proceed = RiskAlertDialog.showAndGet(project, assessment)
                if (!proceed) LOG.info("Heuristic risk alert blocked command: ${assessment.score}")
                !proceed
            }
            PolicyDecision.BLOCK -> {
                notify(
                    project,
                    "⛔ Risky Command Auto-Blocked",
                    "Enforcement policy blocked ${assessment.level.displayName}-risk command " +
                        "(${assessment.score}/100): <b>${escapeHtml(command)}</b>",
                    NotificationType.ERROR,
                )
                true
            }
        }
    }

    private fun recordAllowed(
        project: Project,
        settings: TerminalSafetySettings,
        command: String,
        result: DangerousCommand,
        auto: Boolean,
    ) {
        RuleAnalyticsService.getInstance(project)
            .recordHit(result.matchedRule.id, if (auto) MatchAction.ALLOWED else MatchAction.CONFIRM_ALLOWED, result.command)
        AuditLogService.getInstance(project).record(
            ruleId = result.matchedRule.id,
            ruleDescription = result.matchedRule.description,
            ruleAction = result.matchedRule.riskLevel.name,
            decision = if (auto) UserDecision.ALLOWED else UserDecision.CONFIRMED_ALLOWED,
            rawCommand = command,
        )
        if (auto) {
            notify(
                project,
                "⚠️ Dangerous Command (warn-only mode)",
                "Executed without confirmation: <b>${escapeHtml(result.command)}</b> — ${result.matchedRule.description}",
                NotificationType.WARNING,
            )
        } else {
            notify(
                project,
                "Command Allowed",
                "User confirmed execution of: <b>${escapeHtml(result.command)}</b>",
                NotificationType.INFORMATION,
            )
        }
        settings.addToAuditLog(command, result.matchedRule.description, true)
    }

    private fun recordBlocked(
        project: Project,
        settings: TerminalSafetySettings,
        command: String,
        result: DangerousCommand,
        auto: Boolean,
    ) {
        RuleAnalyticsService.getInstance(project)
            .recordHit(result.matchedRule.id, if (auto) MatchAction.BLOCKED else MatchAction.CONFIRM_BLOCKED, result.command)
        AuditLogService.getInstance(project).record(
            ruleId = result.matchedRule.id,
            ruleDescription = result.matchedRule.description,
            ruleAction = result.matchedRule.riskLevel.name,
            decision = if (auto) UserDecision.BLOCKED else UserDecision.CONFIRMED_BLOCKED,
            rawCommand = command,
        )
        notify(
            project,
            if (auto) "⛔ Dangerous Command Auto-Blocked" else "⚠️ Dangerous Command Blocked",
            (if (auto) "Enforcement policy blocked: " else "Command blocked: ") +
                "<b>${escapeHtml(result.command)}</b> — ${result.matchedRule.description}",
            if (auto) NotificationType.ERROR else NotificationType.WARNING,
        )
        settings.addToAuditLog(command, result.matchedRule.description, false)
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Terminal Safety Rails")
            .createNotification(title, content, type)
            .notify(project)
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    companion object {
        private val LOG = Logger.getInstance(CommandInterceptor::class.java)
    }
}
