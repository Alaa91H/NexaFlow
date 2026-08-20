package com.nexaflow.domain.updates

import kotlinx.serialization.Serializable

/**
 * Persisted options for the Google Play update action. Defaults are deliberately
 * conservative: the action evaluates eligibility only and never assumes that a
 * source or silent installer exists.
 */
@Serializable
data class GooglePlayUpdateRequest(
    val packageFilter: String? = null,
    val includeGoogleApps: Boolean = true,
    val includeUserApps: Boolean = false,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = true,
    val maxConcurrentDownloads: Int = 1,
    val retryCount: Int = 0,
    val allowReboot: Boolean = false,
    val requireSilentInstall: Boolean = true,
    val dryRun: Boolean = true
) {
    init {
        require(maxConcurrentDownloads in 1..4) { "maxConcurrentDownloads must be 1..4" }
        require(retryCount in 0..3) { "retryCount must be 0..3" }
    }

    companion object {
        fun fromConfig(config: Map<String, String>): GooglePlayUpdateRequest = GooglePlayUpdateRequest(
            packageFilter = config["packageFilter"]?.trim()?.takeIf(String::isNotEmpty),
            includeGoogleApps = config["includeGoogleApps"]?.toBooleanStrictOrNull() ?: true,
            includeUserApps = config["includeUserApps"]?.toBooleanStrictOrNull() ?: false,
            wifiOnly = config["wifiOnly"]?.toBooleanStrictOrNull() ?: true,
            chargingOnly = config["chargingOnly"]?.toBooleanStrictOrNull() ?: true,
            maxConcurrentDownloads = config["maxConcurrentDownloads"]?.toIntOrNull()?.coerceIn(1, 4) ?: 1,
            retryCount = config["retryCount"]?.toIntOrNull()?.coerceIn(0, 3) ?: 0,
            allowReboot = config["allowReboot"]?.toBooleanStrictOrNull() ?: false,
            requireSilentInstall = config["requireSilentInstall"]?.toBooleanStrictOrNull() ?: true,
            dryRun = config["dryRun"]?.toBooleanStrictOrNull() ?: true
        )
    }
}

/** Facts observed locally. None of these facts imply a Google Play catalog source. */
@Serializable
data class GooglePlayUpdateEnvironment(
    val deviceOwner: Boolean,
    val affiliatedProfileOwner: Boolean,
    val rootAvailable: Boolean,
    val shizukuRunning: Boolean,
    val shizukuGranted: Boolean,
    /** True only after an EMM integration has supplied a managed Play policy channel. */
    val managedGooglePlayPolicyAvailable: Boolean = false
)

@Serializable
enum class GooglePlayUpdateDecision {
    /** A managed Google Play / EMM policy channel can own update delivery. */
    MANAGED_POLICY_REQUIRED,
    /** Normal apps and elevated shells have no supported Play catalog source. */
    PLAY_DISCOVERY_NOT_EXPOSED
}

/**
 * Produces a conservative execution decision. It never treats Root, Shizuku,
 * Device Owner, or PackageInstaller as proof that this app can access Google
 * Play's catalog or download official update bytes.
 */
object GooglePlayUpdatePlanner {
    fun decide(environment: GooglePlayUpdateEnvironment): GooglePlayUpdateDecision =
        if (environment.managedGooglePlayPolicyAvailable) {
            GooglePlayUpdateDecision.MANAGED_POLICY_REQUIRED
        } else {
            GooglePlayUpdateDecision.PLAY_DISCOVERY_NOT_EXPOSED
        }
}
