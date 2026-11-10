/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RiskSignalTest {

    @Test
    fun `valid signal`() {
        val s = RiskSignal("x", "d", 40, "m")
        assertEquals(40, s.weight)
    }

    @Test
    fun `weight out of range rejected`() {
        assertThrows(IllegalArgumentException::class.java) { RiskSignal("x", "d", -1, "m") }
        assertThrows(IllegalArgumentException::class.java) { RiskSignal("x", "d", 101, "m") }
    }

    @Test
    fun `blank id rejected`() {
        assertThrows(IllegalArgumentException::class.java) { RiskSignal("  ", "d", 10, "m") }
    }
}
