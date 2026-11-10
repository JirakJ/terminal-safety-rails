/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrivilegeEscalationDetectorTest {
    private val d = PrivilegeEscalationDetector()

    @Test fun sudo() { assertTrue(d.detect("sudo apt update").any { it.id == "privilege.sudo" }) }
    @Test fun `su dash`() { assertTrue(d.detect("su - root").any { it.id == "privilege.su-dash" }) }
    @Test fun doas() { assertTrue(d.detect("doas pkg install").any { it.id == "privilege.doas" }) }
    @Test fun pkexec() { assertTrue(d.detect("pkexec systemctl restart x").any { it.id == "privilege.pkexec" }) }

    @Test fun `bare su`() { assertTrue(d.detect("su alice").any { it.id == "privilege.su" }) }

    @Test fun `does not match pseudo word sudoers`() {
        assertEquals(0, d.detect("cat /etc/sudoers.d/readme").size)
    }

    @Test fun `does not match word inside other identifiers`() {
        assertEquals(0, d.detect("echo antisudo").size)
    }
}
