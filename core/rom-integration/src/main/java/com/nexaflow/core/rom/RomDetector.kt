package com.nexaflow.core.rom

import android.os.Build
import com.nexaflow.core.rom.model.RomBuildInfo
import com.nexaflow.core.rom.model.RomFamily

object RomDetector {

    /** Test seam: real values come from [Build] (android.os); pure JVM tests inject theirs. */
    internal var buildValues: () -> RomBuildInfo = { defaultBuildValues() }

    /** Snapshot of the Build.* fields, so pure-JVM tests can substitute them. */
    private data class BuildValues(
        val brand: String,
        val device: String,
        val model: String,
        val release: String,
        val securityPatch: String,
        val id: String,
        val display: String
    )

    private fun defaultBuildValues(): RomBuildInfo {
        val v = BuildValues(
            brand = Build.BRAND,
            device = Build.DEVICE,
            model = Build.MODEL,
            release = Build.VERSION.RELEASE,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            id = Build.ID,
            display = Build.DISPLAY
        )
        return RomBuildInfo(
            family = RomFamily.OTHER,
            brand = v.brand ?: "",
            device = v.device ?: "",
            model = v.model ?: "",
            androidVersion = v.release ?: "",
            securityPatch = v.securityPatch ?: "",
            buildId = v.id ?: "",
            buildDisplay = v.display ?: ""
        )
    }

    fun detect(): RomBuildInfo {
        val lineageVersion = SystemPropertyProvider.get("ro.lineage.version")
        val crDroidVersion = SystemPropertyProvider.get("ro.crdroid.version")
        val evolutionVersion = SystemPropertyProvider.get("ro.evolution.version")
        val pixelExperienceVersion = SystemPropertyProvider.get("ro.pixelexperience.version")
        val paranoidAndroidVersion = SystemPropertyProvider.get("ro.pa.version")
        val miUiVersion = SystemPropertyProvider.get("ro.miui.ui.version.name")
        val hyperOsVersion = SystemPropertyProvider.get("ro.mi.os.version.name")
        val colorOsVersion = SystemPropertyProvider.get("ro.oplus.version")
        val oxygenVersion = SystemPropertyProvider.get("ro.oxygen.version")
        val oneUiVersion = SystemPropertyProvider.get("ro.build.version.oneui")
        val evolutionBuildType = SystemPropertyProvider.get("ro.evolution.buildtype")
        val isGoogle = Build.MANUFACTURER.equals("Google", ignoreCase = true)

        // Evolution X is a fork of LineageOS: it sets both ro.evolution.version
        // (its own) and ro.lineage.version (inherited), so an Evolution X build
        // must be classified as EVOLUTION_X first — even though the LineageOS
        // property is present. Other LineageOS-based ROMs (crDroid) are also
        // checked after Evolution X to avoid misclassification.
        val family = when {
            evolutionVersion.isNotBlank() -> RomFamily.EVOLUTION_X
            hyperOsVersion.isNotBlank() -> RomFamily.HYPER_OS
            miUiVersion.isNotBlank() -> RomFamily.MIUI
            crDroidVersion.isNotBlank() -> RomFamily.CR_DROID
            lineageVersion.isNotBlank() -> RomFamily.LINEAGE_OS
            pixelExperienceVersion.isNotBlank() -> RomFamily.PIXEL_EXPERIENCE
            paranoidAndroidVersion.isNotBlank() -> RomFamily.PARANOID_ANDROID
            oneUiVersion.isNotBlank() -> RomFamily.ONE_UI
            oxygenVersion.isNotBlank() -> RomFamily.OXYGEN_OS
            colorOsVersion.isNotBlank() -> RomFamily.COLOR_OS
            isGoogle -> RomFamily.PIXEL
            else -> RomFamily.OTHER
        }

        val base = buildValues()
        return base.copy(
            family = family,
            evolutionVersion = evolutionVersion,
            lineageVersion = lineageVersion,
            evolutionBuildType = evolutionBuildType
        )
    }
}
