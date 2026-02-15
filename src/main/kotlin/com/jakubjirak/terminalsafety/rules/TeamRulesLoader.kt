package com.jakubjirak.terminalsafety.rules

import com.jakubjirak.terminalsafety.model.CommandCategory
import com.jakubjirak.terminalsafety.model.CommandRule
import com.jakubjirak.terminalsafety.model.RiskLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * Loads team-shared command rules from .terminal-safety-rules.yaml in the project root.
 */
object TeamRulesLoader {

    private val logger = Logger.getInstance(TeamRulesLoader::class.java)
    private const val CONFIG_FILENAME = ".terminal-safety-rules.yaml"

    fun loadFromDirectory(directory: Path): List<CommandRule> {
        val configFile = directory.resolve(CONFIG_FILENAME).toFile()
        if (!configFile.exists()) return emptyList()
        return try {
            parseRules(configFile.readText())
        } catch (e: Exception) {
            logger.warn("Failed to load team rules from $directory/$CONFIG_FILENAME", e)
            emptyList()
        }
    }

    fun findConfigFile(project: Project): VirtualFile? {
        return project.guessProjectDir()?.findChild(CONFIG_FILENAME)
    }

    fun loadRules(project: Project): List<CommandRule> {
        val configFile = findConfigFile(project) ?: return emptyList()
        return try {
            val content = String(configFile.contentsToByteArray())
            parseRules(content)
        } catch (e: Exception) {
            logger.warn("Failed to load team rules from $CONFIG_FILENAME", e)
            emptyList()
        }
    }

    fun parseRules(yamlContent: String): List<CommandRule> {
        // Simple YAML-like parser for rules (avoids SnakeYAML dependency)
        val rules = mutableListOf<CommandRule>()
        var currentPattern: String? = null
        var currentRisk: RiskLevel = RiskLevel.MEDIUM
        var currentCategory: CommandCategory = CommandCategory.CUSTOM
        var currentDescription = ""
        var currentIsRegex = false

        for (line in yamlContent.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("- pattern:") -> {
                    addPendingRule(rules, buildRule(currentPattern, currentRisk, currentCategory, currentDescription, currentIsRegex))
                    currentPattern = trimmed.removePrefix("- pattern:").trim().removeSurrounding("\"").removeSurrounding("'")
                    currentRisk = RiskLevel.MEDIUM
                    currentCategory = CommandCategory.CUSTOM
                    currentDescription = ""
                    currentIsRegex = false
                }
                trimmed.startsWith("risk:") || trimmed.startsWith("risk_level:") -> {
                    val value = trimmed.substringAfter(":").trim().removeSurrounding("\"").removeSurrounding("'").uppercase()
                    currentRisk = try { RiskLevel.valueOf(value) } catch (_: IllegalArgumentException) { RiskLevel.MEDIUM }
                }
                trimmed.startsWith("category:") -> {
                    val value = trimmed.substringAfter(":").trim().removeSurrounding("\"").removeSurrounding("'").uppercase()
                    currentCategory = try { CommandCategory.valueOf(value) } catch (_: IllegalArgumentException) {
                        CommandCategory.CUSTOM
                    }
                }
                trimmed.startsWith("description:") -> {
                    currentDescription = trimmed.substringAfter(":").trim().removeSurrounding("\"").removeSurrounding("'")
                }
                trimmed.startsWith("regex:") || trimmed.startsWith("is_regex:") -> {
                    currentIsRegex = trimmed.substringAfter(":").trim().lowercase() == "true"
                }
            }
        }
        addPendingRule(rules, buildRule(currentPattern, currentRisk, currentCategory, currentDescription, currentIsRegex))
        return rules
    }

    private fun addPendingRule(rules: MutableList<CommandRule>, rule: CommandRule?) {
        if (rule != null) {
            rules.add(rule)
        }
    }

    private fun buildRule(
        pattern: String?,
        riskLevel: RiskLevel,
        category: CommandCategory,
        description: String,
        isRegex: Boolean,
    ): CommandRule? {
        if (pattern.isNullOrBlank()) return null
        return CommandRule(
            pattern = pattern,
            riskLevel = riskLevel,
            category = category,
            description = description.ifBlank { "Team rule: $pattern" },
            isRegex = isRegex,
        )
    }
}
