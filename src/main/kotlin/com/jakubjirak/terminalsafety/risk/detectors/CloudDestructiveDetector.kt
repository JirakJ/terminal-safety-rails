/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags destructive cloud-provider CLI operations — the single most common
 * way teams turn a typo into an outage or an irreversible data-loss incident.
 *
 * Covers the three major clouds and a few popular adjacent tools:
 *
 *  * **AWS** — `s3 rb`, `s3 rm … --recursive`, `ec2 terminate-instances`,
 *    `rds delete-db-instance`, `dynamodb delete-table`,
 *    `cloudformation delete-stack`, `eks delete-cluster`.
 *  * **GCP** — `gcloud … delete`, `gsutil rm -r`.
 *  * **Azure** — `az group delete`, `az … delete`.
 *  * **Tooling** — `eksctl delete`, `doctl … delete`, `flyctl … destroy`.
 *
 * Each match yields a single signal; the [com.jakubjirak.terminalsafety.risk.CommandRiskScorer]
 * sums and clamps. Weights are tuned so that a bare destructive cloud command
 * lands in the HIGH band on its own (≥60) — enough for the interceptor to step
 * in proactively even when no explicit rule is configured. A `--force`/
 * `--yes`/`--no-prompts` style auto-approval bumps it into CRITICAL.
 *
 * Pure, deterministic, no IntelliJ dependencies — shared by the service layer,
 * the CLI and unit tests.
 */
class CloudDestructiveDetector : RiskSignalDetector {
    override val id: String = "cloud-destructive"

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun detect(command: String): List<RiskSignal> {
        val out = mutableListOf<RiskSignal>()

        // ── AWS ──────────────────────────────────────────────────────────
        AWS_S3_REMOVE_BUCKET.find(command)?.let { m ->
            out += signal("cloud.aws-s3-rb", "AWS S3 bucket deletion (`aws s3 rb`) — removes the bucket", 60, m.value)
        }
        AWS_S3_RECURSIVE_RM.find(command)?.let { m ->
            out += signal("cloud.aws-s3-rm-recursive", "Recursive AWS S3 object delete (`s3 rm --recursive`)", 55, m.value)
        }
        AWS_EC2_TERMINATE.find(command)?.let { m ->
            out += signal("cloud.aws-ec2-terminate", "AWS EC2 instance termination — instances are destroyed", 60, m.value)
        }
        AWS_RDS_DELETE.find(command)?.let { m ->
            out += signal("cloud.aws-rds-delete", "AWS RDS database deletion — a database instance is removed", 65, m.value)
        }
        AWS_DYNAMODB_DELETE.find(command)?.let { m ->
            out += signal("cloud.aws-dynamodb-delete", "AWS DynamoDB table deletion — all items are lost", 60, m.value)
        }
        AWS_CFN_DELETE_STACK.find(command)?.let { m ->
            out += signal(
                "cloud.aws-cfn-delete-stack",
                "AWS CloudFormation stack deletion — every managed resource is destroyed",
                60,
                m.value,
            )
        }
        AWS_EKS_DELETE_CLUSTER.find(command)?.let { m ->
            out += signal("cloud.aws-eks-delete-cluster", "AWS EKS cluster deletion", 60, m.value)
        }

        // ── GCP ──────────────────────────────────────────────────────────
        GCLOUD_DELETE.find(command)?.let { m ->
            out += signal("cloud.gcloud-delete", "GCP resource deletion (`gcloud … delete`)", 55, m.value)
        }
        GSUTIL_RECURSIVE_RM.find(command)?.let { m ->
            out += signal("cloud.gsutil-rm-recursive", "Recursive GCS object delete (`gsutil rm -r`)", 55, m.value)
        }

        // ── Azure ────────────────────────────────────────────────────────
        AZ_GROUP_DELETE.find(command)?.let { m ->
            out += signal("cloud.az-group-delete", "Azure resource-group deletion — every resource in the group is destroyed", 65, m.value)
        }
        if (out.none { it.id == "cloud.az-group-delete" }) {
            AZ_DELETE.find(command)?.let { m ->
                out += signal("cloud.az-delete", "Azure resource deletion (`az … delete`)", 55, m.value)
            }
        }

        // ── Adjacent tooling ────────────────────────────────────────────
        TOOLING_DELETE.find(command)?.let { m ->
            out += signal("cloud.tooling-delete", "Cloud tooling deletion (`${m.groupValues[1]} … ${m.groupValues[2]}`)", 55, m.value)
        }

        // ── Auto-approval escalation ─────────────────────────────────────
        if (out.isNotEmpty() && AUTO_APPROVE.containsMatchIn(command)) {
            out += signal(
                "cloud.auto-approve",
                "Auto-approval flag skips the provider's confirmation prompt",
                20,
                AUTO_APPROVE.find(command)?.value?.trim() ?: "--force",
            )
        }

        return out
    }

    private fun signal(id: String, description: String, weight: Int, matched: String) =
        RiskSignal(id = id, description = description, weight = weight, matched = matched.trim())

    private companion object {
        // Boundaries: \b on the leading verb keeps `myaws`/`gcloudx` from matching.
        val AWS_S3_REMOVE_BUCKET = Regex("""\baws\s+s3\s+rb\b[^|;&]*""", RegexOption.IGNORE_CASE)
        val AWS_S3_RECURSIVE_RM = Regex("""\baws\s+s3\s+rm\b[^|;&]*--recursive\b""", RegexOption.IGNORE_CASE)
        val AWS_EC2_TERMINATE = Regex("""\baws\s+ec2\s+terminate-instances\b[^|;&]*""", RegexOption.IGNORE_CASE)
        val AWS_RDS_DELETE = Regex("""\baws\s+rds\s+delete-db-(?:instance|cluster)\b[^|;&]*""", RegexOption.IGNORE_CASE)
        val AWS_DYNAMODB_DELETE = Regex("""\baws\s+dynamodb\s+delete-table\b[^|;&]*""", RegexOption.IGNORE_CASE)
        val AWS_CFN_DELETE_STACK = Regex("""\baws\s+cloudformation\s+delete-stack\b[^|;&]*""", RegexOption.IGNORE_CASE)
        val AWS_EKS_DELETE_CLUSTER = Regex("""\baws\s+eks\s+delete-cluster\b[^|;&]*""", RegexOption.IGNORE_CASE)

        val GCLOUD_DELETE = Regex("""\bgcloud\s+[\w-]+(?:\s+[\w-]+)*\s+delete\b[^|;&]*""", RegexOption.IGNORE_CASE)
        val GSUTIL_RECURSIVE_RM = Regex("""\bgsutil\s+(?:-m\s+)?rm\b[^|;&]*\s-[a-zA-Z]*r[a-zA-Z]*\b[^|;&]*""", RegexOption.IGNORE_CASE)

        val AZ_GROUP_DELETE = Regex("""\baz\s+group\s+delete\b[^|;&]*""", RegexOption.IGNORE_CASE)
        val AZ_DELETE = Regex("""\baz\s+[\w-]+(?:\s+[\w-]+)*\s+delete\b[^|;&]*""", RegexOption.IGNORE_CASE)

        val TOOLING_DELETE = Regex("""\b(eksctl|doctl)\s+[\w\s-]*?\b(delete)\b[^|;&]*""", RegexOption.IGNORE_CASE)

        val AUTO_APPROVE = Regex(
            """(?<!\S)(--force|--yes|-y|--no-prompts?|--quiet|--skip-final-snapshot)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}
