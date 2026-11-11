/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.explain

/**
 * One decoded flag of an explained command.
 *
 * @property flag The flag as written (`-rf`, `--force`).
 * @property meaning Plain-English meaning.
 * @property dangerous Whether this flag specifically contributes to the
 *   command's destructive potential (rendered highlighted in dialogs).
 */
data class ExplainedFlag(
    val flag: String,
    val meaning: String,
    val dangerous: Boolean,
)

/**
 * Structured explanation of a command line.
 *
 * @property program Resolved program name (`rm`, `git`, …) after unwrapping
 *   `sudo`/`env` prefixes.
 * @property subcommand Subcommand for multi-tool CLIs (`push` for git), or `null`.
 * @property sudo Whether the command runs with elevated privileges.
 * @property flags Decoded flags in the order they appear.
 * @property targets Non-flag arguments (paths, branches, resources).
 * @property summary One-sentence plain-English description of the effect.
 */
data class CommandExplanation(
    val program: String,
    val subcommand: String?,
    val sudo: Boolean,
    val flags: List<ExplainedFlag>,
    val targets: List<String>,
    val summary: String,
) {
    val hasDangerousFlags: Boolean get() = flags.any { it.dangerous }
}

/**
 * Decodes well-known dangerous commands into a human-readable explanation —
 * "what will this actually do?" — shown in the confirmation dialog so users
 * approve commands with understanding instead of alarm fatigue.
 *
 * Supported programs: `rm`, `git` (push/clean/reset/checkout/branch),
 * `kubectl` (delete/drain/scale), `docker` (rm/rmi/system prune/volume rm),
 * `terraform` (destroy/apply), `chmod`, `chown`, `dd`, `find` (-delete/-exec),
 * `mkfs*`, `shutdown`, `reboot`, `kill`/`pkill`.
 *
 * Returns `null` for programs it does not understand — callers must treat
 * that as "no explanation available", never as "safe".
 *
 * Pure Kotlin, deterministic, no IntelliJ dependencies.
 */
class CommandExplainer {

    /** Explains [command] or returns `null` when the program is not supported. */
    fun explain(command: String): CommandExplanation? {
        val tokens = tokenize(command.trim())
        if (tokens.isEmpty()) return null

        var sudo = false
        var idx = 0
        while (idx < tokens.size) {
            val t = tokens[idx]
            when {
                t == "sudo" -> { sudo = true; idx++ }
                t == "env" || t == "time" || t == "nohup" || ENV_ASSIGNMENT.matches(t) -> idx++
                else -> break
            }
        }
        if (idx >= tokens.size) return null
        val program = tokens[idx].substringAfterLast('/')
        val rest = tokens.drop(idx + 1)

        return when {
            program == "rm" -> explainRm(program, sudo, rest)
            program == "git" -> explainGit(program, sudo, rest)
            program == "kubectl" -> explainKubectl(program, sudo, rest)
            program == "docker" -> explainDocker(program, sudo, rest)
            program == "terraform" -> explainTerraform(program, sudo, rest)
            program == "chmod" || program == "chown" -> explainChmodChown(program, sudo, rest)
            program == "dd" -> explainDd(program, sudo, rest)
            program == "find" -> explainFind(program, sudo, rest)
            program.startsWith("mkfs") -> explainMkfs(program, sudo, rest)
            program == "shutdown" || program == "reboot" || program == "halt" -> explainShutdown(program, sudo, rest)
            program == "kill" || program == "pkill" || program == "killall" -> explainKill(program, sudo, rest)
            else -> null
        }
    }

    // ── Per-program explainers ───────────────────────────────────────────

    private fun explainRm(program: String, sudo: Boolean, args: List<String>) =
        flagsAndTargets(args, RM_FLAGS).let { (flags, targets) ->
            val recursive = flags.any { it.flag.contains('r', true) || it.flag == "--recursive" }
            val force = flags.any { it.flag.contains('f') || it.flag == "--force" }
            val what = describeTargets(targets, "file")
            val summary = buildString {
                append("Permanently deletes ").append(what)
                if (recursive) append(" including every file and subdirectory inside")
                if (force) append(", without asking for confirmation and ignoring missing files")
                if (sudo) append(", with root privileges (can remove system files)")
                append(". There is no undo.")
            }
            CommandExplanation(program, null, sudo, flags, targets, summary)
        }

