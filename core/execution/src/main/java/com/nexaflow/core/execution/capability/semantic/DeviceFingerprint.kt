package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.core.rom.RomDetector
import com.nexaflow.core.rom.model.RomFamily
import kotlinx.serialization.Serializable

/**
 * Stable, non-sensitive device identity used to scope capability evidence.
 * It contains only public build metadata, never leaves the device, and is the
 * key that lets the router prefer a strategy proven to work on *this* device
 * family instead of trusting a static privilege assumption.
 */
@Serializable
data class DeviceFingerprint(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidApi: Int,
    val securityPatch: String,
    val romFamily: RomFamily
) {
    /** Family-level key: evidence shared across devices of the same ROM family. */
    val familyKey: String
        get() = "$manufacturer/$androidApi/$romFamily"

    /** Exact-device key: strongest evidence scope. */
    val deviceKey: String
        get() = "$manufacturer/$model/$device/$androidApi/$securityPatch"

    companion object {
        /** Captures the live device. Pure metadata; never fails. */
        fun capture(now: () -> RomBuildInfoSnapshot = ::liveSnapshot): DeviceFingerprint =
            with(now()) {
                DeviceFingerprint(
                    manufacturer = manufacturer,
                    model = model,
                    device = device,
                    androidApi = apiLevel,
                    securityPatch = securityPatch,
                    romFamily = romFamily
                )
            }

        private fun liveSnapshot(): RomBuildInfoSnapshot = RomBuildInfoSnapshot(
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL,
            device = android.os.Build.DEVICE,
            apiLevel = android.os.Build.VERSION.SDK_INT,
            securityPatch = android.os.Build.VERSION.SECURITY_PATCH ?: "",
            romFamily = RomDetector.detect().family
        )

        /** Test seam mirroring the RomDetector boundary. */
        data class RomBuildInfoSnapshot(
            val manufacturer: String,
            val model: String,
            val device: String,
            val apiLevel: Int,
            val securityPatch: String,
            val romFamily: RomFamily
        )
    }
}
