package com.jakubjirak.terminalsafety.rules

import com.jakubjirak.terminalsafety.model.CommandCategory
import com.jakubjirak.terminalsafety.model.CommandRule
import com.jakubjirak.terminalsafety.model.DangerousCommand
import com.jakubjirak.terminalsafety.model.RiskLevel

/**
 * Engine that evaluates terminal commands against a set of safety rules.
 * Rules are evaluated in order — first match wins.
 */
object CommandRuleEngine {

    @Suppress("LongMethod")
    val DEFAULT_RULES: List<CommandRule> = listOf(
        // CRITICAL — File system destruction
        CommandRule("rm\\s+-[^\\s]*r[^\\s]*f[^\\s]*\\s+/", RiskLevel.CRITICAL, CommandCategory.FILESYSTEM,
            "Recursive force delete from root", isRegex = true),
        CommandRule("rm\\s+-[^\\s]*f[^\\s]*r[^\\s]*\\s+/", RiskLevel.CRITICAL, CommandCategory.FILESYSTEM,
            "Recursive force delete from root", isRegex = true),
        CommandRule("rm\\s+-[^\\s]*r[^\\s]*f[^\\s]*\\s+~", RiskLevel.CRITICAL, CommandCategory.FILESYSTEM,
            "Recursive force delete of home directory", isRegex = true),
        CommandRule("rm\\s+-[^\\s]*f[^\\s]*r[^\\s]*\\s+~", RiskLevel.CRITICAL, CommandCategory.FILESYSTEM,
            "Recursive force delete of home directory", isRegex = true),
        CommandRule("rm\\s+-[^\\s]*r[^\\s]*f[^\\s]*\\s+\\*", RiskLevel.CRITICAL, CommandCategory.FILESYSTEM,
            "Recursive force delete with wildcard", isRegex = true),
        CommandRule("rm\\s+-[^\\s]*f[^\\s]*r[^\\s]*\\s+\\*", RiskLevel.CRITICAL, CommandCategory.FILESYSTEM,
            "Recursive force delete with wildcard", isRegex = true),

        // CRITICAL — Disk/partition destruction
        CommandRule("mkfs", RiskLevel.CRITICAL, CommandCategory.SYSTEM, "Filesystem format command"),
        CommandRule("dd\\s+if=", RiskLevel.CRITICAL, CommandCategory.SYSTEM, "Raw disk write", isRegex = true),

        // CRITICAL — Database destruction
        CommandRule("DROP\\s+TABLE", RiskLevel.CRITICAL, CommandCategory.DATABASE,
            "SQL DROP TABLE", isRegex = true),
        CommandRule("DROP\\s+DATABASE", RiskLevel.CRITICAL, CommandCategory.DATABASE,
            "SQL DROP DATABASE", isRegex = true),
        CommandRule("TRUNCATE", RiskLevel.CRITICAL, CommandCategory.DATABASE, "SQL TRUNCATE"),
        CommandRule("DELETE\\s+FROM\\s+\\S+\\s*;", RiskLevel.CRITICAL, CommandCategory.DATABASE,
            "SQL DELETE without WHERE — wipes the whole table", isRegex = true),
        CommandRule("UPDATE\\s+\\S+\\s+SET\\b(?:(?!\\bWHERE\\b).)*;", RiskLevel.HIGH, CommandCategory.DATABASE,
            "SQL UPDATE without WHERE — rewrites every row", isRegex = true),

        // HIGH/CRITICAL — Cloud provider destruction
        CommandRule("aws\\s+s3\\s+rb\\b", RiskLevel.HIGH, CommandCategory.CLOUD,
            "AWS S3 bucket deletion", isRegex = true),
        CommandRule("aws\\s+s3\\s+rm\\b.*--recursive", RiskLevel.HIGH, CommandCategory.CLOUD,
            "Recursive AWS S3 object delete", isRegex = true),
        CommandRule("aws\\s+ec2\\s+terminate-instances", RiskLevel.HIGH, CommandCategory.CLOUD,
            "AWS EC2 instance termination", isRegex = true),
        CommandRule("aws\\s+rds\\s+delete-db-(instance|cluster)", RiskLevel.CRITICAL, CommandCategory.CLOUD,
            "AWS RDS database deletion", isRegex = true),
        CommandRule("aws\\s+dynamodb\\s+delete-table", RiskLevel.HIGH, CommandCategory.CLOUD,
            "AWS DynamoDB table deletion", isRegex = true),
        CommandRule("aws\\s+cloudformation\\s+delete-stack", RiskLevel.HIGH, CommandCategory.CLOUD,
            "AWS CloudFormation stack deletion", isRegex = true),
        CommandRule("gsutil\\s+(-m\\s+)?rm\\b.*\\s-[a-zA-Z]*r", RiskLevel.HIGH, CommandCategory.CLOUD,
            "Recursive Google Cloud Storage delete", isRegex = true),
        CommandRule("az\\s+group\\s+delete", RiskLevel.CRITICAL, CommandCategory.CLOUD,
            "Azure resource-group deletion", isRegex = true),

        // HIGH — Kubernetes dangerous operations
        CommandRule("kubectl\\s+delete", RiskLevel.HIGH, CommandCategory.KUBERNETES,
            "Kubernetes resource deletion", isRegex = true),
        CommandRule("kubectl\\s+apply.*--force", RiskLevel.HIGH, CommandCategory.KUBERNETES,
            "Kubernetes force apply", isRegex = true),

        // HIGH — Terraform
        CommandRule("terraform\\s+apply", RiskLevel.HIGH, CommandCategory.TERRAFORM,
            "Terraform apply — may modify infrastructure", isRegex = true),
        CommandRule("terraform\\s+destroy", RiskLevel.HIGH, CommandCategory.TERRAFORM,
            "Terraform destroy — removes infrastructure", isRegex = true),

        // MEDIUM — Docker
        CommandRule("docker\\s+system\\s+prune", RiskLevel.MEDIUM, CommandCategory.DOCKER,
            "Docker system prune — removes all unused data", isRegex = true),
        CommandRule("docker\\s+rm\\s+-f\\s+\\$\\(docker\\s+ps", RiskLevel.MEDIUM, CommandCategory.DOCKER,
            "Docker force remove all containers", isRegex = true),

        // MEDIUM — Git
        CommandRule("git\\s+push\\s+--force", RiskLevel.MEDIUM, CommandCategory.GIT,
            "Git force push — rewrites remote history", isRegex = true),
        CommandRule("git\\s+push\\s+-f\\b", RiskLevel.MEDIUM, CommandCategory.GIT,
            "Git force push — rewrites remote history", isRegex = true),
        CommandRule("git\\s+reset\\s+--hard", RiskLevel.MEDIUM, CommandCategory.GIT,
            "Git hard reset — discards all changes", isRegex = true),

        // MEDIUM — Permissions
        CommandRule("chmod\\s+(-R\\s+)?777", RiskLevel.MEDIUM, CommandCategory.PERMISSIONS,
            "chmod 777 — world-writable permissions", isRegex = true),

        // MEDIUM — System services
        CommandRule("systemctl\\s+stop", RiskLevel.MEDIUM, CommandCategory.SYSTEM,
            "Stopping system service", isRegex = true),
        CommandRule("service\\s+\\S+\\s+stop", RiskLevel.MEDIUM, CommandCategory.SYSTEM,
            "Stopping system service", isRegex = true),
    )

