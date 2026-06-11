/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.safer

/**
 * A concrete, copy-pasteable safer way to achieve what a dangerous command
 * was trying to do.
 *
 * @property id Stable identifier (e.g. `safer.git-force-with-lease`) for
 *   analytics and tests.
 * @property title One-line label shown in dialogs ("Use --force-with-lease").
 * @property command Ready-to-run replacement command. When the original
 *   command's arguments could be carried over they are; otherwise the
 *   placeholder form is shown.
 * @property rationale Why the alternative is safer — one sentence.
 */
data class SaferAlternative(
    val id: String,
    val title: String,
    val command: String,
    val rationale: String,
)

/**
 * Suggests safer equivalents for well-known dangerous terminal commands.
 *
 * The advisor is intentionally conservative: it only fires on patterns where
 * a strictly-safer, broadly-accepted alternative exists (force-with-lease,
 * dry runs, interactive deletes, backups before drops). It never rewrites
 * arbitrary commands.
 *
 * Pure Kotlin — no IntelliJ dependencies — so the dialog layer, the CLI and
 * unit tests all share the same logic.
 */
class SaferAlternativeAdvisor {

    /** Returns alternatives for [command], best match first. Empty when none apply. */
    fun suggest(command: String): List<SaferAlternative> {
        val cmd = command.trim()
        if (cmd.isEmpty()) return emptyList()
        val normalized = normalize(cmd)
        val out = ArrayList<SaferAlternative>()

        suggestRm(cmd, normalized, out)
        suggestGit(cmd, normalized, out)
        suggestKubectl(cmd, normalized, out)
        suggestTerraform(cmd, normalized, out)
        suggestDocker(cmd, normalized, out)
        suggestChmod(cmd, normalized, out)
        suggestDd(normalized, out)
        suggestPipeToShell(normalized, out)
        suggestDatabase(normalized, out)
        suggestSql(normalized, out)
        suggestCloud(normalized, out)
        return out
    }

    /** Strips leading `sudo`, `env VAR=…`, `time`, `nohup` wrappers. */
    internal fun normalize(command: String): String {
        var rest = command.trim()
        while (true) {
            val token = rest.substringBefore(' ')
            rest = when {
                token == "sudo" || token == "time" || token == "nohup" ->
                    rest.substringAfter(' ', "").trim()
                token == "env" || ENV_ASSIGNMENT.matches(token) ->
                    rest.substringAfter(' ', "").trim()
                else -> return rest
            }
            if (rest.isEmpty()) return rest
        }
    }

    private fun suggestRm(original: String, cmd: String, out: MutableList<SaferAlternative>) {
        if (!cmd.startsWith("rm ")) return
        val hasRecursive = RM_RECURSIVE.containsMatchIn(cmd)
        val hasForce = RM_FORCE.containsMatchIn(cmd)
        if (!hasRecursive && !hasForce) return

        val targets = cmd.split(WHITESPACE).drop(1).filterNot { it.startsWith("-") }
        val targetStr = targets.joinToString(" ").ifEmpty { "<path>" }
        out += SaferAlternative(
            id = "safer.rm-interactive",
            title = "Delete interactively (confirm each item)",
            command = "rm -rI $targetStr",
            rationale = "-I prompts once before removing more than three files, keeping bulk deletes intentional.",
        )
        out += SaferAlternative(
            id = "safer.rm-list-first",
            title = "List what would be deleted first",
            command = "find $targetStr -mindepth 0 | head -50",
            rationale = "Seeing the file list before deleting catches wrong paths and unexpanded variables.",
        )
        out += SaferAlternative(
            id = "safer.rm-trash",
            title = "Move to a trash folder instead",
            command = "mkdir -p ~/.Trash-cli && mv $targetStr ~/.Trash-cli/",
            rationale = "A move is reversible; rm -rf is not.",
        )
    }

