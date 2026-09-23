package com.nexaflow.app.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.wearprotocol.WearCapability
import com.nexaflow.core.wearprotocol.WearCapabilitySnapshot
import com.nexaflow.core.wearprotocol.WearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WearDeviceRegistryTest {

    private lateinit var scope: CoroutineScope
    private lateinit var registry: WearDeviceRegistry

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        registry = WearDeviceRegistry(context, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `same install identity updates transport node without duplication`() {
        val first = WearCapabilitySnapshot(
            watchInstallId = "watch-stable-id",
            capabilities = setOf(WearCapability.PROTOCOL_V1),
            deviceName = "Watch",
            updatedAtEpochMs = 1_000L,
        )
        val second = first.copy(
            deviceName = "Watch re-paired",
            updatedAtEpochMs = 2_000L,
        )

        assertTrue(registry.acceptSnapshot("node-old", first))
        assertTrue(registry.acceptSnapshot("node-new", second))

        assertEquals(1, registry.devices.value.size)
        val device = registry.findByInstallId("watch-stable-id")
        assertNotNull(device)
        assertEquals("node-new", device?.nodeId)
        assertEquals("Watch re-paired", device?.displayName)
        assertEquals(2_000L, device?.lastSeenEpochMs)
    }

    @Test
    fun `unsupported protocol advertisement is rejected`() {
        val unsupported = WearCapabilitySnapshot(
            watchInstallId = "future-watch",
            protocolVersion = WearProtocol.CURRENT_VERSION + 1,
            capabilities = emptySet(),
            updatedAtEpochMs = 3_000L,
        )

        assertFalse(registry.acceptSnapshot("node-future", unsupported))
        assertTrue(registry.devices.value.isEmpty())
    }
}
