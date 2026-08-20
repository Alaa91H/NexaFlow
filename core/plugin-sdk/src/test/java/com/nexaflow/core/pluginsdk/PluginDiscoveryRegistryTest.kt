package com.nexaflow.core.pluginsdk

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PluginDiscoveryRegistryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun discoversCompatibleLocaleSettingFromPublicEditAndFireComponents() = runBlocking {
        val packageName = "com.example.plugin"
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(LocaleContract.ACTION_EDIT_SETTING),
            resolveInfo(packageName, "$packageName.EditActivity", "Example plugin")
        )
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(LocaleContract.ACTION_FIRE_SETTING),
            resolveInfo(packageName, "$packageName.FireReceiver", "Example plugin")
        )

        val result = PluginDiscoveryRegistry(context, nowMs = { 123L }).refresh()
        val descriptor = result.descriptors.single { it.packageName == packageName && it.type == PluginType.SETTING }

        assertEquals(PluginCompatibilityStatus.COMPATIBLE, descriptor.compatibility)
        assertEquals(PluginTrustLevel.UNTRUSTED, descriptor.trustLevel)
        assertEquals("$packageName.EditActivity", descriptor.editActivity?.className)
        assertEquals("$packageName.FireReceiver", descriptor.receiver?.className)
        assertTrue(descriptor.supportsConfiguration)
        assertFalse(descriptor.supportsOutputVariables)
        assertEquals(123L, result.refreshedAtMs)
    }

    @Test
    fun marksPluginWithEditActivityButNoReceiverAsUnavailableMetadata() = runBlocking {
        val packageName = "com.example.missingreceiver"
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(LocaleContract.ACTION_EDIT_SETTING),
            resolveInfo(packageName, "$packageName.EditActivity", "Incomplete plugin")
        )

        val result = PluginDiscoveryRegistry(context).refresh()
        val descriptor = result.descriptors.single { it.packageName == packageName && it.type == PluginType.SETTING }

        assertEquals(PluginCompatibilityStatus.MISSING_RECEIVER, descriptor.compatibility)
        assertTrue(descriptor.editActivity != null)
        assertEquals(null, descriptor.receiver)
    }

    private fun resolveInfo(packageName: String, className: String, label: String): ResolveInfo = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = className
            enabled = true
            exported = true
            nonLocalizedLabel = label
            applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                enabled = true
                flags = 0
            }
        }
    }
}