    private fun explainGit(program: String, sudo: Boolean, args: List<String>): CommandExplanation? {
        val sub = args.firstOrNull() ?: return null
        val rest = args.drop(1)
        return when (sub) {
            "push" -> flagsAndTargets(rest, GIT_PUSH_FLAGS).let { (flags, targets) ->
                val force = flags.any { it.dangerous }
                val summary = if (force)
                    "Overwrites the remote branch history with your local history — " +
                        "commits pushed by others since your last fetch are discarded."
                else "Uploads local commits to the remote repository."
                CommandExplanation(program, sub, sudo, flags, targets, summary)
            }
            "clean" -> flagsAndTargets(rest, GIT_CLEAN_FLAGS).let { (flags, targets) ->
                CommandExplanation(
                    program, sub, sudo, flags, targets,
                    "Deletes untracked files from the working tree — files never committed are unrecoverable afterwards.",
                )
            }
            "reset" -> flagsAndTargets(rest, GIT_RESET_FLAGS).let { (flags, targets) ->
                val hard = flags.any { it.flag == "--hard" }
                CommandExplanation(
                    program, sub, sudo, flags, targets,
                    if (hard)
                        "Moves the branch pointer and overwrites the index AND working tree — all uncommitted changes are lost."
                    else "Moves the branch pointer; working tree handling depends on the mode flag.",
                )
            }
            "checkout" -> flagsAndTargets(rest, emptyMap()).let { (flags, targets) ->
                val discardAll = targets == listOf(".") || targets.contains("--") && targets.contains(".")
                CommandExplanation(
                    program, sub, sudo, flags, targets,
                    if (discardAll) "Discards every uncommitted modification in the current directory tree."
                    else "Switches branches or restores files from the index.",
                )
            }
            "branch" -> flagsAndTargets(rest, GIT_BRANCH_FLAGS).let { (flags, targets) ->
                CommandExplanation(
                    program, sub, sudo, flags, targets,
                    if (flags.any { it.flag == "-D" })
                        "Force-deletes ${describeTargets(targets, "branch")} even if its commits are not merged " +
                            "anywhere — those commits become unreachable."
                    else "Manages branches.",
                )
            }
            else -> null
        }
    }

    private fun explainKubectl(program: String, sudo: Boolean, args: List<String>): CommandExplanation? {
        val sub = args.firstOrNull() ?: return null
        val rest = args.drop(1)
        return when (sub) {
            "delete" -> flagsAndTargets(rest, KUBECTL_DELETE_FLAGS).let { (flags, targets) ->
                val all = flags.any { it.flag == "--all" }
                val ns = rest.windowed(2).firstOrNull { it[0] == "-n" || it[0] == "--namespace" }?.get(1)
                    ?: rest.firstOrNull { it.startsWith("--namespace=") }?.substringAfter('=')
                val summary = buildString {
                    append("Deletes ")
                    append(if (all) "ALL resources of the given type" else describeTargets(targets, "resource"))
                    append(if (ns != null) " in namespace '$ns'" else " in the CURRENT kubectl context/namespace")
                    append(" — pods are terminated and resource definitions removed from the cluster.")
                }
                CommandExplanation(program, sub, sudo, flags, targets, summary)
            }
            "drain" -> flagsAndTargets(rest, KUBECTL_DRAIN_FLAGS).let { (flags, targets) ->
                CommandExplanation(
                    program, sub, sudo, flags, targets,
                    "Evicts every pod from ${describeTargets(targets, "node")} and marks it unschedulable — workloads are disrupted.",
                )
            }
            "scale" -> flagsAndTargets(rest, emptyMap()).let { (flags, targets) ->
                val replicas = rest.firstOrNull { it.startsWith("--replicas=") }?.substringAfter('=')
                CommandExplanation(
                    program, sub, sudo, flags, targets,
                    if (replicas == "0") "Scales the workload to ZERO replicas — the service stops serving traffic."
                    else "Changes the number of replicas to ${replicas ?: "?"}.",
                )
            }
            else -> null
        }
    }

