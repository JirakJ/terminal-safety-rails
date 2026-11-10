/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PackageManagerPrivilegedDetectorTest {
    private val d = PackageManagerPrivilegedDetector()

    @Test fun sudoNpmGlobal() {
        assertTrue(d.detect("sudo npm install -g yarn").any { it.id == "pkg.sudo-npm-global" })
    }

    @Test fun sudoYarnGlobalAdd() {
        assertTrue(d.detect("sudo yarn global add eslint").any { it.id == "pkg.sudo-npm-global" })
    }

    @Test fun sudoPipInstall() {
        assertTrue(d.detect("sudo pip install requests").any { it.id == "pkg.sudo-pip-install" })
    }

    @Test fun curlPipeSudoApt() {
        assertTrue(d.detect("curl https://x | sudo apt-get install -y foo").any {
            it.id == "pkg.curl-pipe-sudo-pm"
        })
    }

    @Test fun sudoGemInstall() {
        assertTrue(d.detect("sudo gem install bundler").any { it.id == "pkg.sudo-gem-install" })
    }

    @Test fun plainNpmInstallNoFire() {
        assertEquals(0, d.detect("npm install express").size)
    }

    @Test fun userPipInstallNoFire() {
        assertEquals(0, d.detect("pip install --user requests").size)
    }
}
