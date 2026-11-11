package com.jakubjirak.terminalsafety.rules

import com.jakubjirak.terminalsafety.model.CommandCategory
import com.jakubjirak.terminalsafety.model.RiskLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TeamRulesLoaderTest {

    @Test
    fun `parses multiple rules with all fields`() {
        val yaml = """
            rules:
              - pattern: "helm uninstall *"
                risk: HIGH
                category: KUBERNETES
                description: 'Removes a release'
              - pattern: ^make\s+nuke$
                risk_level: critical
                is_regex: true
        """.trimIndent()

        val rules = TeamRulesLoader.parseRules(yaml)

        assertEquals(2, rules.size)
        with(rules[0]) {
            assertEquals("helm uninstall *", pattern)
            assertEquals(RiskLevel.HIGH, riskLevel)
            assertEquals(CommandCategory.KUBERNETES, category)
            assertEquals("Removes a release", description)
            assertFalse(isRegex)
        }
        with(rules[1]) {
            assertEquals("^make\\s+nuke$", pattern)
            assertEquals(RiskLevel.CRITICAL, riskLevel)
            assertEquals(CommandCategory.CUSTOM, category)
            assertTrue(isRegex)
        }
    }

    @Test
    fun `defaults apply and do not leak between rules`() {
        val yaml = """
            - pattern: first
              risk: LOW
              regex: true
              description: one
            - pattern: second
        """.trimIndent()

        val second = TeamRulesLoader.parseRules(yaml)[1]
        assertEquals(RiskLevel.MEDIUM, second.riskLevel)
        assertEquals(CommandCategory.CUSTOM, second.category)
        assertFalse(second.isRegex)
        assertEquals("Team rule: second", second.description)
    }

    @Test
    fun `unknown risk and category fall back to defaults`() {
        val rule = TeamRulesLoader.parseRules(
            """
            - pattern: x
              risk: APOCALYPTIC
              category: NOPE
            """.trimIndent(),
        ).single()
        assertEquals(RiskLevel.MEDIUM, rule.riskLevel)
        assertEquals(CommandCategory.CUSTOM, rule.category)
    }

    @Test
    fun `blank patterns and stray keys are ignored`() {
        val yaml = """
            risk: HIGH
            - pattern: ""
              risk: HIGH
            - pattern:
            # comment
        """.trimIndent()
        assertTrue(TeamRulesLoader.parseRules(yaml).isEmpty())
        assertTrue(TeamRulesLoader.parseRules("").isEmpty())
    }

    @Test
    fun `loadFromDirectory reads the dotfile and tolerates its absence`(@TempDir dir: Path) {
        assertTrue(TeamRulesLoader.loadFromDirectory(dir).isEmpty())

        Files.writeString(dir.resolve(".terminal-safety-rules.yaml"), "- pattern: docker system prune\n  risk: HIGH\n")
        val rules = TeamRulesLoader.loadFromDirectory(dir)
        assertEquals(1, rules.size)
        assertEquals(RiskLevel.HIGH, rules.single().riskLevel)
    }
}