    private fun explainDocker(program: String, sudo: Boolean, args: List<String>): CommandExplanation? {
        val joined = args.joinToString(" ")
        return when {
            joined.startsWith("system prune") -> flagsAndTargets(args.drop(2), DOCKER_PRUNE_FLAGS).let { (flags, targets) ->
                val all = flags.any { it.flag == "-a" || it.flag == "--all" }
                val volumes = flags.any { it.flag == "--volumes" }
                CommandExplanation(
                    program, "system prune", sudo, flags, targets,
                    buildString {
                        append("Removes stopped containers, unused networks and dangling images")
                        if (all) append(", plus EVERY image not used by a running container")
                        if (volumes) append(", plus unused volumes (data loss)")
                        append(".")
                    },
                )
            }
            joined.startsWith("volume rm") -> flagsAndTargets(args.drop(2), emptyMap()).let { (flags, targets) ->
                CommandExplanation(
                    program, "volume rm", sudo, flags, targets,
                    "Deletes ${describeTargets(targets, "volume")} and all data stored in it — irreversible.",
                )
            }
            args.firstOrNull() == "rmi" -> flagsAndTargets(args.drop(1), DOCKER_FORCE_FLAGS).let { (flags, targets) ->
                CommandExplanation(
                    program, "rmi", sudo, flags, targets,
                    "Removes ${describeTargets(targets, "image")} from the local image store.",
                )
            }
            args.firstOrNull() == "rm" -> flagsAndTargets(args.drop(1), DOCKER_FORCE_FLAGS).let { (flags, targets) ->
                CommandExplanation(
                    program, "rm", sudo, flags, targets,
                    "Removes ${describeTargets(targets, "container")}" +
                        (if (flags.any { it.dangerous }) ", killing it first if it is still running" else "") + ".",
                )
            }
            else -> null
        }
    }

    private fun explainTerraform(program: String, sudo: Boolean, args: List<String>): CommandExplanation? {
        val sub = args.firstOrNull() ?: return null
        if (sub != "destroy" && sub != "apply") return null
        val (flags, targets) = flagsAndTargets(args.drop(1), TERRAFORM_FLAGS)
        val auto = flags.any { it.flag == "-auto-approve" }
        val summary = when (sub) {
            "destroy" -> "Destroys EVERY resource tracked in the Terraform state" +
                (if (auto) " without any confirmation prompt" else " after a confirmation prompt") + "."
            else -> "Applies infrastructure changes" + (if (auto) " without showing or confirming the plan first" else "") + "."
        }
        return CommandExplanation(program, sub, sudo, flags, targets, summary)
    }

    private fun explainChmodChown(program: String, sudo: Boolean, args: List<String>): CommandExplanation {
        val (flags, targets) = flagsAndTargets(args, CHMOD_FLAGS)
        val mode = targets.firstOrNull() ?: ""
        val paths = targets.drop(1)
        val recursive = flags.any { it.flag.lowercase().contains("r") }
        val summary = buildString {
            if (program == "chmod") {
                append("Changes permissions of ").append(describeTargets(paths, "path"))
                if (mode == "777" || mode == "666") {
                    append(" to $mode — EVERY user on the system can ")
                    append(if (mode == "777") "read, write and execute" else "read and write")
                    append(" these files")
                } else if (mode.isNotEmpty()) append(" to $mode")
            } else {
                append("Changes the owner of ").append(describeTargets(paths, "path")).append(" to $mode")
            }
            if (recursive) append(", recursively for everything inside")
            append(".")
        }
        return CommandExplanation(program, null, sudo, flags, listOf(mode) + paths, summary)
    }

    private fun explainDd(program: String, sudo: Boolean, args: List<String>): CommandExplanation {
        val flags = ArrayList<ExplainedFlag>()
        var input: String? = null
        var output: String? = null
        for (a in args) {
            when {
                a.startsWith("if=") -> {
                    input = a.substringAfter('=')
                    flags += ExplainedFlag(a, "read from ${a.substringAfter('=')}", false)
                }
                a.startsWith("of=") -> {
                    output = a.substringAfter('=')
                    flags += ExplainedFlag(a, "write to ${a.substringAfter('=')}", output.startsWith("/dev/"))
                }
                a.startsWith("bs=") -> flags += ExplainedFlag(a, "block size ${a.substringAfter('=')}", false)
            }
        }
        val summary = buildString {
            append("Copies raw bytes from ").append(input ?: "stdin").append(" to ").append(output ?: "stdout")
            if (output?.startsWith("/dev/") == true) {
                append(" — writing directly to a device OVERWRITES it, destroying the filesystem and all data on it")
            }
            append(".")
        }
        return CommandExplanation(program, null, sudo, flags, listOfNotNull(input, output), summary)
    }

