package com.nexaflow.feature.builder

import android.Manifest
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `legacy elevated command specs surface one shared elevated hint`() {
        assertEquals(
            SpecialPermission.ELEVATED,
            PermissionCatalog.specialPermissionFor(ActionType.SYSTEM_REBOOT)
        )
        assertEquals(
            SpecialPermission.ELEVATED,
            PermissionCatalog.specialPermissionFor(ActionType.SYSTEM_SHUTDOWN)
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
    fun `bluetooth device trigger requests the runtime dialog instead of the settings screen`() {
        // The Bluetooth settings screen cannot grant BLUETOOTH_CONNECT; the
        // requirement must travel through the runtime (system-dialog) channel
        // with its explain screen instead of a dead-end settings detour.
        assertNull(PermissionCatalog.specialPermissionFor(TriggerType.BLUETOOTH_DEVICE))
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_CONNECT),
            PermissionCatalog.runtimePermissionsFor(TriggerType.BLUETOOTH_DEVICE)
        )
    }

    @Test
    fun `merged device trigger configured for bluetooth requires the connect runtime permission`() {
        val trigger = Trigger(
            type = TriggerType.DEVICE,
            config = mapOf("event" to "BLUETOOTH_CONNECTED", "deviceName" to "Pixel Buds")
        )
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_CONNECT),
            PermissionCatalog.runtimePermissionsFor(trigger)
        )
        // Non-Bluetooth DEVICE events keep the previous (permission-free) contract.
        val headsetTrigger = Trigger(
            type = TriggerType.DEVICE,
            config = mapOf("event" to "HEADSET_CONNECTED")
        )
        assertTrue(PermissionCatalog.runtimePermissionsFor(headsetTrigger).isEmpty())
    }

    @Test
    fun `bluetooth trigger requirement aggregates as runtime-only`() {
        // Regression guard for the save flow: a Bluetooth task must produce a
        // runtime requirement (system dialog) and never a special one
        // (settings screen), otherwise saving strands the user in settings.
        val requirements = PermissionCatalog.requirementsFor(
            triggers = listOf(
                Trigger(TriggerType.BLUETOOTH_DEVICE, mapOf("deviceName" to "Car audio"))
            ),
            actions = emptyList()
        )
        assertEquals(1, requirements.size)
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), requirements.single().runtimePermissions)
        assertNull(requirements.single().special)
    }
}
