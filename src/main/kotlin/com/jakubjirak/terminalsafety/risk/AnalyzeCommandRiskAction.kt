/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel

/**
 * Tools menu entry: "Analyze Command Risk".
 *
 * Presents a free-text area to paste a terminal command and, on "Analyze",
 * shows a breakdown of every heuristic [RiskSignal] that fired plus a
 * colour-coded level badge driven by [RiskLevel.colorHex].
 *
 * Scoring itself runs on the EDT — the scorer is a pure, in-memory, sub-
 * millisecond computation for realistic command lengths — but the input
 * is capped at 100 kB to guarantee bounded latency.
 */
class AnalyzeCommandRiskAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        AnalyzeDialog(project).show()
    }

    private class AnalyzeDialog(private val project: com.intellij.openapi.project.Project) :
        DialogWrapper(project, true) {

        private val input = JBTextArea(5, 60).apply { lineWrap = true; wrapStyleWord = true }
        private val levelBadge = JBLabel("—", SwingConstants.CENTER).apply {
            isOpaque = true
            preferredSize = Dimension(260, 32)
            border = BorderFactory.createLineBorder(Color.GRAY, 1)
        }
        private val scoreLabel = JBLabel("Score: –")
        private val tableModel = object : DefaultTableModel(
            arrayOf<Any?>("Signal", "Weight", "Description", "Match"), 0,
        ) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        private val table = JBTable(tableModel)

        init {
            title = "Analyze Command Risk"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val root = JPanel(BorderLayout(8, 8))
            val top = JPanel(BorderLayout(4, 4))
            top.add(JBLabel("Paste a terminal command to analyze its risk:"), BorderLayout.NORTH)
            top.add(JBScrollPane(input), BorderLayout.CENTER)
            val analyzeBtn = JButton("Analyze")
            analyzeBtn.addActionListener { runAnalysis() }
            val btnRow = JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(analyzeBtn) }
            top.add(btnRow, BorderLayout.SOUTH)

            val mid = JPanel(FlowLayout(FlowLayout.LEFT, 12, 4))
            mid.add(levelBadge)
            mid.add(scoreLabel)

            root.add(top, BorderLayout.NORTH)
            root.add(mid, BorderLayout.CENTER)
            root.add(JBScrollPane(table).apply { preferredSize = Dimension(720, 240) }, BorderLayout.SOUTH)
            return root
        }

        private fun runAnalysis() {
            val cmd = input.text.trim()
            if (cmd.isEmpty()) return
            val limited = cmd.take(100_000)
            val a = RiskScoringService.getInstance(project).assess(limited)
            scoreLabel.text = "Score: ${a.score}/100  (${a.signals.size} signals)"
            levelBadge.text = a.level.displayName
            levelBadge.background = Color.decode(a.level.colorHex)
            levelBadge.foreground = Color.BLACK
            tableModel.rowCount = 0
            for (s in a.signals) {
                tableModel.addRow(arrayOf(s.id, s.weight, s.description, s.matched))
            }
        }
    }
}
