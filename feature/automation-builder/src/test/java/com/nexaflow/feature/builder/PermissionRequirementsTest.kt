package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionRequirementsTest {
    @Test fun explicitOptInControlsPermissionsOnAndroid17() {
        for (url in listOf("https://8.8.8.8", "https://192.168.1.10", "https://{host}", "")) {
            assertEquals(emptyList<String>(), PermissionRequirements.runtimePermissionsForAction(
                ActionType.SYSTEM_HTTP_REQUEST, mapOf("url" to url), 37))
            assertEquals(listOf("android.permission.ACCESS_LOCAL_NETWORK"), PermissionRequirements.runtimePermissionsForAction(
                ActionType.SYSTEM_HTTP_REQUEST, mapOf("url" to url, "allowPrivateNetwork" to "true"), 37))
            assertEquals(emptyList<String>(), PermissionRequirements.runtimePermissionsForAction(
                ActionType.SYSTEM_HTTP_REQUEST, mapOf("url" to url, "allowPrivateNetwork" to "true"), 36))
        }
    }
}
