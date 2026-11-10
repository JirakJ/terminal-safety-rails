/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RiskLevelTest {

    @Test
    fun `fromScore bands`() {
        assertEquals(RiskLevel.SAFE, RiskLevel.fromScore(0))
        assertEquals(RiskLevel.SAFE, RiskLevel.fromScore(19))
        assertEquals(RiskLevel.LOW, RiskLevel.fromScore(20))
        assertEquals(RiskLevel.LOW, RiskLevel.fromScore(39))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromScore(40))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromScore(59))
        assertEquals(RiskLevel.HIGH, RiskLevel.fromScore(60))
        assertEquals(RiskLevel.HIGH, RiskLevel.fromScore(79))
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(80))
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(100))
    }

    @Test
    fun `fromScore clamps out of range values`() {
        assertEquals(RiskLevel.SAFE, RiskLevel.fromScore(-5))
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(9_999))
    }

    @Test
    fun `every band has monotonically ordered bounds`() {
        for (lvl in RiskLevel.values()) {
            assert(lvl.minScore <= lvl.maxScore) { "$lvl bounds are reversed" }
        }
    }
}
