/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.safer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaferAlternativeAdvisorTest {

    private val advisor = SaferAlternativeAdvisor()

    private fun ids(command: String) = advisor.suggest(command).map { it.id }

    @Test
    fun `blank and safe commands yield nothing`() {
        assertTrue(advisor.suggest("").isEmpty())
        assertTrue(advisor.suggest("   ").isEmpty())
        assertTrue(advisor.suggest("ls -la").isEmpty())
        assertTrue(advisor.suggest("git status").isEmpty())
    }

    @Test
    fun `rm -rf suggests interactive, list-first and trash`() {
        val ids = ids("rm -rf build/")
        assertTrue("safer.rm-interactive" in ids)
        assertTrue("safer.rm-list-first" in ids)
        assertTrue("safer.rm-trash" in ids)
    }

    @Test
    fun `rm alternatives carry the original target`() {
        val alts = advisor.suggest("rm -rf build/ dist/")
        val interactive = alts.first { it.id == "safer.rm-interactive" }
        assertEquals("rm -rI build/ dist/", interactive.command)
    }

    @Test
    fun `plain rm single file is not flagged`() {
        assertTrue(advisor.suggest("rm notes.txt").isEmpty())
    }

    @Test
    fun `sudo prefix is unwrapped`() {
        assertTrue("safer.rm-interactive" in ids("sudo rm -rf /var/log/old"))
    }

    @Test
    fun `env assignment prefix is unwrapped`() {
        assertTrue("safer.git-force-with-lease" in ids("GIT_TRACE=1 git push --force origin main"))
    }

    @Test
    fun `git push --force suggests force-with-lease with rewritten command`() {
        val alts = advisor.suggest("git push --force origin main")
        val alt = alts.single { it.id == "safer.git-force-with-lease" }
        assertEquals("git push --force-with-lease origin main", alt.command)
    }

    @Test
    fun `git push -f short flag is rewritten too`() {
        val alt = advisor.suggest("git push -f origin main").single { it.id == "safer.git-force-with-lease" }
        assertEquals("git push --force-with-lease origin main", alt.command)
    }

    @Test
    fun `git push --force-with-lease is already safe`() {
        assertTrue(advisor.suggest("git push --force-with-lease origin main").isEmpty())
    }

    @Test
    fun `git clean -fd suggests dry run and stash`() {
        val ids = ids("git clean -fd")
        assertTrue("safer.git-clean-dry-run" in ids)
        assertTrue("safer.git-stash-untracked" in ids)
    }

    @Test
    fun `git clean with dry-run already is not flagged`() {
        assertTrue(advisor.suggest("git clean -fdn").isEmpty())
    }

    @Test
    fun `git reset --hard suggests stash first and --keep`() {
        val alts = advisor.suggest("git reset --hard origin/main")
        assertEquals(
            "git stash && git reset --hard origin/main",
            alts.single { it.id == "safer.git-stash-before-reset" }.command,
        )
        assertEquals(
            "git reset --keep origin/main",
            alts.single { it.id == "safer.git-reset-keep" }.command,
        )
    }

    @Test
    fun `git branch -D suggests safe delete`() {
        val alt = advisor.suggest("git branch -D feature/x").single { it.id == "safer.git-branch-safe-delete" }
        assertEquals("git branch -d feature/x", alt.command)
    }

    @Test
    fun `kubectl delete suggests dry run and context pin`() {
        val ids = ids("kubectl delete deployment api")
        assertTrue("safer.kubectl-dry-run" in ids)
        assertTrue("safer.kubectl-pin-context" in ids)
    }

    @Test
    fun `kubectl delete with dry-run only suggests context pin`() {
        val ids = ids("kubectl delete deployment api --dry-run=client")
        assertFalse("safer.kubectl-dry-run" in ids)
        assertTrue("safer.kubectl-pin-context" in ids)
    }

    @Test
    fun `kubectl delete with explicit context only suggests dry run`() {
        val ids = ids("kubectl delete pod x --context=staging")
        assertTrue("safer.kubectl-dry-run" in ids)
        assertFalse("safer.kubectl-pin-context" in ids)
    }

    @Test
    fun `terraform destroy suggests plan-destroy`() {
        assertTrue("safer.terraform-plan-destroy" in ids("terraform destroy"))
    }

    @Test
    fun `terraform destroy -auto-approve also suggests dropping the flag`() {
        val alts = advisor.suggest("terraform destroy -auto-approve")
        assertEquals(
            "terraform destroy",
            alts.single { it.id == "safer.terraform-no-auto-approve" }.command,
        )
    }

    @Test
    fun `docker system prune -a suggests df and scoped prune`() {
        val alts = advisor.suggest("docker system prune -a -f")
        assertTrue(alts.any { it.id == "safer.docker-df-first" })
        assertEquals("docker system prune -f", alts.single { it.id == "safer.docker-prune-scoped" }.command)
    }

    @Test
    fun `docker volume rm suggests inspect`() {
        val alt = advisor.suggest("docker volume rm pgdata").single { it.id == "safer.docker-volume-inspect" }
        assertEquals("docker volume inspect pgdata", alt.command)
    }

    @Test
    fun `chmod 777 suggests least privilege`() {
        val alt = advisor.suggest("chmod -R 777 /var/www").single { it.id == "safer.chmod-least-privilege" }
        assertEquals("chmod -R 755 /var/www", alt.command)
    }

    @Test
    fun `chmod 644 is not flagged`() {
        assertTrue(advisor.suggest("chmod 644 file.txt").isEmpty())
    }

    @Test
    fun `dd to device suggests verifying target and progress`() {
        val ids = ids("dd if=ubuntu.iso of=/dev/sdb bs=4M")
        assertTrue("safer.dd-verify-target" in ids)
        assertTrue("safer.dd-progress" in ids)
    }

    @Test
    fun `dd to regular file is not flagged`() {
        assertTrue(advisor.suggest("dd if=/dev/zero of=test.img bs=1M count=10").isEmpty())
    }

    @Test
    fun `curl piped to shell suggests download-inspect-run`() {
        val alt = advisor.suggest("curl -fsSL https://example.com/install.sh | sh")
            .single { it.id == "safer.download-inspect-run" }
        assertTrue(alt.command.startsWith("curl -fsSL https://example.com/install.sh"))
        assertTrue(alt.command.contains("less install.sh"))
    }

    @Test
    fun `wget piped to sudo bash is flagged`() {
        assertTrue("safer.download-inspect-run" in ids("wget -qO- https://x.io/i.sh | sudo bash"))
    }

    @Test
    fun `drop database suggests backup with database name`() {
        val alt = advisor.suggest("""mysql -e "DROP DATABASE production;"""")
            .single { it.id == "safer.db-backup-first" }
        assertTrue(alt.command.contains("production_backup.sql"))
    }

    @Test
    fun `normalize strips sudo env time and nohup`() {
        assertEquals("rm -rf x", advisor.normalize("sudo rm -rf x"))
        assertEquals("rm -rf x", advisor.normalize("env FOO=1 rm -rf x"))
        assertEquals("rm -rf x", advisor.normalize("FOO=1 rm -rf x"))
        assertEquals("rm -rf x", advisor.normalize("time nohup rm -rf x"))
        assertEquals("ls", advisor.normalize("ls"))
    }

    @Test
    fun `DELETE without WHERE suggests SELECT preview and transaction`() {
        val ids = ids("DELETE FROM orders")
        assertTrue("safer.sql-delete-select-first" in ids)
        assertTrue("safer.sql-delete-transaction" in ids)
    }

    @Test
    fun `DELETE with WHERE yields no unscoped suggestion`() {
        assertFalse("safer.sql-delete-select-first" in ids("DELETE FROM orders WHERE id = 1"))
    }

    @Test
    fun `DELETE select-preview rewrites the verb`() {
        val alt = advisor.suggest("DELETE FROM orders").first { it.id == "safer.sql-delete-select-first" }
        assertEquals("SELECT * FROM orders", alt.command)
    }

    @Test
    fun `UPDATE without WHERE suggests transaction`() {
        assertTrue("safer.sql-update-transaction" in ids("UPDATE users SET active = 0"))
    }

    @Test
    fun `UPDATE with WHERE yields no unscoped suggestion`() {
        assertFalse("safer.sql-update-transaction" in ids("UPDATE users SET active = 0 WHERE id = 1"))
    }

    @Test
    fun `aws s3 rb suggests listing first`() {
        assertTrue("safer.aws-s3-list-first" in ids("aws s3 rb s3://my-bucket --force"))
    }

    @Test
    fun `aws ec2 terminate suggests stop instead`() {
        val alt = advisor.suggest("aws ec2 terminate-instances --instance-ids i-1")
            .first { it.id == "safer.aws-ec2-stop" }
        assertTrue(alt.command.contains("stop-instances"))
    }

    @Test
    fun `gcloud delete suggests describe first`() {
        val alt = advisor.suggest("gcloud compute instances delete vm --zone z")
            .first { it.id == "safer.gcloud-describe-first" }
        assertTrue(alt.command.contains("describe"))
    }

    @Test
    fun `az group delete suggests listing the group`() {
        assertTrue("safer.az-group-list" in ids("az group delete --name rg --yes"))
    }

    @Test
    fun `every alternative has non-blank fields`() {
        val commands = listOf(
            "rm -rf /tmp/x", "git push --force", "git clean -fd", "git reset --hard",
            "git branch -D x", "kubectl delete pod x", "terraform destroy -auto-approve",
            "docker system prune -a", "docker volume rm v", "chmod 777 f",
            "dd if=a of=/dev/sdb", "curl https://x | sh", "psql -c 'drop database d'",
            "DELETE FROM orders", "UPDATE users SET active = 0",
            "aws s3 rb s3://b", "aws ec2 terminate-instances --instance-ids i-1",
            "gcloud compute instances delete vm", "az group delete --name rg",
        )
        for (cmd in commands) {
            for (alt in advisor.suggest(cmd)) {
                assertTrue(alt.id.startsWith("safer."), "id of $alt")
                assertTrue(alt.title.isNotBlank(), "title of $alt")
                assertTrue(alt.command.isNotBlank(), "command of $alt")
                assertTrue(alt.rationale.isNotBlank(), "rationale of $alt")
            }
        }
    }
}