    private fun explainFind(program: String, sudo: Boolean, args: List<String>): CommandExplanation? {
        val hasDelete = args.contains("-delete")
        val execRm = args.windowed(2).any { it[0] == "-exec" && it[1].substringAfterLast('/') == "rm" }
        if (!hasDelete && !execRm) return null
        val root = args.firstOrNull { !it.startsWith("-") } ?: "."
        val flags = buildList {
            if (hasDelete) add(ExplainedFlag("-delete", "delete every matched file", true))
            if (execRm) add(ExplainedFlag("-exec rm", "run rm on every matched file", true))
        }
        return CommandExplanation(
            program, null, sudo, flags, listOf(root),
            "Walks '$root' recursively and DELETES every file matching the criteria — a too-broad pattern deletes far more than intended.",
        )
    }

    private fun explainMkfs(program: String, sudo: Boolean, args: List<String>): CommandExplanation {
        val target = args.lastOrNull { !it.startsWith("-") } ?: "<device>"
        return CommandExplanation(
            program, null, sudo, emptyList(), listOf(target),
            "Creates a new filesystem on $target — ALL existing data on the device is destroyed.",
        )
    }

    private fun explainShutdown(program: String, sudo: Boolean, args: List<String>): CommandExplanation {
        val now = args.contains("now") || args.contains("-h") || program != "shutdown"
        return CommandExplanation(
            program, null, sudo, emptyList(), args,
            "Stops the operating system${if (now) " immediately" else ""} — every running process and unsaved work is terminated.",
        )
    }

