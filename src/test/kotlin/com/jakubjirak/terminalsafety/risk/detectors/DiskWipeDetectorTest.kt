/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiskWipeDetectorTest {
    private val d = DiskWipeDetector()

    @Test fun ddToSda() {
        assertTrue(d.detect("dd if=/dev/zero of=/dev/sda bs=1M").any { it.id == "disk.dd-of-device" })
    }

    @Test fun mkfsOnSdb1() {
        assertTrue(d.detect("mkfs.ext4 /dev/sdb1").any { it.id == "disk.mkfs-device" })
    }

    @Test fun shredDevice() {
        assertTrue(d.detect("shred -v /dev/nvme0n1").any { it.id == "disk.shred-device" })
    }

    @Test fun ddToNullNoFire() {
        assertEquals(0, d.detect("dd if=file.iso of=/dev/null").size)
    }

    @Test fun ddToRegularFileNoFire() {
        assertEquals(0, d.detect("dd if=disk.img of=/tmp/copy.img").size)
    }

    @Test fun shredRegularFileNoFire() {
        assertEquals(0, d.detect("shred -v secret.log").size)
    }
}
