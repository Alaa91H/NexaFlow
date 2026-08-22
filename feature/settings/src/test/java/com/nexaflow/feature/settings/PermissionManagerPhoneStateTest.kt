package com.nexaflow.feature.settings

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PermissionManagerPhoneStateTest {

    @Test
    fun phoneStateEntryRequestsTheActualAndroidRuntimePermission() {
        val entry = buildPermissionEntries().singleOrNull { it.key == "phone_state" }

        assertNotNull("Permission Manager must expose phone state for network mode", entry)
        assertEquals(listOf(Manifest.permission.READ_PHONE_STATE), entry?.runtimePermissions)
        assertEquals(R.string.phone_state_permission, entry?.titleRes)
        assertEquals(R.string.phone_state_permission_sub, entry?.subtitleRes)
    }

    @Test
    fun managerExposesEveryDedicatedRuntimePermissionRow() {
        val entries = buildPermissionEntries().associateBy { entry -> entry.key }

        assertEquals(listOf(Manifest.permission.CAMERA), entries["camera"]?.runtimePermissions)
        assertEquals(
            listOf(Manifest.permission.ACTIVITY_RECOGNITION),
            entries["activity_recognition"]?.runtimePermissions
        )
        assertEquals(
            if (Build.VERSION.SDK_INT >= 37) listOf(Manifest.permission.ACCESS_LOCAL_NETWORK) else emptyList(),
            entries["local_network"]?.runtimePermissions
        )
        assertEquals(listOf(Manifest.permission.READ_CALENDAR), entries["calendar"]?.runtimePermissions)
    }
}