    private fun explainKill(program: String, sudo: Boolean, args: List<String>): CommandExplanation {
        val nine = args.any { it == "-9" || it.equals("-KILL", true) || it == "-s SIGKILL" }
        val (flags, targets) = flagsAndTargets(args, mapOf("-9" to ("send SIGKILL — process cannot clean up or save state" to true)))
        return CommandExplanation(
            program, null, sudo, flags, targets,
            if (nine)
                "Force-kills ${describeTargets(targets, "process")} with SIGKILL — no graceful shutdown, " +
                    "open files and transactions are abandoned."
            else "Sends a termination signal to ${describeTargets(targets, "process")}.",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Splits a command line into tokens, honouring single and double quotes
     * (quotes are stripped from the token value).
     */
    internal fun tokenize(command: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quote: Char? = null
        for (c in command) {
            when {
                quote != null -> if (c == quote) quote = null else sb.append(c)
                c == '\'' || c == '"' -> quote = c
                c.isWhitespace() -> { if (sb.isNotEmpty()) { out += sb.toString(); sb.clear() } }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    private fun flagsAndTargets(
        args: List<String>,
        dictionary: Map<String, Pair<String, Boolean>>,
    ): Pair<List<ExplainedFlag>, List<String>> {
        val flags = ArrayList<ExplainedFlag>()
        val targets = ArrayList<String>()
        for (a in args) {
            if (!a.startsWith("-")) { targets += a; continue }
            val known = dictionary[a]
            if (known != null) {
                flags += ExplainedFlag(a, known.first, known.second)
                continue
            }
            // Bundled short flags like -rf: explain each letter found in the dictionary.
            if (a.length > 1 && a[1] != '-') {
                val letters = a.drop(1)
                val meanings = letters.mapNotNull { ch -> dictionary["-$ch"]?.let { Triple(ch, it.first, it.second) } }
                if (meanings.isNotEmpty()) {
                    flags += ExplainedFlag(
                        a,
                        meanings.joinToString("; ") { "${it.first}: ${it.second}" },
                        meanings.any { it.third },
                    )
                    continue
                }
            }
            flags += ExplainedFlag(a, "unrecognized flag", false)
        }
        return flags to targets
    }

    private fun describeTargets(targets: List<String>, noun: String): String = when {
        targets.isEmpty() -> "the given $noun"
        targets.size == 1 -> "'${targets[0]}'"
        else -> "${targets.size} ${noun}s (${targets.joinToString(", ") { "'$it'" }})"
    }

    private companion object {
        val ENV_ASSIGNMENT = Regex("""^[A-Za-z_][A-Za-z0-9_]*=.*""")

        // flag -> (meaning, dangerous)
        val RM_FLAGS = mapOf(
            "-r" to ("recursive — descend into directories" to true),
            "-R" to ("recursive — descend into directories" to true),
            "--recursive" to ("recursive — descend into directories" to true),
            "-f" to ("force — never prompt, ignore missing files" to true),
            "--force" to ("force — never prompt, ignore missing files" to true),
            "-i" to ("prompt before every removal" to false),
            "-I" to ("prompt once before removing many files" to false),
            "-v" to ("verbose output" to false),
            "--no-preserve-root" to ("allow operating on '/' itself" to true),
        )
        val GIT_PUSH_FLAGS = mapOf(
            "--force" to ("overwrite remote history unconditionally" to true),
            "-f" to ("overwrite remote history unconditionally" to true),
            "--force-with-lease" to ("overwrite remote history only if nobody pushed meanwhile" to false),
            "--all" to ("push all branches" to true),
            "--tags" to ("push all tags" to false),
        )
        val GIT_CLEAN_FLAGS = mapOf(
            "-f" to ("force — actually delete (required by git)" to true),
            "-d" to ("also remove untracked directories" to true),
            "-x" to ("also remove ignored files (build outputs, .env files…)" to true),
            "-n" to ("dry run — only show what would be deleted" to false),
            "--dry-run" to ("dry run — only show what would be deleted" to false),
        )
        val GIT_RESET_FLAGS = mapOf(
            "--hard" to ("overwrite index AND working tree — uncommitted changes are lost" to true),
            "--soft" to ("move branch pointer only, keep changes staged" to false),
            "--mixed" to ("reset index, keep working tree" to false),
            "--keep" to ("abort instead of overwriting local changes" to false),
        )
        val GIT_BRANCH_FLAGS = mapOf(
            "-D" to ("force delete even if unmerged — commits become unreachable" to true),
            "-d" to ("delete only if fully merged" to false),
        )
        val KUBECTL_DELETE_FLAGS = mapOf(
            "--all" to ("delete ALL resources of the type, not just named ones" to true),
            "--force" to ("skip graceful termination" to true),
            "--grace-period=0" to ("kill pods immediately" to true),
            "--cascade=orphan" to ("leave dependents orphaned" to false),
            "--dry-run=client" to ("preview only — nothing is deleted" to false),
        )
        val KUBECTL_DRAIN_FLAGS = mapOf(
            "--force" to ("evict even unmanaged pods" to true),
            "--ignore-daemonsets" to ("proceed despite DaemonSet pods" to false),
            "--delete-emptydir-data" to ("delete pods using emptyDir volumes — their local data is lost" to true),
        )
        val DOCKER_PRUNE_FLAGS = mapOf(
            "-a" to ("remove ALL unused images, not only dangling ones" to true),
            "--all" to ("remove ALL unused images, not only dangling ones" to true),
            "--volumes" to ("also remove unused volumes — stored data is lost" to true),
            "-f" to ("do not prompt for confirmation" to true),
            "--force" to ("do not prompt for confirmation" to true),
        )
        val DOCKER_FORCE_FLAGS = mapOf(
            "-f" to ("force — kill running container / remove in-use image" to true),
            "--force" to ("force — kill running container / remove in-use image" to true),
        )
        val TERRAFORM_FLAGS = mapOf(
            "-auto-approve" to ("skip the interactive confirmation of the plan" to true),
            "-refresh-only" to ("only refresh state" to false),
        )
        val CHMOD_FLAGS = mapOf(
            "-R" to ("recursive — apply to everything inside" to true),
            "-f" to ("suppress error messages" to false),
        )
    }
}