    private fun suggestGit(original: String, cmd: String, out: MutableList<SaferAlternative>) {
        if (!cmd.startsWith("git ")) return
        when {
            GIT_PUSH_FORCE.containsMatchIn(cmd) && !cmd.contains("--force-with-lease") -> {
                out += SaferAlternative(
                    id = "safer.git-force-with-lease",
                    title = "Use --force-with-lease",
                    command = cmd.replace(GIT_PUSH_FORCE_FLAG, "--force-with-lease"),
                    rationale = "Refuses the push if someone else pushed in the meantime — plain --force silently overwrites their work.",
                )
            }
            GIT_CLEAN.containsMatchIn(cmd) && !GIT_CLEAN_DRY_RUN.containsMatchIn(cmd) -> {
                out += SaferAlternative(
                    id = "safer.git-clean-dry-run",
                    title = "Dry-run first",
                    command = cmd.replaceFirst("git clean", "git clean -n"),
                    rationale = "Shows which untracked files would be deleted before actually deleting them.",
                )
                out += SaferAlternative(
                    id = "safer.git-stash-untracked",
                    title = "Stash untracked files instead",
                    command = "git stash -u",
                    rationale = "Stashed files can be restored with git stash pop; cleaned files are gone.",
                )
            }
            GIT_RESET_HARD.containsMatchIn(cmd) -> {
                out += SaferAlternative(
                    id = "safer.git-stash-before-reset",
                    title = "Stash your changes first",
                    command = "git stash && ${cmd}",
                    rationale = "Keeps a recoverable copy of the work the hard reset would destroy.",
                )
                out += SaferAlternative(
                    id = "safer.git-reset-keep",
                    title = "Use --keep instead of --hard",
                    command = cmd.replace("--hard", "--keep"),
                    rationale = "--keep aborts instead of overwriting local changes to differing files.",
                )
            }
            GIT_BRANCH_FORCE_DELETE.containsMatchIn(cmd) -> {
                out += SaferAlternative(
                    id = "safer.git-branch-safe-delete",
                    title = "Use -d (safe delete)",
                    command = cmd.replace(Regex("""\s-D\b"""), " -d"),
                    rationale = "-d refuses to delete a branch that isn't fully merged; -D throws the commits away.",
                )
            }
        }
    }

    private fun suggestKubectl(original: String, cmd: String, out: MutableList<SaferAlternative>) {
        if (!KUBECTL_DELETE.containsMatchIn(cmd)) return
        if (!cmd.contains("--dry-run")) {
            out += SaferAlternative(
                id = "safer.kubectl-dry-run",
                title = "Preview the deletion",
                command = "$cmd --dry-run=client",
                rationale = "Lists exactly which resources would be deleted without touching the cluster.",
            )
        }
        if (!cmd.contains("--context")) {
            out += SaferAlternative(
                id = "safer.kubectl-pin-context",
                title = "Pin the context explicitly",
                command = "kubectl config current-context  # verify, then add --context=<ctx>",
                rationale = "Most cluster disasters are 'wrong context' disasters — make the target explicit.",
            )
        }
    }

    private fun suggestTerraform(original: String, cmd: String, out: MutableList<SaferAlternative>) {
        if (!TERRAFORM_DESTROY.containsMatchIn(cmd)) return
        out += SaferAlternative(
            id = "safer.terraform-plan-destroy",
            title = "Review the destroy plan first",
            command = "terraform plan -destroy -out=destroy.tfplan",
            rationale = "Shows every resource that will be destroyed; apply the reviewed plan file afterwards.",
        )
        if (cmd.contains("-auto-approve")) {
            out += SaferAlternative(
                id = "safer.terraform-no-auto-approve",
                title = "Drop -auto-approve",
                command = cmd.replace(Regex("""\s+-auto-approve\b"""), ""),
                rationale = "Keeps the interactive confirmation that lists the blast radius before destroying.",
            )
        }
    }

    private fun suggestDocker(original: String, cmd: String, out: MutableList<SaferAlternative>) {
        if (DOCKER_PRUNE_ALL.containsMatchIn(cmd)) {
            out += SaferAlternative(
                id = "safer.docker-df-first",
                title = "Check what's reclaimable first",
                command = "docker system df -v",
                rationale = "Shows images/volumes that would be removed; prune -a also deletes images you still use.",
            )
            out += SaferAlternative(
                id = "safer.docker-prune-scoped",
                title = "Prune without -a",
                command = cmd.replace(Regex("""\s+(-a|--all)\b"""), ""),
                rationale = "Removes only dangling resources instead of every unused image.",
            )
        }
        if (DOCKER_VOLUME_RM.containsMatchIn(cmd)) {
            out += SaferAlternative(
                id = "safer.docker-volume-inspect",
                title = "Inspect the volume first",
                command = "docker volume inspect ${cmd.substringAfterLast(' ')}",
                rationale = "Volume deletion destroys data permanently — verify the mountpoint and labels first.",
            )
        }
    }

