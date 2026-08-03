package com.nexaflow.core.rom.model

data class RomBuildInfo(
    val family: RomFamily,
    val brand: String,
    val device: String,
    val model: String,
    val androidVersion: String,
    val securityPatch: String,
    val buildId: String,
    val buildDisplay: String
)
