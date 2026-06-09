package com.jakubjirak.terminalsafety.settings

import com.jakubjirak.terminalsafety.policy.EnforcementMode
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import javax.swing.DefaultComboBoxModel
import javax.swing.table.DefaultTableModel

class TerminalSafetyConfigurable : BoundConfigurable("Terminal Safety Rails") {

    private lateinit var rulesTableModel: DefaultTableModel

    override fun createPanel() = panel {
        val settings = TerminalSafetySettings.getInstance()

        group("General") {
            row {
                checkBox("Enable Terminal Safety Rails")
                    .bindSelected(settings::enabled)
                    .comment("Enable terminal command safety analysis and blocking")
            }
            row("Enforcement mode:") {
                comboBox(
                    DefaultComboBoxModel(EnforcementMode.entries.toTypedArray()),
                )
                    .bindItem(
                        { settings.enforcementModeEnum },
                        { settings.enforcementMode = (it ?: EnforcementMode.CONFIRM).name },
                    )
                    .comment(
                        "Warn only — never block, just notify. Confirm — ask before running (default). " +
                            "Block critical — auto-block CRITICAL findings without an override. " +
                            "Lockdown — auto-block HIGH and CRITICAL (production freeze / incident response).",
                    )
            }
        }

        group("Custom Rules") {
            val columnNames = arrayOf("Pattern", "Risk Level", "Category", "Description", "Regex")
            rulesTableModel = object : DefaultTableModel(columnNames, 0) {
                override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
                    4 -> java.lang.Boolean::class.java
                    else -> String::class.java
                }

                override fun isCellEditable(row: Int, column: Int): Boolean = true
            }

            settings.customRules.forEach { rule ->
                rulesTableModel.addRow(
                    arrayOf<Any>(rule.pattern, rule.riskLevel, rule.category, rule.description, rule.isRegex),
                )
            }

            val rulesTable = JBTable(rulesTableModel)
            val toolbarPanel = ToolbarDecorator.createDecorator(rulesTable)
                .setAddAction {
                    rulesTableModel.addRow(arrayOf<Any>("", "MEDIUM", "CUSTOM", "", false))
                }
                .setRemoveAction {
                    val selectedRow = rulesTable.selectedRow
                    if (selectedRow >= 0) {
                        rulesTableModel.removeRow(selectedRow)
                    }
                }
                .createPanel()

            row {
                cell(toolbarPanel)
                    .align(Align.FILL)
            }.resizableRow()
        }
    }

    override fun isModified(): Boolean {
        if (super.isModified()) return true
        if (!::rulesTableModel.isInitialized) return false
        return getTableRules() != TerminalSafetySettings.getInstance().customRules.toList()
    }

    override fun apply() {
        super.apply()
        if (::rulesTableModel.isInitialized) {
            TerminalSafetySettings.getInstance().customRules = getTableRules().toMutableList()
        }
    }

    override fun reset() {
        super.reset()
        if (::rulesTableModel.isInitialized) {
            val settings = TerminalSafetySettings.getInstance()
            rulesTableModel.rowCount = 0
            settings.customRules.forEach { rule ->
                rulesTableModel.addRow(
                    arrayOf<Any>(rule.pattern, rule.riskLevel, rule.category, rule.description, rule.isRegex),
                )
            }
        }
    }

    private fun getTableRules(): List<SerializableCommandRule> {
        return (0 until rulesTableModel.rowCount).map { row ->
            SerializableCommandRule(
                pattern = rulesTableModel.getValueAt(row, 0) as? String ?: "",
                riskLevel = rulesTableModel.getValueAt(row, 1) as? String ?: "MEDIUM",
                category = rulesTableModel.getValueAt(row, 2) as? String ?: "CUSTOM",
                description = rulesTableModel.getValueAt(row, 3) as? String ?: "",
                isRegex = rulesTableModel.getValueAt(row, 4) as? Boolean ?: false,
            )
        }
    }
}
