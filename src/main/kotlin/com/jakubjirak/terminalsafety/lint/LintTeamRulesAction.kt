package com.jakubjirak.terminalsafety.lint

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.jakubjirak.terminalsafety.rules.TeamRulesLoader
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.io.IOException
import java.nio.file.Files
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

/**
 * Tools-menu action that runs [RuleLinter] against the current project's
 * team rules file (or a user-picked file) and shows the results in a
 * dialog.
 *
 * Parsing and linting run on a [Task.Backgroundable] — the Event Dispatch
 * Thread is used only to show dialogs and update the table model.
 */
class LintTeamRulesAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val existing = TeamRulesLoader.findConfigFile(project)
        val source: VirtualFile = existing ?: pickFile(project) ?: return

        object : Task.Backgroundable(project, "Linting team rules", false) {
            override fun run(indicator: ProgressIndicator) {
                val text = try {
                    String(source.contentsToByteArray())
                } catch (io: IOException) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Failed to read ${source.path}: ${io.message}",
                            "Terminal Safety — Lint Team Rules",
                        )
                    }
                    return
                }
                val issues = RuleLinter.lintSource(text)
                ApplicationManager.getApplication().invokeLater {
                    updateStatusBar(project, issues)
                    LintResultsDialog(project, source, issues).show()
                }
            }
        }.queue()
    }

    private fun pickFile(project: Project): VirtualFile? {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle("Select Team Rules File")
            .withDescription("Choose a .terminal-safety-rules.yaml (or similar) file to lint.")
        return FileChooser.chooseFile(descriptor, project, null)
    }

    private fun updateStatusBar(project: Project, issues: List<RuleLintIssue>) {
        val err = issues.count { it.severity == LintSeverity.ERROR }
        val warn = issues.count { it.severity == LintSeverity.WARNING }
        val info = issues.count { it.severity == LintSeverity.INFO }
        val msg = "Terminal Safety lint: $err errors, $warn warnings, $info info"
        WindowManager.getInstance().getStatusBar(project)?.setInfo(msg)
    }
}

/** DialogWrapper rendering [RuleLintIssue]s in a JBTable. */
internal class LintResultsDialog(
    private val project: Project,
    private val source: VirtualFile,
    private val issues: List<RuleLintIssue>,
) : DialogWrapper(project, true) {

    private val model = LintIssueTableModel(issues)
    private val table = JBTable(model).apply {
        setShowGrid(false)
        intercellSpacing = Dimension(0, 0)
        autoCreateRowSorter = true
    }

    init {
        title = "Terminal Safety — Team Rule Lint (${issues.size} issue${if (issues.size == 1) "" else "s"})"
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout())
        root.preferredSize = JBUI.size(960, 520)
        root.add(JBScrollPane(table), BorderLayout.CENTER)

        val buttons = JPanel()
        buttons.add(JButton("Copy Markdown Report").apply {
            addActionListener { copyMarkdown() }
        })
        buttons.add(JButton("Export Report…").apply {
            addActionListener { exportReport() }
        })
        buttons.add(JButton("Open Source File").apply {
            addActionListener { openSource() }
        })
        root.add(buttons, BorderLayout.SOUTH)
        return root
    }

    private fun copyMarkdown() {
        val md = LintMarkdownFormatter.format(issues, source.path)
        CopyPasteManager.getInstance().setContents(StringSelection(md))
    }

    private fun exportReport() {
        val descriptor = FileSaverDescriptor(
            "Export Lint Report",
            "Save the lint report as Markdown",
        ).apply { withExtensionFilter("Markdown files", "md") }
        val saver = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
        val filename = "team-rules-lint-${System.currentTimeMillis()}.md"
        val wrapper: VirtualFileWrapper =
            saver.save(null as VirtualFile?, filename) ?: return
        val file = wrapper.file
        object : Task.Backgroundable(project, "Exporting lint report", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    Files.writeString(
                        file.toPath(),
                        LintMarkdownFormatter.format(issues, source.path),
                    )
                } catch (io: IOException) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Failed to export report: ${io.message}",
                            "Terminal Safety",
                        )
                    }
                }
            }
        }.queue()
    }

    private fun openSource() {
        val fresh = LocalFileSystem.getInstance().refreshAndFindFileByPath(source.path) ?: source
        FileEditorManager.getInstance(project).openTextEditor(
            OpenFileDescriptor(project, fresh),
            true,
        )
    }
}

/** Table model for the lint results dialog. */
internal class LintIssueTableModel(
    private val issues: List<RuleLintIssue>,
) : AbstractTableModel() {

    override fun getRowCount(): Int = issues.size
    override fun getColumnCount(): Int = COLUMNS.size
    override fun getColumnName(column: Int): String = COLUMNS[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val i = issues[rowIndex]
        return when (columnIndex) {
            COL_SEVERITY -> i.severity.name
            COL_RULE_ID -> i.ruleId ?: ""
            COL_LINE -> i.line?.toString() ?: ""
            COL_CODE -> i.code
            COL_MESSAGE -> i.message
            else -> ""
        }
    }

    companion object {
        const val COL_SEVERITY = 0
        const val COL_RULE_ID = 1
        const val COL_LINE = 2
        const val COL_CODE = 3
        const val COL_MESSAGE = 4
        private val COLUMNS = arrayOf("Severity", "Rule ID", "Line", "Code", "Message")
    }
}
