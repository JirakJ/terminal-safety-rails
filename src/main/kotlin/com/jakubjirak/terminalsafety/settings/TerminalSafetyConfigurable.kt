package com.jakubjirak.terminalsafety.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

class TerminalSafetyConfigurable : Configurable {

    private var enabledCheckbox: JBCheckBox? = null
    private var rulesTableModel: DefaultTableModel? = null
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "Terminal Safety Rails"

    override fun createComponent(): JComponent {
        val settings = TerminalSafetySettings.getInstance()

        enabledCheckbox = JBCheckBox("Enable Terminal Safety Rails", settings.enabled)

        val columnNames = arrayOf("Pattern", "Risk Level", "Category", "Description", "Regex")
        rulesTableModel = object : DefaultTableModel(columnNames, 0) {
            override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
                4 -> java.lang.Boolean::class.java
                else -> String::class.java
            }

            override fun isCellEditable(row: Int, column: Int): Boolean = true
        }

        settings.customRules.forEach { rule ->
            rulesTableModel?.addRow(arrayOf<Any>(rule.pattern, rule.riskLevel, rule.category, rule.description, rule.isRegex))
        }

        val rulesTable = JBTable(rulesTableModel)

        val toolbarDecorator = ToolbarDecorator.createDecorator(rulesTable)
            .setAddAction {
                rulesTableModel?.addRow(arrayOf<Any>("", "MEDIUM", "CUSTOM", "", false))
            }
            .setRemoveAction {
                val selectedRow = rulesTable.selectedRow
                if (selectedRow >= 0) {
                    rulesTableModel?.removeRow(selectedRow)
                }
            }

        val rulesPanel = toolbarDecorator.createPanel().apply {
            border = IdeBorderFactory.createTitledBorder("Custom Command Rules (evaluated before defaults)")
        }

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(enabledCheckbox!!)
            .addComponentFillVertically(rulesPanel, 10)
            .panel

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = TerminalSafetySettings.getInstance()
        if (enabledCheckbox?.isSelected != settings.enabled) return true
        if (getTableRules().size != settings.customRules.size) return true
        return getTableRules() != settings.customRules
    }

    override fun apply() {
        val settings = TerminalSafetySettings.getInstance()
        settings.enabled = enabledCheckbox?.isSelected ?: true
        settings.customRules = getTableRules().toMutableList()
    }

    override fun reset() {
        val settings = TerminalSafetySettings.getInstance()
        enabledCheckbox?.isSelected = settings.enabled

        rulesTableModel?.rowCount = 0
        settings.customRules.forEach { rule ->
            rulesTableModel?.addRow(arrayOf<Any>(rule.pattern, rule.riskLevel, rule.category, rule.description, rule.isRegex))
        }
    }

    private fun getTableRules(): List<SerializableCommandRule> {
        val model = rulesTableModel ?: return emptyList()
        return (0 until model.rowCount).map { row ->
            SerializableCommandRule(
                pattern = model.getValueAt(row, 0) as? String ?: "",
                riskLevel = model.getValueAt(row, 1) as? String ?: "MEDIUM",
                category = model.getValueAt(row, 2) as? String ?: "CUSTOM",
                description = model.getValueAt(row, 3) as? String ?: "",
                isRegex = model.getValueAt(row, 4) as? Boolean ?: false,
            )
        }
    }

    override fun disposeUIResources() {
        enabledCheckbox = null
        rulesTableModel = null
        mainPanel = null
    }
}
