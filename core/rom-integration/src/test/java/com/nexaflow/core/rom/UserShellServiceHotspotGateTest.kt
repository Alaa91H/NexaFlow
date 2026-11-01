package com.nexaflow.core.rom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The public request-scoped tethering contract only exists on Android 16, and
 * the privileged UserService runs on whatever platform the user has. On older
 * releases the operation must be refused with the fixed internal-error envelope
 * instead of falling back to a raw shell command.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserShellServiceHotspotGateTest {

    @Test
    fun android15RefusesTheHotspotOperationBeforeTouchingThePublicContract() {
        val output = UserShellService().executeOperation("hotspot.set", "true", "", "")

        assertTrue(output.contains("requires Android 16 or newer"))
    }

    @Test
    fun invalidHotspotPayloadIsRejectedBeforeTheVersionGate() {
        val output = UserShellService().executeOperation("hotspot.set", "maybe", "", "")

        assertFalse(output.contains("requires Android 16 or newer"))
    }
}
