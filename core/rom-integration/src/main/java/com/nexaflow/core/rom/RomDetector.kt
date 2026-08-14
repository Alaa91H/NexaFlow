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
        val manufacturer: String,
        val release: String,
        val securityPatch: String,
        val id: String,
        val display: String,
        val sdkInt: Int
    )

    private fun defaultBuildValues(): RomBuildInfo {
        val v = BuildValues(
            brand = Build.BRAND,
            device = Build.DEVICE,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            release = Build.VERSION.RELEASE,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            id = Build.ID,
            display = Build.DISPLAY,
            sdkInt = Build.VERSION.SDK_INT
        )
        return RomBuildInfo(
            family = RomFamily.OTHER,
            brand = v.brand ?: "",
            device = v.device ?: "",
            model = v.model ?: "",
            androidVersion = v.release ?: "",
            securityPatch = v.securityPatch ?: "",
            buildId = v.id ?: "",
            buildDisplay = v.display ?: "",
            androidSdk = v.sdkInt
        )
    }

    /**
     * Detects the ROM family through [RomDetectionMatrix]: reads every
     * `ro.*` property the matrix understands in one snapshot, then classifies
     * with the brand/manufacturer tiebreaks (custom ROMs before OEM skins,
     * forks before bases, ColorOS-family brand disambiguation).
     */
    fun detect(): RomBuildInfo {
        // Metadata keys the matrix reads into the build info beyond classification.
        val metadataProps = setOf("ro.evolution.buildtype")
        val props = (RomDetectionMatrix.ALL_PROPERTIES + metadataProps)
            .associateWith { SystemPropertyProvider.get(it) }
            .filterValues { it.isNotBlank() }
        val base = buildValues()
        val info = RomDetectionMatrix.detect(
            props = props,
            brand = base.brand,
            manufacturer = base.manufacturer,
            device = base.device,
            model = base.model,
            androidVersion = base.androidVersion,
            securityPatch = base.securityPatch,
            buildId = base.buildId,
            buildDisplay = base.buildDisplay,
            sdkInt = base.androidSdk
        )
        // The injected seam may carry over the legacy evolution/lineage fields
        // from defaultBuildValues; the matrix result is authoritative.
        return info
    }
}
