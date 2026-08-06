package com.nexaflow.core.compat

import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.RomFamily

/**
 * A pure snapshot of a device's integration state, used by [ProviderSelector]
 * to pick the best [ExecutionProvider]. Kept free of Android types so the
 * selector and its tests run on the JVM with a fully simulated device matrix.
 *
 * Production detection lives in [DeviceProfileDetector].
 */
data class DeviceProfile(
    val integrationLevel: IntegrationLevel,
    val romFamily: RomFamily,
    val capabilities: Set<RomCapability>,
    val accessibilityEnabled: Boolean = false,
    val shizukuGranted: Boolean = false,
    val rootAvailable: Boolean = false,
    val adbConnected: Boolean = false,
    val androidSdk: Int = 0
)
