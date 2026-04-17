package com.jakubjirak.terminalsafety.analytics

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.IOException
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SortOrder
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

/**
 * Tools-menu action that opens the Rule Effectiveness Analytics dialog.
 *
 * The dialog surfaces per-rule hit counters collected by
 * [RuleAnalyticsService]. Heavy work (snapshot retrieval, CSV writing) runs
 * off the EDT via [Task.Backgroundable]; only the `JBTable` repaint touches
 * Swing.
 */
class ShowRuleAnalyticsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        object : Task.Backgroundable(project, "Loading rule analytics", false) {
            override fun run(indicator: ProgressIndicator) {
                val snapshot = RuleAnalyticsService.getInstance(project).getSnapshot()
                ApplicationManager.getApplication().invokeLater {
                    RuleAnalyticsDialog(project, snapshot).show()
                }
            }
        }.queue()
    }
}

/**
 * Dialog that renders a snapshot of rule analytics in a [JBTable] and
 * offers CSV export + reset.
 */
internal class RuleAnalyticsDialog(
    private val project: Project,
    initialSnapshot: List<RuleStatSnapshot>,
) : DialogWrapper(project, true) {

    private val model = RuleAnalyticsTableModel(initialSnapshot)
    private val table = JBTable(model).apply {
        autoCreateRowSorter = false
        rowSorter = TableRowSorter(model).apply {
            toggleSortOrder(RuleAnalyticsTableModel.COL_HITS)
            toggleSortOrder(RuleAnalyticsTableModel.COL_HITS) // desc
            setSortKeys(listOf(javax.swing.RowSorter.SortKey(
                RuleAnalyticsTableModel.COL_HITS, SortOrder.DESCENDING)))
        }
        setShowGrid(false)
        intercellSpacing = Dimension(0, 0)
    }

    init {
        title = "Terminal Safety — Rule Effectiveness Analytics"
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<javax.swing.Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout())
        root.preferredSize = JBUI.size(900, 500)
        val scroll = JBScrollPane(table)
        root.add(scroll, BorderLayout.CENTER)

        val buttons = JPanel()
        val export = javax.swing.JButton("Export CSV…").apply {
            addActionListener { exportCsv() }
        }
        val resetAll = javax.swing.JButton("Reset All").apply {
            addActionListener { resetAll() }
        }
        buttons.add(export)
        buttons.add(resetAll)
        root.add(buttons, BorderLayout.SOUTH)
        return root
    }

    private fun exportCsv() {
        val descriptor = FileSaverDescriptor(
            "Export Rule Analytics",
            "Save the current analytics snapshot as a CSV file",
        ).apply { withExtensionFilter("CSV files", "csv") }
        val saver = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
        val filename = "rule-analytics-${System.currentTimeMillis()}.csv"
        val wrapper: VirtualFileWrapper = saver.save(null as com.intellij.openapi.vfs.VirtualFile?, filename) ?: return
        val file = wrapper.file
        object : Task.Backgroundable(project, "Exporting rule analytics", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    writeCsv(model.snapshot, file.toPath())
                } catch (io: IOException) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Failed to export CSV: ${io.message}",
                            "Terminal Safety",
                        )
                    }
                }
            }
        }.queue()
    }

    private fun resetAll() {
        val confirm = Messages.showYesNoDialog(
            project,
            "Reset analytics for every rule? This cannot be undone.",
            "Reset Rule Analytics",
            Messages.getWarningIcon(),
        )
        if (confirm != Messages.YES) return
        RuleAnalyticsService.getInstance(project).reset()
        model.replace(emptyList())
    }

    companion object {
        private val DATE = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

        internal fun formatDate(epoch: Long): String =
            if (epoch <= 0L) "" else DATE.format(Date(epoch))

        /** CSV-escape per RFC 4180: quote fields containing `,`, `"`, or newlines. */
        internal fun csvEscape(field: String): String {
            val needsQuote = field.contains(',') ||
                field.contains('"') ||
                field.contains('\n') ||
                field.contains('\r')
            if (!needsQuote) return field
            return "\"" + field.replace("\"", "\"\"") + "\""
        }

        /** Writes the snapshot as CSV; visible for testing. */
        internal fun writeCsv(snapshot: List<RuleStatSnapshot>, target: java.nio.file.Path) {
            val sb = StringBuilder()
            sb.append("Rule ID,Hits,Blocked,Allowed,Confirm Blocked,Confirm Allowed,")
                .append("Block %,First Seen,Last Seen,Last Command\n")
            for (s in snapshot) {
                sb.append(csvEscape(s.ruleId)).append(',')
                    .append(s.hitCount).append(',')
                    .append(s.blockCount).append(',')
                    .append(s.allowCount).append(',')
                    .append(s.confirmBlockedCount).append(',')
                    .append(s.confirmAllowedCount).append(',')
                    .append(String.format(Locale.ROOT, "%.2f", s.blockPercentage)).append(',')
                    .append(csvEscape(formatDate(s.firstSeenEpoch))).append(',')
                    .append(csvEscape(formatDate(s.lastSeenEpoch))).append(',')
                    .append(csvEscape(s.lastMatchedCommand))
                    .append('\n')
            }
            Files.writeString(target, sb.toString())
        }
    }
}

/** Swing table model backed by an immutable snapshot list. */
internal class RuleAnalyticsTableModel(
    initial: List<RuleStatSnapshot>,
) : AbstractTableModel() {

    @Volatile
    var snapshot: List<RuleStatSnapshot> = initial
        private set

    fun replace(newSnapshot: List<RuleStatSnapshot>) {
        snapshot = newSnapshot
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = snapshot.size
    override fun getColumnCount(): Int = COLUMNS.size
    override fun getColumnName(column: Int): String = COLUMNS[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        COL_HITS, COL_BLOCKED, COL_ALLOWED -> java.lang.Long::class.java
        COL_BLOCK_PCT -> java.lang.Double::class.java
        else -> String::class.java
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val s = snapshot[rowIndex]
        return when (columnIndex) {
            COL_ID -> s.ruleId
            COL_HITS -> s.hitCount
            COL_BLOCKED -> s.blockCount + s.confirmBlockedCount
            COL_ALLOWED -> s.allowCount + s.confirmAllowedCount
            COL_BLOCK_PCT -> s.blockPercentage
            COL_LAST_SEEN -> RuleAnalyticsDialog.formatDate(s.lastSeenEpoch)
            COL_LAST_CMD -> s.lastMatchedCommand
            else -> ""
        }
    }

    companion object {
        const val COL_ID = 0
        const val COL_HITS = 1
        const val COL_BLOCKED = 2
        const val COL_ALLOWED = 3
        const val COL_BLOCK_PCT = 4
        const val COL_LAST_SEEN = 5
        const val COL_LAST_CMD = 6
        private val COLUMNS = arrayOf(
            "Rule ID", "Hits", "Blocked", "Allowed", "Block %", "Last Seen", "Last Command",
        )
    }
}
