package com.nexaflow.core.rom.model

/**
 * Live build snapshot of the running device. Fields describe protocol-visible
 * facts (properties, versions, identity strings) in neutral terms; which
 * product a build is has no meaning to the engine — only its capability tier
 * ([family]) and evidence values do. Not serialized: process-local.
 */
data class RomBuildInfo(
    val family: RomFamily,
    val brand: String,
    val manufacturer: String = "",
    val device: String,
    val model: String,
    val androidVersion: String,
    val securityPatch: String,
    val buildId: String,
    val buildDisplay: String,
    /** Version property of the privileged community-ROM base, when present. */
    val vendorVersion: String = "",
    /** The shared base version property inherited by derived forks, when present. */
    val baseVersion: String = "",
    /** `Build.VERSION.SDK_INT` (12 = S_V2/31, ...). Drives version-aware capability gating. */
    val androidSdk: Int = 0
)
