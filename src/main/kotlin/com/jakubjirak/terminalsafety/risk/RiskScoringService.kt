/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Project-level facade around [CommandRiskScorer] with a short in-memory
 * cache so repeated assessments of the same command (e.g. interceptor +
 * dialog) don't re-run every detector.
 *
 * All work is pure Kotlin — no I/O, no EDT blocking — so callers can invoke
 * [assess] from any thread including the EDT. The cache is bounded and
 * entries expire after [CACHE_TTL_MS].
 */
@Service(Service.Level.PROJECT)
class RiskScoringService(@Suppress("unused") private val project: Project) {

    private data class CacheEntry(val assessment: RiskAssessment, val expiresAt: Long)

    private val scorer = CommandRiskScorer()
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /** Scores [command], using a short cache window to avoid recomputation. */
    fun assess(command: String): RiskAssessment {
        val now = System.currentTimeMillis()
        val cached = cache[command]
        if (cached != null && cached.expiresAt > now) {
            return cached.assessment
        }
        if (cache.size > MAX_CACHE) cache.clear()
        val fresh = scorer.score(command)
        cache[command] = CacheEntry(fresh, now + CACHE_TTL_MS)
        return fresh
    }

    /** Clears the cache — intended for tests and for "reset" UI actions. */
    fun clearCache() {
        cache.clear()
    }

    companion object {
        private val CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(30)
        private const val MAX_CACHE = 256

        /** Convenience accessor mirroring other project services. */
        fun getInstance(project: Project): RiskScoringService =
            project.getService(RiskScoringService::class.java)
    }
}
