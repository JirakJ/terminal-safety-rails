/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForkBombDetectorTest {
    private val d = ForkBombDetector()

    @Test fun canonical() {
        assertTrue(d.detect(":(){ :|:& };:").any { it.id == "forkbomb.canonical" })
    }

    @Test fun canonicalNoSpace() {
        assertTrue(d.detect(":(){:|:&};:").any { it.id == "forkbomb.canonical" })
    }

    @Test fun genericNamed() {
        assertTrue(d.detect("bomb(){ bomb|bomb& };bomb").any { it.id == "forkbomb.generic" })
    }

    @Test fun innocentFunctionNoFire() {
        assertEquals(0, d.detect("foo(){ echo hi; }; foo").size)
    }

    @Test fun innocentPipeNoFire() {
        assertEquals(0, d.detect("cat file | grep hi").size)
    }
}
