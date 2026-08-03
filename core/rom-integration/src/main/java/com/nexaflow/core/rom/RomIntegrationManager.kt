package com.nexaflow.core.rom

import android.content.Context
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomBuildInfo
import com.nexaflow.core.rom.model.RomCapability

object RomIntegrationManager {
    private val lock = Any()

    @Volatile
    private var initialized = false
    private lateinit var buildInfo: RomBuildInfo
    private lateinit var integrationLevel: IntegrationLevel
    private var capabilities: List<RomCapability> = emptyList()

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val appContext = context.applicationContext
            buildInfo = RomDetector.detect()
            integrationLevel = SystemAppStatusDetector.detect(appContext)
            val provider = RomCapabilityProvider(appContext, integrationLevel, buildInfo.family)
            capabilities = provider.availableCapabilities()
            initialized = true
        }
    }

    fun buildInfo(context: Context): RomBuildInfo {
        ensureInitialized(context)
        return buildInfo
    }

    fun integrationLevel(context: Context): IntegrationLevel {
        ensureInitialized(context)
        return integrationLevel
    }

    fun availableCapabilities(context: Context): List<RomCapability> {
        ensureInitialized(context)
        return capabilities
    }

    fun isCapabilityAvailable(context: Context, capability: RomCapability): Boolean {
        return capability in availableCapabilities(context)
    }

    fun isSystemIntegrated(context: Context): Boolean {
        return when (integrationLevel(context)) {
            IntegrationLevel.SYSTEM_APP,
            IntegrationLevel.PRIVILEGED_SYSTEM_APP,
            IntegrationLevel.PLATFORM_SIGNED_SYSTEM_APP -> true
            else -> false
        }
    }

    fun canControlSystem(context: Context): Boolean {
        return availableCapabilities(context).isNotEmpty()
    }

    fun controller(context: Context): SystemController {
        ensureInitialized(context)
        val appContext = context.applicationContext
        val provider = RomCapabilityProvider(appContext, integrationLevel, buildInfo.family)
        return SystemController(appContext, provider)
    }
}
