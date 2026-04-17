/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CredentialExposureDetectorTest {
    private val d = CredentialExposureDetector()

    @Test fun echoPassword() {
        assertTrue(d.detect("echo \$PASSWORD").any { it.id == "cred.echo-secret-var" })
    }

    @Test fun echoBraceAwsSecret() {
        assertTrue(d.detect("echo \${AWS_SECRET_ACCESS_KEY}").any { it.id == "cred.echo-secret-var" })
    }

    @Test fun printenvBare() {
        assertTrue(d.detect("printenv").any { it.id == "cred.printenv" })
    }

    @Test fun printenvGithubToken() {
        assertTrue(d.detect("printenv GITHUB_TOKEN").any { it.id == "cred.printenv" })
    }

    @Test fun literalGhpPrefix() {
        assertTrue(d.detect("curl -H 'Authorization: token ghp_abcd1234' https://api").any {
            it.id.startsWith("cred.literal.")
        })
    }

    @Test fun `plain echo no fire`() {
        assertEquals(0, d.detect("echo hello world").size)
    }

    @Test fun `echo non-secret var no fire`() {
        assertEquals(0, d.detect("echo \$PATH").size)
    }
}
