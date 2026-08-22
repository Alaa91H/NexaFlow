package com.nexaflow.feature.settings

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PermissionManagerPhoneStateTest {

    @Test
    fun phoneStateEntryRequestsTheActualAndroidRuntimePermission() {
        val entry = buildPermissionEntries().singleOrNull { it.key == "phone_state" }

        assertNotNull("Permission Manager must expose phone state for network mode", entry)
        assertEquals(Manifest.permission.READ_PHONE_STATE, entry?.runtimePermission)
        assertEquals(R.string.phone_state_permission, entry?.titleRes)
        assertEquals(R.string.phone_state_permission_sub, entry?.subtitleRes)
    }
}
