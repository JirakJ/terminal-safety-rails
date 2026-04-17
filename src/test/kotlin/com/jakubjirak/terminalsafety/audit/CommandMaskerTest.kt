package com.jakubjirak.terminalsafety.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandMaskerTest {

    @Test
    fun `long hex tokens are masked`() {
        val masked = CommandMasker.mask("curl -H X-Trace: a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6")
        assertTrue(masked.contains(CommandMasker.MASK), masked)
        assertFalse(masked.contains("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"), masked)
    }

    @Test
    fun `base64-ish tokens are masked`() {
        val token = "AAAABBBBCCCCDDDDEEEEFFFF" // 24 chars
        val masked = CommandMasker.mask("deploy --token $token rest")
        assertFalse(masked.contains(token), masked)
    }

    @Test
    fun `password equals value preserves key prefix`() {
        val masked = CommandMasker.mask("mysql --password=hunter2seekrit")
        assertTrue(masked.contains("--password=${CommandMasker.MASK}"), masked)
        assertFalse(masked.contains("hunter2seekrit"), masked)
    }

    @Test
    fun `api_key and api-key variants both masked`() {
        val a = CommandMasker.mask("run api_key=supersecretvalue123456")
        val b = CommandMasker.mask("run api-key=anothersecret9876543210")
        assertTrue(a.contains("api_key=${CommandMasker.MASK}"), a)
        assertTrue(b.contains("api-key=${CommandMasker.MASK}"), b)
    }

    @Test
    fun `token equals value is masked`() {
        val masked = CommandMasker.mask("curl -H token=abcabcabcabcabcabc")
        assertTrue(masked.contains("token=${CommandMasker.MASK}"), masked)
    }

    @Test
    fun `authorization bearer is masked`() {
        val masked = CommandMasker.mask("curl -H 'Authorization: Bearer eyJabc.def.ghi' https://x")
        assertTrue(masked.contains("Authorization: Bearer ${CommandMasker.MASK}"), masked)
        assertFalse(masked.contains("eyJabc.def.ghi"), masked)
    }

    @Test
    fun `mysql -p short argument is masked`() {
        val masked = CommandMasker.mask("mysql -u root -p supersecret -h db")
        assertTrue(masked.contains("-p ${CommandMasker.MASK}"), masked)
        assertFalse(masked.contains("supersecret"), masked)
    }

    @Test
    fun `credit card like 16 digit sequence is masked`() {
        val masked = CommandMasker.mask("echo 4111111111111111 > /tmp/x")
        assertFalse(masked.contains("4111111111111111"), masked)
        assertTrue(masked.contains(CommandMasker.MASK), masked)
    }

    @Test
    fun `short plain tokens are not masked`() {
        val cmd = "ls -la /etc hello world 1234"
        assertEquals(cmd, CommandMasker.mask(cmd))
    }

    @Test
    fun `command longer than max is truncated with suffix`() {
        // Use chars outside every mask pattern (no hex, no base64, no digits, no whitespace-delimited flags).
        val long = "!".repeat(CommandMasker.MAX_LENGTH + 500)
        val masked = CommandMasker.mask(long)
        assertEquals(CommandMasker.MAX_LENGTH, masked.length)
        assertTrue(masked.endsWith(CommandMasker.TRUNCATION_SUFFIX))
    }

    @Test
    fun `no false positive on -p inside word`() {
        val cmd = "grep -rpQ pattern file"
        assertEquals(cmd, CommandMasker.mask(cmd))
    }
}