    fun evaluate(command: String, rules: List<CommandRule> = DEFAULT_RULES): DangerousCommand? {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return null

        for (rule in rules) {
            val match = matchRule(trimmed, rule)
            if (match != null) {
                return DangerousCommand(
                    command = trimmed,
                    matchedRule = rule,
                    matchedPortion = match,
                )
            }
        }
        return null
    }

    private fun matchRule(command: String, rule: CommandRule): String? {
        return if (rule.isRegex) {
            matchRegex(command, rule.pattern)
        } else {
            matchGlob(command, rule.pattern)
        }
    }

    private fun matchRegex(command: String, pattern: String): String? {
        return try {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val result = regex.find(command)
            result?.value
        } catch (_: Exception) {
            null
        }
    }

    private fun matchGlob(command: String, pattern: String): String? {
        val regex = globToRegex(pattern)
        return try {
            val compiled = Regex(regex, RegexOption.IGNORE_CASE)
            if (compiled.containsMatchIn(command)) pattern else null
        } catch (_: Exception) {
            null
        }
    }

    fun globToRegex(pattern: String): String {
        return buildString {
            for (ch in pattern) {
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.', '(', ')', '[', ']', '{', '}', '\\', '^', '$', '|', '+' -> {
                        append("\\")
                        append(ch)
                    }
                    else -> append(ch)
                }
            }
        }
    }
}
