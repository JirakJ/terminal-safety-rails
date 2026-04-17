/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk

/**
 * Coarse-grained risk classification derived from the numeric 0..100 score
 * produced by [CommandRiskScorer].
 *
 * Thresholds are inclusive:
 *
 *  * `SAFE`     — `0..19`
 *  * `LOW`      — `20..39`
 *  * `MEDIUM`   — `40..59`
 *  * `HIGH`     — `60..79`
 *  * `CRITICAL` — `80..100`
 *
 * This enum is deliberately decoupled from [com.jakubjirak.terminalsafety.model.RiskLevel]
 * because rule-based levels ("what rule matched") and heuristic risk scoring
 * ("how dangerous does this command look overall") serve different purposes
 * and must be able to evolve independently.
 */
enum class RiskLevel(
    val displayName: String,
    val colorHex: String,
    val minScore: Int,
    val maxScore: Int,
) {
    SAFE("Safe", "#66BB6A", 0, 19),
    LOW("Low", "#9CCC65", 20, 39),
    MEDIUM("Medium", "#FDD835", 40, 59),
    HIGH("High", "#FB8C00", 60, 79),
    CRITICAL("Critical", "#E53935", 80, 100);

    companion object {
        /**
         * Maps a raw score into one of the five risk bands. Scores outside the
         * legal 0..100 range are clamped before classification so callers do
         * not need to guard against bad input.
         */
        fun fromScore(score: Int): RiskLevel {
            val clamped = score.coerceIn(0, 100)
            return values().first { clamped in it.minScore..it.maxScore }
        }
    }
}
