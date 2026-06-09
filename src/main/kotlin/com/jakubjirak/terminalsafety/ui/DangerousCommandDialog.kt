package com.jakubjirak.terminalsafety.ui

import com.jakubjirak.terminalsafety.explain.CommandExplainer
import com.jakubjirak.terminalsafety.model.DangerousCommand
import com.jakubjirak.terminalsafety.model.RiskLevel
import com.jakubjirak.terminalsafety.safer.SaferAlternative
import com.jakubjirak.terminalsafety.safer.SaferAlternativeAdvisor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Confirmation dialog shown when a dangerous command is detected.
 * Color-coded by risk level. Besides the matched rule it explains what the
 * command will actually do ([CommandExplainer]) and offers copy-pasteable
 * safer alternatives ([SaferAlternativeAdvisor]).
 */
class DangerousCommandDialog(
    private val dangerousCommand: DangerousCommand,
) : DialogWrapper(true) {

    private val explanation = CommandExplainer().explain(dangerousCommand.command)
    private val alternatives = SaferAlternativeAdvisor().suggest(dangerousCommand.command)

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
                "Category: <b>${dangerousCommand.matchedRule.category.displayName}</b>" +
                "</div></html>"
        ).apply {
            horizontalAlignment = SwingConstants.CENTER
        }

        val builder = FormBuilder.createFormBuilder()
            .addComponent(warningLabel)
            .addVerticalGap(8)
            .addComponent(detailsLabel)

        if (explanation != null) {
            val dangerFlags = explanation.flags.filter { it.dangerous }
            val flagsHtml = if (dangerFlags.isEmpty()) "" else
                "<br/>" + dangerFlags.joinToString("<br/>") {
                    "<code>${escapeHtml(it.flag)}</code> — ${escapeHtml(it.meaning)}"
                }
            builder.addComponent(
                JBLabel(
                    "<html><div style='margin:4px 10px;'>" +
                        "<b>What will this do?</b><br/>" +
                        "${escapeHtml(explanation.summary)}$flagsHtml" +
                        "</div></html>"
                )
            )
        }

        if (alternatives.isNotEmpty()) {
            builder.addVerticalGap(6)
            builder.addComponent(
                JBLabel("<html><div style='margin:0 10px;'><b>Safer alternatives</b></div></html>")
            )
            for (alt in alternatives.take(MAX_ALTERNATIVES)) {
                builder.addComponent(alternativeRow(alt))
            }
        }

        builder.addVerticalGap(6)
        builder.addComponent(
            JBLabel(
                "<html><div style='text-align:center;'><span style='color:${risk.colorHex};'>" +
                    "This command may cause irreversible damage. Proceed with caution." +
                    "</span></div></html>"
            ).apply { horizontalAlignment = SwingConstants.CENTER }
        )

        return builder.panel.apply {
            border = JBUI.Borders.empty(16)
            preferredSize = JBUI.size(640, if (alternatives.isEmpty() && explanation == null) 300 else 460)
        }
    }

    private fun alternativeRow(alt: SaferAlternative): JComponent {
        val panel = JPanel(BorderLayout(8, 0)).apply { border = JBUI.Borders.empty(2, 14, 2, 10) }
        panel.add(
            JBLabel(
                "<html><b>${escapeHtml(alt.title)}</b><br/>" +
                    "<code>${escapeHtml(alt.command)}</code><br/>" +
                    "<span style='color:gray;'>${escapeHtml(alt.rationale)}</span></html>"
            ),
            BorderLayout.CENTER,
        )
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        val copy = JButton("Copy")
        copy.addActionListener {
            CopyPasteManager.getInstance().setContents(StringSelection(alt.command))
            copy.text = "Copied ✓"
            copy.isEnabled = false
        }
        actions.add(copy)
        panel.add(actions, BorderLayout.EAST)
        return panel
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
        private const val MAX_ALTERNATIVES = 3

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
