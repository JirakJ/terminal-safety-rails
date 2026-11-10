/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProductionTargetDetectorTest {
    private val d = ProductionTargetDetector()

    @Test fun kubectlProdContext() {
        assertTrue(d.detect("kubectl --context=prod-us-east delete pod x").any {
            it.id == "prod.kubectl-context"
        })
    }

    @Test fun kubectlNamespaceProd() {
        assertTrue(d.detect("kubectl -n prod delete deployment x").any {
            it.id == "prod.kubectl-context"
        })
    }

    @Test fun psqlProdHost() {
        assertTrue(d.detect("psql -h prod-db.internal -U admin").any { it.id == "prod.db-host" })
    }

    @Test fun sshProdHost() {
        assertTrue(d.detect("ssh deploy@production-01.example.com").any { it.id == "prod.hostname" })
    }

    @Test fun reproductionDirNoFire() {
        assertEquals(0, d.detect("cd reproduction/ && ls").size)
    }

    @Test fun stagingHostNoFire() {
        assertEquals(0, d.detect("ssh deploy@staging-01").size)
    }
}
