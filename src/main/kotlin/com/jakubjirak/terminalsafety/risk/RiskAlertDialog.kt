/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel

/**
 * Non-blocking "risk alert" dialog presented by [com.jakubjirak.terminalsafety
 * .interceptor.CommandInterceptor] when heuristic scoring returns HIGH or
 * CRITICAL but no rule blocked the command.
 *
 * This dialog is purely informational — the user can proceed. It serves as
 * an extra signal layer on top of the existing rule engine.
 */
object RiskAlertDialog {

    /** Shows the dialog; returns `true` if the user confirms execution. */
    fun showAndGet(project: Project, assessment: RiskAssessment): Boolean {
        val dlg = Dialog(project, assessment)
        return dlg.showAndGet()
    }

    private class Dialog(project: Project, private val a: RiskAssessment) :
        DialogWrapper(project, true) {

        init {
            title = "Risk Alert — ${a.level.displayName} (${a.score}/100)"
            setOKButtonText("Run Anyway")
            setCancelButtonText("Cancel")
            init()
        }

        override fun createCenterPanel(): JComponent {
            val root = JPanel(BorderLayout(8, 8))
            val badge = JBLabel(a.level.displayName, SwingConstants.CENTER).apply {
                isOpaque = true
                background = Color.decode(a.level.colorHex)
                foreground = Color.BLACK
                preferredSize = Dimension(200, 32)
                border = BorderFactory.createLineBorder(Color.GRAY, 1)
            }
            val top = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
                add(badge)
                add(JBLabel("Score: ${a.score}/100 • Signals: ${a.signals.size}"))
            }
            root.add(top, BorderLayout.NORTH)

            root.add(JBLabel("<html><b>Command:</b> ${escape(a.command)}</html>"), BorderLayout.CENTER)

            val model = object : DefaultTableModel(
                arrayOf<Any?>("Signal", "Weight", "Description", "Match"), 0,
            ) {
                override fun isCellEditable(row: Int, column: Int): Boolean = false
            }
            for (s in a.signals) model.addRow(arrayOf(s.id, s.weight, s.description, s.matched))
            val table = JBTable(model)
            root.add(
                JBScrollPane(table).apply { preferredSize = Dimension(720, 220) },
                BorderLayout.SOUTH,
            )
            return root
        }

        private fun escape(s: String): String =
            s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
