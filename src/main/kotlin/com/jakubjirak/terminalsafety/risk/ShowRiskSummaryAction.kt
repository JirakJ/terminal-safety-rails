/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk

import com.jakubjirak.terminalsafety.settings.TerminalSafetySettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * Tools menu entry: "Show Risk Summary".
 *
 * Iterates recent commands retained in the settings audit log
 * ([TerminalSafetySettings.auditLog] — lightweight strings), scores each,
 * and shows the top-risk commands in a JBTable.
 *
 * This action intentionally reads only from already-persisted state so it
 * never performs any network or heavy IO on the EDT.
 */
class ShowRiskSummaryAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = RiskScoringService.getInstance(project)
        val settings = TerminalSafetySettings.getInstance()
        val recent = settings.auditLog.takeLast(RECENT_LIMIT).mapNotNull { extractCommand(it) }
        val ranked = recent.map { service.assess(it) }
            .filter { it.hasSignals }
            .sortedByDescending { it.score }
            .take(TOP_N)
        SummaryDialog(project, ranked).show()
    }

    private fun extractCommand(entry: String): String? {
        // audit log entries are free-form strings; best-effort extraction:
        // if entry contains " | " use the first segment, else the whole string.
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return null
        val pipeIdx = trimmed.indexOf(" | ")
        return if (pipeIdx > 0) trimmed.substring(0, pipeIdx) else trimmed
    }

    private class SummaryDialog(
        project: com.intellij.openapi.project.Project,
        private val data: List<RiskAssessment>,
    ) : DialogWrapper(project, true) {
        init {
            title = "Command Risk Summary"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val root = JPanel(BorderLayout(8, 8))
            val header = JBLabel("Top ${data.size} highest-risk recent commands")
            root.add(header, BorderLayout.NORTH)
            val model = object : DefaultTableModel(
                arrayOf<Any?>("Level", "Score", "Signals", "Command"), 0,
            ) {
                override fun isCellEditable(row: Int, column: Int): Boolean = false
            }
            for (a in data) {
                model.addRow(arrayOf(a.level.displayName, a.score, a.signals.size, a.command))
            }
            val table = JBTable(model)
            root.add(
                JBScrollPane(table).apply { preferredSize = Dimension(720, 320) },
                BorderLayout.CENTER,
            )
            return root
        }
    }

    companion object {
        private const val RECENT_LIMIT = 200
        private const val TOP_N = 20
    }
}
