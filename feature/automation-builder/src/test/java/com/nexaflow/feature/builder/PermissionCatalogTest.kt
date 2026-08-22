package com.nexaflow.feature.builder

import android.Manifest
import com.nexaflow.domain.models.ActionType
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
    fun `private dns and charging feedback require elevated access`() {
        assertEquals(SpecialPermission.ELEVATED, PermissionCatalog.specialPermissionFor(ActionType.SYSTEM_PRIVATE_DNS))
        assertEquals(SpecialPermission.ELEVATED, PermissionCatalog.specialPermissionFor(ActionType.SYSTEM_CHARGING_FEEDBACK))
    }

    @Test
    fun `charging limit requires root because shell cannot write sysfs power nodes`() {
        assertEquals(SpecialPermission.ROOT, PermissionCatalog.specialPermissionFor(ActionType.SYSTEM_CHARGING_LIMIT))
    }
}
