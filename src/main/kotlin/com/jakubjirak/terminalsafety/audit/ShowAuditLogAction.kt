package com.jakubjirak.terminalsafety.audit

import com.intellij.ide.actions.RevealFileAction
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.jakubjirak.terminalsafety.settings.TerminalSafetySettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Files
import java.util.Calendar
import java.util.Date
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel
import javax.swing.SpinnerNumberModel
import javax.swing.table.AbstractTableModel

/**
 * Tools-menu action that opens the Incident Audit Log viewer dialog.
 *
 * The dialog renders events from [AuditLogService]'s backing [AuditLogStore],
 * applies [AuditFilter] values from the UI, and offers CSV/JSON export.
 * All I/O (query, export, clear) runs on a [Task.Backgroundable]; only
 * Swing repaints happen on the EDT.
 */
class ShowAuditLogAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = AuditLogService.getInstance(project)
        object : Task.Backgroundable(project, "Loading audit log", false) {
            override fun run(indicator: ProgressIndicator) {
                val initial = service.store()
                    .query(AuditFilter(limit = DEFAULT_LIMIT))
                    .toList()
                ApplicationManager.getApplication().invokeLater {
                    AuditLogDialog(project, service, initial).show()
                }
            }
        }.queue()
    }

    companion object {
        internal const val DEFAULT_LIMIT: Int = 1000
    }
}

/**
 * The dialog window. Intentionally kept in the same file as its action to
 * keep the audit feature's Swing surface in one place.
 */
