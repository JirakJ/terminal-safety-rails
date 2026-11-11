package com.jakubjirak.terminalsafety.settings

import com.jakubjirak.terminalsafety.model.CommandCategory
import com.jakubjirak.terminalsafety.model.CommandRule
import com.jakubjirak.terminalsafety.model.RiskLevel
import com.jakubjirak.terminalsafety.policy.EnforcementMode
import com.jakubjirak.terminalsafety.rules.CommandRuleEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TerminalSafetySettingsTest {

    private val teamRule = CommandRule(
        pattern = "make nuke",
        riskLevel = RiskLevel.CRITICAL,
        category = CommandCategory.CUSTOM,
        description = "team",
    )

    @Test
    fun `effective rules are custom then team then built-in`() {
        val settings = TerminalSafetySettings()
        settings.customRules.add(SerializableCommandRule(pattern = "my-cmd", riskLevel = "HIGH"))

        val rules = settings.getEffectiveRules(listOf(teamRule))

        assertEquals("my-cmd", rules[0].pattern)
        assertEquals(teamRule, rules[1])
        assertEquals(CommandRuleEngine.DEFAULT_RULES, rules.drop(2))
    }

    @Test
    fun `team rules are evaluated by the engine`() {
        val rules = TerminalSafetySettings().getEffectiveRules(listOf(teamRule))
        val result = CommandRuleEngine.evaluate("make nuke", rules)
        assertEquals(teamRule, result?.matchedRule)
    }

    @Test
    fun `without custom or team rules only built-ins apply`() {
        assertEquals(CommandRuleEngine.DEFAULT_RULES, TerminalSafetySettings().getEffectiveRules())
    }

    @Test
    fun `audit log keeps the most recent 1000 entries`() {
        val settings = TerminalSafetySettings()
        repeat(1005) { settings.addToAuditLog("cmd-$it", "reason", allowed = it % 2 == 0) }

        assertEquals(1000, settings.auditLog.size)
        assertTrue(settings.auditLog.first().endsWith("|cmd-5"))
        assertTrue(settings.auditLog.last().contains("|ALLOWED|reason|cmd-1004"))
    }

    @Test
    fun `unknown enforcement mode falls back to confirm`() {
        val settings = TerminalSafetySettings()
        assertEquals(EnforcementMode.CONFIRM, settings.enforcementModeEnum)
        settings.enforcementMode = "SOMETHING_ELSE"
        assertEquals(EnforcementMode.CONFIRM, settings.enforcementModeEnum)
    }

    @Test
    fun `serializable rule round trips and tolerates bad enum names`() {
        val rule = CommandRule("x", RiskLevel.HIGH, CommandCategory.GIT, "d", isRegex = true)
        assertEquals(rule, SerializableCommandRule.fromCommandRule(rule).toCommandRule())

        val broken = SerializableCommandRule(pattern = "y", riskLevel = "NOPE", category = "NOPE").toCommandRule()
        assertEquals(RiskLevel.MEDIUM, broken.riskLevel)
        assertEquals(CommandCategory.CUSTOM, broken.category)
        assertEquals("Custom rule: y", broken.description)
    }
}
