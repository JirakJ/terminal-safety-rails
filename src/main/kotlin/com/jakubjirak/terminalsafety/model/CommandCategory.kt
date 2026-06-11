package com.jakubjirak.terminalsafety.model

/**
 * Category of a dangerous command rule.
 */
enum class CommandCategory(val displayName: String) {
    FILESYSTEM("File System"),
    KUBERNETES("Kubernetes"),
    TERRAFORM("Terraform"),
    DOCKER("Docker"),
    CLOUD("Cloud"),
    GIT("Git"),
    DATABASE("Database"),
    SYSTEM("System"),
    PERMISSIONS("Permissions"),
    CUSTOM("Custom"),
}
