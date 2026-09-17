package com.nexaflow.feature.builder
import com.nexaflow.domain.models.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class HttpPermissionWiringTest {
    @Test fun aggregationUsesConfigInsteadOfActionType() {
        val public = Action(ActionType.SYSTEM_HTTP_REQUEST, mapOf("url" to "https://example.com"))
        assertTrue(PermissionCatalog.allRuntimePermissions(emptyList(), listOf(public)).isEmpty())
        val private = public.copy(config = public.config + ("allowPrivateNetwork" to "true"))
        assertEquals(listOf("android.permission.ACCESS_LOCAL_NETWORK"),
            PermissionCatalog.allRuntimePermissions(emptyList(), listOf(private)).toList())
        assertEquals(listOf("android.permission.ACCESS_LOCAL_NETWORK"),
            PermissionCatalog.allRuntimePermissions(emptyList(), listOf(public), listOf(private)).toList())
    }
}
