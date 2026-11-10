/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DestructiveFileOpsDetectorTest {
    private val d = DestructiveFileOpsDetector()

    @Test fun `rm -rf root fires`() {
        val s = d.detect("rm -rf /")
        assertTrue(s.any { it.id == "destructive.rm-rf-root" })
    }

    @Test fun `rm -rf tmp does NOT fire root signal`() {
        val s = d.detect("rm -rf /tmp/build")
        assertTrue(s.none { it.id == "destructive.rm-rf-root" })
    }

    @Test fun `rm -rf home tilde fires`() {
        val s = d.detect("rm -rf ~")
        assertTrue(s.any { it.id == "destructive.rm-rf-home" })
    }

    @Test fun `rm -rf HOME var fires`() {
        val s = d.detect("rm -rf \$HOME")
        assertTrue(s.any { it.id == "destructive.rm-rf-home" })
    }

    @Test fun `no-preserve-root flag fires`() {
        val s = d.detect("rm -rf --no-preserve-root /")
        assertTrue(s.any { it.id == "destructive.no-preserve-root" })
    }

    @Test fun `find -delete fires`() {
        val s = d.detect("find . -name '*.tmp' -delete")
        assertTrue(s.any { it.id == "destructive.find-delete" })
    }

    @Test fun `harmless rm does not fire`() {
        assertEquals(0, d.detect("rm build.log").size)
    }

    @Test fun `ls -rf does not fire`() {
        assertEquals(0, d.detect("ls -la /").size)
    }
}
