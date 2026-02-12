package com.jakubjirak.terminalsafety.settings

import com.jakubjirak.terminalsafety.model.CommandCategory
import com.jakubjirak.terminalsafety.model.CommandRule
import com.jakubjirak.terminalsafety.model.RiskLevel
import com.jakubjirak.terminalsafety.rules.CommandRuleEngine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

@State(
    name = "TerminalSafetySettings",
    storages = [Storage(value = "terminalSafetySettings.xml", roamingType = RoamingType.DEFAULT)],
)
class TerminalSafetySettings : PersistentStateComponent<TerminalSafetySettings> {

    var enabled: Boolean = true

    @XCollection(style = XCollection.Style.v2)
    var customRules: MutableList<SerializableCommandRule> = mutableListOf()

    @XCollection(style = XCollection.Style.v2)
    var auditLog: MutableList<String> = mutableListOf()

    override fun getState(): TerminalSafetySettings = this

    override fun loadState(state: TerminalSafetySettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun getEffectiveRules(): List<CommandRule> {
        val custom = customRules.map { it.toCommandRule() }
        return if (custom.isNotEmpty()) {
            custom + CommandRuleEngine.DEFAULT_RULES
        } else {
            CommandRuleEngine.DEFAULT_RULES
        }
    }

    fun addToAuditLog(command: String, reason: String, allowed: Boolean) {
        val action = if (allowed) "ALLOWED" else "BLOCKED"
        val entry = "${System.currentTimeMillis()}|$action|$reason|$command"
        auditLog.add(entry)
        // Keep last 1000 entries
        while (auditLog.size > MAX_AUDIT_LOG_SIZE) {
            auditLog.removeAt(0)
        }
    }

    companion object {
        private const val MAX_AUDIT_LOG_SIZE = 1000

        fun getInstance(): TerminalSafetySettings =
            ApplicationManager.getApplication().getService(TerminalSafetySettings::class.java)
    }
}

@Tag("rule")
data class SerializableCommandRule(
    var pattern: String = "",
    var riskLevel: String = "MEDIUM",
    var category: String = "CUSTOM",
    var description: String = "",
    var isRegex: Boolean = false,
) {
    fun toCommandRule(): CommandRule = CommandRule(
        pattern = pattern,
        riskLevel = try { RiskLevel.valueOf(riskLevel) } catch (_: IllegalArgumentException) { RiskLevel.MEDIUM },
        category = try { CommandCategory.valueOf(category) } catch (_: IllegalArgumentException) { CommandCategory.CUSTOM },
        description = description.ifBlank { "Custom rule: $pattern" },
        isRegex = isRegex,
    )

    companion object {
        fun fromCommandRule(rule: CommandRule): SerializableCommandRule = SerializableCommandRule(
            pattern = rule.pattern,
            riskLevel = rule.riskLevel.name,
            category = rule.category.name,
            description = rule.description,
            isRegex = rule.isRegex,
        )
    }
}