internal class AuditLogDialog(
    private val project: Project,
    private val service: AuditLogService,
    initialRows: List<AuditEvent>,
) : DialogWrapper(project, true) {

    private val ruleIdCombo: JComboBox<String>
    private val decisionChecks: Map<UserDecision, JCheckBox> =
        UserDecision.values().associateWith { JCheckBox(it.name, true) }
    private val commandContainsField = JBTextField(20)
    private val sinceSpinner: JSpinner
    private val untilSpinner: JSpinner
    private val limitSpinner: JSpinner = JSpinner(
        SpinnerNumberModel(ShowAuditLogAction.DEFAULT_LIMIT, 1, 1_000_000, 100)
    )
    private val model = AuditLogTableModel(initialRows)
    private val table = JBTable(model).apply {
        setShowGrid(false)
        intercellSpacing = Dimension(0, 0)
    }

    init {
        title = "Terminal Safety — Incident Audit Log"
        setOKButtonText("Close")

        val ruleIds = sortedSetOf<String>().apply {
            TerminalSafetySettings.getInstance().getEffectiveRules()
                .forEach { add(it.id) }
            initialRows.forEach { add(it.ruleId) }
        }
        ruleIdCombo = JComboBox((listOf(ANY_RULE) + ruleIds).toTypedArray())

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_MONTH, -30) }
        sinceSpinner = JSpinner(SpinnerDateModel(cal.time, null, Date(now), Calendar.DAY_OF_MONTH))
        untilSpinner = JSpinner(SpinnerDateModel(Date(now), null, null, Calendar.DAY_OF_MONTH))

        init()
    }

    override fun createActions(): Array<javax.swing.Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout())
        root.preferredSize = JBUI.size(1000, 600)
        root.add(buildFilterPanel(), BorderLayout.NORTH)
        root.add(JBScrollPane(table), BorderLayout.CENTER)
        root.add(buildButtonPanel(), BorderLayout.SOUTH)
        return root
    }

    private fun buildFilterPanel(): JComponent {
        val p = JPanel(GridBagLayout())
        p.border = JBUI.Borders.empty(8)
        val g = GridBagConstraints().apply {
            insets = Insets(2, 4, 2, 4)
            fill = GridBagConstraints.HORIZONTAL
        }
        var row = 0

        fun addLabeled(label: String, comp: JComponent) {
            g.gridx = 0; g.gridy = row; g.weightx = 0.0
            p.add(JBLabel(label), g)
            g.gridx = 1; g.weightx = 1.0
            p.add(comp, g)
            row++
        }

        addLabeled("Since:", sinceSpinner)
        addLabeled("Until:", untilSpinner)
        addLabeled("Rule ID:", ruleIdCombo)
        addLabeled("Command contains:", commandContainsField)
        addLabeled("Limit:", limitSpinner)

        val decisionsPanel = JPanel()
        decisionChecks.values.forEach { decisionsPanel.add(it) }
        addLabeled("Decisions:", decisionsPanel)

        val applyBtn = JButton("Apply Filter").apply { addActionListener { refresh() } }
        g.gridx = 1; g.gridy = row
        p.add(applyBtn, g)
        return p
    }

    private fun buildButtonPanel(): JComponent {
        val p = JPanel()
        p.add(JButton("Export CSV…").apply { addActionListener { export(Format.CSV) } })
        p.add(JButton("Export JSON…").apply { addActionListener { export(Format.JSON) } })
        p.add(JButton("Clear Log…").apply { addActionListener { confirmClear() } })
        p.add(JButton("Reveal File").apply { addActionListener { reveal() } })
        return p
    }

    private fun currentFilter(): AuditFilter {
        val decisions = decisionChecks.filterValues { it.isSelected }.keys
        val ruleSel = ruleIdCombo.selectedItem as? String
        return AuditFilter(
            sinceEpochMs = (sinceSpinner.value as Date).time,
            untilEpochMs = (untilSpinner.value as Date).time + ONE_DAY_MS,
            ruleIds = if (ruleSel == null || ruleSel == ANY_RULE) null else setOf(ruleSel),
            decisions = if (decisions.size == UserDecision.values().size) null else decisions,
            commandContains = commandContainsField.text.takeIf { it.isNotBlank() },
            limit = limitSpinner.value as Int,
        )
    }

    private fun refresh() {
        val filter = currentFilter()
        object : Task.Backgroundable(project, "Querying audit log", false) {
            override fun run(indicator: ProgressIndicator) {
                val rows = service.store().query(filter).toList()
                ApplicationManager.getApplication().invokeLater { model.replace(rows) }
            }
        }.queue()
    }

    private fun export(format: Format) {
        val ext = format.ext
        val descriptor = FileSaverDescriptor(
            "Export Audit Log",
            "Save the filtered audit log as a $ext file",
            ext,
        )
        val wrapper: VirtualFileWrapper = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
            .save(null as VirtualFile?, "audit-${System.currentTimeMillis()}.$ext") ?: return
        val file = wrapper.file
        val filter = currentFilter()
        object : Task.Backgroundable(project, "Exporting audit log", false) {
            override fun run(indicator: ProgressIndicator) {
                val events = service.store().query(filter)
                val text = when (format) {
                    Format.CSV -> AuditExporter.toCsv(events)
                    Format.JSON -> AuditExporter.toJson(events)
                }
                Files.writeString(file.toPath(), text)
            }
        }.queue()
    }

    private fun confirmClear() {
        val yes = Messages.showYesNoDialog(
            project,
            "Clear the entire incident audit log, including archives? This cannot be undone.",
            "Clear Audit Log",
            Messages.getWarningIcon(),
        )
        if (yes != Messages.YES) return
        object : Task.Backgroundable(project, "Clearing audit log", false) {
            override fun run(indicator: ProgressIndicator) {
                service.store().clear()
                ApplicationManager.getApplication().invokeLater { model.replace(emptyList()) }
            }
        }.queue()
    }

    private fun reveal() {
        val file = service.store().activeFile.toFile()
        if (file.exists()) RevealFileAction.openFile(file) else
            Messages.showInfoMessage(project, "No audit log file exists yet.", "Reveal File")
    }

    private enum class Format(val ext: String) { CSV("csv"), JSON("json") }

    companion object {
        private const val ANY_RULE = "<any>"
        private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    }
}

/** Swing table model backed by an immutable snapshot list. */
internal class AuditLogTableModel(initial: List<AuditEvent>) : AbstractTableModel() {

    @Volatile
    var rows: List<AuditEvent> = initial
        private set

    fun replace(newRows: List<AuditEvent>) {
        rows = newRows
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = COLUMNS.size
    override fun getColumnName(column: Int): String = COLUMNS[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val e = rows[rowIndex]
        return when (columnIndex) {
            0 -> e.isoTimestamp
            1 -> e.user
            2 -> e.host
            3 -> e.ruleId
            4 -> e.decision.name
            5 -> e.command
            else -> ""
        }
    }

    companion object {
        private val COLUMNS = arrayOf(
            "Timestamp (UTC)", "User", "Host", "Rule ID", "Decision", "Command",
        )
    }
}
