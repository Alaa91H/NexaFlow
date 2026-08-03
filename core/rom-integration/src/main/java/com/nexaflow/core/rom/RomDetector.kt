package com.nexaflow.core.rom

import android.os.Build
import com.nexaflow.core.rom.model.RomBuildInfo
import com.nexaflow.core.rom.model.RomFamily

object RomDetector {
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
        val isGoogle = Build.MANUFACTURER.equals("Google", ignoreCase = true)

        val family = when {
            hyperOsVersion.isNotBlank() -> RomFamily.HYPER_OS
            miUiVersion.isNotBlank() -> RomFamily.MIUI
            lineageVersion.isNotBlank() -> RomFamily.LINEAGE_OS
            crDroidVersion.isNotBlank() -> RomFamily.CR_DROID
            evolutionVersion.isNotBlank() -> RomFamily.EVOLUTION_X
            pixelExperienceVersion.isNotBlank() -> RomFamily.PIXEL_EXPERIENCE
            paranoidAndroidVersion.isNotBlank() -> RomFamily.PARANOID_ANDROID
            oneUiVersion.isNotBlank() -> RomFamily.ONE_UI
            oxygenVersion.isNotBlank() -> RomFamily.OXYGEN_OS
            colorOsVersion.isNotBlank() -> RomFamily.COLOR_OS
            isGoogle -> RomFamily.PIXEL
            else -> RomFamily.OTHER
        }

        return RomBuildInfo(
            family = family,
            brand = Build.BRAND,
            device = Build.DEVICE,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            buildId = Build.ID,
            buildDisplay = Build.DISPLAY
        )
    }
}