    private fun suggestChmod(original: String, cmd: String, out: MutableList<SaferAlternative>) {
        if (!CHMOD_777.containsMatchIn(cmd)) return
        out += SaferAlternative(
            id = "safer.chmod-least-privilege",
            title = "Use least-privilege permissions",
            command = cmd.replace("777", "755").replace("666", "644"),
            rationale = "755/644 gives owners full control without making files world-writable (a common audit finding).",
        )
    }

    private fun suggestDd(cmd: String, out: MutableList<SaferAlternative>) {
        if (!DD_TO_DEVICE.containsMatchIn(cmd)) return
        out += SaferAlternative(
            id = "safer.dd-verify-target",
            title = "Verify the target device first",
            command = "lsblk -o NAME,SIZE,MODEL,MOUNTPOINT   # macOS: diskutil list",
            rationale = "dd to the wrong device erases a disk in seconds — confirm device name and size before writing.",
        )
        if (!cmd.contains("status=progress")) {
            out += SaferAlternative(
                id = "safer.dd-progress",
                title = "Add progress reporting",
                command = "$cmd status=progress",
                rationale = "Progress output makes it obvious immediately if dd is writing more than expected.",
            )
        }
    }

    private fun suggestPipeToShell(cmd: String, out: MutableList<SaferAlternative>) {
        val m = PIPE_TO_SHELL.find(cmd) ?: return
        val fetcher = m.groupValues[1]
        out += SaferAlternative(
            id = "safer.download-inspect-run",
            title = "Download, inspect, then run",
            command = "$fetcher -o install.sh && less install.sh && sh install.sh",
            rationale = "Piping a remote script straight into a shell executes code you have never seen — including a compromised or truncated download.",
        )
    }

    private fun suggestDatabase(cmd: String, out: MutableList<SaferAlternative>) {
        val m = DROP_DATABASE.find(cmd) ?: return
        val db = m.groupValues[2].trim('`', '"', '\'', ';').ifEmpty { "<db>" }
        out += SaferAlternative(
            id = "safer.db-backup-first",
            title = "Back up before dropping",
            command = "pg_dump $db > ${db}_backup.sql   # MySQL: mysqldump $db > ${db}_backup.sql",
            rationale = "A dump turns an irreversible DROP into a recoverable operation.",
        )
    }

    private fun suggestSql(cmd: String, out: MutableList<SaferAlternative>) {
        if (SQL_DELETE_NO_WHERE.containsMatchIn(cmd)) {
            out += SaferAlternative(
                id = "safer.sql-delete-select-first",
                title = "Preview the rows with SELECT first",
                command = cmd.replaceFirst(Regex("""(?i)delete\s+from"""), "SELECT * FROM"),
                rationale = "Run the SELECT to see exactly which rows match before deleting — then add a WHERE clause.",
            )
            out += SaferAlternative(
                id = "safer.sql-delete-transaction",
                title = "Wrap it in a transaction you can roll back",
                command = "BEGIN; ${cmd.trimEnd(';')}; -- inspect row count, then COMMIT or ROLLBACK;",
                rationale = "A transaction lets you check the affected row count and ROLLBACK if it is not what you expected.",
            )
        }
        if (SQL_UPDATE_NO_WHERE.containsMatchIn(cmd)) {
            out += SaferAlternative(
                id = "safer.sql-update-transaction",
                title = "Wrap it in a transaction you can roll back",
                command = "BEGIN; ${cmd.trimEnd(';')}; -- inspect row count, then COMMIT or ROLLBACK;",
                rationale = "Without a WHERE clause this rewrites every row — a transaction lets you verify the count and ROLLBACK.",
            )
        }
    }

