package com.jakubjirak.terminalsafety.licensing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

/**
 * Central gate for freemium feature access.
 * Free tier: 10 default rules, basic confirmation dialog.
 * Paid tier: unlimited custom rules, audit log, team rules.
 */
@Service(Service.Level.APP)
class FreemiumGate {

    enum class LicenseStatus {
        FREE, TRIAL, PAID, EXPIRED
    }

    /**
     * Check if a paid feature is available for the current license.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isPaidFeatureAvailable(featureId: String): Boolean {
        val status = getLicenseStatus()
        return status == LicenseStatus.PAID || status == LicenseStatus.TRIAL
    }

    fun getLicenseStatus(): LicenseStatus {
        // TODO: Integrate with JetBrains Marketplace Licensing API
        // For now, return FREE — all paid features gated behind this check
        return LicenseStatus.FREE
    }

    fun getMaxCustomRules(): Int {
        return when (getLicenseStatus()) {
            LicenseStatus.FREE -> MAX_FREE_CUSTOM_RULES
            LicenseStatus.EXPIRED -> MAX_FREE_CUSTOM_RULES
            LicenseStatus.TRIAL, LicenseStatus.PAID -> Int.MAX_VALUE
        }
    }

    companion object {
        const val MAX_FREE_CUSTOM_RULES = 5

        // Feature IDs
        const val FEATURE_AUDIT_LOG = "audit_log"
        const val FEATURE_UNLIMITED_RULES = "unlimited_rules"
        const val FEATURE_TEAM_RULES = "team_rules"
        const val FEATURE_STATISTICS = "statistics"

        fun getInstance(): FreemiumGate =
            ApplicationManager.getApplication().getService(FreemiumGate::class.java)
    }
}
