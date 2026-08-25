package com.nexaflow.feature.builder

import android.Manifest
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
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
}
