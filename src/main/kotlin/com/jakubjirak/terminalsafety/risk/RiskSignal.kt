/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk

/**
 * A single heuristic observation about a command line.
 *
 * Each [RiskSignalDetector] returns zero, one, or several [RiskSignal]s when
 * it fires. The [CommandRiskScorer] then sums [weight]s (capped at 100) and
 * exposes every matched signal as evidence to the user ("why was I warned?").
 *
 * @property id Stable identifier (e.g. `"destructive.rm-rf-root"`) used in
 *   audit logs, dialogs, and JSON reports.
 * @property description Short human-readable explanation of what the signal
 *   represents — shown verbatim in the risk dialog.
 * @property weight Contribution to the total risk score, in the 0..100 range.
 *   Weights compose additively but the final score is clamped to 100.
 * @property matched The exact substring (or short preview) of the input
 *   command that triggered this signal. Useful for "match preview" columns.
 */
data class RiskSignal(
    val id: String,
    val description: String,
    val weight: Int,
    val matched: String,
) {
    init {
        require(weight in 0..100) { "weight must be 0..100, got $weight" }
        require(id.isNotBlank()) { "signal id must not be blank" }
    }
}
