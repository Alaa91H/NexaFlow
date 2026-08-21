package com.nexaflow.feature.settings

import com.nexaflow.core.pluginsdk.PluginCompatibilityStatus
import com.nexaflow.core.pluginsdk.PluginComponentRef
import com.nexaflow.core.pluginsdk.PluginDescriptor
import com.nexaflow.core.pluginsdk.PluginProtocol
import com.nexaflow.core.pluginsdk.PluginTrustLevel
import com.nexaflow.core.pluginsdk.PluginType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCatalogTest {

    @Test
    fun organize_places_installed_recommended_package_in_installed_and_remaining_recommendations_in_available() {
        val installedDefinition = PluginCatalog.recommended.first()

        val state = PluginCatalog.organize(
            installedPackages = mapOf(installedDefinition.packageName to true),
            descriptors = emptyList()
        )

        assertEquals(listOf(installedDefinition.packageName), state.installed.map { it.packageName })
        assertTrue(state.installed.single().installed)
        assertTrue(state.installed.single().appEnabled)
        assertFalse(allAvailable(state).any { it.packageName == installedDefinition.packageName })
        assertEquals(PluginCatalog.recommended.size - 1, allAvailable(state).size)
    }

    @Test
    fun organize_keeps_discovered_external_plugin_in_installed_section() {
        val external = descriptor(
            packageName = "com.example.externalplugin",
            type = PluginType.SETTING,
            compatibility = PluginCompatibilityStatus.COMPATIBLE,
            receiver = true
        )

        val state = PluginCatalog.organize(
            installedPackages = emptyMap(),
            descriptors = listOf(external)
        )

        val entry = state.installed.single()
        assertEquals(external.packageName, entry.packageName)
        assertEquals(external.displayName, entry.displayName)
        assertEquals(null, entry.definition)
        assertTrue(entry.installed)
        assertTrue(entry.appEnabled)
        assertEquals(PluginCatalog.recommended.size, allAvailable(state).size)
    }

    @Test
    fun organize_preserves_android_disabled_state_for_installed_recommended_plugin() {
        val installedDefinition = PluginCatalog.recommended.first()

        val state = PluginCatalog.organize(
            installedPackages = mapOf(installedDefinition.packageName to false),
            descriptors = emptyList()
        )

        assertFalse(state.installed.single().appEnabled)
    }

    @Test
    fun organize_places_high_risk_command_plugins_in_advanced_available_section() {
        val state = PluginCatalog.organize(installedPackages = emptyMap(), descriptors = emptyList())

        assertTrue(state.advancedAvailable.isNotEmpty())
        assertTrue(state.advancedAvailable.all { it.isAdvanced && it.isHighRisk })
        assertTrue(state.advancedAvailable.any { it.packageName == "com.termux.tasker" })
        assertTrue(state.advancedAvailable.any { it.packageName == "com.ADBPlugin" })
        assertTrue(state.recommendedAvailable.none { it.isHighRisk })
    }

    @Test
    fun organize_keeps_installed_high_risk_plugin_visible_in_installed_section() {
        val state = PluginCatalog.organize(
            installedPackages = mapOf("com.termux.tasker" to true),
            descriptors = emptyList()
        )

        val entry = state.installed.single()
        assertEquals("com.termux.tasker", entry.packageName)
        assertTrue(entry.isAdvanced)
        assertTrue(entry.isHighRisk)
        assertTrue(entry.appEnabled)
    }

    @Test
    fun testablePlugin_is_null_when_setting_plugin_is_not_compatible() {
        val installedDefinition = PluginCatalog.recommended.first()
        val incompatibleDescriptor = descriptor(
            packageName = installedDefinition.packageName,
            type = PluginType.SETTING,
            compatibility = PluginCompatibilityStatus.MISSING_RECEIVER,
            receiver = false
        )

        val state = PluginCatalog.organize(
            installedPackages = mapOf(installedDefinition.packageName to true),
            descriptors = listOf(incompatibleDescriptor)
        )

        assertNull(state.installed.single().testablePlugin)
    }

    private fun allAvailable(state: PluginCatalogUiState): List<PluginCatalogEntry> =
        state.recommendedAvailable + state.advancedAvailable

    private fun descriptor(
        packageName: String,
        type: PluginType,
        compatibility: PluginCompatibilityStatus,
        receiver: Boolean
    ) = PluginDescriptor(
        id = "catalog-test-$packageName-$type",
        packageName = packageName,
        type = type,
        protocol = PluginProtocol.LOCALE_BASE,
        receiver = if (receiver) {
            PluginComponentRef(packageName, "com.example.PluginReceiver")
        } else {
            null
        },
        displayName = "Catalog test plugin",
        trustLevel = PluginTrustLevel.LOCALE_COMPATIBLE,
        compatibility = compatibility
    )
}
