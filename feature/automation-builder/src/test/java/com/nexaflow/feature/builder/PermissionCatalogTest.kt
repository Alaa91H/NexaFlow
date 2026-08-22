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
}
