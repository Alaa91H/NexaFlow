package com.nexaflow.feature.builder

import android.Manifest
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionCatalogTest {

    @Test
    fun `network mode requires phone state read permission for per SIM capability discovery`() {
        assertEquals(
            listOf(Manifest.permission.READ_PHONE_STATE),
            PermissionCatalog.runtimePermissionsFor(ActionType.SYSTEM_NETWORK_MODE)
        )
    }

    @Test
    fun `network mode shows the phone state permission hint instead of a location hint`() {
        assertEquals(
            R.string.network_mode_phone_permission_hint,
            permissionHintTextForAction(ActionType.SYSTEM_NETWORK_MODE)
        )
    }

    @Test
    fun `private dns and charging feedback require elevated access`() {
        assertEquals(SpecialPermission.ELEVATED, PermissionCatalog.specialPermissionFor(ActionType.SYSTEM_PRIVATE_DNS))
        assertEquals(SpecialPermission.ELEVATED, PermissionCatalog.specialPermissionFor(ActionType.SYSTEM_CHARGING_FEEDBACK))
    }

    @Test
    fun `notification trigger requires listener access but not notification posting permission`() {
        assertEquals(emptyList<String>(), PermissionCatalog.runtimePermissionsFor(TriggerType.NOTIFICATION))
        assertEquals(SpecialPermission.NOTIFICATION_ACCESS, PermissionCatalog.specialPermissionFor(TriggerType.NOTIFICATION))
    }

    @Test
    fun `time trigger requires exact alarm access`() {
        assertEquals(SpecialPermission.EXACT_ALARM, PermissionCatalog.specialPermissionFor(TriggerType.TIME))
    }

    @Test
    fun `charging limit requires root because shell cannot write sysfs power nodes`() {
        assertEquals(SpecialPermission.ROOT, PermissionCatalog.specialPermissionFor(ActionType.SYSTEM_CHARGING_LIMIT))
    }

    @Test
    fun `bluetooth device trigger needs connect runtime but no save-time settings detour`() {
        // Regression: the old BLUETOOTH special forced the Bluetooth settings
        // screen on every save (its isGranted could never return true), even
        // with Bluetooth ON and BLUETOOTH_CONNECT granted.
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_CONNECT),
            PermissionCatalog.runtimePermissionsFor(TriggerType.BLUETOOTH_DEVICE)
        )
        assertNull(PermissionCatalog.specialPermissionFor(TriggerType.BLUETOOTH_DEVICE))
    }

    @Test
    fun `legacy device trigger with bluetooth event needs connect runtime`() {
        listOf("BLUETOOTH_CONNECTED", "BLUETOOTH_DISCONNECTED").forEach { event ->
            assertEquals(
                listOf(Manifest.permission.BLUETOOTH_CONNECT),
                PermissionCatalog.runtimePermissionsFor(
                    Trigger(type = TriggerType.DEVICE, config = mapOf("event" to event))
                )
            )
        }
        assertEquals(
            emptyList<String>(),
            PermissionCatalog.runtimePermissionsFor(
                Trigger(type = TriggerType.DEVICE, config = mapOf("event" to "SCREEN_ON"))
            )
        )
    }
}
