package com.nexaflow.core.rom.model

data class RomBuildInfo(
    val family: RomFamily,
    val brand: String,
    val device: String,
    val model: String,
    val androidVersion: String,
    val securityPatch: String,
    val buildId: String,
    val buildDisplay: String,
    /** `ro.evolution.version` (e.g. "12.0" on Evolution X 12); blank on other ROMs. */
    val evolutionVersion: String = "",
    /** `ro.lineage.version` — Evolution X is LineageOS-based, so this is populated there too. */
    val lineageVersion: String = "",
    /** `ro.evolution.buildtype` (OFFICIAL / COMMUNITY / UNOFFICIAL) when present. */
    val evolutionBuildType: String = ""
)
