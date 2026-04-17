/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitHistoryRewriteDetectorTest {
    private val d = GitHistoryRewriteDetector()

    @Test fun pushForceMain() {
        assertTrue(d.detect("git push --force origin main").any { it.id == "git.push-force-shared" })
    }

    @Test fun pushForceWithLeaseMaster() {
        assertTrue(d.detect("git push --force-with-lease origin master").any {
            it.id == "git.push-force-shared"
        })
    }

    @Test fun filterBranch() {
        assertTrue(d.detect("git filter-branch --tree-filter 'rm secret'").any {
            it.id == "git.filter-branch"
        })
    }

    @Test fun filterRepo() {
        assertTrue(d.detect("git filter-repo --path secret --invert-paths").any {
            it.id == "git.filter-branch"
        })
    }

    @Test fun rebaseMain() {
        assertTrue(d.detect("git rebase -i main").any { it.id == "git.rebase-i-main" })
    }

    @Test fun pushForcePersonalBranchNoFire() {
        assertEquals(0, d.detect("git push --force origin feature/my-branch").size)
    }

    @Test fun plainPushNoFire() {
        assertEquals(0, d.detect("git push origin main").size)
    }
}
