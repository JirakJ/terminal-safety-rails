/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.explain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandExplainerTest {

    private val explainer = CommandExplainer()

    @Test
    fun `unknown programs return null`() {
        assertNull(explainer.explain("ls -la"))
        assertNull(explainer.explain("mycustomtool --whatever"))
        assertNull(explainer.explain(""))
    }

    @Test
    fun `rm -rf is decoded with bundled flags and targets`() {
        val e = explainer.explain("rm -rf build/ dist/")!!
        assertEquals("rm", e.program)
        assertNull(e.subcommand)
        assertFalse(e.sudo)
        assertEquals(listOf("build/", "dist/"), e.targets)
        assertTrue(e.hasDangerousFlags)
        val rf = e.flags.single()
        assertEquals("-rf", rf.flag)
        assertTrue(rf.meaning.contains("recursive"))
        assertTrue(rf.meaning.contains("force"))
        assertTrue(e.summary.contains("Permanently deletes"))
        assertTrue(e.summary.contains("no undo"))
    }

    @Test
    fun `sudo rm mentions root privileges`() {
        val e = explainer.explain("sudo rm -rf /var/log")!!
        assertTrue(e.sudo)
        assertTrue(e.summary.contains("root privileges"))
    }

    @Test
    fun `rm -i is not dangerous`() {
        val e = explainer.explain("rm -i old.txt")!!
        assertFalse(e.hasDangerousFlags)
    }

    @Test
    fun `git push --force explains history overwrite`() {
        val e = explainer.explain("git push --force origin main")!!
        assertEquals("git", e.program)
        assertEquals("push", e.subcommand)
        assertTrue(e.hasDangerousFlags)
        assertTrue(e.summary.contains("Overwrites the remote branch history"))
    }

    @Test
    fun `git push without force is benign`() {
        val e = explainer.explain("git push origin main")!!
        assertFalse(e.hasDangerousFlags)
        assertTrue(e.summary.contains("Uploads local commits"))
    }

    @Test
    fun `git clean -fdx explains ignored files too`() {
        val e = explainer.explain("git clean -fdx")!!
        assertEquals("clean", e.subcommand)
        assertTrue(e.summary.contains("untracked files"))
        assertTrue(e.flags.single().meaning.contains("ignored files"))
    }

    @Test
    fun `git reset --hard warns about losing changes`() {
        val e = explainer.explain("git reset --hard HEAD~3")!!
        assertTrue(e.summary.contains("uncommitted changes are lost"))
        assertEquals(listOf("HEAD~3"), e.targets)
    }

    @Test
    fun `git checkout dot discards modifications`() {
        val e = explainer.explain("git checkout .")!!
        assertTrue(e.summary.contains("Discards every uncommitted modification"))
    }

    @Test
    fun `git branch -D explains unreachable commits`() {
        val e = explainer.explain("git branch -D feature/spike")!!
        assertTrue(e.summary.contains("unreachable"))
        assertTrue(e.summary.contains("'feature/spike'"))
    }

    @Test
    fun `unknown git subcommand returns null`() {
        assertNull(explainer.explain("git log --oneline"))
    }

    @Test
    fun `kubectl delete --all in explicit namespace`() {
        val e = explainer.explain("kubectl delete pods --all -n staging")!!
        assertEquals("kubectl", e.program)
        assertEquals("delete", e.subcommand)
        assertTrue(e.summary.contains("ALL resources"))
        assertTrue(e.summary.contains("'staging'"))
    }

    @Test
    fun `kubectl delete without namespace warns about current context`() {
        val e = explainer.explain("kubectl delete deployment api")!!
        assertTrue(e.summary.contains("CURRENT kubectl context"))
    }

    @Test
    fun `kubectl delete with --namespace= form is parsed`() {
        val e = explainer.explain("kubectl delete pod x --namespace=prod")!!
        assertTrue(e.summary.contains("'prod'"))
    }

    @Test
    fun `kubectl scale to zero is called out`() {
        val e = explainer.explain("kubectl scale deployment api --replicas=0")!!
        assertTrue(e.summary.contains("ZERO replicas"))
    }

    @Test
    fun `kubectl drain explains eviction`() {
        val e = explainer.explain("kubectl drain node-1 --force")!!
        assertTrue(e.summary.contains("Evicts every pod"))
        assertTrue(e.hasDangerousFlags)
    }

    @Test
    fun `docker system prune -a --volumes warns about data loss`() {
        val e = explainer.explain("docker system prune -a --volumes")!!
        assertEquals("system prune", e.subcommand)
        assertTrue(e.summary.contains("EVERY image"))
        assertTrue(e.summary.contains("data loss"))
    }

    @Test
    fun `docker volume rm is irreversible`() {
        val e = explainer.explain("docker volume rm pgdata")!!
        assertTrue(e.summary.contains("irreversible"))
        assertTrue(e.summary.contains("'pgdata'"))
    }

    @Test
    fun `docker rm -f mentions killing the container`() {
        val e = explainer.explain("docker rm -f web")!!
        assertTrue(e.summary.contains("killing it first"))
    }

    @Test
    fun `terraform destroy with auto-approve skips confirmation`() {
        val e = explainer.explain("terraform destroy -auto-approve")!!
        assertTrue(e.summary.contains("EVERY resource"))
        assertTrue(e.summary.contains("without any confirmation"))
    }

    @Test
    fun `terraform destroy without auto-approve keeps the prompt`() {
        val e = explainer.explain("terraform destroy")!!
        assertTrue(e.summary.contains("after a confirmation prompt"))
    }

    @Test
    fun `chmod 777 recursive warns about every user`() {
        val e = explainer.explain("chmod -R 777 /var/www")!!
        assertTrue(e.summary.contains("EVERY user"))
        assertTrue(e.summary.contains("recursively"))
    }

    @Test
    fun `chown explains ownership change`() {
        val e = explainer.explain("chown -R nobody /srv/app")!!
        assertTrue(e.summary.contains("Changes the owner"))
        assertTrue(e.summary.contains("nobody"))
    }

    @Test
    fun `dd to device warns about overwriting`() {
        val e = explainer.explain("dd if=ubuntu.iso of=/dev/disk2 bs=4M")!!
        assertTrue(e.summary.contains("OVERWRITES"))
        assertTrue(e.flags.any { it.flag.startsWith("of=") && it.dangerous })
        assertEquals(listOf("ubuntu.iso", "/dev/disk2"), e.targets)
    }

    @Test
    fun `dd to file is calm`() {
        val e = explainer.explain("dd if=/dev/urandom of=key.bin bs=32 count=1")!!
        assertFalse(e.summary.contains("OVERWRITES"))
    }

    @Test
    fun `find -delete is explained, plain find is not`() {
        val e = explainer.explain("find /tmp/cache -name '*.tmp' -delete")!!
        assertTrue(e.summary.contains("DELETES every file"))
        assertEquals(listOf("/tmp/cache"), e.targets)
        assertNull(explainer.explain("find . -name '*.kt'"))
    }

    @Test
    fun `find -exec rm is recognized`() {
        val e = explainer.explain("find . -mtime +30 -exec rm {} ;")!!
        assertTrue(e.flags.any { it.flag == "-exec rm" })
    }

    @Test
    fun `mkfs destroys the device`() {
        val e = explainer.explain("mkfs.ext4 /dev/sdb1")!!
        assertEquals("mkfs.ext4", e.program)
        assertTrue(e.summary.contains("ALL existing data"))
    }

    @Test
    fun `shutdown now is immediate`() {
        val e = explainer.explain("shutdown -h now")!!
        assertTrue(e.summary.contains("immediately"))
    }

    @Test
    fun `kill -9 explains SIGKILL`() {
        val e = explainer.explain("kill -9 1234")!!
        assertTrue(e.summary.contains("SIGKILL"))
        assertTrue(e.hasDangerousFlags)
    }

    @Test
    fun `plain kill is a normal termination signal`() {
        val e = explainer.explain("kill 1234")!!
        assertFalse(e.summary.contains("SIGKILL"))
    }

    @Test
    fun `tokenizer honours quotes`() {
        assertEquals(
            listOf("git", "commit", "-m", "a b c"),
            explainer.tokenize("""git commit -m "a b c""""),
        )
        assertEquals(
            listOf("rm", "my file.txt"),
            explainer.tokenize("rm 'my file.txt'"),
        )
    }

    @Test
    fun `env and path prefixes are unwrapped`() {
        val e = explainer.explain("FOO=1 /usr/bin/rm -rf x")!!
        assertEquals("rm", e.program)
    }
}
