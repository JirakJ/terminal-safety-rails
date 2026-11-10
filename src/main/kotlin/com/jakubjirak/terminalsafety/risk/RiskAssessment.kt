/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk

/**
 * Immutable result of scoring one command.
 *
 * @property command The command that was assessed (unmasked; callers
 *   must mask before persisting).
 * @property score Raw score in the 0..100 range (clamped).
 * @property level Classification band for [score].
 * @property signals Ordered list of [RiskSignal]s contributing to the
 *   score, sorted by descending weight so UIs can render "top reasons first".
 * @property timestampEpochMs Epoch millis at which the assessment was made.
 */
data class RiskAssessment(
    val command: String,
    val score: Int,
    val level: RiskLevel,
    val signals: List<RiskSignal>,
    val timestampEpochMs: Long,
) {
    init {
        require(score in 0..100) { "score must be 0..100, got $score" }
    }

    /** True when at least one signal contributed. */
    val hasSignals: Boolean get() = signals.isNotEmpty()

    /** Top-N signals for UI rendering (max [limit]). */
    fun topSignals(limit: Int): List<RiskSignal> =
        signals.asSequence().take(limit.coerceAtLeast(0)).toList()
}
