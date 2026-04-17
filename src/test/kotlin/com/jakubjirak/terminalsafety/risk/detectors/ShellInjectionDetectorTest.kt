/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShellInjectionDetectorTest {
    private val d = ShellInjectionDetector()

    @Test fun backticks() { assertTrue(d.detect("echo `whoami`").any { it.id == "shell.backticks" }) }
    @Test fun dollarParen() { assertTrue(d.detect("echo \$(id)").any { it.id == "shell.dollar-paren" }) }
    @Test fun eval() { assertTrue(d.detect("eval \"rm -rf .\"").any { it.id == "shell.eval" }) }
    @Test fun bashDashC() { assertTrue(d.detect("bash -c \"whoami\"").any { it.id == "shell.dash-c" }) }
    @Test fun shDashC() { assertTrue(d.detect("sh -c 'echo hi'").any { it.id == "shell.dash-c" }) }

    @Test fun `plain text no fire`() { assertEquals(0, d.detect("ls -la").size) }

    @Test fun `word eval inside identifier no fire`() {
        assertEquals(0, d.detect("./doevaluate").size)
    }
}
