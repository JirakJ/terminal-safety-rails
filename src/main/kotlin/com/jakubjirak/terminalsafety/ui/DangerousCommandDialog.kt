package com.jakubjirak.terminalsafety.ui

import com.jakubjirak.terminalsafety.model.DangerousCommand
import com.jakubjirak.terminalsafety.model.RiskLevel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.SwingConstants

/**
 * Confirmation dialog shown when a dangerous command is detected.
 * Color-coded by risk level.
 */
class DangerousCommandDialog(
    private val dangerousCommand: DangerousCommand,
) : DialogWrapper(true) {

    init {
        title = "⚠️ Dangerous Command Detected"
        setOKButtonText("Execute Anyway")
        setCancelButtonText("Cancel")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val risk = dangerousCommand.matchedRule.riskLevel
        val riskLabel = getRiskDisplayLabel(risk)

        val warningLabel = JBLabel(
            "<html><b style='font-size:14pt; color:${risk.colorHex};'>$riskLabel</b></html>"
        ).apply {
            horizontalAlignment = SwingConstants.CENTER
        }

        val escapedCommand = escapeHtml(dangerousCommand.command)
        val detailsLabel = JBLabel(
            "<html><div style='text-align:center; margin:10px;'>" +
                "Command:<br/>" +
                "<b style='font-size:11pt; font-family:monospace;'>$escapedCommand</b><br/>" +
                "<br/>" +
                "Reason: <b>${escapeHtml(dangerousCommand.matchedRule.description)}</b><br/>" +
                "Category: <b>${dangerousCommand.matchedRule.category.displayName}</b><br/>" +
                "<br/>" +
                "<span style='color:${risk.colorHex};'>This command may cause irreversible damage. " +
                "Proceed with caution.</span>" +
                "</div></html>"
        ).apply {
            horizontalAlignment = SwingConstants.CENTER
        }

        return FormBuilder.createFormBuilder()
            .addComponent(warningLabel)
            .addVerticalGap(10)
            .addComponent(detailsLabel)
            .addVerticalGap(10)
            .panel.apply {
                border = JBUI.Borders.empty(20)
                preferredSize = JBUI.size(500, 300)
            }
    }

    private fun getRiskDisplayLabel(risk: RiskLevel): String {
        return when (risk) {
            RiskLevel.CRITICAL -> "🔴 CRITICAL RISK"
            RiskLevel.HIGH -> "🟠 HIGH RISK"
            RiskLevel.MEDIUM -> "🟡 MEDIUM RISK"
            RiskLevel.LOW -> "🟢 LOW RISK"
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun showAndGet(project: Project, dangerousCommand: DangerousCommand): Boolean {
            val result = AtomicBoolean(false)
            ApplicationManager.getApplication().invokeAndWait {
                result.set(DangerousCommandDialog(dangerousCommand).showAndGet())
            }
            return result.get()
        }
    }
}