    private fun suggestCloud(cmd: String, out: MutableList<SaferAlternative>) {
        when {
            AWS_S3_RB.containsMatchIn(cmd) -> out += SaferAlternative(
                id = "safer.aws-s3-list-first",
                title = "List the bucket contents first",
                command = cmd.replaceFirst(Regex("""(?i)s3\s+rb"""), "s3 ls"),
                rationale = "Confirm the bucket name and that it holds nothing you need before removing it.",
            )
            AWS_EC2_TERMINATE.containsMatchIn(cmd) -> out += SaferAlternative(
                id = "safer.aws-ec2-stop",
                title = "Stop the instance instead of terminating",
                command = cmd.replaceFirst("terminate-instances", "stop-instances"),
                rationale = "Stop is reversible and keeps the EBS volume; terminate destroys the instance and ephemeral storage.",
            )
            AWS_DELETE_GENERIC.containsMatchIn(cmd) -> out += SaferAlternative(
                id = "safer.aws-verify-identity",
                title = "Confirm the account and region first",
                command = "aws sts get-caller-identity && echo \"region: ${'$'}{AWS_REGION:-${'$'}(aws configure get region)}\"",
                rationale = "Most cloud disasters are 'wrong account/region' disasters — verify both before a destructive call.",
            )
            GCLOUD_DELETE.containsMatchIn(cmd) && !cmd.contains("--verbosity") -> out += SaferAlternative(
                id = "safer.gcloud-describe-first",
                title = "Describe the resource before deleting",
                command = cmd.replaceFirst(Regex("""\bdelete\b"""), "describe"),
                rationale = "Verify you are pointed at the right project and resource — gcloud uses your active config by default.",
            )
            AZ_GROUP_DELETE.containsMatchIn(cmd) -> out += SaferAlternative(
                id = "safer.az-group-list",
                title = "List what the group contains first",
                command = cmd.replaceFirst(Regex("""(?i)group\s+delete"""), "resource list --resource-group")
                    .replace(Regex("""\s+--yes\b|\s+-y\b"""), ""),
                rationale = "Deleting a resource group destroys every resource inside it — confirm the inventory first.",
            )
        }
    }

    private companion object {
        val WHITESPACE = Regex("""\s+""")
        val ENV_ASSIGNMENT = Regex("""^[A-Za-z_][A-Za-z0-9_]*=.*""")
        val RM_RECURSIVE = Regex("""^rm\s+(-[a-zA-Z]*[rR][a-zA-Z]*\b|.*\s--recursive\b)""")
        val RM_FORCE = Regex("""^rm\s+(-[a-zA-Z]*f[a-zA-Z]*\b|.*\s--force\b)""")
        val GIT_PUSH_FORCE = Regex("""^git\s+push\b.*(\s--force\b|\s-f\b)""")
        val GIT_PUSH_FORCE_FLAG = Regex("""--force\b|(?<=\s)-f\b""")
        val GIT_CLEAN = Regex("""^git\s+clean\b.*-[a-zA-Z]*[fd]""")
        val GIT_CLEAN_DRY_RUN = Regex("""\s(-[a-zA-Z]*n[a-zA-Z]*|--dry-run)\b""")
        val GIT_RESET_HARD = Regex("""^git\s+reset\s+--hard\b""")
        val GIT_BRANCH_FORCE_DELETE = Regex("""^git\s+branch\b.*\s-D\b""")
        val KUBECTL_DELETE = Regex("""^kubectl\s+delete\b""")
        val TERRAFORM_DESTROY = Regex("""^terraform\s+destroy\b""")
        val DOCKER_PRUNE_ALL = Regex("""^docker\s+system\s+prune\b.*(\s-a\b|\s--all\b)""")
        val DOCKER_VOLUME_RM = Regex("""^docker\s+volume\s+rm\b""")
        val CHMOD_777 = Regex("""^chmod\s+(-[a-zA-Z]+\s+)*(777|666)\b""")
        val DD_TO_DEVICE = Regex("""^dd\b.*\bof=/dev/""")
        val PIPE_TO_SHELL = Regex("""^((?:curl|wget)\s+\S[^|]*)\|\s*(?:sudo\s+)?(?:ba|z)?sh\b""")
        val DROP_DATABASE = Regex("""\bdrop\s+(database|schema)\s+(?:if\s+exists\s+)?(\S+)""", RegexOption.IGNORE_CASE)
        val SQL_DELETE_NO_WHERE = Regex("""\bdelete\s+from\s+[`"\[\]\w.]+\s*;?\s*$""", RegexOption.IGNORE_CASE)
        val SQL_UPDATE_NO_WHERE = Regex("""\bupdate\s+[`"\[\]\w.]+\s+set\b(?:(?!\bwhere\b).)*$""", RegexOption.IGNORE_CASE)
        val AWS_S3_RB = Regex("""^aws\s+s3\s+rb\b""", RegexOption.IGNORE_CASE)
        val AWS_EC2_TERMINATE = Regex("""^aws\s+ec2\s+terminate-instances\b""", RegexOption.IGNORE_CASE)
        val AWS_DELETE_GENERIC = Regex("""^aws\s+\w[\w-]*\s+(delete|terminate)[\w-]*\b""", RegexOption.IGNORE_CASE)
        val GCLOUD_DELETE = Regex("""^gcloud\s+[\w\s-]*?\bdelete\b""", RegexOption.IGNORE_CASE)
        val AZ_GROUP_DELETE = Regex("""^az\s+group\s+delete\b""", RegexOption.IGNORE_CASE)
    }
}
