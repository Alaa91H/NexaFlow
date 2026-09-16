package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** P0.6 — conditional ACCESS_LOCAL_NETWORK requirement inference tests. */
@RunWith(JUnit4::class)
class PermissionRequirementsTest {

    private val lanPermission = "android.permission.ACCESS_LOCAL_NETWORK"

    @Test
    fun `public URL http action requires no LAN permission`() {
        val perms = PermissionRequirements.runtimePermissionsForAction(
            ActionType.SYSTEM_HTTP_REQUEST,
            mapOf("url" to "https://8.8.8.8/v1/api")
        )
        assertTrue(perms.isEmpty())
        assertFalse(
            PermissionRequirements.isConditionalLanRequirement(
                ActionType.SYSTEM_HTTP_REQUEST,
                mapOf("url" to "https://8.8.8.8/v1/api")
            )
        )
    }

    @Test
    fun `private URL http action requires the LAN permission`() {
        val perms = PermissionRequirements.runtimePermissionsForAction(
            ActionType.SYSTEM_HTTP_REQUEST,
            mapOf("url" to "http://192.168.1.10/status")
        )
        assertEquals(listOf(lanPermission), perms)
        assertFalse(
            PermissionRequirements.isConditionalLanRequirement(
                ActionType.SYSTEM_HTTP_REQUEST,
                mapOf("url" to "http://192.168.1.10/status")
            )
        )
    }

    @Test
    fun `templated URL keeps the permission but is flagged conditional`() {
        val config = mapOf("url" to "https://{server_ip}/status")
        val perms = PermissionRequirements.runtimePermissionsForAction(
            ActionType.SYSTEM_HTTP_REQUEST,
            config
        )
        assertEquals(listOf(lanPermission), perms)
        assertTrue(
            PermissionRequirements.isConditionalLanRequirement(
                ActionType.SYSTEM_HTTP_REQUEST,
                config
            )
        )
    }

    @Test
    fun `empty url defaults to the safe permission surface`() {
        val perms = PermissionRequirements.runtimePermissionsForAction(
            ActionType.SYSTEM_HTTP_REQUEST,
            emptyMap()
        )
        assertEquals(listOf(lanPermission), perms)
    }

    @Test
    fun `non-http actions fall through to the legacy catalog`() {
        // A type with no runtime permissions must stay empty.
        val perms = PermissionRequirements.runtimePermissionsForAction(
            ActionType.SYSTEM_WIFI,
            emptyMap()
        )
        assertEquals(PermissionCatalog.runtimePermissionsFor(ActionType.SYSTEM_WIFI), perms)
    }
}
