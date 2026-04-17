/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk

import com.jakubjirak.terminalsafety.risk.detectors.CredentialExposureDetector
import com.jakubjirak.terminalsafety.risk.detectors.DestructiveFileOpsDetector
import com.jakubjirak.terminalsafety.risk.detectors.DiskWipeDetector
import com.jakubjirak.terminalsafety.risk.detectors.ForkBombDetector
import com.jakubjirak.terminalsafety.risk.detectors.GitHistoryRewriteDetector
import com.jakubjirak.terminalsafety.risk.detectors.NetworkExfiltrationDetector
import com.jakubjirak.terminalsafety.risk.detectors.PackageManagerPrivilegedDetector
import com.jakubjirak.terminalsafety.risk.detectors.PrivilegeEscalationDetector
import com.jakubjirak.terminalsafety.risk.detectors.ProductionTargetDetector
import com.jakubjirak.terminalsafety.risk.detectors.RemoteCodeExecutionDetector
import com.jakubjirak.terminalsafety.risk.detectors.ShellInjectionDetector

/**
 * Pure scorer that orchestrates every [RiskSignalDetector] and produces a
 * single [RiskAssessment] per command.
 *
 * The scorer is deterministic, thread-safe and has no IntelliJ platform
 * dependency, so it can be invoked from the service layer, from the CLI
 * ([CommandRiskCli]), or directly from unit tests.
 *
 * Scoring rules:
 *
 *  1. Every detector emits zero or more [RiskSignal]s.
 *  2. Weights are summed.
 *  3. The sum is clamped to `0..100`.
 *  4. [RiskLevel.fromScore] maps the score to a band.
 *  5. Signals are returned ordered by descending weight so UIs can show
 *     the most relevant reasons first.
 */
class CommandRiskScorer(
    private val detectors: List<RiskSignalDetector> = DEFAULT_DETECTORS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Runs every detector against [command] and returns an assessment. */
    fun score(command: String): RiskAssessment {
        if (command.isBlank()) {
            return RiskAssessment(
                command = command,
                score = 0,
                level = RiskLevel.SAFE,
                signals = emptyList(),
                timestampEpochMs = clock(),
            )
        }
        val collected = detectors.flatMap { runSafely(it, command) }
        val total = collected.sumOf { it.weight }.coerceIn(0, 100)
        val ordered = collected.sortedByDescending { it.weight }
        return RiskAssessment(
            command = command,
            score = total,
            level = RiskLevel.fromScore(total),
            signals = ordered,
            timestampEpochMs = clock(),
        )
    }

    private fun runSafely(detector: RiskSignalDetector, command: String): List<RiskSignal> =
        try {
            detector.detect(command)
        } catch (t: Throwable) {
            emptyList()
        }

    companion object {
        /** Default detector set in the order they are executed. */
        val DEFAULT_DETECTORS: List<RiskSignalDetector> = listOf(
            DestructiveFileOpsDetector(),
            PrivilegeEscalationDetector(),
            ShellInjectionDetector(),
            RemoteCodeExecutionDetector(),
            CredentialExposureDetector(),
            NetworkExfiltrationDetector(),
            ProductionTargetDetector(),
            DiskWipeDetector(),
            ForkBombDetector(),
            GitHistoryRewriteDetector(),
            PackageManagerPrivilegedDetector(),
        )
    }
}
