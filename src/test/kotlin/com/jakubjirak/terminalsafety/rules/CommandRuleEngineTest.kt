package com.jakubjirak.terminalsafety.rules

import com.jakubjirak.terminalsafety.model.CommandCategory
import com.jakubjirak.terminalsafety.model.CommandRule
import com.jakubjirak.terminalsafety.model.RiskLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommandRuleEngineTest {

    @Test
    fun `detect rm -rf slash as critical`() {
        val result = CommandRuleEngine.evaluate("rm -rf /")
        assertNotNull(result)
        assertEquals(RiskLevel.CRITICAL, result!!.matchedRule.riskLevel)
        assertEquals(CommandCategory.FILESYSTEM, result.matchedRule.category)
    }

    @Test
    fun `detect rm -rf home as critical`() {
        val result = CommandRuleEngine.evaluate("rm -rf ~")
        assertNotNull(result)
        assertEquals(RiskLevel.CRITICAL, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `detect rm -rf wildcard as critical`() {
        val result = CommandRuleEngine.evaluate("rm -rf *")
        assertNotNull(result)
        assertEquals(RiskLevel.CRITICAL, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `detect rm -fr variant`() {
        val result = CommandRuleEngine.evaluate("rm -fr /etc")
        assertNotNull(result)
        assertEquals(RiskLevel.CRITICAL, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `detect kubectl delete as high risk`() {
        val result = CommandRuleEngine.evaluate("kubectl delete pod my-pod")
        assertNotNull(result)
        assertEquals(RiskLevel.HIGH, result!!.matchedRule.riskLevel)
        assertEquals(CommandCategory.KUBERNETES, result.matchedRule.category)
    }

    @Test
    fun `detect terraform apply as high risk`() {
        val result = CommandRuleEngine.evaluate("terraform apply -auto-approve")
        assertNotNull(result)
        assertEquals(RiskLevel.HIGH, result!!.matchedRule.riskLevel)
        assertEquals(CommandCategory.TERRAFORM, result.matchedRule.category)
    }

    @Test
    fun `detect terraform destroy as high risk`() {
        val result = CommandRuleEngine.evaluate("terraform destroy")
        assertNotNull(result)
        assertEquals(RiskLevel.HIGH, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `detect docker system prune as medium risk`() {
        val result = CommandRuleEngine.evaluate("docker system prune -a")
        assertNotNull(result)
        assertEquals(RiskLevel.MEDIUM, result!!.matchedRule.riskLevel)
        assertEquals(CommandCategory.DOCKER, result.matchedRule.category)
    }

    @Test
    fun `detect git push force as medium risk`() {
        val result = CommandRuleEngine.evaluate("git push --force origin main")
        assertNotNull(result)
        assertEquals(RiskLevel.MEDIUM, result!!.matchedRule.riskLevel)
        assertEquals(CommandCategory.GIT, result.matchedRule.category)
    }

    @Test
    fun `detect git push -f shorthand`() {
        val result = CommandRuleEngine.evaluate("git push -f origin main")
        assertNotNull(result)
        assertEquals(RiskLevel.MEDIUM, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `detect git reset hard as medium risk`() {
        val result = CommandRuleEngine.evaluate("git reset --hard HEAD~3")
        assertNotNull(result)
        assertEquals(RiskLevel.MEDIUM, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `detect chmod 777 as medium risk`() {
        val result = CommandRuleEngine.evaluate("chmod 777 /var/www")
        assertNotNull(result)
        assertEquals(RiskLevel.MEDIUM, result!!.matchedRule.riskLevel)
        assertEquals(CommandCategory.PERMISSIONS, result.matchedRule.category)
    }

    @Test
    fun `detect chmod -R 777 as medium risk`() {
        val result = CommandRuleEngine.evaluate("chmod -R 777 /var/www")
        assertNotNull(result)
        assertEquals(RiskLevel.MEDIUM, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `detect DROP TABLE as critical`() {
        val result = CommandRuleEngine.evaluate("mysql -e 'DROP TABLE users'")
        assertNotNull(result)
        assertEquals(RiskLevel.CRITICAL, result!!.matchedRule.riskLevel)
        assertEquals(CommandCategory.DATABASE, result.matchedRule.category)
    }

    @Test
    fun `detect DROP DATABASE as critical`() {
        val result = CommandRuleEngine.evaluate("DROP DATABASE production")
        assertNotNull(result)
        assertEquals(RiskLevel.CRITICAL, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `detect TRUNCATE as critical`() {
        val result = CommandRuleEngine.evaluate("TRUNCATE TABLE logs")
        assertNotNull(result)
        assertEquals(RiskLevel.CRITICAL, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `detect mkfs as critical`() {
        val result = CommandRuleEngine.evaluate("mkfs.ext4 /dev/sda1")
        assertNotNull(result)
        assertEquals(RiskLevel.CRITICAL, result!!.matchedRule.riskLevel)
        assertEquals(CommandCategory.SYSTEM, result.matchedRule.category)
    }

    @Test
    fun `detect dd if as critical`() {
        val result = CommandRuleEngine.evaluate("dd if=/dev/zero of=/dev/sda")
        assertNotNull(result)
        assertEquals(RiskLevel.CRITICAL, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `detect systemctl stop as medium risk`() {
        val result = CommandRuleEngine.evaluate("systemctl stop nginx")
        assertNotNull(result)
        assertEquals(RiskLevel.MEDIUM, result!!.matchedRule.riskLevel)
        assertEquals(CommandCategory.SYSTEM, result.matchedRule.category)
    }

    @Test
    fun `detect service stop as medium risk`() {
        val result = CommandRuleEngine.evaluate("service nginx stop")
        assertNotNull(result)
        assertEquals(RiskLevel.MEDIUM, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `safe command returns null`() {
        val result = CommandRuleEngine.evaluate("ls -la")
        assertNull(result)
    }

    @Test
    fun `safe git push returns null`() {
        val result = CommandRuleEngine.evaluate("git push origin main")
        assertNull(result)
    }

    @Test
    fun `safe rm without recursive flag returns null`() {
        val result = CommandRuleEngine.evaluate("rm file.txt")
        assertNull(result)
    }

    @Test
    fun `empty command returns null`() {
        val result = CommandRuleEngine.evaluate("")
        assertNull(result)
    }

    @Test
    fun `blank command returns null`() {
        val result = CommandRuleEngine.evaluate("   ")
        assertNull(result)
    }

    @Test
    fun `custom rules take precedence over defaults`() {
        val customRule = CommandRule("ls", RiskLevel.LOW, CommandCategory.CUSTOM, "Custom ls rule")
        val rules = listOf(customRule) + CommandRuleEngine.DEFAULT_RULES

        val result = CommandRuleEngine.evaluate("ls -la", rules)
        assertNotNull(result)
        assertEquals(RiskLevel.LOW, result!!.matchedRule.riskLevel)
        assertEquals("Custom ls rule", result.matchedRule.description)
    }

    @Test
    fun `custom regex rules work correctly`() {
        val customRule = CommandRule("npm\\s+publish", RiskLevel.HIGH, CommandCategory.CUSTOM,
            "npm publish", isRegex = true)
        val result = CommandRuleEngine.evaluate("npm publish --tag latest", listOf(customRule))
        assertNotNull(result)
        assertEquals(RiskLevel.HIGH, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `case insensitive matching for SQL commands`() {
        val result = CommandRuleEngine.evaluate("drop table users")
        assertNotNull(result)
        assertEquals(RiskLevel.CRITICAL, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `evaluate with empty rules returns null`() {
        val result = CommandRuleEngine.evaluate("rm -rf /", emptyList())
        assertNull(result)
    }

    @Test
    fun `first matching rule wins`() {
        val rules = listOf(
            CommandRule("rm", RiskLevel.LOW, CommandCategory.CUSTOM, "Low rm"),
            CommandRule("rm\\s+-rf", RiskLevel.CRITICAL, CommandCategory.FILESYSTEM, "Critical rm -rf", isRegex = true),
        )

        val result = CommandRuleEngine.evaluate("rm -rf /tmp", rules)
        assertNotNull(result)
        assertEquals(RiskLevel.LOW, result!!.matchedRule.riskLevel)
    }

    @Test
    fun `glob to regex conversion`() {
        assertEquals(".*", CommandRuleEngine.globToRegex("*"))
        assertEquals(".", CommandRuleEngine.globToRegex("?"))
        assertEquals("test\\.txt", CommandRuleEngine.globToRegex("test.txt"))
    }
}
