/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.history

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel

/**
 * Tools-menu action that audits the user's shell history file with the
 * heuristic risk scorer and shows a [HistoryAuditDialog] with safety score,
 * risk distribution and the riskiest commands.
 *
 * The default history file is auto-detected (`$HISTFILE`-style lookup of
 * `~/.zsh_history`, `~/.bash_history`, `~/.history`); the user can pick a
 * different file. Parsing and scoring run on a background thread.
 */
class AuditShellHistoryAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val detected = detectHistoryFile()
        val path: Path? = if (detected != null) {
            val choice = Messages.showYesNoCancelDialog(
                project,
                "Audit '$detected'?\n\nChoose 'No' to pick a different history file.",
                "Shell History Risk Audit",
                "Audit This File",
                "Pick Another…",
                "Cancel",
                Messages.getQuestionIcon(),
            )
            when (choice) {
                Messages.YES -> detected
                Messages.NO -> pickFile(project)
                else -> null
            }
        } else {
            pickFile(project)
        }
        if (path == null) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Auditing Shell History", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Scoring commands from ${path.fileName}…"
                try {
                    val content = String(Files.readAllBytes(path), Charsets.UTF_8)
                    val report = HistoryAuditor().audit(content, path.toString())
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) HistoryAuditDialog(project, report).show()
                    }
                } catch (t: Throwable) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) {
                            Messages.showErrorDialog(project, "Audit failed: ${t.message}", "Shell History Risk Audit")
                        }
                    }
                }
            }
        })
    }

    private fun pickFile(project: Project): Path? {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle("Select Shell History File")
            .withShowHiddenFiles(true)
        val home = LocalFileSystem.getInstance().findFileByPath(System.getProperty("user.home"))
        val vf = FileChooser.chooseFile(descriptor, project, home) ?: return null
        return Paths.get(vf.path)
    }

    /** Returns the first existing well-known history file, preferring zsh. */
    internal fun detectHistoryFile(home: String = System.getProperty("user.home")): Path? =
        listOf(".zsh_history", ".bash_history", ".history")
            .map { Paths.get(home, it) }
            .firstOrNull { Files.isRegularFile(it) }
}

/** Results dialog for the Shell History Risk Audit. */
class HistoryAuditDialog(
    private val project: Project,
    private val report: HistoryAuditReport,
) : DialogWrapper(project, true) {

    init {
        title = "Shell History Risk Audit — Safety Score ${report.safetyScore}/100"
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 10))
        root.border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        root.preferredSize = Dimension(900, 560)

        val header = JPanel()
        header.layout = BoxLayout(header, BoxLayout.Y_AXIS)
        val summary = JBLabel(
            "Safety score: ${report.safetyScore}/100   •   Entries: ${report.totalEntries}   •   " +
                "Unique: ${report.uniqueCommands}   •   MEDIUM+ commands: ${report.riskyCount}",
            SwingConstants.LEFT,
        )
        summary.font = summary.font.deriveFont(summary.font.size2D + 3f)
        header.add(summary)
        header.add(JBLabel("Source: ${report.sourceLabel}"))
        root.add(header, BorderLayout.NORTH)

        val model = object : DefaultTableModel(
            arrayOf<Any>("Score", "Level", "×", "Command", "Top signal", "Safer alternative"), 0,
        ) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        val risky = report.topRisky(MAX_ROWS)
        risky.forEach { e ->
            model.addRow(
                arrayOf<Any>(
                    e.assessment.score,
                    e.assessment.level.displayName,
                    e.occurrences,
                    e.command,
                    e.assessment.signals.firstOrNull()?.description ?: "",
                    e.alternatives.firstOrNull()?.command ?: "",
                )
            )
        }
        if (model.rowCount == 0) model.addRow(arrayOf<Any>("—", "—", "—", "No risky commands found — well done!", "", ""))
        val table = JBTable(model)
        table.setShowGrid(true)
        table.fillsViewportHeight = true
        root.add(JBScrollPane(table), BorderLayout.CENTER)

        val footer = JPanel(BorderLayout())
        val leftBtns = JPanel()
        val copy = JButton("Copy Markdown")
        copy.addActionListener {
            CopyPasteManager.getInstance().setContents(StringSelection(HistoryAuditMarkdownReporter.render(report)))
            copy.text = "Copied ✓"
            copy.isEnabled = false
        }
        val exportHtml = JButton("Export HTML…")
        exportHtml.addActionListener { export("history-audit", "html") { HistoryAuditHtmlReporter.render(report) } }
        val exportJson = JButton("Export JSON…")
        exportJson.addActionListener { export("history-audit", "json") { HistoryAuditJsonReporter.render(report) } }
        leftBtns.add(copy)
        leftBtns.add(exportHtml)
        leftBtns.add(exportJson)
        footer.add(leftBtns, BorderLayout.WEST)
        root.add(footer, BorderLayout.SOUTH)

        return root
    }

    private fun export(baseName: String, extension: String, producer: () -> String) {
        val chooser = JFileChooser()
        chooser.selectedFile = File("$baseName-${System.currentTimeMillis()}.$extension")
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile ?: return
            try {
                file.writeText(producer())
                Messages.showInfoMessage(project, "Report saved to ${file.absolutePath}", "Shell History Risk Audit")
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to save: ${ex.message}", "Shell History Risk Audit")
            }
        }
    }

    private companion object {
        const val MAX_ROWS = 500
    }
}
