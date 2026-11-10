/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk

/**
 * A pluggable heuristic that inspects a raw command string and returns
 * zero, one, or more [RiskSignal]s.
 *
 * Implementations **must** be pure — no I/O, no EDT work, no
 * platform-specific lookups — so they can be exercised in unit tests and
 * embedded into the CLI ([CommandRiskCli]) that has no IntelliJ platform
 * dependency.
 *
 * Each detector deliberately carries an explicit `id` so [CommandRiskScorer]
 * can deduplicate signals emitted by the same detector and so downstream
 * reporters can group/filter by detector.
 */
interface RiskSignalDetector {
    /** Stable detector id — used for deduplication and telemetry. */
    val id: String

    /**
     * Inspects [command] and returns any matched signals. An empty list
     * means "no evidence from this heuristic".
     */
    fun detect(command: String): List<RiskSignal>
}
